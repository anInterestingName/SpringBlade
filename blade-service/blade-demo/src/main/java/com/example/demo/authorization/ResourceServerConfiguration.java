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
package com.example.demo.authorization;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springblade.core.tool.api.R;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Demo 的 OAuth2 资源端安全配置，验证签名、issuer、audience 与 scope。
 *
 * @author BladeX
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class ResourceServerConfiguration {

	@Bean
	public JwtDecoder resourceJwtDecoder(@Value("${blade.authorization.resource.issuer}") String issuer,
		@Value("${blade.authorization.resource.jwk-set-uri}") String jwkSetUri) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
			JwtValidators.createDefaultWithIssuer(issuer), new JwtAudienceValidator("blade-demo")));
		return decoder;
	}

	@Bean
	@Order(1)
	public SecurityFilterChain resourceSecurityFilterChain(HttpSecurity http, JwtDecoder resourceJwtDecoder,
		ObjectMapper mapper) {
		BearerTokenAuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();
		BearerTokenAccessDeniedHandler bearerDeniedHandler = new BearerTokenAccessDeniedHandler();
		AuthenticationEntryPoint entryPoint = (request, response, exception) -> {
			// 保留标准 Bearer 认证响应头，并补充统一 JSON；不回显令牌或异常内容。
			bearerEntryPoint.commence(request, response, exception);
			writeSecurityError(response, mapper, 401, "未登录或访问令牌已失效，请重新登录");
		};
		AccessDeniedHandler deniedHandler = (request, response, exception) -> {
			bearerDeniedHandler.handle(request, response, exception);
			writeSecurityError(response, mapper, 403, "权限不足，无法访问该接口");
		};
		http.securityMatcher("/authorization/**")
			.authorizeHttpRequests(authorize -> authorize
				.requestMatchers("/authorization/public", "/authorization/callback").permitAll()
				.requestMatchers("/authorization/messages").hasAuthority("SCOPE_demo.read")
				.requestMatchers("/authorization/identity").authenticated()
				.requestMatchers("/authorization/restricted").hasAuthority("SCOPE_demo.write")
				.anyRequest().denyAll())
			.csrf(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(entryPoint)
				.accessDeniedHandler(deniedHandler))
			.oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.decoder(resourceJwtDecoder))
				.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler));
		return http.build();
	}

	private void writeSecurityError(HttpServletResponse response, ObjectMapper mapper,
		int status, String message) throws IOException {
		response.setStatus(status);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		mapper.writeValue(response.getWriter(), R.fail(status, message));
	}

}
