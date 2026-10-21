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
package org.springblade.system.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springblade.core.tool.api.R;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

/**
 * 客户端管理按已验证主体限流；Redis 故障返回503，原角色和数据权限继续执行。
 *
 * @author SpringBlade
 */
public final class ClientManagementRateFilter extends OncePerRequestFilter {
	private static final DefaultRedisScript<Long> RATE=new DefaultRedisScript<>(
		"local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],ARGV[1]); end; return n",Long.class);
	private final StringRedisTemplate redis;
	private final ObjectMapper mapper;
	private final int maxRequests;
	public ClientManagementRateFilter(StringRedisTemplate redis,ObjectMapper mapper,int maxRequests) {
		if (maxRequests<=0) throw new IllegalArgumentException("客户端管理限流必须为正数");
		this.redis=redis;this.mapper=mapper;this.maxRequests=maxRequests;
	}
	@Override
	protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws IOException,ServletException {
		var principal=SecurityContextHolder.getContext().getAuthentication();
		if (principal==null || !principal.isAuthenticated()) { chain.doFilter(request,response);return; }
		Long count;
		try { count=redis.execute(RATE,List.of("blade:system:client-management:"+principal.getName()),"60"); }
		catch (RuntimeException error) { failure(response,503);return; }
		if (count==null) { failure(response,503);return; }
		if (count>maxRequests) { failure(response,429);return; }
		chain.doFilter(request,response);
	}
	private void failure(HttpServletResponse response,int status) throws IOException {
		response.setStatus(status);response.setContentType("application/json");response.setHeader("Cache-Control","no-store");
		mapper.writeValue(response.getOutputStream(),R.fail(status,status==429 ? "操作过于频繁" : "客户端管理暂不可用"));
	}
}
