/**
 * Copyright (c) 2018-2099, Chill Zhuang 庄骞 (bladejava@qq.com).
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springblade.system.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.ToString;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serial;
import java.io.Serializable;

/**
 * system 注册编排向用户服务提交的受控命令。
 *
 * @author Codex
 */
@Data
@Schema(description = "用户自助注册内部命令")
public class UserRegisterCommand implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "目标租户编号", requiredMode = Schema.RequiredMode.REQUIRED)
	private String tenantId;

	@Schema(description = "登录账号", requiredMode = Schema.RequiredMode.REQUIRED)
	private String account;

	@Schema(description = "用户昵称", requiredMode = Schema.RequiredMode.REQUIRED)
	private String name;

	@Schema(description = "真实密码，仅在system内部编码使用")
	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	@ToString.Exclude
	private String password;
}
