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
package org.springblade.ai.prompt.wrapper;

import lombok.RequiredArgsConstructor;
import org.springblade.ai.prompt.engine.PromptSchemaCodec;
import org.springblade.ai.prompt.entity.Prompt;
import org.springblade.ai.prompt.enums.PromptStatus;
import org.springblade.ai.prompt.vo.PromptActionVO;
import org.springblade.ai.prompt.vo.PromptDetailVO;
import org.springblade.ai.prompt.vo.PromptListVO;
import org.springframework.stereotype.Component;

/** 提示词管理视图转换器。 @author BladeX */
@Component
@RequiredArgsConstructor
public class PromptWrapper {
	private final PromptSchemaCodec schemaCodec;

	public PromptListVO listVO(Prompt prompt, boolean hasHistory) {
		PromptListVO vo = new PromptListVO();
		vo.setId(prompt.getId());
		vo.setPromptCode(prompt.getPromptCode());
		vo.setPromptName(prompt.getPromptName());
		vo.setStatus(prompt.getStatus());
		vo.setStatusName(PromptStatus.of(prompt.getStatus()).getLabel());
		vo.setCurrentVersionNo(prompt.getCurrentVersionNo());
		vo.setDraftDirty(prompt.getDraftDirty());
		vo.setLockVersion(prompt.getLockVersion());
		vo.setCreateUser(prompt.getCreateUser());
		vo.setUpdateUser(prompt.getUpdateUser());
		vo.setCreateTime(prompt.getCreateTime());
		vo.setUpdateTime(prompt.getUpdateTime());
		vo.setActions(actions(prompt, hasHistory));
		return vo;
	}

	public PromptDetailVO detailVO(Prompt prompt, boolean hasHistory) {
		PromptDetailVO vo = new PromptDetailVO();
		vo.setId(prompt.getId());
		vo.setPromptCode(prompt.getPromptCode());
		vo.setPromptName(prompt.getPromptName());
		vo.setFixedInstruction(prompt.getFixedInstruction());
		vo.setUserTemplate(prompt.getUserTemplate());
		vo.setVariables(schemaCodec.decode(prompt.getVariableSchema()));
		vo.setDraftRevision(prompt.getDraftRevision());
		vo.setDraftDirty(prompt.getDraftDirty());
		vo.setStatus(prompt.getStatus());
		vo.setStatusName(PromptStatus.of(prompt.getStatus()).getLabel());
		vo.setCurrentVersionId(prompt.getCurrentVersionId());
		vo.setCurrentVersionNo(prompt.getCurrentVersionNo());
		vo.setLockVersion(prompt.getLockVersion());
		vo.setCreateTime(prompt.getCreateTime());
		vo.setUpdateTime(prompt.getUpdateTime());
		vo.setActions(actions(prompt, hasHistory));
		return vo;
	}

	private PromptActionVO actions(Prompt prompt, boolean hasHistory) {
		PromptActionVO actions = new PromptActionVO();
		actions.setEditable(true);
		actions.setRemovable(prompt.getCurrentVersionId() == null && !hasHistory);
		actions.setPublishable(true);
		actions.setDisableable(prompt.getCurrentVersionId() != null && prompt.getStatus() != PromptStatus.DISABLED.getValue());
		actions.setRollbackable(hasHistory);
		return actions;
	}
}
