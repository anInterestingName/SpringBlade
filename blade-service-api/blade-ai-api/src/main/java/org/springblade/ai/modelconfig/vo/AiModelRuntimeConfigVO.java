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
package org.springblade.ai.modelconfig.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * AI模型内部运行配置VO（含运行凭据，仅限受控内部调用）
 *
 * @author SpringBlade
 */
@Data
@Schema(description = "AI模型内部运行配置VO")
public class AiModelRuntimeConfigVO implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * 模型配置ID
	 */
	@JsonSerialize(using = ToStringSerializer.class)
	@Schema(description = "模型配置ID")
	private Long modelConfigId;

	/**
	 * 供应商配置ID
	 */
	@JsonSerialize(using = ToStringSerializer.class)
	@Schema(description = "供应商配置ID")
	private Long providerId;

	/**
	 * 所属租户ID
	 */
	@Schema(description = "所属租户ID")
	private String tenantId;

	/**
	 * 配置所有人用户ID
	 */
	@JsonSerialize(using = ToStringSerializer.class)
	@Schema(description = "配置所有人用户ID")
	private Long ownerUserId;

	/**
	 * 接入根地址
	 */
	@Schema(description = "接入根地址")
	private String baseUrl;

	/**
	 * 接入凭据（运行时原值）
	 */
	@Schema(description = "接入凭据")
	private String apiKeyValue;

	/**
	 * 上游模型标识
	 */
	@Schema(description = "上游模型标识")
	private String upstreamModelId;

	/**
	 * 模型API协议稳定标识
	 */
	@Schema(description = "模型API协议稳定标识")
	private String apiProtocol;

	/**
	 * 模型主要能力类型
	 */
	@Schema(description = "模型主要能力类型")
	private String capabilityType;

	/**
	 * 上下文窗口
	 */
	@Schema(description = "上下文窗口")
	private Integer contextWindow;

	/**
	 * 单模型输入模态数组
	 */
	@Schema(description = "单模型输入模态数组")
	private List<String> inputTypes;

	/**
	 * 单模型推理档位映射
	 */
	@Schema(description = "单模型推理档位映射")
	private Map<String, String> reasoningEfforts;

}
