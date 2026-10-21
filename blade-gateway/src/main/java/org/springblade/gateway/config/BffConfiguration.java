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
package org.springblade.gateway.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springblade.core.tool.api.R;
import org.springblade.gateway.props.BffProperties;
import org.springblade.gateway.security.BffSessionAuthorizedClientRepository;
import org.springblade.gateway.security.SessionSerialAuthorizedClientManager;
import org.springframework.session.ReactiveSessionRepository;
import org.springframework.session.Session;
import org.springframework.core.env.Environment;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.TokenRelayGatewayFilterFactory;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.server.DefaultServerOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.server.WebSessionServerOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationFailureHandler;
import org.springframework.security.web.server.authentication.logout.SecurityContextServerLogoutHandler;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.security.web.server.csrf.CsrfWebFilter;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;
import org.springframework.web.server.session.CookieWebSessionIdResolver;
import org.springframework.web.server.session.WebSessionIdResolver;
import reactor.core.publisher.Mono;

import java.net.URI;

/**
 * 网关BFF：Redis会话登录、服务端保存令牌，并向资源服务转发Access Token。
 *
 * @author BladeX
 */
@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
@EnableConfigurationProperties(BffProperties.class)
public class BffConfiguration {

	public static final String REGISTRATION_ID = "blade";

	@Bean
	public ReactiveClientRegistrationRepository bffClients(BffProperties properties, Environment environment) {
		Assert.hasText(properties.issuer(), "认证服务 issuer 不能为空");
		Assert.hasText(properties.clientId(), "BFF 客户端编号不能为空");
		Assert.hasText(properties.clientSecret(), "请配置 AUTHORIZATION_CLIENT_SECRET 提供BFF客户端密钥");
		Assert.hasText(properties.redirectUri(), "BFF 回调地址不能为空");
		Assert.hasText(properties.demoUrl(), "demo 地址不能为空");
		Assert.hasText(properties.frontendUri(), "BFF 前端回跳地址不能为空");
		URI frontend = URI.create(properties.frontendUri());
		Assert.isTrue(("http".equals(frontend.getScheme()) || "https".equals(frontend.getScheme()))
			&& frontend.getHost() != null && frontend.getUserInfo() == null
			&& frontend.getQuery() == null && frontend.getFragment() == null,
			"BFF 前端地址必须是无凭据、查询参数和片段的 HTTP(S) 地址");
		if (environment.matchesProfiles("prod")) {
			Assert.isTrue(properties.cookieSecure(), "生产 BFF Cookie 必须启用 Secure");
			URI issuerUri = URI.create(properties.issuer());
			URI callback = URI.create(properties.redirectUri());
			Assert.isTrue("https".equals(issuerUri.getScheme()) && "https".equals(frontend.getScheme())
				&& issuerUri.getAuthority().equals(frontend.getAuthority()) && issuerUri.getAuthority().equals(callback.getAuthority())
				&& issuerUri.getPath().isEmpty() && issuerUri.getQuery() == null && issuerUri.getFragment() == null
				&& issuerUri.getUserInfo() == null && callback.getUserInfo() == null && callback.getQuery() == null
				&& callback.getFragment() == null && "https".equals(callback.getScheme()) && "/login/oauth2/code/blade".equals(callback.getPath()),
				"生产 issuer、前端与 BFF 回调必须同源 HTTPS");
		}
		String issuer = properties.issuer();
		String endpointBase = issuer.replaceAll("/+$", "");
		// 显式提供协议地址，启动时不通过 issuer 发现端点连接认证服务。
		ClientRegistration client = ClientRegistration.withRegistrationId(REGISTRATION_ID)
			.clientId(properties.clientId()).clientSecret(properties.clientSecret())
			.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
			.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
			.redirectUri(properties.redirectUri()).scope("openid", "profile", "demo.read", "demo.write", "service.read")
			.authorizationUri(endpointBase + "/oauth2/authorize").tokenUri(endpointBase + "/oauth2/token")
			.jwkSetUri(endpointBase + "/oauth2/jwks").userInfoUri(endpointBase + "/userinfo")
			.userNameAttributeName(IdTokenClaimNames.SUB).issuerUri(issuer)
			.clientName("SpringBlade BFF").build();
		return new InMemoryReactiveClientRegistrationRepository(client);
	}

