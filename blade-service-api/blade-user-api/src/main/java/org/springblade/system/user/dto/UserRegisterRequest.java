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
package org.springblade.system.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 用户自助注册请求。
 *
 * @author Codex
 */
@Data
@Schema(description = "用户自助注册请求")
public class UserRegisterRequest {

	@Schema(description = "租户编号，留空使用默认租户")
	@Size(max = 12, message = "租户编号长度不能超过12位")
	private String tenantId;

	@Schema(description = "登录账号", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotBlank(message = "请输入账号")
	@Size(max = 32, message = "账号长度不能超过32位")
	private String account;

	@Schema(description = "用户昵称")
	@Size(max = 32, message = "昵称长度不能超过32位")
	private String name;

	@Schema(description = "真实密码", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotBlank(message = "请输入密码")
	@Size(max = 64, message = "密码参数不合法")
	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	@ToString.Exclude
	private String password;

	@Schema(description = "真实确认密码", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotBlank(message = "请输入确认密码")
	@Size(max = 64, message = "确认密码参数不合法")
	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	@ToString.Exclude
	private String confirmPassword;

	@Schema(description = "图形验证码标识", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotBlank(message = "验证码标识不能为空")
	@Size(max = 64, message = "验证码标识不合法")
	private String captchaKey;

	@Schema(description = "图形验证码内容", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotBlank(message = "请输入验证码")
	@Size(max = 20, message = "验证码不合法")
	private String captchaCode;
}
