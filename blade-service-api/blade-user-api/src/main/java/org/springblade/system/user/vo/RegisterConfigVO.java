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
package org.springblade.system.user.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 注册页面所需的最小公开配置。
 *
 * @author Codex
 */
@Data
@Schema(description = "用户自助注册配置")
public class RegisterConfigVO {
	private Boolean enabled;
	private String defaultTenantId;
	private Boolean captchaEnabled;
	private Integer accountMinLength;
	private Integer accountMaxLength;
	private Integer passwordMinLength;
	private Integer passwordMaxLength;
}
