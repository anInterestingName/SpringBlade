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
import org.springblade.ai.prompt.engine.PromptHash;
import org.springblade.ai.prompt.engine.PromptSchemaCodec;
import org.springblade.ai.prompt.engine.PromptValidationResult;
import org.springblade.ai.prompt.entity.Prompt;
import org.springblade.ai.prompt.entity.PromptVersion;
import org.springblade.ai.prompt.enums.PromptStatus;
import org.springblade.ai.prompt.enums.VersionSourceType;
import org.springblade.ai.prompt.mapper.PromptMapper;
import org.springblade.ai.prompt.mapper.PromptVersionMapper;
import org.springblade.ai.prompt.service.IPromptPublishService;
import org.springblade.ai.prompt.service.IPromptService;
import org.springblade.ai.prompt.vo.PromptMutationVO;
import org.springblade.core.log.exception.ServiceException;
import org.springblade.core.secure.utils.SecureUtil;
import org.springblade.core.tool.api.ResultCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Objects;

/** 提示词发布、停用和回滚事务实现。 @author BladeX */
@Service
@RequiredArgsConstructor
public class PromptPublishServiceImpl implements IPromptPublishService {
	private final IPromptService promptService;
	private final PromptMapper promptMapper;
	private final PromptVersionMapper versionMapper;
	private final PromptSchemaCodec schemaCodec;
	private final PromptContentValidator validator;
	private final PromptHash promptHash;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public PromptMutationVO publish(PromptPublishDTO dto) {
		String tenantId = promptService.currentTenantId();
		Prompt prompt = lockedPrompt(tenantId, dto.getId(), dto.getLockVersion());
		PromptValidationResult validation = validator.validatePublish(prompt.getFixedInstruction(), prompt.getUserTemplate(),
			schemaCodec.decode(prompt.getVariableSchema()));
		if (!validation.isValid()) {
			throw new ServiceException(PromptResultCode.PROMPT_TEMPLATE_INVALID.detail(firstIssue(validation)));
		}
		int versionNo = prompt.getCurrentVersionNo() + 1;
		PromptVersion version = buildVersion(prompt, versionNo, VersionSourceType.PUBLISH, null,
			prompt.getDraftRevision(), dto.getChangeNote());
		versionMapper.insert(version);
		if (promptMapper.updatePublishedState(tenantId, prompt.getId(), dto.getLockVersion(), version.getId(),
			versionNo, false, currentUserId()) != 1) {
			throw new ServiceException(PromptResultCode.PROMPT_CONFLICT);
		}
		return mutation(prompt, version, PromptStatus.PUBLISHED, false, validation);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public PromptMutationVO disable(PromptDisableDTO dto) {
		String tenantId = promptService.currentTenantId();
		Prompt prompt = lockedPrompt(tenantId, dto.getId(), dto.getLockVersion());
		if (prompt.getCurrentVersionId() == null) {
			throw new ServiceException(PromptResultCode.PROMPT_NOT_PUBLISHED);
		}
		if (Objects.equals(prompt.getStatus(), PromptStatus.DISABLED.getValue())) {
			throw new ServiceException(PromptResultCode.PROMPT_DISABLED);
		}
		if (promptMapper.updateDisabledState(tenantId, prompt.getId(), dto.getLockVersion(), currentUserId()) != 1) {
			throw new ServiceException(PromptResultCode.PROMPT_CONFLICT);
		}
		PromptMutationVO vo = new PromptMutationVO();
		vo.setId(prompt.getId());
		vo.setStatus(PromptStatus.DISABLED.getValue());
		vo.setLockVersion(dto.getLockVersion() + 1);
		vo.setDraftRevision(prompt.getDraftRevision());
		vo.setVersionId(prompt.getCurrentVersionId());
		vo.setVersionNo(prompt.getCurrentVersionNo());
		return vo;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public PromptMutationVO rollback(PromptRollbackDTO dto) {
		String tenantId = promptService.currentTenantId();
		Prompt prompt = lockedPrompt(tenantId, dto.getId(), dto.getLockVersion());
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
		PromptVersion version = buildVersion(target, prompt.getId(), versionNo, dto.getChangeNote());
		versionMapper.insert(version);
		if (promptMapper.updatePublishedState(tenantId, prompt.getId(), dto.getLockVersion(), version.getId(),
			versionNo, true, currentUserId()) != 1) {
			throw new ServiceException(PromptResultCode.PROMPT_CONFLICT);
		}
		return mutation(prompt, version, PromptStatus.PUBLISHED, true, validation);
	}

	private Prompt lockedPrompt(String tenantId, Long id, Long expectedLockVersion) {
		Prompt prompt = promptMapper.selectForUpdate(tenantId, id);
		if (prompt == null) {
			throw new ServiceException(PromptResultCode.PROMPT_NOT_FOUND);
		}
		if (!Objects.equals(prompt.getLockVersion(), expectedLockVersion)) {
			throw new ServiceException(PromptResultCode.PROMPT_CONFLICT);
		}
		return prompt;
	}

	private PromptVersion buildVersion(Prompt prompt, int versionNo, VersionSourceType sourceType,
		Long sourceVersionId, Long sourceDraftRevision, String note) {
		PromptVersion version = new PromptVersion();
		version.setPromptId(prompt.getId());
		version.setVersionNo(versionNo);
		version.setPromptCode(prompt.getPromptCode());
		version.setPromptName(prompt.getPromptName());
		version.setFixedInstruction(prompt.getFixedInstruction());
		version.setUserTemplate(prompt.getUserTemplate());
		version.setVariableSchema(prompt.getVariableSchema());
		version.setSourceType(sourceType.getValue());
		version.setSourceVersionId(sourceVersionId);
		version.setSourceDraftRevision(sourceDraftRevision);
		version.setContentHash(promptHash.calculate(prompt.getPromptCode(), prompt.getPromptName(),
			prompt.getFixedInstruction(), prompt.getUserTemplate(), prompt.getVariableSchema()));
		version.setChangeNote(note.trim());
		version.setPublishUser(currentUserId());
		version.setPublishTime(new Date());
		version.setStatus(1);
		version.setTenantId(prompt.getTenantId());
		version.setIsDeleted(0);
		return version;
	}

	private PromptVersion buildVersion(PromptVersion target, Long promptId, int versionNo, String note) {
		PromptVersion version = new PromptVersion();
		version.setPromptId(promptId);
		version.setVersionNo(versionNo);
		version.setPromptCode(target.getPromptCode());
		version.setPromptName(target.getPromptName());
		version.setFixedInstruction(target.getFixedInstruction());
		version.setUserTemplate(target.getUserTemplate());
		version.setVariableSchema(target.getVariableSchema());
		version.setSourceType(VersionSourceType.ROLLBACK.getValue());
		version.setSourceVersionId(target.getId());
		version.setContentHash(promptHash.calculate(target.getPromptCode(), target.getPromptName(),
			target.getFixedInstruction(), target.getUserTemplate(), target.getVariableSchema()));
		version.setChangeNote(note.trim());
		version.setPublishUser(currentUserId());
		version.setPublishTime(new Date());
		version.setStatus(1);
		version.setTenantId(target.getTenantId());
		version.setIsDeleted(0);
		return version;
	}

	private PromptMutationVO mutation(Prompt prompt, PromptVersion version, PromptStatus status, boolean draftDirty,
		PromptValidationResult validation) {
		PromptMutationVO vo = new PromptMutationVO();
		vo.setId(prompt.getId());
		vo.setStatus(status.getValue());
		vo.setLockVersion(prompt.getLockVersion() + 1);
		vo.setDraftRevision(prompt.getDraftRevision());
		vo.setVersionId(version.getId());
		vo.setVersionNo(version.getVersionNo());
		vo.setWarnings(validation.getWarnings());
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
