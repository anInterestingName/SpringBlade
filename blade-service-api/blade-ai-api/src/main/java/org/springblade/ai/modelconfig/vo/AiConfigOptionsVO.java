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

/**
 * AI大模型配置选项集合VO
 *
 * @author SpringBlade
 */
@Data
@Schema(description = "AI大模型配置选项集合VO")
public class AiConfigOptionsVO implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * 供应商预设字典项列表
	 */
	@Schema(description = "供应商预设字典项列表")
	private List<PresetOptionVO> providerPresets;

	/**
	 * 模型API协议选项列表
	 */
	@Schema(description = "模型API协议选项列表")
	private List<SimpleOptionVO> apiProtocols;

	/**
	 * 模型能力类型选项列表
	 */
	@Schema(description = "模型能力类型选项列表")
	private List<SimpleOptionVO> capabilityTypes;

	@Data
	@Schema(description = "预设选项VO")
	public static class PresetOptionVO implements Serializable {
		private static final long serialVersionUID = 1L;

		@JsonSerialize(using = ToStringSerializer.class)
		@Schema(description = "字典项ID")
		private Long id;

		@Schema(description = "字典项值/供应商名称")
		private String value;
	}

	@Data
	@Schema(description = "简单字典选项VO")
	public static class SimpleOptionVO implements Serializable {
		private static final long serialVersionUID = 1L;

		@Schema(description = "稳定字典值")
		private String value;
	}

}
