/**
 * Copyright (c) 2018-2099, Chill Zhuang 庄骞 (bladejava@qq.com).
 * Licensed under the Apache License, Version 2.0.
 */
package org.springblade.ai.prompt.reverse.service;

import lombok.RequiredArgsConstructor;
import org.springblade.ai.config.PromptReverseProperties;
import org.springblade.ai.prompt.constant.PromptReverseResultCode;
import org.springblade.ai.prompt.dto.PromptRenderRequest;
import org.springblade.ai.prompt.entity.Prompt;
import org.springblade.ai.prompt.enums.PromptStatus;
import org.springblade.ai.prompt.enums.PromptType;
import org.springblade.ai.prompt.enums.PublishMode;
import org.springblade.ai.prompt.reverse.model.PromptStrategy;
import org.springblade.ai.prompt.service.IPromptRenderService;
import org.springblade.ai.prompt.service.IPromptService;
import org.springblade.ai.prompt.service.PromptSystemPolicyGuard;
import org.springblade.ai.prompt.vo.PromptMessageVO;
import org.springblade.ai.prompt.vo.PromptRenderVO;
import org.springblade.ai.prompt.vo.PromptStrategyVersionVO;
import org.springblade.core.log.exception.ServiceException;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** 图片反推当前发布系统策略读取与渲染。 @author BladeX */
@Service
@RequiredArgsConstructor
public class PromptStrategyService {
	private static final Pattern CONTENT_HASH = Pattern.compile("^[0-9a-f]{64}$");

	private final IPromptService promptService;
	private final IPromptRenderService renderService;
	private final PromptReverseProperties properties;

	public PromptStrategy current() {
		Prompt prompt = promptService.getTenantPromptByCode(PromptSystemPolicyGuard.IMAGE_PROMPT_REVERSE);
		if (prompt == null || !Objects.equals(prompt.getStatus(), PromptStatus.PUBLISHED.getValue())
			|| prompt.getCurrentVersionId() == null || prompt.getCurrentVersionNo() == null
			|| !PromptType.SYSTEM.getCode().equals(prompt.getPromptType())
			|| !Objects.equals(prompt.getPublishMode(), PublishMode.MANUAL.getValue())) {
			throw invalid();
		}

		PromptRenderRequest request = new PromptRenderRequest();
		request.setCode(PromptSystemPolicyGuard.IMAGE_PROMPT_REVERSE);
		Map<String, Object> variables = new LinkedHashMap<>();
		variables.put("schemaVersion", "1.0");
		variables.put("outputLanguage", properties.getOutputLanguage());
		variables.put("targetEngine", properties.getTargetEngine());
		request.setVariables(variables);

		try {
			PromptRenderVO rendered = renderService.render(request);
			validateRendered(rendered);
			PromptStrategyVersionVO version = new PromptStrategyVersionVO();
			version.setCode(rendered.getPromptCode());
			version.setPromptType(rendered.getPromptType());
			version.setVersionId(rendered.getVersionId());
			version.setVersionNo(rendered.getVersionNo());
			version.setContentHash(rendered.getContentHash());
			return new PromptStrategy(version, List.copyOf(rendered.getMessages()));
		} catch (ServiceException exception) {
			if (exception.getResultCode() == PromptReverseResultCode.STRATEGY_INVALID) {
				throw exception;
			}
			throw new ServiceException(PromptReverseResultCode.STRATEGY_INVALID);
		} catch (RuntimeException exception) {
			throw new ServiceException(PromptReverseResultCode.STRATEGY_INVALID);
		}
	}

	private void validateRendered(PromptRenderVO rendered) {
		if (rendered == null || !Boolean.TRUE.equals(rendered.getValid())
			|| !PromptSystemPolicyGuard.IMAGE_PROMPT_REVERSE.equals(rendered.getPromptCode())
			|| !PromptType.SYSTEM.getCode().equals(rendered.getPromptType())
			|| rendered.getVersionId() == null || rendered.getVersionNo() == null
			|| rendered.getVersionNo() <= 0 || rendered.getContentHash() == null
			|| !CONTENT_HASH.matcher(rendered.getContentHash()).matches()) {
			throw invalid();
		}
		List<PromptMessageVO> messages = rendered.getMessages();
		if (messages == null || messages.isEmpty() || messages.size() > 2
			|| !validMessage(messages.getFirst(), "SYSTEM")) {
			throw invalid();
		}
		if (messages.size() == 2 && !validMessage(messages.get(1), "USER")) {
			throw invalid();
		}
	}

	private boolean validMessage(PromptMessageVO message, String role) {
		return message != null && role.equals(message.getRole())
			&& message.getContent() != null && !message.getContent().isBlank();
	}

	private ServiceException invalid() {
		return new ServiceException(PromptReverseResultCode.STRATEGY_INVALID);
	}
}
