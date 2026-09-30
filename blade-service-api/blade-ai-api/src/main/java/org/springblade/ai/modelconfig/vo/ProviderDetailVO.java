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
import lombok.EqualsAndHashCode;
import org.springblade.core.tool.utils.DateUtil;

import java.util.Date;

/**
 * 供应商接入配置详情VO
 *
 * @author SpringBlade
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "供应商接入配置详情VO")
public class ProviderDetailVO extends ProviderListVO {

	private static final long serialVersionUID = 1L;

	/**
	 * 创建人
	 */
	@JsonSerialize(using = ToStringSerializer.class)
	@Schema(description = "创建人")
	private Long createUser;

	/**
	 * 创建时间
	 */
	@JsonFormat(pattern = DateUtil.PATTERN_DATETIME)
	@Schema(description = "创建时间")
	private Date createTime;

	/**
	 * 最近修改人
	 */
	@JsonSerialize(using = ToStringSerializer.class)
	@Schema(description = "最近修改人")
	private Long updateUser;

}
