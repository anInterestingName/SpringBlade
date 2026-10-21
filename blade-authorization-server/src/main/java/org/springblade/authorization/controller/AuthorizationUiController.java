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
package org.springblade.authorization.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springblade.authorization.props.AuthorizationProperties;
import org.springblade.core.tool.api.R;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * 前端认证页面的只读上下文，不返回密码、客户端密钥或 OAuth 令牌。
 * 登录提交和授权确认仍由 Spring Security 的原有过滤器处理。
 *
 * @author BladeX
 */
@RestController
@Tag(name = "认证页面上下文")
public class AuthorizationUiController {

	private final HttpSessionRequestCache requestCache;
	private final OAuth2AuthorizationService authorizations;
	private final RegisteredClientRepository clients;
	private final AuthorizationProperties properties;

	public AuthorizationUiController(HttpSessionRequestCache requestCache,
		OAuth2AuthorizationService authorizations, RegisteredClientRepository clients,
		AuthorizationProperties properties) {
		this.requestCache = requestCache;
		this.authorizations = authorizations;
		this.clients = clients;
		this.properties = properties;
	}

	@GetMapping({"/", "/login"})
	@Operation(summary = "打开前端账号密码页面")
	public ResponseEntity<Void> loginPage() {
		return ResponseEntity.status(HttpStatus.FOUND).cacheControl(CacheControl.noStore())
			.location(URI.create(properties.frontendUri("/auth/login"))).build();
	}

	@GetMapping("/ui/login-context")
	@Operation(summary = "获取登录前授权状态及 CSRF 校验值")
	public ResponseEntity<R<LoginContext>> loginContext(HttpServletRequest request, HttpServletResponse response,
		Authentication authentication) {
		CsrfToken csrf = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
		boolean authenticated = authentication != null && authentication.isAuthenticated()
			&& !(authentication instanceof AnonymousAuthenticationToken);
		if (csrf == null) {
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).cacheControl(CacheControl.noStore())
				.body(R.<LoginContext>fail(503, "CSRF 配置不可用"));
		}
		LoginContext context = new LoginContext(requestCache.getRequest(request, response) != null,
			authenticated, properties.getDefaultTenantId(), new CsrfContext(csrf.getParameterName(), csrf.getHeaderName(), csrf.getToken()));
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(R.data(context));
	}

	@GetMapping("/ui/consent")
	@Operation(summary = "获取当前待确认授权的客户端与权限")
	public ResponseEntity<R<ConsentContext>> consent(
		@RequestParam(name = "client_id", defaultValue = "") String clientId,
		@RequestParam(defaultValue = "") String state, Authentication authentication, HttpServletRequest request) {
		if (state.isBlank() || clientId.isBlank() || state.length() > 512 || clientId.length() > 200) {
			return invalidConsent();
		}
		OAuth2Authorization authorization = authorizations.findByToken(state,
			new OAuth2TokenType(OAuth2ParameterNames.STATE));
		RegisteredClient client = clients.findByClientId(clientId);
		if (authorization == null || client == null || authentication == null
			|| !authentication.getName().equals(authorization.getPrincipalName())
			|| !client.getId().equals(authorization.getRegisteredClientId())) {
			return invalidConsent();
		}
		OAuth2AuthorizationRequest pending = authorization.getAttribute(OAuth2AuthorizationRequest.class.getName());
		if (pending == null || !client.getScopes().containsAll(pending.getScopes())) {
			return invalidConsent();
		}
		List<ScopeContext> scopes = pending.getScopes().stream().sorted()
			.map(scope -> new ScopeContext(scope, scopeDescription(scope), OidcScopes.OPENID.equals(scope))).toList();
		CsrfToken csrf = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
		if (csrf == null) {
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).cacheControl(CacheControl.noStore())
				.body(R.fail(503, "CSRF 配置不可用"));
		}
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
			.body(R.data(new ConsentContext(client.getClientId(), client.getClientName(), displayAccount(authentication), scopes,
				new CsrfContext(csrf.getParameterName(), csrf.getHeaderName(), csrf.getToken()))));
	}

	private ResponseEntity<R<ConsentContext>> invalidConsent() {
		return ResponseEntity.badRequest().cacheControl(CacheControl.noStore())
			.body(R.fail(400, "授权请求无效或已处理，请重新发起登录"));
	}

	private String displayAccount(Authentication authentication) {
		if (authentication.getDetails() instanceof java.util.Map<?, ?> details && details.get("account") instanceof String account) {
			return account;
		}
		return authentication.getName();
	}

	private String scopeDescription(String scope) {
		return switch (scope) {
			case "openid" -> "确认登录身份";
			case "profile" -> "读取基本用户信息";
			case "demo.read" -> "读取验证服务的数据";
			default -> scope;
		};
	}

	public record CsrfContext(String parameterName, String headerName, String token) { }

	public record LoginContext(boolean authorizationPending, boolean authenticated, String defaultTenantId, CsrfContext csrf) { }

	public record ScopeContext(String code, String description, boolean autoApproved) { }

	public record ConsentContext(String clientId, String clientName, String username, List<ScopeContext> scopes, CsrfContext csrf) { }
}
