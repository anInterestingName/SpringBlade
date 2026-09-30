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
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;

/**
 * 供应商接入配置修改DTO
 *
 * @author SpringBlade
 */
@Data
@Schema(description = "供应商接入配置修改DTO")
public class ProviderUpdateDTO implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * 主键ID
	 */
	@NotNull(message = "配置ID不能为空")
	@JsonSerialize(using = ToStringSerializer.class)
	@Schema(description = "主键ID")
	private Long id;

	/**
	 * 并发控制版本
	 */
	@NotNull(message = "并发控制版本不能为空")
	@Schema(description = "并发控制版本")
	private Long lockVersion;

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
	 * 接入凭据（null表示保留旧凭据，非空表示替换，不支持空白串）
	 */
	@Schema(description = "接入凭据")
	private String apiKeyValue;

	/**
	 * 目标租户ID（平台超级管理员修改时必填断言）
	 */
	@Schema(description = "目标租户ID")
	private String tenantId;

}
