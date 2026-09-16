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
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** 提示词创建参数。 @author BladeX */
@Data
@Schema(description = "提示词创建参数")
public class PromptCreateDTO implements Serializable {
	@Serial private static final long serialVersionUID = 1L;
	@NotBlank @Size(max = 100) private String promptName;
	@NotBlank @Size(max = 64) private String promptCode;
	@NotBlank @Size(max = 32) private String promptType;
	@NotNull private Integer publishMode;
	private String fixedInstruction;
	private String userTemplate;
	@Valid @NotNull private List<PromptVariableDTO> variables = new ArrayList<>();
	@Size(max = 500) private String changeNote;
}
