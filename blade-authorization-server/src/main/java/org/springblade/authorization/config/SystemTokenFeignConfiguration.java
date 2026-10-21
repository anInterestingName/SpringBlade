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

import feign.RequestInterceptor;
import org.springblade.authorization.props.AuthorizationProperties;
import org.springblade.system.security.SystemAuthorizationContract;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.util.StringUtils;

/**
 * 为授权服务到 system 的 Feign 调用自动获取并注入标准 Bearer Token。
 *
 * @author SpringBlade
 */
@Configuration(proxyBeanMethods = false)
public class SystemTokenFeignConfiguration {
	private static final String REGISTRATION_ID = "system";
	private static final String PRINCIPAL = "blade-authorization-server";

	@Bean
	@org.springframework.context.annotation.Primary
	public ClientRegistrationRepository systemClientRegistrationRepository(AuthorizationProperties properties) {
		String tokenUri = properties.getIssuer().replaceAll("/+$", "") + "/oauth2/token";
		ClientRegistration.Builder registration = ClientRegistration.withRegistrationId(REGISTRATION_ID)
			.clientId(SystemAuthorizationContract.CLIENT_ID).clientSecret(properties.getInternalClientSecret())
			.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
			.authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS).tokenUri(tokenUri)
			.scope(SystemAuthorizationContract.ALLOWED_SCOPES);
		return new InMemoryClientRegistrationRepository(registration.build());
	}

	@Bean
	@org.springframework.context.annotation.Primary
	public OAuth2AuthorizedClientService systemAuthorizedClientService(
		ClientRegistrationRepository registrations) {
		return new InMemoryOAuth2AuthorizedClientService(registrations);
	}

	@Bean
	@org.springframework.context.annotation.Primary
	public OAuth2AuthorizedClientManager systemAuthorizedClientManager(
		ClientRegistrationRepository registrations, OAuth2AuthorizedClientService authorizedClients) {
		AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
			new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, authorizedClients);
		manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
			.clientCredentials().build());
		return manager;
	}

	@Bean
	@org.springframework.context.annotation.Primary
	public RequestInterceptor systemTokenFeignInterceptor(OAuth2AuthorizedClientManager manager) {
		return template -> {
			var authorized = manager.authorize(OAuth2AuthorizeRequest.withClientRegistrationId(REGISTRATION_ID)
				.principal(PRINCIPAL).build());
			if (authorized == null || authorized.getAccessToken() == null
				|| !StringUtils.hasText(authorized.getAccessToken().getTokenValue())) {
				throw new IllegalStateException("system 服务 Token 获取失败");
			}
			template.header("Authorization", "Bearer " + authorized.getAccessToken().getTokenValue());
		};
	}
}
