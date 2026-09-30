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
package org.springblade.ai.modelconfig.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springblade.core.mp.base.TenantEntity;

/**
 * 用户大模型配置实体
 *
 * @author SpringBlade
 */
@Data
@TableName("blade_ai_model_config")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "用户大模型配置实体")
public class AiModelConfig extends TenantEntity {

	private static final long serialVersionUID = 1L;

	/**
	 * 所属供应商配置ID
	 */
	@Schema(description = "所属供应商配置ID")
	private Long providerId;

	/**
	 * 所有人范围内的模型管理编码
	 */
	@Schema(description = "所有人范围内的模型管理编码")
	private String modelCode;

	/**
	 * 模型显示名称
	 */
	@Schema(description = "模型显示名称")
	private String modelName;

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
	@TableField(updateStrategy = FieldStrategy.ALWAYS)
	private Integer contextWindow;

	/**
	 * 单模型输入模态JSON数组
	 */
	@Schema(description = "单模型输入模态JSON数组")
	private String inputTypes;

	/**
	 * 单模型推理档位JSON对象
	 */
	@Schema(description = "单模型推理档位JSON对象")
	@TableField(updateStrategy = FieldStrategy.ALWAYS)
	private String reasoningEfforts;

	/**
	 * 配置所有人
	 */
	@Schema(description = "配置所有人")
	private Long ownerUserId;

	/**
	 * 并发控制版本
	 */
	@Schema(description = "并发控制版本")
	private Long lockVersion;

}
