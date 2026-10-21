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
package org.springblade.authorization.props;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

import java.net.URI;
import java.time.Duration;

/**
 * 授权服务器配置，账号和业务客户端由系统服务管理，system bootstrap 客户端由部署配置提供。
 *
 * @author SpringBlade
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "blade.authorization")
public class AuthorizationProperties {

	private long workerId;
	private long datacenterId;
	private String issuer;
	private String frontendBaseUri;
	private String bffLoginUri;
	private String defaultTenantId = "000000";
	/** 授权服务访问内部契约的服务客户端密钥，仅从部署环境或受控 Nacos 密钥配置注入。 */
	private String internalClientSecret;
	private String jwkSetLocation;
	private String activeKeyId;
	private boolean requireHttps;
	private int protocolMaxRequests = 120;
	private Duration protocolRateWindow = Duration.ofMinutes(1);
	private boolean cleanupEnabled = true;
	private int cleanupBatchSize = 1000;
	private Duration cleanupRetention = Duration.ofDays(1);
	/** 授权状态信封加密密钥，只从部署环境注入。 */
	private String payloadEncryptionKey;
	private boolean allowLegacyPayload;
	private Duration pendingAuthorizationTtl = Duration.ofMinutes(10);
	private int loginMaxAttempts = 5;
	private Duration loginLockDuration = Duration.ofMinutes(30);

	public String frontendUri(String path) {
		Assert.hasText(frontendBaseUri, "前端认证页面地址不能为空");
		URI base = URI.create(frontendBaseUri);
		Assert.isTrue(("http".equals(base.getScheme()) || "https".equals(base.getScheme()))
			&& base.getHost() != null && base.getUserInfo() == null
			&& base.getQuery() == null && base.getFragment() == null,
			"前端地址必须是无凭据、查询参数和片段的 HTTP(S) 地址");
		return frontendBaseUri.replaceAll("/+$", "") + path;
	}
}
