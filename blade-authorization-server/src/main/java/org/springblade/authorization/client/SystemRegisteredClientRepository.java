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
package org.springblade.authorization.client;

import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springblade.authorization.props.AuthorizationProperties;
import org.springblade.core.tool.api.R;
import org.springblade.system.feign.IClientRegistrationClient;
import org.springblade.system.security.SystemAuthorizationContract;
import org.springblade.system.vo.ClientRegistrationVO;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;
import java.net.ConnectException;
import java.net.SocketTimeoutException;

/**
 * 将系统应用主数据适配为 SAS 客户端，不缓存授权安全配置。
 *
 * @author SpringBlade
 */
@Repository
@Slf4j
public class SystemRegisteredClientRepository implements RegisteredClientRepository {
	private static final String BOOTSTRAP_ID = SystemAuthorizationContract.BOOTSTRAP_CLIENT_ID_NUM;
	private final MeterRegistry metrics;
	private final IClientRegistrationClient clients;
	private final AuthorizationProperties properties;
	private final RegisteredClient bootstrapClient;
	private final ClientRegistrationVO bootstrapRegistration;
	public SystemRegisteredClientRepository(IClientRegistrationClient clients, AuthorizationProperties properties, MeterRegistry metrics) {
		this.clients = clients;
		this.metrics = metrics;
		this.properties = properties;
		if (!StringUtils.hasText(properties.getInternalClientSecret())) {
			throw new AuthenticationServiceException("内部服务客户端配置无效");
		}
		this.bootstrapRegistration = new ClientRegistrationVO();
		bootstrapRegistration.setId(BOOTSTRAP_ID);
		bootstrapRegistration.setClientId(SystemAuthorizationContract.CLIENT_ID);
		bootstrapRegistration.setClientName("Authorization Server System Client");
		bootstrapRegistration.setClientSecretHash(PasswordEncoderFactories.createDelegatingPasswordEncoder()
			.encode(properties.getInternalClientSecret()));
		bootstrapRegistration.setClientAuthenticationMethods("client_secret_basic");
		bootstrapRegistration.setGrantTypes("client_credentials");
		bootstrapRegistration.setScopes(String.join(",", SystemAuthorizationContract.ALLOWED_SCOPES));
		bootstrapRegistration.setAudiences(SystemAuthorizationContract.AUDIENCE);
		bootstrapRegistration.setAccessTokenTtl(300);
		bootstrapRegistration.setRefreshTokenTtl(1800);
		bootstrapRegistration.setRequireProofKey(false);
		bootstrapRegistration.setRequireAuthorizationConsent(false);
		bootstrapRegistration.setReuseRefreshTokens(false);
		this.bootstrapClient = map(bootstrapRegistration);
	}
	@Override
	public void save(RegisteredClient client) {
		throw new UnsupportedOperationException("客户端请通过系统应用管理维护");
	}
	@Override
	public RegisteredClient findById(String id) {
		if (BOOTSTRAP_ID.equals(id)) {
			return bootstrapClient;
		}
		return map(load(() -> clients.byId(id)));
	}
	@Override
	public RegisteredClient findByClientId(String clientId) {
		if (SystemAuthorizationContract.CLIENT_ID.equals(clientId)) {
			return bootstrapClient;
		}
		return map(load(() -> clients.byClientId(clientId)));
	}
	public ClientRegistrationVO registration(String id) {
		if (BOOTSTRAP_ID.equals(id)) {
			return bootstrapRegistration;
		}
		return load(() -> clients.byId(id));
	}
	private ClientRegistrationVO load(Supplier<R<ClientRegistrationVO>> query) {
		R<ClientRegistrationVO> result;
		try {
			long started = System.nanoTime();
			try { result = query.get(); }
			finally { metrics.timer("blade.authorization.dependency.duration", "operation", "client-registration")
				.record(System.nanoTime() - started, java.util.concurrent.TimeUnit.NANOSECONDS); }
		} catch (RuntimeException exception) {
			Throwable cause = exception;
			while (cause.getCause() != null && cause.getCause() != cause) {
				cause = cause.getCause();
			}
			int status = exception instanceof FeignException failure ? failure.status() : -1;
			// 仅记录错误分类，不输出Feign请求头、响应正文或完整异常。
			log.warn("客户端注册调用失败：service=blade-system, status={}, type={}, causeType={}",
				status, exception.getClass().getSimpleName(), cause.getClass().getSimpleName());
			if (cause instanceof ConnectException) {
				throw new AuthenticationServiceException("无法连接system服务，请检查 Nacos 注册状态和 blade-system 实例");
			}
			if (cause instanceof SocketTimeoutException) {
				throw new AuthenticationServiceException("调用system服务超时，请检查现有SystemApplication状态");
			}
			if (status == 401 || status == 403) {
				throw new AuthenticationServiceException("system 标准 Token 调用被拒绝，请检查服务客户端和资源范围");
			}
			throw new AuthenticationServiceException("客户端注册服务调用失败，HTTP状态=" + status);
		}
		if (result != null && result.getCode() == 404) {
			return null;
		}
		if (result == null || !result.isSuccess() || result.getData() == null) {
			if (result != null && result.getCode() == 403) {
				throw new AuthenticationServiceException("system 标准 Token 校验失败，请检查服务客户端和资源范围");
			}
			log.warn("客户端注册结果失败：service=blade-system, code={}", result == null ? null : result.getCode());
			throw new AuthenticationServiceException("客户端注册服务不可用");
		}
		return result.getData();
	}
	private RegisteredClient map(ClientRegistrationVO client) {
		if (client == null) {
			return null;
		}
		Set<String> methods = values(client.getClientAuthenticationMethods());
		Set<String> grants = values(client.getGrantTypes());
		if (methods.size() != 1 || !Set.of("none", "client_secret_basic").containsAll(methods)
			|| grants.isEmpty() || !Set.of("authorization_code", "refresh_token", "client_credentials").containsAll(grants)
			|| (methods.contains("none") && !grants.equals(Set.of("authorization_code")))
			|| (grants.contains("authorization_code") && (!Boolean.TRUE.equals(client.getRequireProofKey())
				|| values(client.getRedirectUris()).isEmpty()))
			|| (grants.contains("refresh_token") && !grants.contains("authorization_code"))
			|| !Boolean.FALSE.equals(client.getReuseRefreshTokens())
			|| client.getAccessTokenTtl() == null || client.getAccessTokenTtl() <= 0
			|| client.getRefreshTokenTtl() == null || client.getRefreshTokenTtl() <= 0
			|| values(client.getScopes()).isEmpty() || values(client.getAudiences()).isEmpty()) {
			throw new AuthenticationServiceException("客户端协议配置无效");
		}
		if (!methods.contains("none") && (!StringUtils.hasText(client.getClientSecretHash())
			|| !client.getClientSecretHash().startsWith("{bcrypt}"))) {
			throw new AuthenticationServiceException("客户端密钥配置无效");
		}
		RegisteredClient.Builder builder = RegisteredClient.withId(client.getId())
			.clientId(client.getClientId()).clientName(client.getClientName())
			.clientSettings(ClientSettings.builder().requireProofKey(client.getRequireProofKey())
				.requireAuthorizationConsent(Boolean.TRUE.equals(client.getRequireAuthorizationConsent())).build())
			.tokenSettings(TokenSettings.builder().accessTokenTimeToLive(Duration.ofSeconds(client.getAccessTokenTtl()))
				.refreshTokenTimeToLive(Duration.ofSeconds(client.getRefreshTokenTtl())).reuseRefreshTokens(false).build());
		if (!methods.contains("none")) {
			builder.clientSecret(client.getClientSecretHash());
		}
		methods.forEach(value -> builder.clientAuthenticationMethod(new ClientAuthenticationMethod(value)));
		grants.forEach(value -> builder.authorizationGrantType(new AuthorizationGrantType(value)));
		values(client.getRedirectUris()).forEach(builder::redirectUri);
		values(client.getScopes()).forEach(builder::scope);
		return builder.build();
	}
	public static Set<String> values(String value) {
		Set<String> result = new LinkedHashSet<>();
		if (StringUtils.hasText(value)) {
			Arrays.stream(value.split(",")).map(String::trim).filter(StringUtils::hasText).forEach(result::add);
		}
		return result;
	}
}
