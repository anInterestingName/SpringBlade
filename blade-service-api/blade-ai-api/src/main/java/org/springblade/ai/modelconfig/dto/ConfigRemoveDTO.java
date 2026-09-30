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

import jakarta.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * 配置逻辑删除DTO
 *
 * @author SpringBlade
 */
@Data
@Schema(description = "配置逻辑删除DTO")
public class ConfigRemoveDTO implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * 配置主键ID
	 */
	@NotNull(message = "配置ID不能为空")
	@JsonSerialize(using = ToStringSerializer.class)
	@Schema(description = "配置主键ID")
	private Long id;

	/**
	 * 并发控制版本
	 */
	@NotNull(message = "并发控制版本不能为空")
	@Schema(description = "并发控制版本")
	private Long lockVersion;

	/**
	 * 目标租户ID（平台超级管理员修改时校验断言）
	 */
	@Schema(description = "目标租户ID")
	private String tenantId;

}
