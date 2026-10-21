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
package org.springblade.system.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * system 公开注册配置，开关默认关闭，验证码不可绕过。
 *
 * @author SpringBlade
 */
@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "blade.registration")
public class RegistrationProperties {
	private boolean enabled;
	private boolean captchaEnabled = true;
	private String defaultTenantId = "000000";
	private int accountMinLength = 4;
	private int accountMaxLength = 32;
	private int passwordMinLength = 8;
	private int passwordMaxLength = 64;
	private RateLimit rateLimit = new RateLimit();
	@Data
	public static class RateLimit {
		private long ipWindowSeconds = 60;
		private long ipMaxAttempts = 10;
		private long tenantWindowSeconds = 60;
		private long tenantMaxAttempts = 30;
		private long accountWindowSeconds = 600;
		private long accountMaxAttempts = 3;
	}
}
