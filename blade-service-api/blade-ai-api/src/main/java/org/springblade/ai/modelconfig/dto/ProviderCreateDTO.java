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
package org.springblade.ai.modelconfig.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.Serializable;

/**
 * 供应商接入配置创建DTO
 *
 * @author SpringBlade
 */
@Data
@Schema(description = "供应商接入配置创建DTO")
public class ProviderCreateDTO implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * 所有人范围内的供应商管理编码
	 */
	@NotBlank(message = "供应商编码不能为空")
	@Size(max = 64, message = "供应商编码不能超过64字符")
	@Schema(description = "供应商编码")
	private String providerCode;

	/**
	 * 供应商显示名称
	 */
	@NotBlank(message = "供应商名称不能为空")
	@Size(max = 100, message = "供应商名称不能超过100字符")
	@Schema(description = "供应商名称")
	private String providerName;

	/**
	 * 供应商预设字典项ID（可选）
	 */
	@JsonSerialize(using = ToStringSerializer.class)
	@Schema(description = "供应商预设字典项ID")
	private Long presetDictId;

	/**
	 * 接入根地址
	 */
	@NotBlank(message = "接入根地址不能为空")
	@Size(max = 1024, message = "接入根地址不能超过1024字符")
	@Schema(description = "接入根地址")
	private String baseUrl;

	/**
	 * 接入凭据（可选）
	 */
	@Schema(description = "接入凭据")
	private String apiKeyValue;

	/**
	 * 配置所有人用户ID（管理员代建时使用）
	 */
	@JsonSerialize(using = ToStringSerializer.class)
	@Schema(description = "配置所有人用户ID")
	private Long ownerUserId;

	/**
	 * 目标租户ID（平台超级管理员代建时必填断言）
	 */
	@Schema(description = "目标租户ID")
	private String tenantId;

	/**
	 * 启停状态（0=停用，1=启用，默认0）
	 */
	@Schema(description = "启停状态")
	private Integer status;

}
