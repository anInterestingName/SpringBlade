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

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

/**
 * 当前用户通过 JSON 提交真实新旧密码，禁止进入日志或普通响应。
 *
 * @author SpringBlade
 */
@Data
@Schema(description = "修改密码请求")
public class UserPasswordUpdateDTO {
	@NotBlank @Size(max = 128)
	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @ToString.Exclude
	private String oldPassword;
	@NotBlank @Size(min = 8, max = 64)
	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @ToString.Exclude
	private String newPassword;
	@NotBlank @Size(min = 8, max = 64)
	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @ToString.Exclude
	private String confirmPassword;
}
