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
package org.springblade.common.security.config;

import org.springblade.common.security.feign.ServiceTokenFeignConfiguration;
import org.springblade.common.security.filter.TokenBladeUserFilter;
import org.springblade.common.security.props.ResourceTokenProperties;
import org.springblade.common.security.props.ServiceTokenProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 所有 Servlet 资源服务统一使用 OAuth2 Bearer Token，拒绝旧的自定义身份头。
 *
 * @author SpringBlade
 */
@AutoConfiguration
@EnableWebSecurity
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({SecurityFilterChain.class, JwtDecoder.class})
@ConditionalOnProperty(prefix = "blade.authorization.resource", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ResourceTokenProperties.class)
public class StandardTokenResourceServerAutoConfiguration {

	@Bean
	public JwtDecoder standardTokenJwtDecoder(ResourceTokenProperties properties) {
		if (properties.getIssuer() == null || properties.getIssuer().isBlank()
			|| properties.getJwkSetUri() == null || properties.getJwkSetUri().isBlank()
			|| properties.getAudience() == null || properties.getAudience().isBlank()
			|| properties.getRequiredScope() == null || properties.getRequiredScope().isBlank()) {
			throw new IllegalStateException("标准 Token 资源服务必须配置 issuer、jwk-set-uri 和 audience");
		}
		org.springframework.http.client.SimpleClientHttpRequestFactory requests = new org.springframework.http.client.SimpleClientHttpRequestFactory();
		requests.setConnectTimeout(3000); requests.setReadTimeout(5000);
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.getJwkSetUri())
			.restOperations(new org.springframework.web.client.RestTemplate(requests)).build();
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
			JwtValidators.createDefaultWithIssuer(properties.getIssuer()),
			new JwtAudienceValidator(properties.getAudience())));
		return decoder;
	}

	@Bean
	public TokenBladeUserFilter tokenBladeUserFilter() {
		return new TokenBladeUserFilter();
	}

	@Bean
	public FilterRegistrationBean<TokenBladeUserFilter> tokenBladeUserRegistration(TokenBladeUserFilter userFilter) {
		FilterRegistrationBean<TokenBladeUserFilter> registration = new FilterRegistrationBean<>(userFilter);
		// 桥接必须在 Bearer 认证后执行，避免 Servlet 自动注册先执行并占用 OncePerRequest 标记。
		registration.setEnabled(false);
		return registration;
	}

	@Bean
	@Order(100)
	public SecurityFilterChain standardTokenSecurityFilterChain(HttpSecurity http,
		JwtDecoder standardTokenJwtDecoder, TokenBladeUserFilter userFilter,
		ResourceTokenProperties properties) throws Exception {
		java.util.List<String> permitUrls = new java.util.ArrayList<>(java.util.List.of(
			"/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"));
		if (properties.getSkipUrl() != null && !properties.getSkipUrl().isEmpty()) {
			properties.getSkipUrl().stream()
				.filter(url -> url != null && !url.isBlank())
				.forEach(permitUrls::add);
		}
		http.authorizeHttpRequests(authorize -> authorize
			.requestMatchers(permitUrls.toArray(new String[0])).permitAll()
			.anyRequest().hasAuthority("SCOPE_" + properties.getRequiredScope()))
			.csrf(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
				.accessDeniedHandler(new BearerTokenAccessDeniedHandler()))
			.oauth2ResourceServer(resource -> resource
				.jwt(jwt -> jwt.decoder(standardTokenJwtDecoder)
					.jwtAuthenticationConverter(new JwtAuthenticationConverter())));
		http.addFilterAfter(userFilter, BearerTokenAuthenticationFilter.class);
		return http.build();
	}
}
