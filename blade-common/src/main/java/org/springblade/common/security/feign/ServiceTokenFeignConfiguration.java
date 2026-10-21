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
package org.springblade.common.security.feign;

import feign.RequestInterceptor;
import org.springblade.common.security.props.ServiceTokenProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.StringUtils;

import java.util.Arrays;

/**
 * Feign 调用优先传播已经验证的用户 Bearer Token；后台调用使用服务客户端 Token。
 *
 * @author SpringBlade
 */
@AutoConfiguration
@ConditionalOnClass(RequestInterceptor.class)
@ConditionalOnProperty(prefix = "blade.authorization.client", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ServiceTokenProperties.class)
public class ServiceTokenFeignConfiguration {
	private static final String REGISTRATION_ID = "service";

	@Bean
	@ConditionalOnMissingBean(ClientRegistrationRepository.class)
	public ClientRegistrationRepository serviceClientRegistrationRepository(ServiceTokenProperties properties) {
		if (!StringUtils.hasText(properties.getIssuer()) || !StringUtils.hasText(properties.getClientId())
			|| !StringUtils.hasText(properties.getClientSecret())) {
			throw new IllegalStateException("服务间标准 Token 必须配置 issuer、client-id 和 client-secret");
		}
		ClientRegistration.Builder registration = ClientRegistration.withRegistrationId(REGISTRATION_ID)
			.clientId(properties.getClientId()).clientSecret(properties.getClientSecret())
			.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
			.authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
			.tokenUri(properties.getIssuer().replaceAll("/+$", "") + "/oauth2/token");
		registration.scope(Arrays.stream(properties.getScopes().split(",")).map(String::trim)
			.filter(StringUtils::hasText).toList());
		return new InMemoryClientRegistrationRepository(registration.build());
	}

	@Bean
	@ConditionalOnMissingBean(OAuth2AuthorizedClientService.class)
	public OAuth2AuthorizedClientService serviceAuthorizedClientService(
		ClientRegistrationRepository registrations) {
		return new InMemoryOAuth2AuthorizedClientService(registrations);
	}

	@Bean
	@ConditionalOnMissingBean(OAuth2AuthorizedClientManager.class)
	public OAuth2AuthorizedClientManager serviceAuthorizedClientManager(
		ClientRegistrationRepository registrations, OAuth2AuthorizedClientService authorizedClients) {
		AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
			new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, authorizedClients);
		manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
			.clientCredentials().build());
		return manager;
	}

	@Bean
	@Primary
	@ConditionalOnMissingBean(name = "serviceTokenFeignInterceptor")
	public RequestInterceptor serviceTokenFeignInterceptor(OAuth2AuthorizedClientManager manager) {
		return template -> {
			String incoming = incomingBearer();
			if (incoming != null) {
				template.header("Authorization", incoming);
				return;
			}
			var authorized = manager.authorize(OAuth2AuthorizeRequest.withClientRegistrationId(REGISTRATION_ID)
				.principal("blade-service").build());
			if (authorized == null || authorized.getAccessToken() == null
				|| !StringUtils.hasText(authorized.getAccessToken().getTokenValue())) {
				throw new IllegalStateException("服务间标准 Token 获取失败");
			}
			template.header("Authorization", "Bearer " + authorized.getAccessToken().getTokenValue());
		};
	}

	private String incomingBearer() {
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication instanceof JwtAuthenticationToken token && token.isAuthenticated()) {
			return "Bearer " + token.getToken().getTokenValue();
		}
		return null;
	}
}
