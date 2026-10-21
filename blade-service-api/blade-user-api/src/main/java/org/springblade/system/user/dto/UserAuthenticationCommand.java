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

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

/**
 * 内部用户密码校验命令，密码只通过受保护的内部请求体传递，不进入 URL 和日志。
 *
 * @author SpringBlade
 */
@Data
public class UserAuthenticationCommand {
	// 仅供授权服务与system的受保护内部通道使用。
	@NotBlank
	@Pattern(regexp = "[0-9]{6,12}")
	private String tenantId;
	@NotBlank
	@Size(max = 64)
	private String account;
	@NotBlank
	@ToString.Exclude
	private String password;
}
