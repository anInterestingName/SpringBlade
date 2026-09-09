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
import org.springblade.ai.prompt.vo.PromptMessageVO;
import org.springblade.ai.prompt.vo.PromptRenderVO;
import org.springblade.ai.prompt.vo.PromptValidationIssueVO;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 非递归字面量模板渲染器。 @author BladeX */
@Component
@RequiredArgsConstructor
public class PromptTemplateRenderer {
	private static final Pattern TOKEN = Pattern.compile("\\{\\{([A-Za-z][A-Za-z0-9_]{0,63})}}", Pattern.DOTALL);
	private final PromptProperties properties;

	public PromptRenderVO render(String code, Long versionId, Integer versionNo, String fixed, String user,
		PromptValidationResult validation) {
		PromptRenderVO result = new PromptRenderVO();
		result.setPromptCode(code);
		result.setVersionId(versionId);
		result.setVersionNo(versionNo);
		result.setReferencedVariables(validation.getReferences().stream().toList());
		result.setErrors(validation.getErrors());
		result.setWarnings(validation.getWarnings());
		result.setUnresolvedVariables(validation.getErrors().stream()
			.map(PromptValidationIssueVO::getVariableName).filter(name -> name != null && !name.isBlank()).distinct().toList());
		if (!validation.isValid()) {
			result.setValid(false);
			return result;
		}
		String renderedFixed = replace(fixed, validation.getResolvedValues());
		String renderedUser = replace(user, validation.getResolvedValues());
		if (renderedFixed.length() + renderedUser.length() > properties.getMaxRenderedLength()) {
			result.setValid(false);
			result.getErrors().add(new PromptValidationIssueVO("content", null, "渲染结果超过最大长度"));
			return result;
		}
		result.setValid(true);
		result.setFixedInstruction(renderedFixed);
		result.setUserMessage(renderedUser);
		if (!renderedFixed.isBlank()) {
			result.getMessages().add(new PromptMessageVO("SYSTEM", renderedFixed));
		}
		if (!renderedUser.isBlank()) {
			result.getMessages().add(new PromptMessageVO("USER", renderedUser));
		}
		return result;
	}

	private String replace(String template, Map<String, Object> values) {
		if (template == null || template.isEmpty()) {
			return "";
		}
		Matcher matcher = TOKEN.matcher(template);
		StringBuilder output = new StringBuilder();
		while (matcher.find()) {
			Object value = values.get(matcher.group(1));
			matcher.appendReplacement(output, Matcher.quoteReplacement(value == null ? "" : String.valueOf(value)));
		}
		matcher.appendTail(output);
		return output.toString();
	}
}
