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

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springblade.ai.prompt.constant.PromptPermission;
import org.springblade.ai.prompt.constant.PromptResultCode;
import org.springblade.ai.prompt.dto.PromptCopyDTO;
import org.springblade.ai.prompt.dto.PromptCreateDTO;
import org.springblade.ai.prompt.dto.PromptDeleteDTO;
import org.springblade.ai.prompt.dto.PromptUpdateDTO;
import org.springblade.ai.prompt.dto.PromptVariableDTO;
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
import org.springblade.ai.prompt.service.IPromptService;
import org.springblade.ai.prompt.service.PromptAccessService;
import org.springblade.ai.prompt.service.PromptVersionFactory;
import org.springblade.ai.prompt.vo.PromptDetailVO;
import org.springblade.ai.prompt.vo.PromptListVO;
import org.springblade.ai.prompt.vo.PromptMutationVO;
import org.springblade.ai.prompt.vo.PromptVersionVO;
import org.springblade.ai.prompt.wrapper.PromptVersionWrapper;
import org.springblade.ai.prompt.wrapper.PromptWrapper;
import org.springblade.core.log.exception.ServiceException;
import org.springblade.core.mp.base.BaseServiceImpl;
import org.springblade.core.mp.support.Condition;
import org.springblade.core.mp.support.Query;
import org.springblade.core.secure.handler.IPermissionHandler;
import org.springblade.core.secure.utils.SecureUtil;
import org.springblade.core.tool.api.ResultCode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 提示词管理服务实现。
 *
 * @author BladeX
 */
@Service
@RequiredArgsConstructor
public class PromptServiceImpl extends BaseServiceImpl<PromptMapper, Prompt> implements IPromptService {

	private final PromptVersionMapper versionMapper;
	private final PromptContentValidator validator;
	private final PromptSchemaCodec schemaCodec;
	private final PromptWrapper promptWrapper;
	private final PromptVersionWrapper versionWrapper;
	private final PromptAccessService accessService;
	private final PromptVersionFactory versionFactory;
	private final IPermissionHandler permissionHandler;

	@Override
	public IPage<PromptListVO> selectPage(String name, String code, Integer status, String promptType,
		Integer publishMode, Query query) {
		String tenantId = currentTenantId();
		PromptType type = hasText(promptType) ? requirePromptType(promptType) : null;
		PublishMode mode = publishMode == null ? null : requirePublishMode(publishMode);
		Long ownerUserId = accessService.requirePageScope();
		IPage<Prompt> page = baseMapper.selectScopePage(Condition.getPage(query), tenantId,
			trimToNull(name), hasText(code) ? normalizeCode(code) : null, status,
			type == null ? null : type.getCode(), mode == null ? null : mode.getValue(), ownerUserId);
		return page.convert(prompt -> promptWrapper.listVO(prompt,
			prompt.getCurrentVersionNo() != null && prompt.getCurrentVersionNo() > 0));
	}

