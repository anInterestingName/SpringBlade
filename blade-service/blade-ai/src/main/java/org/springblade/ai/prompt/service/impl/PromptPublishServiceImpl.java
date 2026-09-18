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
import org.springblade.ai.prompt.dto.PromptDisableDTO;
import org.springblade.ai.prompt.dto.PromptPublishDTO;
import org.springblade.ai.prompt.dto.PromptRollbackDTO;
import org.springblade.ai.prompt.engine.PromptContentValidator;
import org.springblade.ai.prompt.engine.PromptSchemaCodec;
import org.springblade.ai.prompt.engine.PromptValidationResult;
import org.springblade.ai.prompt.entity.Prompt;
import org.springblade.ai.prompt.entity.PromptVersion;
import org.springblade.ai.prompt.enums.PromptStatus;
import org.springblade.ai.prompt.enums.PromptType;
import org.springblade.ai.prompt.enums.PublishMode;
import org.springblade.ai.prompt.enums.VersionSourceType;
import org.springblade.ai.prompt.mapper.PromptMapper;
import org.springblade.ai.prompt.mapper.PromptVersionMapper;
import org.springblade.ai.prompt.service.IPromptPublishService;
import org.springblade.ai.prompt.service.IPromptService;
import org.springblade.ai.prompt.service.PromptAccessService;
import org.springblade.ai.prompt.service.PromptSystemPolicyGuard;
import org.springblade.ai.prompt.service.PromptVersionFactory;
import org.springblade.ai.prompt.vo.PromptMutationVO;
import org.springblade.core.log.exception.ServiceException;
import org.springblade.core.secure.utils.SecureUtil;
import org.springblade.core.tool.api.ResultCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 提示词发布、停用和回滚事务实现。
 *
 * @author BladeX
 */
@Service
@RequiredArgsConstructor
public class PromptPublishServiceImpl implements IPromptPublishService {

