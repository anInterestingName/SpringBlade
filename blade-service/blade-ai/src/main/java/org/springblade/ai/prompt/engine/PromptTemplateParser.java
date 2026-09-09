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

import org.springblade.ai.prompt.vo.PromptValidationIssueVO;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 仅识别双花括号变量的无执行模板解析器。 @author BladeX */
@Component
public class PromptTemplateParser {
	public static final Pattern VARIABLE_NAME = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,63}$");
	private static final Pattern TOKEN = Pattern.compile("\\{\\{([^{}]*)}}", Pattern.DOTALL);

	public PromptParseResult parse(String field, String template) {
		PromptParseResult result = new PromptParseResult();
		if (template == null || template.isEmpty()) {
			return result;
		}
		Matcher matcher = TOKEN.matcher(template);
		StringBuilder unmatched = new StringBuilder();
		while (matcher.find()) {
			String name = matcher.group(1);
			if (VARIABLE_NAME.matcher(name).matches()) {
				result.getReferences().add(name);
			} else {
				result.getErrors().add(new PromptValidationIssueVO(field, name, "变量标记名称不合法"));
			}
			matcher.appendReplacement(unmatched, "");
		}
		matcher.appendTail(unmatched);
		if (unmatched.indexOf("{{") >= 0 || unmatched.indexOf("}}") >= 0) {
			result.getErrors().add(new PromptValidationIssueVO(field, null, "模板包含未闭合或无法解析的双花括号"));
		}
		return result;
	}
}
