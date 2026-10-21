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
package org.springblade.common.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springblade.core.secure.BladeUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 把已经由 Resource Server 验证的 JWT 声明桥接到现有 BladeUser 请求属性。
 *
 * @author SpringBlade
 */
public class TokenBladeUserFilter extends OncePerRequestFilter {
	public static final String BLADE_USER_REQUEST_ATTRIBUTE = "_BLADE_USER_REQUEST_ATTR_";

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
		FilterChain filterChain) throws ServletException, IOException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication instanceof JwtAuthenticationToken token) {
			BladeUser user = map(token.getToken());
			if (user != null) {
				request.setAttribute(BLADE_USER_REQUEST_ATTRIBUTE, user);
			}
		}
		filterChain.doFilter(request, response);
	}

	private BladeUser map(Jwt jwt) {
		Long userId = number(jwt.getClaim("user_id"));
		// 服务客户端的 sub 也可能是数字，不能据此伪造业务用户。
		if (userId == null || !userId.toString().equals(jwt.getSubject())
			|| jwt.getClaimAsString("tenant_id") == null || jwt.getClaimAsString("tenant_id").isBlank()) {
			return null;
		}
		BladeUser user = new BladeUser();
		user.setUserId(userId);
		user.setClientId(first(jwt.getClaimAsString("client_id"), jwt.getClaimAsString("azp")));
		user.setTenantId(jwt.getClaimAsString("tenant_id"));
		user.setDeptId(jwt.getClaimAsString("dept_id"));
		user.setAccount(first(jwt.getClaimAsString("account"), jwt.getClaimAsString("preferred_username")));
		user.setUserName(first(jwt.getClaimAsString("user_name"), jwt.getClaimAsString("name")));
		user.setRoleId(jwt.getClaimAsString("role_id"));
		user.setRoleName(roles(jwt.getClaim("roles")));
		return user;
	}

	private Long number(Object value) {
		if (value == null) {
			return null;
		}
		try {
			String text = String.valueOf(value);
			return text.matches("[1-9][0-9]{0,18}") ? Long.valueOf(text) : null;
		} catch (NumberFormatException exception) {
			return null;
		}
	}

	private String first(String first, String second) {
		return first == null || first.isBlank() ? second : first;
	}

	private String roles(Object value) {
		if (value instanceof Collection<?> collection) {
			return collection.stream().filter(Objects::nonNull).map(String::valueOf)
				.filter(item -> !item.isBlank()).collect(Collectors.joining(","));
		}
		return value == null ? "" : String.valueOf(value);
	}
}
