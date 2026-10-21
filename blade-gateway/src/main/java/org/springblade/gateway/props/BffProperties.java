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
package org.springblade.gateway.props;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * BFF 客户端和资源路由配置，客户端密钥由环境变量提供。
 *
 * @author BladeX
 */
@ConfigurationProperties(prefix = "blade.bff")
public record BffProperties(String issuer, String clientId, String clientSecret,
	String redirectUri, String demoUrl, String frontendUri, boolean cookieSecure,
	String systemUrl, String deskUrl, String aiUrl, String logUrl,
	String resourceUrl, String developUrl, String reportUrl, String trustedProxyPattern) {
}
