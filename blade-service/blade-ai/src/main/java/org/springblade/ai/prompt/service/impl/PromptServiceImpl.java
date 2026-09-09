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
import org.springblade.ai.prompt.constant.PromptResultCode;
import org.springblade.ai.prompt.dto.PromptCopyDTO;
import org.springblade.ai.prompt.dto.PromptCreateDTO;
import org.springblade.ai.prompt.dto.PromptDeleteDTO;
import org.springblade.ai.prompt.dto.PromptUpdateDTO;
import org.springblade.ai.prompt.engine.PromptContentValidator;
import org.springblade.ai.prompt.engine.PromptSchemaCodec;
import org.springblade.ai.prompt.engine.PromptValidationResult;
import org.springblade.ai.prompt.entity.Prompt;
import org.springblade.ai.prompt.entity.PromptVersion;
import org.springblade.ai.prompt.enums.PromptStatus;
import org.springblade.ai.prompt.mapper.PromptMapper;
import org.springblade.ai.prompt.mapper.PromptVersionMapper;
import org.springblade.ai.prompt.service.IPromptService;
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
import org.springblade.core.secure.utils.SecureUtil;
import org.springblade.core.tool.api.ResultCode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Objects;

/** 提示词管理服务实现。 @author BladeX */
@Service
@RequiredArgsConstructor
public class PromptServiceImpl extends BaseServiceImpl<PromptMapper, Prompt> implements IPromptService {
	private final PromptVersionMapper versionMapper;
	private final PromptContentValidator validator;
	private final PromptSchemaCodec schemaCodec;
	private final PromptWrapper promptWrapper;
	private final PromptVersionWrapper versionWrapper;

	@Override
	public IPage<PromptListVO> selectPage(String name, String code, Integer status, Query query) {
		String tenantId = currentTenantId();
		var wrapper = Wrappers.<Prompt>lambdaQuery()
			.eq(Prompt::getTenantId, tenantId).eq(Prompt::getIsDeleted, 0)
			.like(hasText(name), Prompt::getPromptName, name)
			.like(hasText(code), Prompt::getPromptCode, normalizeCode(code))
			.eq(status != null, Prompt::getStatus, status)
			.orderByDesc(Prompt::getUpdateTime).orderByDesc(Prompt::getId);
		return page(Condition.getPage(query), wrapper)
			.convert(prompt -> promptWrapper.listVO(prompt, prompt.getCurrentVersionNo() != null && prompt.getCurrentVersionNo() > 0));
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
		String code = normalizeCode(dto.getPromptCode());
		validateDraft(dto.getFixedInstruction(), dto.getUserTemplate(), dto.getVariables());
		ensureCodeAvailable(tenantId, code);
		Prompt prompt = new Prompt();
		prompt.setTenantId(tenantId);
		prompt.setPromptCode(code);
		prompt.setPromptName(dto.getPromptName().trim());
		prompt.setFixedInstruction(dto.getFixedInstruction());
		prompt.setUserTemplate(dto.getUserTemplate());
		prompt.setVariableSchema(schemaCodec.encode(dto.getVariables()));
		prompt.setDraftRevision(1L);
		prompt.setDraftDirty(true);
		prompt.setCurrentVersionNo(0);
		prompt.setLockVersion(0L);
		prompt.setStatus(PromptStatus.DRAFT.getValue());
		prompt.setIsDeleted(0);
		try {
			save(prompt);
		} catch (DuplicateKeyException exception) {
			throw new ServiceException(PromptResultCode.PROMPT_CODE_DUPLICATE, exception);
		}
		return mutation(prompt, null, null);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public PromptMutationVO update(PromptUpdateDTO dto) {
		String tenantId = currentTenantId();
		Prompt existing = getTenantPrompt(dto.getId());
		if (!Objects.equals(existing.getPromptCode(), normalizeCode(dto.getPromptCode()))) {
			throw new ServiceException(PromptResultCode.PROMPT_CODE_IMMUTABLE);
		}
		if (!Objects.equals(existing.getLockVersion(), dto.getLockVersion())) {
			throw new ServiceException(PromptResultCode.PROMPT_CONFLICT);
		}
		PromptValidationResult validation = validateDraft(dto.getFixedInstruction(), dto.getUserTemplate(), dto.getVariables());
		Prompt update = new Prompt();
		update.setId(dto.getId());
		update.setPromptName(dto.getPromptName().trim());
		update.setFixedInstruction(dto.getFixedInstruction());
		update.setUserTemplate(dto.getUserTemplate());
		update.setVariableSchema(schemaCodec.encode(dto.getVariables()));
		update.setUpdateUser(currentUserId());
		if (baseMapper.updateDraft(update, tenantId, dto.getLockVersion()) != 1) {
			throw new ServiceException(PromptResultCode.PROMPT_CONFLICT);
		}
		PromptMutationVO result = mutation(existing, null, null);
		result.setLockVersion(dto.getLockVersion() + 1);
		result.setDraftRevision(existing.getDraftRevision() + 1);
		result.setWarnings(validation.getWarnings());
		return result;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public PromptMutationVO copy(PromptCopyDTO dto) {
		Prompt source = getTenantPrompt(dto.getSourcePromptId());
		PromptCreateDTO create = new PromptCreateDTO();
		create.setPromptName(dto.getPromptName());
		create.setPromptCode(dto.getPromptCode());
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
		if (baseMapper.logicalDelete(tenantId, prompt.getId(), dto.getLockVersion(), currentUserId()) != 1) {
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
		Prompt prompt = baseMapper.selectTenantPrompt(currentTenantId(), id);
		if (prompt == null) {
			throw new ServiceException(PromptResultCode.PROMPT_NOT_FOUND);
		}
		return prompt;
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

	private PromptValidationResult validateDraft(String fixed, String user, java.util.List<org.springblade.ai.prompt.dto.PromptVariableDTO> variables) {
		PromptValidationResult validation = validator.validateDraft(fixed, user, variables);
		if (!validation.isValid()) {
			throw new ServiceException(PromptResultCode.PROMPT_TEMPLATE_INVALID.detail(firstIssue(validation)));
		}
		return validation;
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

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
