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
package org.springblade.ai.prompt.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springblade.ai.prompt.enums.VariableType;

import java.io.Serial;
import java.io.Serializable;

/**
 * 提示词变量定义。
 *
 * @author BladeX
 */
@Data
@Schema(description = "提示词变量定义")
public class PromptVariableDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@NotBlank
	@Size(max = 64)
	@Schema(description = "变量名")
	private String name;

	@NotBlank
	@Size(max = 100)
	@Schema(description = "显示名称")
	private String displayName;

	@NotNull
	@Schema(description = "变量类型")
	private VariableType type;

	@NotNull
	@Schema(description = "是否必填")
	private Boolean required;

	@Schema(description = "默认值")
	private Object defaultValue;

	@Schema(description = "示例值")
	private Object exampleValue;

	@Schema(description = "文本最大长度")
	private Integer maxLength;

	@Size(max = 500)
	@Schema(description = "输入说明")
	private String description;
}