	@Override
	public PromptDetailVO detail(Long id) {
		Prompt prompt = getTenantPrompt(id);
		boolean hasHistory = versionMapper.countTenantVersions(currentTenantId(), id) > 0;
		PromptDetailVO vo = promptWrapper.detailVO(prompt, hasHistory);
		if (prompt.getCurrentVersionId() != null) {
			vo.setCurrentVersion(versionWrapper.entityVO(versionMapper.selectTenantVersion(currentTenantId(), id,
				prompt.getCurrentVersionId())));
		}
		PromptValidationResult validation = validator.validateDraft(prompt.getFixedInstruction(), prompt.getUserTemplate(),
			schemaCodec.decode(prompt.getVariableSchema()));
		vo.setWarnings(validation.getWarnings());
		return vo;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public PromptMutationVO create(PromptCreateDTO dto) {
		String tenantId = currentTenantId();
		accessService.requireManagementScopes();
		Long userId = currentUserId();
		String code = normalizeCode(dto.getPromptCode());
		PromptType type = requirePromptType(dto.getPromptType());
		PublishMode mode = requirePublishMode(dto.getPublishMode());
		PromptValidationResult validation = mode == PublishMode.AUTO
			? validatePublish(dto.getFixedInstruction(), dto.getUserTemplate(), dto.getVariables())
			: validateDraft(dto.getFixedInstruction(), dto.getUserTemplate(), dto.getVariables());
		if (mode == PublishMode.AUTO) {
			requireAutoPublish(dto.getChangeNote());
		}
		ensureCodeAvailable(tenantId, code);

		Prompt prompt = new Prompt();
		prompt.setTenantId(tenantId);
		prompt.setPromptCode(code);
		prompt.setPromptName(dto.getPromptName().trim());
		prompt.setPromptType(type.getCode());
		prompt.setPublishMode(mode.getValue());
		prompt.setFixedInstruction(dto.getFixedInstruction());
		prompt.setUserTemplate(dto.getUserTemplate());
		prompt.setVariableSchema(schemaCodec.encode(dto.getVariables()));
		prompt.setDraftRevision(1L);
		prompt.setDraftDirty(true);
		prompt.setCurrentVersionNo(0);
		prompt.setLockVersion(0L);
		prompt.setStatus(PromptStatus.DRAFT.getValue());
		prompt.setCreateUser(userId);
		prompt.setUpdateUser(userId);
		prompt.setIsDeleted(0);
		try {
			save(prompt);
		} catch (DuplicateKeyException exception) {
			throw new ServiceException(PromptResultCode.PROMPT_CODE_DUPLICATE, exception);
		}

		PromptVersion version = null;
		if (mode == PublishMode.AUTO) {
			version = versionFactory.fromPrompt(prompt, 1, VersionSourceType.AUTO_PUBLISH, null,
				prompt.getDraftRevision(), dto.getChangeNote(), userId);
			versionMapper.insert(version);
			if (baseMapper.updatePublishedState(tenantId, prompt.getId(), prompt.getCreateUser(), 0L,
				version.getId(), 1, false, userId) != 1) {
				throw new ServiceException(PromptResultCode.PROMPT_CONFLICT);
			}
			prompt.setCurrentVersionId(version.getId());
			prompt.setCurrentVersionNo(1);
			prompt.setDraftDirty(false);
			prompt.setStatus(PromptStatus.PUBLISHED.getValue());
			prompt.setLockVersion(1L);
		}
		PromptMutationVO result = mutation(prompt, version == null ? null : version.getId(),
			version == null ? null : version.getVersionNo());
		result.setWarnings(validation.getWarnings());
		return result;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public PromptMutationVO update(PromptUpdateDTO dto) {
		String tenantId = currentTenantId();
		Prompt existing = getTenantPrompt(dto.getId());
		validateUpdateIdentity(existing, dto.getPromptCode(), dto.getLockVersion());
		PromptType type = requirePromptType(dto.getPromptType());
		PublishMode mode = requirePublishMode(dto.getPublishMode());
		return mode == PublishMode.AUTO
			? autoPublishUpdate(dto, tenantId, existing, type)
			: manualUpdate(dto, tenantId, existing, type);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public PromptMutationVO copy(PromptCopyDTO dto) {
		Prompt source = getTenantPrompt(dto.getSourcePromptId());
		PromptCreateDTO create = new PromptCreateDTO();
		create.setPromptName(dto.getPromptName());
		create.setPromptCode(dto.getPromptCode());
		create.setPromptType(source.getPromptType());
		create.setPublishMode(PublishMode.MANUAL.getValue());
		create.setFixedInstruction(source.getFixedInstruction());
		create.setUserTemplate(source.getUserTemplate());
		create.setVariables(schemaCodec.decode(source.getVariableSchema()));
		return create(create);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean remove(PromptDeleteDTO dto) {
		String tenantId = currentTenantId();
		Prompt prompt = getTenantPrompt(dto.getId());
		if (!Objects.equals(prompt.getLockVersion(), dto.getLockVersion())) {
			throw new ServiceException(PromptResultCode.PROMPT_CONFLICT);
		}
		if (prompt.getCurrentVersionId() != null || versionMapper.countTenantVersions(tenantId, prompt.getId()) > 0) {
			throw new ServiceException(PromptResultCode.PROMPT_DELETE_FORBIDDEN);
		}
		if (baseMapper.logicalDelete(tenantId, prompt.getId(), prompt.getCreateUser(), dto.getLockVersion(),
			currentUserId()) != 1) {
			throw new ServiceException(PromptResultCode.PROMPT_CONFLICT);
		}
		return true;
	}

	@Override
	public IPage<PromptVersionVO> versionPage(Long promptId, Query query) {
		getTenantPrompt(promptId);
		String tenantId = currentTenantId();
		IPage<PromptVersion> page = versionMapper.selectPage(Condition.getPage(query),
			Wrappers.<PromptVersion>lambdaQuery().eq(PromptVersion::getTenantId, tenantId)
				.eq(PromptVersion::getPromptId, promptId).eq(PromptVersion::getIsDeleted, 0)
				.orderByDesc(PromptVersion::getVersionNo));
		return page.convert(versionWrapper::entityVO);
	}

	@Override
	public PromptVersionVO versionDetail(Long promptId, Long versionId) {
		getTenantPrompt(promptId);
		PromptVersion version = versionMapper.selectTenantVersion(currentTenantId(), promptId, versionId);
		if (version == null) {
			throw new ServiceException(PromptResultCode.PROMPT_NOT_FOUND);
		}
		return versionWrapper.entityVO(version);
	}

	@Override
	public Prompt getTenantPrompt(Long id) {
		return accessService.requireAccessible(currentTenantId(), id);
	}

	@Override
	public Prompt getTenantPromptByCode(String code) {
		return baseMapper.selectTenantPromptByCode(currentTenantId(), normalizeCode(code));
	}

	@Override
	public String currentTenantId() {
		String tenantId = SecureUtil.getTenantId();
		if (!SecureUtil.hasAuth() || !hasText(tenantId)) {
			throw new ServiceException(ResultCode.UN_AUTHORIZED);
		}
		return tenantId;
	}

	private PromptMutationVO manualUpdate(PromptUpdateDTO dto, String tenantId, Prompt existing, PromptType type) {
		PromptValidationResult validation = validateDraft(dto.getFixedInstruction(), dto.getUserTemplate(), dto.getVariables());
		Prompt update = draftUpdate(dto, type, PublishMode.MANUAL);
		if (baseMapper.updateDraft(update, tenantId, existing.getCreateUser(), dto.getLockVersion()) != 1) {
			throw new ServiceException(PromptResultCode.PROMPT_CONFLICT);
		}
		update.setStatus(existing.getStatus());
		update.setLockVersion(dto.getLockVersion() + 1);
		update.setDraftRevision(existing.getDraftRevision() + 1);
		PromptMutationVO result = mutation(update, null, null);
		result.setWarnings(validation.getWarnings());
		return result;
	}

	private PromptMutationVO autoPublishUpdate(PromptUpdateDTO dto, String tenantId, Prompt accessible,
		PromptType type) {
		requireAutoPublish(dto.getChangeNote());
		PromptValidationResult validation = validatePublish(dto.getFixedInstruction(), dto.getUserTemplate(),
			dto.getVariables());
		Prompt locked = baseMapper.selectForUpdate(tenantId, dto.getId());
		accessService.verifyLocked(accessible, locked);
		validateUpdateIdentity(locked, dto.getPromptCode(), dto.getLockVersion());

		Prompt update = draftUpdate(dto, type, PublishMode.AUTO);
		update.setPromptCode(locked.getPromptCode());
		update.setTenantId(tenantId);
		update.setCreateUser(locked.getCreateUser());
		update.setDraftRevision(locked.getDraftRevision() + 1);
		int versionNo = locked.getCurrentVersionNo() + 1;
		PromptVersion version = versionFactory.fromPrompt(update, versionNo, VersionSourceType.AUTO_PUBLISH,
			null, update.getDraftRevision(), dto.getChangeNote(), update.getUpdateUser());
		versionMapper.insert(version);
		if (baseMapper.updateAutoPublishedDraft(update, tenantId, locked.getCreateUser(), dto.getLockVersion(),
			version.getId(), versionNo) != 1) {
			throw new ServiceException(PromptResultCode.PROMPT_CONFLICT);
		}
		update.setStatus(PromptStatus.PUBLISHED.getValue());
		update.setDraftDirty(false);
		update.setLockVersion(dto.getLockVersion() + 1);
		PromptMutationVO result = mutation(update, version.getId(), versionNo);
		result.setWarnings(validation.getWarnings());
		return result;
	}

	private Prompt draftUpdate(PromptUpdateDTO dto, PromptType type, PublishMode mode) {
		Prompt update = new Prompt();
		update.setId(dto.getId());
		update.setPromptName(dto.getPromptName().trim());
		update.setPromptType(type.getCode());
		update.setPublishMode(mode.getValue());
		update.setFixedInstruction(dto.getFixedInstruction());
		update.setUserTemplate(dto.getUserTemplate());
		update.setVariableSchema(schemaCodec.encode(dto.getVariables()));
		update.setUpdateUser(currentUserId());
		return update;
	}

	private void validateUpdateIdentity(Prompt prompt, String code, Long lockVersion) {
		if (!Objects.equals(prompt.getPromptCode(), normalizeCode(code))) {
			throw new ServiceException(PromptResultCode.PROMPT_CODE_IMMUTABLE);
		}
		if (!Objects.equals(prompt.getLockVersion(), lockVersion)) {
			throw new ServiceException(PromptResultCode.PROMPT_CONFLICT);
		}
	}

	private PromptValidationResult validateDraft(String fixed, String user, List<PromptVariableDTO> variables) {
		return requireValid(validator.validateDraft(fixed, user, variables));
	}

	private PromptValidationResult validatePublish(String fixed, String user, List<PromptVariableDTO> variables) {
		return requireValid(validator.validatePublish(fixed, user, variables));
	}

	private PromptValidationResult requireValid(PromptValidationResult validation) {
		if (!validation.isValid()) {
			throw new ServiceException(PromptResultCode.PROMPT_TEMPLATE_INVALID.detail(firstIssue(validation)));
		}
		return validation;
	}

	private void requireAutoPublish(String changeNote) {
		if (!hasText(changeNote)) {
			throw new ServiceException(PromptResultCode.PROMPT_AUTO_PUBLISH_NOTE_REQUIRED);
		}
		if (!permissionHandler.hasPermission(PromptPermission.PUBLISH)) {
			throw new ServiceException(ResultCode.REQ_REJECT);
		}
	}

	private PromptType requirePromptType(String code) {
		PromptType type = PromptType.of(code);
		if (type == null) {
			throw new ServiceException(PromptResultCode.PROMPT_TYPE_INVALID);
		}
		return type;
	}

	private PublishMode requirePublishMode(Integer value) {
		PublishMode mode = PublishMode.of(value);
		if (mode == null) {
			throw new ServiceException(PromptResultCode.PROMPT_PUBLISH_MODE_INVALID);
		}
		return mode;
	}

	private String firstIssue(PromptValidationResult validation) {
		var issue = validation.getErrors().getFirst();
		String target = issue.getVariableName() == null ? issue.getField() : issue.getVariableName();
		return target + " " + issue.getMessage();
	}

	private void ensureCodeAvailable(String tenantId, String code) {
		if (baseMapper.selectTenantPromptByCodeIncludingDeleted(tenantId, code) != null) {
			throw new ServiceException(PromptResultCode.PROMPT_CODE_DUPLICATE);
		}
	}

	private PromptMutationVO mutation(Prompt prompt, Long versionId, Integer versionNo) {
		PromptMutationVO vo = new PromptMutationVO();
		vo.setId(prompt.getId());
		PromptType type = PromptType.of(prompt.getPromptType());
		vo.setPromptType(prompt.getPromptType());
		vo.setPromptTypeName(type == null ? null : type.getLabel());
		PublishMode mode = PublishMode.of(prompt.getPublishMode());
		vo.setPublishMode(prompt.getPublishMode());
		vo.setPublishModeName(mode == null ? null : mode.getLabel());
		vo.setStatus(prompt.getStatus());
		vo.setLockVersion(prompt.getLockVersion());
		vo.setDraftRevision(prompt.getDraftRevision());
		vo.setVersionId(versionId);
		vo.setVersionNo(versionNo);
		return vo;
	}

	private Long currentUserId() {
		Long userId = SecureUtil.getUserId();
		if (userId == null) {
			throw new ServiceException(ResultCode.UN_AUTHORIZED);
		}
		return userId;
	}

	private String normalizeCode(String code) {
		return code == null ? null : code.trim().toLowerCase(Locale.ROOT);
	}

	private String trimToNull(String value) {
		return hasText(value) ? value.trim() : null;
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

}
