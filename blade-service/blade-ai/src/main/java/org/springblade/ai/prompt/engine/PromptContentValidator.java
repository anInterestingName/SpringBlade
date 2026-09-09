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

import lombok.RequiredArgsConstructor;
import org.springblade.ai.config.PromptProperties;
import org.springblade.ai.prompt.dto.PromptVariableDTO;
import org.springblade.ai.prompt.enums.VariableType;
import org.springblade.ai.prompt.vo.PromptValidationIssueVO;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 提示词内容、变量定义和运行时输入校验器。 @author BladeX */
@Component
@RequiredArgsConstructor
public class PromptContentValidator {
	private final PromptProperties properties;
	private final PromptTemplateParser parser;

	public PromptValidationResult validateDraft(String fixed, String user, List<PromptVariableDTO> variables) {
		return validateTemplate(fixed, user, variables, false);
	}

	public PromptValidationResult validatePublish(String fixed, String user, List<PromptVariableDTO> variables) {
		return validateTemplate(fixed, user, variables, true);
	}

	public PromptValidationResult validateRender(String fixed, String user, List<PromptVariableDTO> variables,
		Map<String, Object> input) {
		PromptValidationResult result = validateTemplate(fixed, user, variables, true);
		Map<String, Object> values = input == null ? Collections.emptyMap() : input;
		for (PromptVariableDTO definition : result.getDefinitions().values()) {
			String name = definition.getName();
			Object value = values.containsKey(name) ? values.get(name) : definition.getDefaultValue();
			if (value == null) {
				if (Boolean.TRUE.equals(definition.getRequired())) {
					result.getErrors().add(issue("variables", name, "必填变量缺失且未配置默认值"));
				} else {
					result.getResolvedValues().put(name, "");
					result.getWarnings().add(issue("variables", name, "可选变量缺失，已替换为空字符串"));
				}
				continue;
			}
			if (isValueValid(definition, value, result.getErrors(), "variables")) {
				result.getResolvedValues().put(name, value);
			}
		}
		for (String name : values.keySet()) {
			if (!result.getDefinitions().containsKey(name)) {
				result.getWarnings().add(issue("variables", name, "提交了未定义变量，已忽略"));
			}
		}
		return result;
	}

	private PromptValidationResult validateTemplate(String fixed, String user, List<PromptVariableDTO> variables,
		boolean strictReferences) {
		PromptValidationResult result = new PromptValidationResult();
		if (isBlank(fixed) && isBlank(user)) {
			result.getErrors().add(issue("content", null, "固定指令和用户模板不能同时为空"));
		}
		if (length(fixed) > properties.getMaxFixedLength()) {
			result.getErrors().add(issue("fixedInstruction", null, "固定指令超过最大长度"));
		}
		if (length(user) > properties.getMaxUserTemplateLength()) {
			result.getErrors().add(issue("userTemplate", null, "用户模板超过最大长度"));
		}

		List<PromptVariableDTO> definitions = variables == null ? List.of() : variables;
		if (definitions.size() > properties.getMaxVariableCount()) {
			result.getErrors().add(issue("variables", null, "变量数量超过最大限制"));
		}
		Set<String> names = new HashSet<>();
		for (PromptVariableDTO definition : definitions) {
			if (definition == null) {
				result.getErrors().add(issue("variables", null, "变量定义不能为空"));
				continue;
			}
			String name = definition.getName();
			if (name == null || !PromptTemplateParser.VARIABLE_NAME.matcher(name).matches()) {
				result.getErrors().add(issue("variables", name, "变量名不符合命名规则"));
				continue;
			}
			if (!names.add(name)) {
				result.getErrors().add(issue("variables", name, "变量定义重复"));
				continue;
			}
			if (definition.getType() == null) {
				result.getErrors().add(issue("variables", name, "变量类型不能为空"));
				continue;
			}
			if (definition.getRequired() == null) {
				result.getErrors().add(issue("variables", name, "是否必填不能为空"));
			}
			validateDefinitionValue(definition, definition.getDefaultValue(), "默认值", result);
			validateDefinitionValue(definition, definition.getExampleValue(), "示例值", result);
			if (!isText(definition.getType()) && definition.getMaxLength() != null) {
				result.getErrors().add(issue("variables", name, "只有文本变量可以设置最大长度"));
			}
			if (isText(definition.getType()) && definition.getMaxLength() != null && definition.getMaxLength() <= 0) {
				result.getErrors().add(issue("variables", name, "最大长度必须大于0"));
			}
			result.getDefinitions().put(name, definition);
		}

		mergeParse(result, parser.parse("fixedInstruction", fixed));
		mergeParse(result, parser.parse("userTemplate", user));
		for (String reference : result.getReferences()) {
			if (!result.getDefinitions().containsKey(reference)) {
				PromptValidationIssueVO issue = issue("variables", reference, "模板引用了未声明变量");
				if (strictReferences) {
					result.getErrors().add(issue);
				} else {
					result.getWarnings().add(issue);
				}
			}
		}
		for (String name : result.getDefinitions().keySet()) {
			if (!result.getReferences().contains(name)) {
				result.getWarnings().add(issue("variables", name, "变量已声明但未被模板引用"));
			}
		}
		return result;
	}

	private void mergeParse(PromptValidationResult target, PromptParseResult parsed) {
		target.getReferences().addAll(parsed.getReferences());
		target.getErrors().addAll(parsed.getErrors());
	}

	private void validateDefinitionValue(PromptVariableDTO definition, Object value, String label,
		PromptValidationResult result) {
		if (value != null && !isValueValid(definition, value, result.getErrors(), "variables")) {
			int last = result.getErrors().size() - 1;
			PromptValidationIssueVO issue = result.getErrors().get(last);
			issue.setMessage(label + issue.getMessage());
		}
	}

	private boolean isValueValid(PromptVariableDTO definition, Object value,
		List<PromptValidationIssueVO> errors, String field) {
		boolean validType = switch (definition.getType()) {
			case TEXT, MULTILINE_TEXT -> value instanceof String;
			case NUMBER -> value instanceof Number;
			case BOOLEAN -> value instanceof Boolean;
		};
		if (!validType) {
			errors.add(issue(field, definition.getName(), "类型与定义不匹配"));
			return false;
		}
		if (value instanceof String text && definition.getMaxLength() != null
			&& text.length() > definition.getMaxLength()) {
			errors.add(issue(field, definition.getName(), "长度超过变量最大限制"));
			return false;
		}
		return true;
	}

	private boolean isText(VariableType type) {
		return type == VariableType.TEXT || type == VariableType.MULTILINE_TEXT;
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private int length(String value) {
		return value == null ? 0 : value.length();
	}

	private PromptValidationIssueVO issue(String field, String variableName, String message) {
		return new PromptValidationIssueVO(field, variableName, message);
	}
}
