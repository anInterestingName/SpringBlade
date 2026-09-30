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

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springblade.core.tool.utils.DateUtil;

import java.io.Serializable;
import java.util.Date;

/**
 * 供应商接入配置列表VO
 *
 * @author SpringBlade
 */
@Data
@Schema(description = "供应商接入配置列表VO")
public class ProviderListVO implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * 主键
	 */
	@JsonSerialize(using = ToStringSerializer.class)
	@Schema(description = "主键")
	private Long id;

	/**
	 * 供应商管理编码
	 */
	@Schema(description = "供应商管理编码")
	private String providerCode;

	/**
	 * 供应商显示名称
	 */
	@Schema(description = "供应商显示名称")
	private String providerName;

	/**
	 * 供应商预设字典项ID
	 */
	@JsonSerialize(using = ToStringSerializer.class)
	@Schema(description = "供应商预设字典项ID")
	private Long presetDictId;

	/**
	 * 接入根地址
	 */
	@Schema(description = "接入根地址")
	private String baseUrl;

	/**
	 * 租户ID
	 */
	@Schema(description = "租户ID")
	private String tenantId;

	/**
	 * 配置所有人用户ID
	 */
	@JsonSerialize(using = ToStringSerializer.class)
	@Schema(description = "配置所有人用户ID")
	private Long ownerUserId;

	/**
	 * 启停状态（0=停用，1=启用）
	 */
	@Schema(description = "启停状态")
	private Integer status;

	/**
	 * 凭据是否已配置（管理接口不暴露原值）
	 */
	@Schema(description = "凭据是否已配置")
	private Boolean credentialConfigured;

	/**
	 * 并发控制版本
	 */
	@Schema(description = "并发控制版本")
	private Long lockVersion;

	/**
	 * 更新时间
	 */
	@JsonFormat(pattern = DateUtil.PATTERN_DATETIME)
	@Schema(description = "更新时间")
	private Date updateTime;

}
