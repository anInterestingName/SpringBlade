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
package org.springblade.ai.prompt.service.impl;

import lombok.RequiredArgsConstructor;
import org.springblade.ai.prompt.constant.PromptResultCode;
import org.springblade.ai.prompt.dto.PromptPreviewDTO;
import org.springblade.ai.prompt.dto.PromptRenderRequest;
import org.springblade.ai.prompt.engine.PromptContentValidator;
import org.springblade.ai.prompt.engine.PromptSchemaCodec;
import org.springblade.ai.prompt.engine.PromptTemplateRenderer;
import org.springblade.ai.prompt.engine.PromptValidationResult;
import org.springblade.ai.prompt.entity.Prompt;
import org.springblade.ai.prompt.entity.PromptVersion;
import org.springblade.ai.prompt.enums.PromptStatus;
import org.springblade.ai.prompt.enums.PromptType;
import org.springblade.ai.prompt.mapper.PromptVersionMapper;
import org.springblade.ai.prompt.service.IPromptRenderService;
import org.springblade.ai.prompt.service.IPromptService;
import org.springblade.ai.prompt.vo.PromptRenderVO;
import org.springblade.core.log.exception.ServiceException;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Objects;

/** 提示词预览与运行时渲染实现。 @author BladeX */
@Service
@RequiredArgsConstructor
public class PromptRenderServiceImpl implements IPromptRenderService {
	private final IPromptService promptService;
	private final PromptVersionMapper versionMapper;
	private final PromptSchemaCodec schemaCodec;
	private final PromptContentValidator validator;
	private final PromptTemplateRenderer renderer;

	@Override
	public PromptRenderVO preview(PromptPreviewDTO dto) {
		PromptType type = PromptType.of(dto.getPromptType());
		if (type == null) {
			throw new ServiceException(PromptResultCode.PROMPT_TYPE_INVALID);
		}
		PromptValidationResult validation = validator.validateRender(dto.getFixedInstruction(), dto.getUserTemplate(),
			dto.getVariables(), dto.getTestVariables());
		return renderer.render(null, type.getCode(), null, null, dto.getFixedInstruction(), dto.getUserTemplate(), validation);
	}

	@Override
	public PromptRenderVO render(PromptRenderRequest request) {
		String tenantId = promptService.currentTenantId();
		String code = request.getCode().trim().toLowerCase(Locale.ROOT);
		Prompt prompt = promptService.getTenantPromptByCode(code);
		if (prompt == null) {
			throw new ServiceException(PromptResultCode.PROMPT_NOT_FOUND);
		}
		if (Objects.equals(prompt.getStatus(), PromptStatus.DISABLED.getValue())) {
			throw new ServiceException(PromptResultCode.PROMPT_DISABLED);
		}
		if (!Objects.equals(prompt.getStatus(), PromptStatus.PUBLISHED.getValue()) || prompt.getCurrentVersionId() == null) {
			throw new ServiceException(PromptResultCode.PROMPT_NOT_PUBLISHED);
		}
		PromptVersion version = versionMapper.selectTenantVersion(tenantId, prompt.getId(), prompt.getCurrentVersionId());
		if (version == null) {
			throw new ServiceException(PromptResultCode.PROMPT_NOT_PUBLISHED);
		}
		PromptValidationResult validation = validator.validateRender(version.getFixedInstruction(), version.getUserTemplate(),
			schemaCodec.decode(version.getVariableSchema()), request.getVariables());
		if (!validation.isValid()) {
			throw new ServiceException(PromptResultCode.PROMPT_VARIABLE_INVALID.detail(firstIssue(validation)));
		}
		PromptRenderVO rendered = renderer.render(version.getPromptCode(), version.getPromptType(), version.getId(), version.getVersionNo(),
			version.getFixedInstruction(), version.getUserTemplate(), validation);
		rendered.setContentHash(version.getContentHash());
		if (!Boolean.TRUE.equals(rendered.getValid())) {
			var issue = rendered.getErrors().getFirst();
			throw new ServiceException(PromptResultCode.PROMPT_VARIABLE_INVALID.detail(issue.getMessage()));
		}
		return rendered;
	}

	private String firstIssue(PromptValidationResult validation) {
		var issue = validation.getErrors().getFirst();
		String target = issue.getVariableName() == null ? issue.getField() : issue.getVariableName();
		return target + " " + issue.getMessage();
	}
}