	@Bean
	public ServerOAuth2AuthorizedClientRepository bffAuthorizedClients(ReactiveSessionRepository<? extends Session> sessions) {
		// 令牌随当前浏览器会话保存，避免同名用户的多个会话相互覆盖。
		return new BffSessionAuthorizedClientRepository(sessions);
	}

	@Bean
	public SessionSerialAuthorizedClientManager bffClientManager(
		ReactiveClientRegistrationRepository clients, ServerOAuth2AuthorizedClientRepository authorizedClients) {
		DefaultReactiveOAuth2AuthorizedClientManager manager =
			new DefaultReactiveOAuth2AuthorizedClientManager(clients, authorizedClients);
		manager.setAuthorizedClientProvider(ReactiveOAuth2AuthorizedClientProviderBuilder.builder()
			.authorizationCode().refreshToken().build());
		// 将请求显式写入授权上下文，避免框架默认 mapper 在 Reactor 回退路径保存 null exchange。
		manager.setContextAttributesMapper(request -> Mono.deferContextual(context -> {
			ServerWebExchange exchange = request.getAttribute(ServerWebExchange.class.getName());
			if (exchange == null) {
				exchange = context.getOrDefault(ServerWebExchange.class, null);
			}
			if (exchange == null) {
				return Mono.error(new IllegalStateException("BFF 授权上下文缺少当前请求"));
			}
			java.util.Map<String, Object> attributes = new java.util.HashMap<>(request.getAttributes());
			attributes.put(ServerWebExchange.class.getName(), exchange);
			return Mono.just(attributes);
		}));
		return new SessionSerialAuthorizedClientManager(manager);
	}

	@Bean(name = "forwardedHeaderTransformer")
	public org.springframework.web.server.adapter.ForwardedHeaderTransformer bffForwardedHeaders(BffProperties properties) {
		java.util.regex.Pattern trusted = java.util.regex.Pattern.compile(properties.trustedProxyPattern());
		var strip = new org.springframework.web.server.adapter.ForwardedHeaderTransformer();
		strip.setRemoveOnly(true);
		return new org.springframework.web.server.adapter.ForwardedHeaderTransformer() {
			@Override
			public org.springframework.http.server.reactive.ServerHttpRequest apply(org.springframework.http.server.reactive.ServerHttpRequest request) {
				var remote = request.getRemoteAddress();
				return remote != null && remote.getAddress() != null && trusted.matcher(remote.getAddress().getHostAddress()).matches()
					? super.apply(request) : strip.apply(request);
			}
		};
	}

	@Bean
	public WebSessionIdResolver bffSessionIdResolver(BffProperties properties) {
		CookieWebSessionIdResolver resolver = new CookieWebSessionIdResolver();
		resolver.setCookieName("BFF_SESSION");
		resolver.addCookieInitializer(cookie -> cookie.path("/").httpOnly(true).sameSite("Lax").secure(properties.cookieSecure()));
		return resolver;
	}

