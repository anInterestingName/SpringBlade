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
package org.springblade.ai.prompt.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** 提示词渲染结果。 @author BladeX */
@Data
public class PromptRenderVO implements Serializable {
	@Serial private static final long serialVersionUID = 1L;
	private Boolean valid;
	private String promptCode;
	private String promptType;
	private String promptTypeName;
	@JsonSerialize(using = ToStringSerializer.class) private Long versionId;
	private Integer versionNo;
	private String fixedInstruction;
	private String userMessage;
	private List<PromptMessageVO> messages = new ArrayList<>();
	private List<String> referencedVariables = new ArrayList<>();
	private List<String> unresolvedVariables = new ArrayList<>();
	private List<PromptValidationIssueVO> errors = new ArrayList<>();
	private List<PromptValidationIssueVO> warnings = new ArrayList<>();
}
