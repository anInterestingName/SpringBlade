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

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.Customizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springblade.common.security.filter.TokenBladeUserFilter;

/**
 * system 内部契约只接受授权服务签发的标准服务 Token。
 *
 * @author SpringBlade
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class ResourceTokenEndpointSecurityConfiguration {

	@Bean
	@Order(0)
	public SecurityFilterChain publicRegistrationSecurity(HttpSecurity http) throws Exception {
		http.securityMatcher("/user/register", "/user/register/**")
			.authorizeHttpRequests(authorize -> authorize
				.requestMatchers(HttpMethod.GET, "/user/register/config", "/user/register/captcha").permitAll()
				.requestMatchers(HttpMethod.POST, "/user/register").permitAll().anyRequest().denyAll())
			.csrf(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
		return http.build();
	}

	@Bean
	@Order(2)
	public SecurityFilterChain clientManagementSecurity(HttpSecurity http, TokenBladeUserFilter userFilter,
		org.springframework.data.redis.core.StringRedisTemplate redis, com.fasterxml.jackson.databind.ObjectMapper mapper,
		@org.springframework.beans.factory.annotation.Value("${blade.authorization.client-management.max-requests:60}") int maxRequests) throws Exception {
		http.securityMatcher("/client/**").authorizeHttpRequests(authorize -> authorize.anyRequest().hasAuthority("SCOPE_service.read"))
			.csrf(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.oauth2ResourceServer(resource -> resource.jwt(Customizer.withDefaults()));
		http.addFilterAfter(userFilter, BearerTokenAuthenticationFilter.class);
		http.addFilterAfter(new ClientManagementRateFilter(redis, mapper, maxRequests), BearerTokenAuthenticationFilter.class);
		return http.build();
	}

	@Bean
	@Order(1)
	public SecurityFilterChain systemResourceTokenSecurity(HttpSecurity http, TokenBladeUserFilter userFilter) throws Exception {
		http.securityMatcher("/feign/client/**")
			.authorizeHttpRequests(authorize -> authorize
				.requestMatchers("/feign/client/user-authentication/authenticate")
				.hasAuthority("SCOPE_" + SystemAuthorizationContract.USER_AUTHENTICATE_SCOPE)
				.requestMatchers("/feign/client/user-authentication/user")
				.hasAuthority("SCOPE_" + SystemAuthorizationContract.USER_READ_SCOPE)
				.requestMatchers("/feign/client/client-registration/**")
				.hasAuthority("SCOPE_" + SystemAuthorizationContract.CLIENT_READ_SCOPE)
				// 既有只读内部契约继续使用标准服务范围；用户归属仍由具体入口复核。
				.requestMatchers(HttpMethod.GET, "/feign/client/system/**", "/feign/client/dict/**",
					"/feign/client/data-scope/**", "/feign/client/api-scope/**", "/feign/client/tag-runtime/**",
					"/feign/client/user/user-info-by-id", "/feign/client/user/owner-scope",
					"/feign/client/user/management-scope")
				.hasAuthority("SCOPE_service.read")
				.anyRequest().denyAll())
			.csrf(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.oauth2ResourceServer(resource -> resource.jwt(Customizer.withDefaults()));
		http.addFilterAfter(userFilter, BearerTokenAuthenticationFilter.class);
		return http.build();
	}
}