	@Bean
	public SecurityWebFilterChain bffSecurity(ServerHttpSecurity http,
		ReactiveClientRegistrationRepository clients, ServerOAuth2AuthorizedClientRepository authorizedClients,
		ObjectMapper mapper, BffProperties properties, SessionSerialAuthorizedClientManager clientManager) {
		Assert.hasText(properties.frontendUri(), "BFF 前端回跳地址不能为空");
		RedirectServerAuthenticationFailureHandler failureHandler =
			new RedirectServerAuthenticationFailureHandler(properties.frontendUri() + "?error=authorization");
		DefaultServerOAuth2AuthorizationRequestResolver resolver =
			new DefaultServerOAuth2AuthorizationRequestResolver(clients);
		resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
		CookieServerCsrfTokenRepository csrfRepository = new CookieServerCsrfTokenRepository();
		csrfRepository.setCookieName("BFF-XSRF-TOKEN");
		csrfRepository.setHeaderName("X-BFF-XSRF-TOKEN");
		csrfRepository.setCookiePath("/");
		csrfRepository.setCookieCustomizer(cookie -> cookie.httpOnly(true).sameSite("Lax").secure(properties.cookieSecure()));
		http.authorizeExchange(authorize -> authorize
				.pathMatchers("/", "/bff/index.html", "/bff/app.js", "/bff/style.css",
					"/bff/public", "/bff/csrf", "/oauth2/authorization/" + REGISTRATION_ID,
					"/login/oauth2/code/" + REGISTRATION_ID).permitAll()
				.pathMatchers("/bff/me", "/bff/messages", "/bff/restricted",
					"/bff/identity", "/bff/logout").authenticated()
				// 标准 Token 路由由资源服务独立校验；未登记路径不由 BFF 放行。
				.pathMatchers("/blade-auth", "/blade-auth/**").denyAll()
				.anyExchange().permitAll())
			.httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
			.formLogin(ServerHttpSecurity.FormLoginSpec::disable)
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint((exchange, error) ->
					writeResponse(exchange, mapper, HttpStatus.UNAUTHORIZED, R.fail(40101, "请先登录")))
				.accessDeniedHandler((exchange, error) ->
					writeResponse(exchange, mapper, HttpStatus.FORBIDDEN, R.fail(403, "访问被拒绝"))))
			.csrf(csrf -> csrf.csrfTokenRepository(csrfRepository).requireCsrfProtectionMatcher(exchange ->
				isBusinessBearer(exchange) && !isPublicRegistration(exchange) ? ServerWebExchangeMatcher.MatchResult.notMatch()
					: CsrfWebFilter.DEFAULT_CSRF_MATCHER.matches(exchange))
				.accessDeniedHandler((exchange, error) ->
				writeResponse(exchange, mapper, HttpStatus.FORBIDDEN, R.fail(403, "CSRF 校验失败"))))
			.oauth2Login(login -> login.clientRegistrationRepository(clients)
				.authorizedClientRepository(authorizedClients).authorizationRequestResolver(resolver)
				.authenticationSuccessHandler(new RedirectServerAuthenticationSuccessHandler(properties.frontendUri()))
				.authenticationFailureHandler((filterExchange, error) ->
					clientManager.invalidate(filterExchange.getExchange())
						.then(failureHandler.onAuthenticationFailure(filterExchange, error))))
			.logout(logout -> logout.requiresLogout(
					ServerWebExchangeMatchers.pathMatchers(HttpMethod.POST, "/bff/logout"))
				.logoutHandler(new SecurityContextServerLogoutHandler())
				.logoutSuccessHandler((filterExchange, authentication) ->
					// 先完成框架的安全上下文和 CSRF 清理，再失效会话，避免继续保存已失效的 Redis session。
					clientManager.invalidate(filterExchange.getExchange())
						.then(Mono.defer(() -> writeResponse(filterExchange.getExchange(), mapper,
							HttpStatus.OK, R.success("退出成功"))))));
		return http.build();
	}

	@Bean
	public RouteLocator bffRoutes(RouteLocatorBuilder builder, BffProperties properties,
		TokenRelayGatewayFilterFactory relayFactory, ObjectMapper mapper, SessionSerialAuthorizedClientManager clientManager) {
		GatewayFilter relay = relayFactory.apply();
		GatewayFilter sessionRelay = (exchange, chain) -> relay.filter(exchange, authorizedExchange -> {
				if (!StringUtils.hasText(authorizedExchange.getRequest().getHeaders()
					.getFirst(HttpHeaders.AUTHORIZATION))) {
					return expireSession(exchange, mapper, clientManager);
				}
				return chain.filter(authorizedExchange);
			})
			// TokenRelay 的授权客户端管理器需要当前请求，以读写同一个 WebSession。
			.contextWrite(context -> context.put(ServerWebExchange.class, exchange))
			.onErrorResume(OAuth2AuthorizationException.class, error -> expireSession(exchange, mapper, clientManager));
		GatewayFilter resourceRelay = (exchange, chain) -> {
			if (hasBearer(exchange)) {
				// 无状态调用保留原始 Bearer，由资源端独立校验；不回退到 Cookie 身份。
				return chain.filter(exchange);
			}
			ServerWebExchange sessionExchange = exchange.mutate().request(request ->
				request.headers(headers -> headers.remove(HttpHeaders.AUTHORIZATION))).build();
			return sessionExchange.getPrincipal().filter(principal -> principal instanceof org.springframework.security.core.Authentication authentication
				&& authentication.isAuthenticated() && !(authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken))
				.hasElement().flatMap(authenticated ->
				authenticated ? sessionRelay.filter(sessionExchange, chain)
					: writeResponse(exchange, mapper, HttpStatus.UNAUTHORIZED, R.fail(40101, "请先登录")));

		};
		return builder.routes()
			.route("registration-config", route -> route.path("/blade-system/user/register/config").and().method(HttpMethod.GET)
				.filters(filters -> filters.stripPrefix(1).removeRequestHeader(HttpHeaders.COOKIE).removeRequestHeader(HttpHeaders.AUTHORIZATION)
					.removeRequestHeader("Blade-Auth").removeRequestHeader("Blade-Auth").removeRequestHeader("X-User-Id").removeRequestHeader("X-Tenant-Id").removeRequestHeader("X-User-Roles"))
				.uri(properties.systemUrl()))
			.route("registration-captcha", route -> route.path("/blade-system/user/register/captcha").and().method(HttpMethod.GET)
				.filters(filters -> filters.stripPrefix(1).removeRequestHeader(HttpHeaders.COOKIE).removeRequestHeader(HttpHeaders.AUTHORIZATION)
					.removeRequestHeader("Blade-Auth").removeRequestHeader("Blade-Auth").removeRequestHeader("X-User-Id").removeRequestHeader("X-Tenant-Id").removeRequestHeader("X-User-Roles"))
				.uri(properties.systemUrl()))
			.route("registration-submit", route -> route.path("/blade-system/user/register").and().method(HttpMethod.POST)
				.filters(filters -> filters.stripPrefix(1).removeRequestHeader(HttpHeaders.COOKIE).removeRequestHeader(HttpHeaders.AUTHORIZATION)
					.removeRequestHeader("Blade-Auth").removeRequestHeader("Blade-Auth").removeRequestHeader("X-User-Id").removeRequestHeader("X-Tenant-Id").removeRequestHeader("X-User-Roles"))
				.uri(properties.systemUrl()))
			.route("demo-public", route -> route.path("/bff/public").and().method(HttpMethod.GET)
				.filters(filters -> filters.setPath("/authorization/public")
					.removeRequestHeader(HttpHeaders.COOKIE).removeRequestHeader(HttpHeaders.AUTHORIZATION)
					.removeRequestHeader("Blade-Auth").removeRequestHeader("X-User-Id").removeRequestHeader("X-Tenant-Id")
					.removeRequestHeader("X-User-Roles"))
				.uri(properties.demoUrl()))
			.route("demo-messages", route -> route.path("/bff/messages").and().method(HttpMethod.GET)
				.filters(filters -> filters.setPath("/authorization/messages")
					.removeRequestHeader(HttpHeaders.COOKIE).removeRequestHeader(HttpHeaders.AUTHORIZATION)
					.removeRequestHeader("Blade-Auth").removeRequestHeader("X-User-Id").removeRequestHeader("X-Tenant-Id")
					.removeRequestHeader("X-User-Roles")
					.filter(sessionRelay))
				.uri(properties.demoUrl()))
			.route("demo-restricted", route -> route.path("/bff/restricted").and().method(HttpMethod.GET)
				.filters(filters -> filters.setPath("/authorization/restricted")
					.removeRequestHeader(HttpHeaders.COOKIE).removeRequestHeader(HttpHeaders.AUTHORIZATION)
					.removeRequestHeader("Blade-Auth").removeRequestHeader("X-User-Id").removeRequestHeader("X-Tenant-Id")
					.removeRequestHeader("X-User-Roles")
					.filter(sessionRelay))
				.uri(properties.demoUrl()))
			.route("demo-token", route -> route.path("/bff/identity").and().method(HttpMethod.GET)
				.filters(filters -> filters.setPath("/authorization/identity")
					.removeRequestHeader(HttpHeaders.COOKIE).removeRequestHeader(HttpHeaders.AUTHORIZATION)
					.removeRequestHeader("Blade-Auth").removeRequestHeader("X-User-Id").removeRequestHeader("X-Tenant-Id")
					.removeRequestHeader("X-User-Roles")
					.filter(sessionRelay))
				.uri(properties.demoUrl()))
			.route("system-service", route -> route.path("/blade-system/**")
				.filters(filters -> filters.stripPrefix(1)
					.removeRequestHeader(HttpHeaders.COOKIE)
					.removeRequestHeader("Blade-Auth").removeRequestHeader("X-User-Id").removeRequestHeader("X-Tenant-Id")
					.removeRequestHeader("X-User-Roles")
					.filter(resourceRelay))
				.uri(properties.systemUrl()))
			.route("desk-service", route -> route.path("/blade-desk/**")
				.filters(filters -> filters.stripPrefix(1)
					.removeRequestHeader(HttpHeaders.COOKIE)
					.removeRequestHeader("Blade-Auth").removeRequestHeader("X-User-Id").removeRequestHeader("X-Tenant-Id")
					.removeRequestHeader("X-User-Roles")
					.filter(resourceRelay))
				.uri(properties.deskUrl()))
			.route("ai-service", route -> route.path("/blade-ai/**")
				.filters(filters -> filters.stripPrefix(1)
					.removeRequestHeader(HttpHeaders.COOKIE)
					.removeRequestHeader("Blade-Auth").removeRequestHeader("X-User-Id").removeRequestHeader("X-Tenant-Id")
					.removeRequestHeader("X-User-Roles")
					.filter(resourceRelay))
				.uri(properties.aiUrl()))
			.route("log-service", route -> route.path("/blade-log/**")
				.filters(filters -> filters.stripPrefix(1)
					.removeRequestHeader(HttpHeaders.COOKIE)
					.removeRequestHeader("Blade-Auth").removeRequestHeader("X-User-Id").removeRequestHeader("X-Tenant-Id")
					.removeRequestHeader("X-User-Roles")
					.filter(resourceRelay))
				.uri(properties.logUrl()))
			.route("resource-service", route -> route.path("/blade-resource/**")
				.filters(filters -> filters.stripPrefix(1)
					.removeRequestHeader(HttpHeaders.COOKIE)
					.removeRequestHeader("Blade-Auth").removeRequestHeader("X-User-Id").removeRequestHeader("X-Tenant-Id")
					.removeRequestHeader("X-User-Roles")
					.filter(resourceRelay))
				.uri(properties.resourceUrl()))
			.route("develop-service", route -> route.path("/blade-develop/**")
				.filters(filters -> filters.stripPrefix(1)
					.removeRequestHeader(HttpHeaders.COOKIE)
					.removeRequestHeader("Blade-Auth").removeRequestHeader("X-User-Id").removeRequestHeader("X-Tenant-Id")
					.removeRequestHeader("X-User-Roles")
					.filter(resourceRelay))
				.uri(properties.developUrl()))
			.route("report-service", route -> route.path("/blade-report/**")
				.filters(filters -> filters.stripPrefix(1)
					.removeRequestHeader(HttpHeaders.COOKIE)
					.removeRequestHeader("Blade-Auth").removeRequestHeader("X-User-Id").removeRequestHeader("X-Tenant-Id")
					.removeRequestHeader("X-User-Roles")
					.filter(resourceRelay))
				.uri(properties.reportUrl()))
			.build();
	}

	private Mono<Void> expireSession(ServerWebExchange exchange, ObjectMapper mapper, SessionSerialAuthorizedClientManager clientManager) {
		return clientManager.invalidate(exchange)
			.then(Mono.defer(() -> writeResponse(exchange, mapper, HttpStatus.UNAUTHORIZED,
				R.fail(40102, "登录已失效，请重新登录"))));
	}

	private boolean isPublicRegistration(ServerWebExchange exchange) {
		String path = exchange.getRequest().getPath().value();
		return java.util.List.of("/blade-system/user/register", "/blade-system/user/register/config",
			"/blade-system/user/register/captcha").contains(path);
	}

	private boolean isBusinessBearer(ServerWebExchange exchange) {
		String path = exchange.getRequest().getPath().value();
		return hasBearer(exchange)
			&& java.util.List.of("/blade-system/", "/blade-desk/", "/blade-ai/", "/blade-log/",
				"/blade-resource/", "/blade-develop/", "/blade-report/").stream().anyMatch(path::startsWith);
	}

	private boolean hasBearer(ServerWebExchange exchange) {
		String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
		return authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7);
	}

	private Mono<Void> writeResponse(ServerWebExchange exchange, ObjectMapper mapper,
		HttpStatus status, R<?> result) {
		exchange.getResponse().setStatusCode(status);
		exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
		exchange.getResponse().getHeaders().setCacheControl("no-store");
		try {
			byte[] bytes = mapper.writeValueAsBytes(result);
			return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
		} catch (JsonProcessingException error) {
			return Mono.error(error);
		}
	}
}
