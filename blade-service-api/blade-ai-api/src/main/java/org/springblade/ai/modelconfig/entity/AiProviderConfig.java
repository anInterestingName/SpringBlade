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
 * 用户大模型供应商接入配置实体
 *
 * @author SpringBlade
 */
@Data
@TableName("blade_ai_provider_config")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "用户大模型供应商接入配置实体")
public class AiProviderConfig extends TenantEntity {

	private static final long serialVersionUID = 1L;

	/**
	 * 所有人范围内的供应商管理编码
	 */
	@Schema(description = "所有人范围内的供应商管理编码")
	private String providerCode;

	/**
	 * 供应商显示名称快照
	 */
	@Schema(description = "供应商显示名称快照")
	private String providerName;

	/**
	 * 供应商预设字典项ID
	 */
	@Schema(description = "供应商预设字典项ID")
	@TableField(updateStrategy = FieldStrategy.ALWAYS)
	private Long presetDictId;

	/**
	 * 接入根地址
	 */
	@Schema(description = "接入根地址")
	private String baseUrl;

	/**
	 * 用户提供的接入凭据（管理查询不返回）
	 */
	@Schema(description = "用户提供的接入凭据")
	private String apiKeyValue;

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
