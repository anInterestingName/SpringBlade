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
package org.springblade.authorization.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springblade.authorization.props.AuthorizationProperties;
import org.springblade.authorization.security.AuthorizationTransactionFilter;
import org.springblade.authorization.security.AuthorizationDependencyFilter;
import org.springblade.authorization.security.AuthorizationProtocolGuardFilter;
import org.springblade.authorization.client.SystemRegisteredClientRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import io.micrometer.core.instrument.MeterRegistry;
import org.springblade.authorization.security.SystemUserAuthenticationProvider;
import org.springblade.core.tool.api.R;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationConsentAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationConsentAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.Assert;
import java.net.URI;

/**
 * OAuth2/OIDC协议端点与前端表单登录的安全配置。
 *
 * @author SpringBlade
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableConfigurationProperties(AuthorizationProperties.class)
public class AuthorizationSecurityConfiguration {
	@Bean
	@Order(1)
	public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http,
		HttpSessionRequestCache authorizationRequestCache, AuthorizationProperties properties,
		PlatformTransactionManager transactions, CookieCsrfTokenRepository authorizationCsrfRepository,
		ObjectMapper mapper, StringRedisTemplate redis, SystemRegisteredClientRepository clients, MeterRegistry metrics) {
		http.addFilterBefore(new AuthorizationTransactionFilter(transactions), SecurityContextHolderFilter.class);
		http.addFilterBefore(new AuthorizationDependencyFilter(), SecurityContextHolderFilter.class);
		http.oauth2AuthorizationServer(server -> {
			http.securityMatcher(server.getEndpointsMatcher());
			server.authorizationEndpoint(endpoint -> endpoint
				.consentPage(properties.frontendUri("/auth/consent"))
				.authenticationProviders(providers -> providers.forEach(provider -> {
					if (provider instanceof OAuth2AuthorizationConsentAuthenticationProvider consentProvider) {
						// 显式拒绝时清除本次同意，包括已有授权，交由框架返回 access_denied。
						consentProvider.setAuthorizationConsentCustomizer(context -> {
							OAuth2AuthorizationConsentAuthenticationToken consent = context.getAuthentication();
							if ("deny".equals(consent.getAdditionalParameters().get("decision"))) {
								context.getAuthorizationConsent().authorities(authorities -> authorities.clear());
							}
						});
					}
				})));
			server.oidc(Customizer.withDefaults());
		});
		// SAS 默认豁免协议端点；单独在同意提交前执行浏览器 CSRF 校验。
		CsrfFilter consentCsrf = new CsrfFilter(authorizationCsrfRepository);
		consentCsrf.setBeanName("authorizationConsentCsrf");
		consentCsrf.setRequestHandler(new XorCsrfTokenRequestAttributeHandler());
		consentCsrf.setRequireCsrfProtectionMatcher(PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/oauth2/authorize"));
		consentCsrf.setAccessDeniedHandler((request, response, error) -> {
			response.setStatus(403);
			response.setHeader("Cache-Control", "no-store");
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			mapper.writeValue(response.getOutputStream(), R.fail(403, "CSRF 校验失败"));
		});
		// SAS 专属过滤器在 configure 阶段才注册，使用已预登记的框架过滤器作为排序锚点。
		// 此位置在标准 CSRF 之后、SAS 授权处理之前，独立过滤器名称避免 OncePerRequest 冲突。
		http.addFilterBefore(consentCsrf, LogoutFilter.class);
		// 位于 SAS 客户端认证之后、AuthorizationFilter 及协议端点处理之前。
		http.addFilterAfter(new AuthorizationProtocolGuardFilter(redis, properties, clients, mapper, metrics),
			BasicAuthenticationFilter.class);
		http.csrf(csrf -> csrf.csrfTokenRepository(authorizationCsrfRepository));
		http.authorizeHttpRequests(authorize -> authorize
			.requestMatchers("/.well-known/appspecific/com.chrome.devtools.json").permitAll()
			.anyRequest().authenticated())
			.requestCache(cache -> cache.requestCache(authorizationRequestCache))
			.exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
				new LoginUrlAuthenticationEntryPoint(properties.frontendUri("/auth/login")),
				new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
			.oauth2ResourceServer(resource -> resource.jwt(Customizer.withDefaults()));
		return http.build();
	}

	@Bean
	@Order(2)
	public SecurityFilterChain loginSecurityFilterChain(HttpSecurity http,
		HttpSessionRequestCache authorizationRequestCache, AuthorizationProperties properties,
		ObjectMapper mapper, SystemUserAuthenticationProvider users, CookieCsrfTokenRepository authorizationCsrfRepository) {
		Assert.hasText(properties.getBffLoginUri(), "BFF 登录发起地址不能为空");
		SavedRequestAwareAuthenticationSuccessHandler successHandler =
			new SavedRequestAwareAuthenticationSuccessHandler();
		successHandler.setRequestCache(authorizationRequestCache);
		// 有授权请求时继续原流程；直接登录时从 BFF 发起，建立正确的 state/PKCE 上下文。
		successHandler.setDefaultTargetUrl(properties.getBffLoginUri());
		http.authenticationProvider(users);
		http.csrf(csrf -> csrf.csrfTokenRepository(authorizationCsrfRepository)
			.csrfTokenRequestHandler(new XorCsrfTokenRequestAttributeHandler()));
		http.authorizeHttpRequests(authorize -> authorize
			.requestMatchers("/", "/login", "/ui/login-context", "/error",
				"/.well-known/appspecific/com.chrome.devtools.json", "/actuator/health/**").permitAll()
			.requestMatchers("/actuator/metrics/**").access((authentication, context) ->
				new org.springframework.security.authorization.AuthorizationDecision(
					new org.springframework.security.web.util.matcher.IpAddressMatcher("127.0.0.1/32").matches(context.getRequest())
					|| new org.springframework.security.web.util.matcher.IpAddressMatcher("::1/128").matches(context.getRequest())))
			.requestMatchers("/ui/consent").authenticated()
			.anyRequest().denyAll())
			.requestCache(cache -> cache.requestCache(authorizationRequestCache))
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint((request, response, error) -> {
					response.setStatus(401);
					response.setHeader("Cache-Control", "no-store");
					response.setContentType(MediaType.APPLICATION_JSON_VALUE);
					mapper.writeValue(response.getOutputStream(), R.fail(401, "认证会话已失效，请重新登录"));
				})
				.accessDeniedHandler((request, response, error) -> {
					if (error instanceof CsrfException && "/login".equals(request.getServletPath())) {
						response.sendRedirect(properties.frontendUri("/auth/login?error=csrf"));
						return;
					}
					response.setStatus(403);
					response.setHeader("Cache-Control", "no-store");
					response.setContentType(MediaType.APPLICATION_JSON_VALUE);
					mapper.writeValue(response.getOutputStream(), R.fail(403,
						error instanceof CsrfException ? "CSRF 校验失败" : "访问被拒绝"));
				}))
			// loginPage 会立即推导提交路径；使用服务内路径，由 GET /login 跳转前端页面。
			.formLogin(login -> login.loginPage("/login")
				.loginProcessingUrl("/login").successHandler(successHandler)
				.failureHandler((request, response, error) ->
					response.sendRedirect(properties.frontendUri(error instanceof AuthenticationServiceException
						? "/auth/login?error=service" : "/auth/login?error=credentials"))))
			.logout(logout -> logout.disable());
		return http.build();
	}

	@Bean
	public CookieCsrfTokenRepository authorizationCsrfRepository(Environment environment) {
		boolean secure = environment.getProperty("server.servlet.session.cookie.secure", Boolean.class, false);
		Assert.isTrue(!environment.matchesProfiles("prod") || secure, "生产 AUTH Cookie 必须启用 Secure");
		CookieCsrfTokenRepository repository = new CookieCsrfTokenRepository();
		repository.setCookieName("AUTH-XSRF-TOKEN");
		repository.setHeaderName("X-AUTH-XSRF-TOKEN");
		repository.setCookiePath("/");
		repository.setCookieCustomizer(cookie -> cookie.httpOnly(true).sameSite("Lax").secure(secure));
		return repository;
	}

	@Bean
	public HttpSessionRequestCache authorizationRequestCache(AuthorizationServerSettings settings) {
		HttpSessionRequestCache cache = new HttpSessionRequestCache();
		// 浏览器探测、静态资源和未知地址不能覆盖登录前的授权请求。
		cache.setRequestMatcher(PathPatternRequestMatcher.pathPattern(HttpMethod.GET,
			settings.getAuthorizationEndpoint()));
		return cache;
	}


	@Bean
	public PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	@Bean
	public AuthorizationServerSettings authorizationServerSettings(AuthorizationProperties properties, Environment environment) {
		Assert.hasText(properties.getIssuer(), "issuer不能为空");
		URI issuer = URI.create(properties.getIssuer());
		Assert.isTrue(issuer.getHost() != null && issuer.getUserInfo() == null && issuer.getQuery() == null
			&& issuer.getFragment() == null && ("https".equals(issuer.getScheme()) || "http".equals(issuer.getScheme())), "issuer必须是有效的HTTP(S)地址");
		Assert.isTrue(!properties.isRequireHttps() || "https".equals(issuer.getScheme()),
			"生产环境issuer必须使用HTTPS");
		if (environment.matchesProfiles("prod")) {
			URI frontend = URI.create(properties.frontendUri(""));
			URI bff = URI.create(properties.getBffLoginUri());
			Assert.isTrue(issuer.getPath().isEmpty(), "生产 issuer 必须为不含路径及尾斜杠的同源根地址");
			Assert.isTrue("https".equals(issuer.getScheme()) && "https".equals(frontend.getScheme())
				&& "https".equals(bff.getScheme()) && issuer.getAuthority().equals(frontend.getAuthority())
				&& issuer.getAuthority().equals(bff.getAuthority()) && bff.getUserInfo() == null && bff.getQuery() == null
				&& bff.getFragment() == null && "/oauth2/authorization/blade".equals(bff.getPath()),
				"生产 issuer、前端与 BFF 登录地址必须同源 HTTPS");
		}
		Assert.isTrue(properties.getProtocolMaxRequests() > 0 && properties.getProtocolRateWindow().toSeconds() > 0
			&& properties.getCleanupBatchSize() > 0 && properties.getCleanupBatchSize() <= 10000
			&& !properties.getCleanupRetention().isNegative(), "协议限流或清理配置无效");
		Assert.hasText(properties.getInternalClientSecret(), "内部服务客户端密钥不能为空");
		Assert.isTrue(properties.getPendingAuthorizationTtl().isPositive() && properties.getLoginLockDuration().isPositive()
			&& properties.getLoginMaxAttempts() > 0, "授权过期和登录锁定配置无效");
		return AuthorizationServerSettings.builder().issuer(properties.getIssuer()).build();
	}
}
