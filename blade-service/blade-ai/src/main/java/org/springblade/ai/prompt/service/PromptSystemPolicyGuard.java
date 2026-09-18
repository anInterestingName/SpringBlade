/**
 * Copyright (c) 2018-2099, Chill Zhuang 庄骞 (bladejava@qq.com).
 * Licensed under the Apache License, Version 2.0.
 */
package org.springblade.ai.prompt.service;

import lombok.RequiredArgsConstructor;
import org.springblade.ai.prompt.constant.PromptPermission;
import org.springblade.ai.prompt.constant.PromptResultCode;
import org.springblade.ai.prompt.entity.Prompt;
import org.springblade.ai.prompt.enums.PromptType;
import org.springblade.ai.prompt.enums.PublishMode;
import org.springblade.core.log.exception.ServiceException;
import org.springblade.core.secure.handler.IPermissionHandler;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** 系统提示词和保留分析策略写操作保护。 @author BladeX */
@Component
@RequiredArgsConstructor
public class PromptSystemPolicyGuard {
	public static final String IMAGE_PROMPT_REVERSE = "image_prompt_reverse";

	private final PromptAccessService accessService;
	private final IPermissionHandler permissionHandler;

	public void requireCreate(String code, PromptType type, PublishMode mode) {
		requireProtectedAccess(null, code, type, mode);
	}

	public void requireUpdate(Prompt current, PromptType type, PublishMode mode) {
		requireProtectedAccess(current, current.getPromptCode(), type, mode);
	}

	public void requireManage(Prompt prompt) {
		PromptType type = PromptType.of(prompt.getPromptType());
		PublishMode mode = PublishMode.of(prompt.getPublishMode());
		requireProtectedAccess(prompt, prompt.getPromptCode(), type, mode);
	}

	private void requireProtectedAccess(Prompt current, String code, PromptType type, PublishMode mode) {
		boolean reserved = IMAGE_PROMPT_REVERSE.equals(code);
		boolean protectedPrompt = reserved || type == PromptType.SYSTEM
			|| current != null && PromptType.SYSTEM.getCode().equals(current.getPromptType());
		if (!protectedPrompt) {
			return;
		}
		if (!permissionHandler.hasPermission(PromptPermission.SYSTEM_MANAGE)
			|| !accessService.hasAllManagementScopes()) {
			throw new ServiceException(PromptResultCode.PROMPT_SYSTEM_POLICY_FORBIDDEN);
		}
		if (reserved && (type != PromptType.SYSTEM || mode != PublishMode.MANUAL)) {
			throw new ServiceException(PromptResultCode.PROMPT_SYSTEM_POLICY_INVALID);
		}
		if (current != null && IMAGE_PROMPT_REVERSE.equals(current.getPromptCode())
			&& (!Objects.equals(code, current.getPromptCode()) || type != PromptType.SYSTEM || mode != PublishMode.MANUAL)) {
			throw new ServiceException(PromptResultCode.PROMPT_SYSTEM_POLICY_INVALID);
		}
	}
}
