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
package org.springblade.system.user.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 当前用户管理范围精简VO
 *
 * @author SpringBlade
 */
@Data
@Schema(description = "当前用户管理范围精简VO")
public class UserManagementScopeVO implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * 当前用户ID
	 */
	@JsonSerialize(using = ToStringSerializer.class)
	@Schema(description = "当前用户ID")
	private Long userId;

	/**
	 * 租户ID
	 */
	@Schema(description = "租户ID")
	private String tenantId;

	/**
	 * 是否平台超级管理员
	 */
	@Schema(description = "是否平台超级管理员")
	private Boolean platformAdmin;

	/**
	 * 是否租户管理员
	 */
	@Schema(description = "是否租户管理员")
	private Boolean tenantAdmin;

}
