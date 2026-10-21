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

import org.springblade.authorization.client.SystemRegisteredClientRepository;
import org.springblade.authorization.props.AuthorizationProperties;
import org.springblade.core.tool.api.R;
import org.springblade.system.user.feign.IUserAuthenticationClient;
import org.springblade.system.user.vo.UserAuthenticationVO;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;
import java.util.ArrayList;

/**
 * 按有效系统身份生成最小JWT声明，刷新时重新检查用户和租户状态。
 *
 * @author SpringBlade
 */
@Component
public class AuthorizationTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {
	private final SystemRegisteredClientRepository clients;
	private final IUserAuthenticationClient users;
	private final AuthorizationProperties properties;
	public AuthorizationTokenCustomizer(SystemRegisteredClientRepository clients, IUserAuthenticationClient users,
		AuthorizationProperties properties) {
		this.clients = clients;
		this.users = users;
		this.properties = properties;
	}
	@Override
	public void customize(JwtEncodingContext context) {
		if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
			var client = clients.registration(context.getRegisteredClient().getId());
			if (client == null) {
				throw failure(OAuth2ErrorCodes.INVALID_CLIENT);
			}
			if (!SystemRegisteredClientRepository.values(client.getScopes()).containsAll(context.getAuthorizedScopes())) {
				throw failure(OAuth2ErrorCodes.INVALID_SCOPE);
			}
			context.getClaims().audience(new ArrayList<>(SystemRegisteredClientRepository.values(client.getAudiences())));
		}
		if (AuthorizationGrantType.CLIENT_CREDENTIALS.equals(context.getAuthorizationGrantType())) {
			return;
		}
		R<UserAuthenticationVO> result;
		try {
			result = users.user(context.getPrincipal().getName());
		} catch (RuntimeException exception) {
			throw failure(OAuth2ErrorCodes.SERVER_ERROR);
		}
		if (result != null && result.getCode() == 401) {
			throw failure(OAuth2ErrorCodes.INVALID_GRANT);
		}
		if (result == null || !result.isSuccess() || result.getData() == null) {
			throw failure(OAuth2ErrorCodes.SERVER_ERROR);
		}
		UserAuthenticationVO user = result.getData();
		if (!context.getPrincipal().getName().equals(user.getUserId()) || user.getTenantId() == null || user.getTenantId().isBlank()) {
			throw failure(OAuth2ErrorCodes.INVALID_GRANT);
		}
		Object details = context.getPrincipal().getDetails();
		if (!(details instanceof java.util.Map<?, ?> identity)
			|| !user.getTenantId().equals(identity.get("tenantId"))) {
			throw failure(OAuth2ErrorCodes.INVALID_GRANT);
		}
		if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
			context.getClaims().claim("user_id", user.getUserId()).claim("tenant_id", user.getTenantId());
		}
		if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType()) && context.getAuthorizedScopes().contains("service.read")) {
			context.getClaims().claim("account", user.getAccount());
			context.getClaims().claim("role_id", user.getRoleId() == null ? "" : user.getRoleId())
				.claim("dept_id", user.getDeptId() == null ? "" : user.getDeptId());
			context.getClaims().claim("roles", user.getRoles() == null ? new ArrayList<>() : new ArrayList<>(user.getRoles()));
		}
		if (context.getAuthorizedScopes().contains(OidcScopes.PROFILE)) {
			context.getClaims().claim("preferred_username", user.getAccount());
			if (user.getName() != null) {
				context.getClaims().claim("name", user.getName());
			}
		}
	}
	private OAuth2AuthenticationException failure(String code) {
		return new OAuth2AuthenticationException(new OAuth2Error(code));
	}
}
