/*
 * Copyright 2026 SpringBlade contributors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * https://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springblade.authorization.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springblade.authorization.client.SystemRegisteredClientRepository;
import org.springblade.authorization.props.AuthorizationProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 协议端点原子限流与固定登记目标校验，依赖故障拒绝继续签发。
 *
 * @author SpringBlade
 */
public final class AuthorizationProtocolGuardFilter extends OncePerRequestFilter {
	private static final Set<String> PATHS=Set.of("/oauth2/authorize","/oauth2/token","/oauth2/revoke","/oauth2/introspect");
	private static final DefaultRedisScript<Long> RATE=new DefaultRedisScript<>(
		"local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],ARGV[1]); end; return n",Long.class);
	private final StringRedisTemplate redis;
	private final AuthorizationProperties properties;
	private final SystemRegisteredClientRepository clients;
	private final ObjectMapper mapper;
	private final MeterRegistry metrics;
	public AuthorizationProtocolGuardFilter(StringRedisTemplate redis,AuthorizationProperties properties,
		SystemRegisteredClientRepository clients,ObjectMapper mapper,MeterRegistry metrics) {
		this.redis=redis;this.properties=properties;this.clients=clients;this.mapper=mapper;this.metrics=metrics;
	}
	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) { return !PATHS.contains(request.getServletPath()); }
	@Override
	protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
		try {
			Long n=redis.execute(RATE,List.of("blade:authorization:protocol:"+request.getServletPath()+":"+request.getRemoteAddr()),
				String.valueOf(properties.getProtocolRateWindow().toSeconds()));
			if (n==null) { failure(response,503,"temporarily_unavailable");return; }
			if (n>properties.getProtocolMaxRequests()) { failure(response,429,"temporarily_unavailable");return; }
			Set<String> targets=new HashSet<>();
			for (String parameter : List.of("resource","audience")) {
				String[] values=request.getParameterValues(parameter);
				if (values!=null) targets.addAll(Arrays.asList(values));
			}
			if (!targets.isEmpty()) {
				String clientId=request.getParameter("client_id");
				var authentication=SecurityContextHolder.getContext().getAuthentication();
				if (authentication instanceof OAuth2ClientAuthenticationToken client && client.isAuthenticated()) clientId=client.getName();
				var registered=clientId==null ? null : clients.findByClientId(clientId);
				var registration=registered==null ? null : clients.registration(registered.getId());
				if (registration==null || !targets.equals(SystemRegisteredClientRepository.values(registration.getAudiences()))) {
					failure(response,400,"invalid_target");return;
				}
			}
		} catch (RuntimeException error) { failure(response,503,"temporarily_unavailable");return; }
		long started=System.nanoTime();
		try { chain.doFilter(request,response); }
		finally {
			String endpoint=request.getServletPath().substring("/oauth2/".length());
			metrics.timer("blade.authorization.protocol.duration","endpoint",endpoint).record(System.nanoTime()-started,java.util.concurrent.TimeUnit.NANOSECONDS);
			metrics.counter("blade.authorization.protocol.requests","endpoint",endpoint,"status",String.valueOf(response.getStatus())).increment();
		}
	}
	private void failure(HttpServletResponse response,int status,String error) throws IOException {
		metrics.counter("blade.authorization.protocol.rejections","error",error).increment();
		response.setStatus(status);response.setHeader("Cache-Control","no-store");response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		mapper.writeValue(response.getOutputStream(),Map.of("error",error));
	}
}
