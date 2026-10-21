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

import jakarta.servlet.http.HttpServletRequest;
import org.springblade.authorization.props.AuthorizationProperties;
import org.springblade.core.tool.api.R;
import org.springblade.system.user.dto.UserAuthenticationCommand;
import org.springblade.system.user.feign.IUserAuthenticationClient;
import org.springblade.system.user.vo.UserAuthenticationVO;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 校验系统用户密码，使用稳定用户ID作为OAuth2主体。
 *
 * @author SpringBlade
 */
@Component
public class SystemUserAuthenticationProvider implements AuthenticationProvider {
	private static final DefaultRedisScript<Long> FAILURE = new DefaultRedisScript<>(
		"local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],ARGV[1]); end; return n", Long.class);
	private final IUserAuthenticationClient users;
	private final AuthorizationProperties properties;
	private final ObjectProvider<HttpServletRequest> requests;
	private final StringRedisTemplate redis;
	private final MeterRegistry metrics;
	public SystemUserAuthenticationProvider(IUserAuthenticationClient users, AuthorizationProperties properties,
		ObjectProvider<HttpServletRequest> requests, StringRedisTemplate redis, MeterRegistry metrics) {
		this.users = users;
		this.properties = properties;
		this.requests = requests;
		this.redis = redis;
		this.metrics = metrics;
	}
	@Override
	public Authentication authenticate(Authentication authentication) {
		HttpServletRequest request = requests.getObject();
		String tenant = request.getParameter("tenantId");
		if (tenant == null || tenant.isBlank()) {
			tenant = properties.getDefaultTenantId();
		}
		String account = authentication.getName().trim();
		if (tenant == null || !tenant.matches("[0-9]{6,12}") || account.isEmpty() || account.length() > 64
			|| authentication.getCredentials() == null) throw new BadCredentialsException("账号或密码错误");
		String accountKey = "blade:authorization:login:account:" + tenant + ":" + account.toLowerCase(Locale.ROOT);
		String ipKey = "blade:authorization:login:ip:" + request.getRemoteAddr();
		try {
			if (locked(accountKey) || locked(ipKey)) {
				throw new LockedException("登录失败次数过多，请稍后重试");
			}
			UserAuthenticationCommand command = new UserAuthenticationCommand();
			command.setTenantId(tenant);
			command.setAccount(account);
			command.setPassword(authentication.getCredentials().toString());
			long dependencyStart = System.nanoTime();
			R<UserAuthenticationVO> result;
			try { result = users.authenticate(command); }
			finally { metrics.timer("blade.authorization.dependency.duration", "operation", "authenticate")
				.record(System.nanoTime() - dependencyStart, java.util.concurrent.TimeUnit.NANOSECONDS); }
			if (result != null && result.getCode() == 401) {
				Long accountFailures = redis.execute(FAILURE, List.of(accountKey), String.valueOf(properties.getLoginLockDuration().toSeconds()));
				Long ipFailures = redis.execute(FAILURE, List.of(ipKey), String.valueOf(properties.getLoginLockDuration().toSeconds()));
				if (accountFailures == null || ipFailures == null) throw new AuthenticationServiceException("登录保护不可用");
				throw new BadCredentialsException("账号或密码错误");
			}
			if (result == null || !result.isSuccess() || result.getData() == null) {
				throw new AuthenticationServiceException("用户认证服务不可用");
			}
			UserAuthenticationVO user = result.getData();
			if (!tenant.equals(user.getTenantId()) || user.getUserId() == null) {
				throw new AuthenticationServiceException("用户认证结果无效");
			}
			List<SimpleGrantedAuthority> roles = new ArrayList<>();
			if (user.getRoles() != null) user.getRoles().stream().filter(value -> value != null && !value.isBlank())
				.forEach(value -> roles.add(new SimpleGrantedAuthority("ROLE_" + value)));
			User principal = new User(user.getUserId(), "", roles);
			List<GrantedAuthority> authorities = new ArrayList<>(roles);
			// Spring Security 7 从认证因子的签发时间生成 OIDC auth_time。
			authorities.add(FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY));
			UsernamePasswordAuthenticationToken resultToken = UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
			Map<String, Object> details = new HashMap<>();
			details.put("tenantId", user.getTenantId());
			details.put("account", user.getAccount());
			details.put("name", user.getName() == null ? user.getAccount() : user.getName());
			resultToken.setDetails(details);
			redis.delete(accountKey);
			metrics.counter("blade.authorization.login", "outcome", "success").increment();
			return resultToken;
		} catch (org.springframework.security.core.AuthenticationException exception) {
			metrics.counter("blade.authorization.login", "outcome", exception instanceof AuthenticationServiceException ? "dependency" : "denied").increment();
			throw exception;
		} catch (RuntimeException exception) {
			metrics.counter("blade.authorization.login", "outcome", "dependency").increment();
			throw new AuthenticationServiceException("认证依赖不可用");
		}
	}
	private boolean locked(String key) {
		String count = redis.opsForValue().get(key);
		return count != null && Long.parseLong(count) >= properties.getLoginMaxAttempts();
	}
	@Override
	public boolean supports(Class<?> authentication) {
		return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
	}
}
