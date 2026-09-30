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
import java.util.List;
import java.util.Map;

/**
 * 模型配置创建DTO
 *
 * @author SpringBlade
 */
@Data
@Schema(description = "模型配置创建DTO")
public class ModelCreateDTO implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * 所属供应商配置ID
	 */
	@NotNull(message = "所属供应商配置ID不能为空")
	@JsonSerialize(using = ToStringSerializer.class)
	@Schema(description = "所属供应商配置ID")
	private Long providerId;

	/**
	 * 所有人范围内的模型管理编码
	 */
	@NotBlank(message = "模型编码不能为空")
	@Size(max = 64, message = "模型编码不能超过64字符")
	@Schema(description = "模型编码")
	private String modelCode;

	/**
	 * 模型显示名称
	 */
	@NotBlank(message = "模型名称不能为空")
	@Size(max = 100, message = "模型名称不能超过100字符")
	@Schema(description = "模型名称")
	private String modelName;

	/**
	 * 上游模型标识
	 */
	@NotBlank(message = "上游模型标识不能为空")
	@Size(max = 128, message = "上游模型标识不能超过128字符")
	@Schema(description = "上游模型标识")
	private String upstreamModelId;

	/**
	 * 模型API协议稳定标识
	 */
	@NotBlank(message = "API协议不能为空")
	@Size(max = 32, message = "API协议不能超过32字符")
	@Schema(description = "模型API协议稳定标识")
	private String apiProtocol;

	/**
	 * 模型主要能力类型
	 */
	@NotBlank(message = "主要能力类型不能为空")
	@Size(max = 16, message = "主要能力类型不能超过16字符")
	@Schema(description = "模型主要能力类型")
	private String capabilityType;

	/**
	 * 上下文窗口（可选，必须为正数）
	 */
	@Schema(description = "上下文窗口")
	private Integer contextWindow;

	/**
	 * 单模型输入模态数组
	 */
	@NotNull(message = "输入模态不能为空")
	@Schema(description = "单模型输入模态数组")
	private List<String> inputTypes;

	/**
	 * 单模型推理档位映射（可选）
	 */
	@Schema(description = "单模型推理档位映射")
	private Map<String, String> reasoningEfforts;

	/**
	 * 目标租户ID（平台超级管理员创建时校验断言）
	 */
	@Schema(description = "目标租户ID")
	private String tenantId;

	/**
	 * 启停状态（0=停用，1=启用，默认0）
	 */
	@Schema(description = "启停状态")
	private Integer status;

}
