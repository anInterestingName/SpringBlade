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
package org.springblade.ai.prompt.service;

import lombok.RequiredArgsConstructor;
import org.springblade.ai.prompt.constant.PromptResultCode;
import org.springblade.ai.prompt.entity.Prompt;
import org.springblade.ai.prompt.mapper.PromptMapper;
import org.springblade.core.datascope.enums.DataScopeEnum;
import org.springblade.core.datascope.model.DataScopeModel;
import org.springblade.core.log.exception.ServiceException;
import org.springblade.core.secure.BladeUser;
import org.springblade.core.secure.utils.SecureUtil;
import org.springblade.system.cache.DataScopeCache;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 提示词管理数据范围访问保护。
 *
 * @author BladeX
 */
@Component
@RequiredArgsConstructor
public class PromptAccessService {

	public static final String PAGE_MAPPER_ID = "org.springblade.ai.prompt.mapper.PromptMapper.selectScopePage";
	public static final String RESOURCE_MAPPER_ID = "org.springblade.ai.prompt.mapper.PromptMapper.selectScopePrompt";

	private final PromptMapper promptMapper;

	public Long requirePageScope() {
		return requireScope(PAGE_MAPPER_ID);
	}

	public void requireManagementScopes() {
		requireScope(PAGE_MAPPER_ID);
		requireScope(RESOURCE_MAPPER_ID);
	}

	public boolean hasAllManagementScopes() {
		try {
			return resolveScope(PAGE_MAPPER_ID) == DataScopeEnum.ALL
				&& resolveScope(RESOURCE_MAPPER_ID) == DataScopeEnum.ALL;
		} catch (RuntimeException exception) {
			return false;
		}
	}

	public Prompt requireAccessible(String tenantId, Long id) {
		Long ownerUserId = requireScope(RESOURCE_MAPPER_ID);
		Prompt prompt = promptMapper.selectScopePrompt(tenantId, id, ownerUserId);
		if (prompt == null) {
			throw new ServiceException(PromptResultCode.PROMPT_NOT_FOUND);
		}
		return prompt;
	}

	public void verifyLocked(Prompt accessible, Prompt locked) {
		if (locked == null
			|| !Objects.equals(accessible.getId(), locked.getId())
			|| !Objects.equals(accessible.getTenantId(), locked.getTenantId())
			|| !Objects.equals(accessible.getCreateUser(), locked.getCreateUser())) {
			throw new ServiceException(PromptResultCode.PROMPT_NOT_FOUND);
		}
	}

	private Long requireScope(String mapperId) {
		DataScopeEnum scope = resolveScope(mapperId);
		BladeUser user = SecureUtil.getUser();
		if (scope == DataScopeEnum.OWN && (user == null || user.getUserId() == null)) {
			throw new ServiceException(PromptResultCode.PROMPT_DATA_SCOPE_UNAVAILABLE);
		}
		return scope == DataScopeEnum.OWN ? user.getUserId() : null;
	}

	private DataScopeEnum resolveScope(String mapperId) {
		BladeUser user = SecureUtil.getUser();
		if (user == null || user.getRoleId() == null || user.getRoleId().isBlank()) {
			throw new ServiceException(PromptResultCode.PROMPT_DATA_SCOPE_UNAVAILABLE);
		}
		try {
			DataScopeModel model = DataScopeCache.getDataScopeByMapper(mapperId, user.getRoleId());
			DataScopeEnum scope = model == null ? null : DataScopeEnum.of(model.getScopeType());
			if (scope != DataScopeEnum.ALL && scope != DataScopeEnum.OWN) {
				throw new ServiceException(PromptResultCode.PROMPT_DATA_SCOPE_UNAVAILABLE);
			}
			return scope;
		} catch (ServiceException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw new ServiceException(PromptResultCode.PROMPT_DATA_SCOPE_UNAVAILABLE, exception);
		}
	}

}
