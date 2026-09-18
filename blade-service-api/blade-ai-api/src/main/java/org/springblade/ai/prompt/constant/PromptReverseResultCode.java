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
package org.springblade.ai.prompt.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springblade.core.tool.api.IResultCode;

/** 图片提示词反推业务错误码。 @author BladeX */
@Getter
@AllArgsConstructor
public enum PromptReverseResultCode implements IResultCode {
	IMAGE_INVALID(48201, "图片文件不合法"),
	TAXONOMY_EMPTY(48202, "当前租户没有有效标签"),
	TAXONOMY_UNAVAILABLE(48203, "标签服务暂不可用，请重试"),
	STRATEGY_INVALID(48204, "图片分析规则未发布或配置错误"),
	UPSTREAM_UNAVAILABLE(48205, "图片分析服务暂不可用"),
	OUTPUT_INVALID(48206, "图片分析结果无法校验");

	private final int code;
	private final String message;
}
