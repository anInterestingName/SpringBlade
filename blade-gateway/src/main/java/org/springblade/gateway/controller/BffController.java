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
package org.springblade.gateway.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springblade.core.tool.api.R;
import org.springblade.gateway.config.BffConfiguration;
import org.springblade.gateway.props.BffProperties;
import org.springblade.gateway.security.SessionSerialAuthorizedClientManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Map;

/**
 * BFF 登录状态与 CSRF 入口，仅返回身份和令牌到期时间，不输出令牌值。
 *
 * @author BladeX
 */
@RestController
@Tag(name = "BFF 会话")
public class BffController {

	private final SessionSerialAuthorizedClientManager clientManager;
	private final BffProperties properties;

	public BffController(SessionSerialAuthorizedClientManager clientManager, BffProperties properties) {
		this.clientManager = clientManager;
		this.properties = properties;
	}

	@GetMapping("/")
	@Operation(summary = "打开前端 BFF 验证页面")
	public ResponseEntity<Void> index() {
		return ResponseEntity.status(HttpStatus.FOUND).cacheControl(CacheControl.noStore()).location(URI.create(properties.frontendUri())).build();
	}

	@GetMapping("/bff/me")
	@Operation(summary = "查询当前 BFF 登录身份")
	public Mono<R<Map<String, Object>>> me(Authentication authentication, ServerWebExchange exchange) {
		exchange.getResponse().getHeaders().setCacheControl(CacheControl.noStore());
		if (authentication == null || !(authentication.getPrincipal() instanceof OidcUser user)) {
			exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
			return Mono.just(R.fail(40101, "请先登录"));
		}
		return clientManager.authorize(OAuth2AuthorizeRequest.withClientRegistrationId(BffConfiguration.REGISTRATION_ID)
				.principal(authentication).attribute(ServerWebExchange.class.getName(), exchange).build())
			.map(client -> R.<Map<String, Object>>data(Map.of(
				"subject", user.getSubject(), "issuer", user.getIssuer().toString(),
				"clientId", client.getClientRegistration().getClientId(),
				"scope", client.getAccessToken().getScopes(),
				"accessTokenExpiresAt", client.getAccessToken().getExpiresAt().toString())))
			.switchIfEmpty(expired(exchange))
			.onErrorResume(OAuth2AuthorizationException.class, error -> expired(exchange));

	}

	private Mono<R<Map<String, Object>>> expired(ServerWebExchange exchange) {
		return Mono.defer(() -> clientManager.invalidate(exchange)
			.then(Mono.fromSupplier(() -> {
				exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
				return R.fail(40102, "登录已失效，请重新登录");
			})));
	}

	@GetMapping("/bff/csrf")
	@Operation(summary = "获取当前会话的 CSRF 校验值")
	public Mono<R<Map<String, String>>> csrf(ServerWebExchange exchange) {
		exchange.getResponse().getHeaders().setCacheControl(CacheControl.noStore());
		Mono<CsrfToken> token = exchange.getAttribute(CsrfToken.class.getName());
		return token == null ? Mono.just(R.fail("CSRF 配置不可用"))
			: token.map(value -> R.data(Map.of("headerName", value.getHeaderName(), "token", value.getToken())));
	}
}
