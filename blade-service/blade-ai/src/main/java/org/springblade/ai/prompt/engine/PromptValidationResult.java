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
package org.springblade.ai.prompt.engine;

import lombok.Getter;
import org.springblade.ai.prompt.dto.PromptVariableDTO;
import org.springblade.ai.prompt.vo.PromptValidationIssueVO;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 提示词校验结果。 @author BladeX */
@Getter
public class PromptValidationResult {
	private final List<PromptValidationIssueVO> errors = new ArrayList<>();
	private final List<PromptValidationIssueVO> warnings = new ArrayList<>();
	private final Set<String> references = new LinkedHashSet<>();
	private final Map<String, PromptVariableDTO> definitions = new LinkedHashMap<>();
	private final Map<String, Object> resolvedValues = new LinkedHashMap<>();

	public boolean isValid() {
		return errors.isEmpty();
	}
}