	private final IPromptService promptService;
	private final PromptMapper promptMapper;
	private final PromptVersionMapper versionMapper;
	private final PromptSchemaCodec schemaCodec;
	private final PromptContentValidator validator;
	private final PromptAccessService accessService;
	private final PromptSystemPolicyGuard systemPolicyGuard;
	private final PromptVersionFactory versionFactory;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public PromptMutationVO publish(PromptPublishDTO dto) {
		String tenantId = promptService.currentTenantId();
		Prompt accessible = promptService.getTenantPrompt(dto.getId());
		Prompt prompt = lockedPrompt(tenantId, accessible, dto.getLockVersion());
		systemPolicyGuard.requireManage(prompt);
		if (!Objects.equals(prompt.getPublishMode(), PublishMode.MANUAL.getValue())) {
			throw new ServiceException(PromptResultCode.PROMPT_PUBLISH_MODE_CONFLICT);
		}
		PromptValidationResult validation = validator.validatePublish(prompt.getFixedInstruction(), prompt.getUserTemplate(),
			schemaCodec.decode(prompt.getVariableSchema()));
		if (!validation.isValid()) {
			throw new ServiceException(PromptResultCode.PROMPT_TEMPLATE_INVALID.detail(firstIssue(validation)));
		}
		int versionNo = prompt.getCurrentVersionNo() + 1;
		PromptVersion version = versionFactory.fromPrompt(prompt, versionNo, VersionSourceType.PUBLISH, null,
			prompt.getDraftRevision(), dto.getChangeNote(), currentUserId());
		versionMapper.insert(version);
		if (promptMapper.updatePublishedState(tenantId, prompt.getId(), prompt.getCreateUser(), dto.getLockVersion(),
			version.getId(), versionNo, false, currentUserId()) != 1) {
			throw new ServiceException(PromptResultCode.PROMPT_CONFLICT);
		}
		return mutation(prompt, version, PromptStatus.PUBLISHED, validation);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public PromptMutationVO disable(PromptDisableDTO dto) {
		String tenantId = promptService.currentTenantId();
		Prompt accessible = promptService.getTenantPrompt(dto.getId());
		Prompt prompt = lockedPrompt(tenantId, accessible, dto.getLockVersion());
		systemPolicyGuard.requireManage(prompt);
		if (prompt.getCurrentVersionId() == null) {
			throw new ServiceException(PromptResultCode.PROMPT_NOT_PUBLISHED);
		}
		if (Objects.equals(prompt.getStatus(), PromptStatus.DISABLED.getValue())) {
			throw new ServiceException(PromptResultCode.PROMPT_DISABLED);
		}
		if (promptMapper.updateDisabledState(tenantId, prompt.getId(), prompt.getCreateUser(), dto.getLockVersion(),
			currentUserId()) != 1) {
			throw new ServiceException(PromptResultCode.PROMPT_CONFLICT);
		}
		PromptMutationVO vo = mutationBase(prompt, prompt.getPromptType());
		vo.setStatus(PromptStatus.DISABLED.getValue());
		vo.setLockVersion(dto.getLockVersion() + 1);
		vo.setVersionId(prompt.getCurrentVersionId());
		vo.setVersionNo(prompt.getCurrentVersionNo());
		return vo;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public PromptMutationVO rollback(PromptRollbackDTO dto) {
		String tenantId = promptService.currentTenantId();
		Prompt accessible = promptService.getTenantPrompt(dto.getId());
		Prompt prompt = lockedPrompt(tenantId, accessible, dto.getLockVersion());
		systemPolicyGuard.requireManage(prompt);
		PromptVersion target = versionMapper.selectTenantVersion(tenantId, prompt.getId(), dto.getTargetVersionId());
		if (target == null) {
			throw new ServiceException(PromptResultCode.PROMPT_ROLLBACK_TARGET_INVALID);
		}
		PromptValidationResult validation = validator.validatePublish(target.getFixedInstruction(), target.getUserTemplate(),
			schemaCodec.decode(target.getVariableSchema()));
		if (!validation.isValid()) {
			throw new ServiceException(PromptResultCode.PROMPT_ROLLBACK_TARGET_INVALID.detail(firstIssue(validation)));
		}
		int versionNo = prompt.getCurrentVersionNo() + 1;
		PromptVersion version = versionFactory.fromVersion(target, prompt.getId(), versionNo, dto.getChangeNote(),
			currentUserId());
		versionMapper.insert(version);
		if (promptMapper.updatePublishedState(tenantId, prompt.getId(), prompt.getCreateUser(), dto.getLockVersion(),
			version.getId(), versionNo, true, currentUserId()) != 1) {
			throw new ServiceException(PromptResultCode.PROMPT_CONFLICT);
		}
		return mutation(prompt, version, PromptStatus.PUBLISHED, validation);
	}

	private Prompt lockedPrompt(String tenantId, Prompt accessible, Long expectedLockVersion) {
		Prompt prompt = promptMapper.selectForUpdate(tenantId, accessible.getId());
		accessService.verifyLocked(accessible, prompt);
		if (!Objects.equals(prompt.getLockVersion(), expectedLockVersion)) {
			throw new ServiceException(PromptResultCode.PROMPT_CONFLICT);
		}
		return prompt;
	}

	private PromptMutationVO mutation(Prompt prompt, PromptVersion version, PromptStatus status,
		PromptValidationResult validation) {
		PromptMutationVO vo = mutationBase(prompt, version.getPromptType());
		vo.setStatus(status.getValue());
		vo.setLockVersion(prompt.getLockVersion() + 1);
		vo.setVersionId(version.getId());
		vo.setVersionNo(version.getVersionNo());
		vo.setWarnings(validation.getWarnings());
		return vo;
	}

	private PromptMutationVO mutationBase(Prompt prompt, String promptType) {
		PromptMutationVO vo = new PromptMutationVO();
		vo.setId(prompt.getId());
		PromptType type = PromptType.of(promptType);
		vo.setPromptType(promptType);
		vo.setPromptTypeName(type == null ? null : type.getLabel());
		PublishMode mode = PublishMode.of(prompt.getPublishMode());
		vo.setPublishMode(prompt.getPublishMode());
		vo.setPublishModeName(mode == null ? null : mode.getLabel());
		vo.setDraftRevision(prompt.getDraftRevision());
		return vo;
	}

	private Long currentUserId() {
		Long userId = SecureUtil.getUserId();
		if (userId == null) {
			throw new ServiceException(ResultCode.UN_AUTHORIZED);
		}
		return userId;
	}

	private String firstIssue(PromptValidationResult validation) {
		var issue = validation.getErrors().getFirst();
		String target = issue.getVariableName() == null ? issue.getField() : issue.getVariableName();
		return target + " " + issue.getMessage();
	}

}
