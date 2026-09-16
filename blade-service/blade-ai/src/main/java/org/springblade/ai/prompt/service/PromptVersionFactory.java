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
import org.springblade.ai.prompt.engine.PromptHash;
import org.springblade.ai.prompt.entity.Prompt;
import org.springblade.ai.prompt.entity.PromptVersion;
import org.springblade.ai.prompt.enums.VersionSourceType;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 提示词不可变发布版本工厂。
 *
 * @author BladeX
 */
@Component
@RequiredArgsConstructor
public class PromptVersionFactory {

	private final PromptHash promptHash;

	public PromptVersion fromPrompt(Prompt prompt, int versionNo, VersionSourceType sourceType,
		Long sourceVersionId, Long sourceDraftRevision, String note, Long publishUser) {
		PromptVersion version = new PromptVersion();
		version.setPromptId(prompt.getId());
		version.setVersionNo(versionNo);
		version.setPromptCode(prompt.getPromptCode());
		version.setPromptName(prompt.getPromptName());
		version.setPromptType(prompt.getPromptType());
		version.setFixedInstruction(prompt.getFixedInstruction());
		version.setUserTemplate(prompt.getUserTemplate());
		version.setVariableSchema(prompt.getVariableSchema());
		version.setSourceType(sourceType.getValue());
		version.setSourceVersionId(sourceVersionId);
		version.setSourceDraftRevision(sourceDraftRevision);
		version.setContentHash(promptHash.calculate(prompt.getPromptCode(), prompt.getPromptName(),
			prompt.getFixedInstruction(), prompt.getUserTemplate(), prompt.getVariableSchema()));
		version.setChangeNote(note.trim());
		version.setPublishUser(publishUser);
		version.setPublishTime(new Date());
		version.setStatus(1);
		version.setTenantId(prompt.getTenantId());
		version.setIsDeleted(0);
		return version;
	}

	public PromptVersion fromVersion(PromptVersion target, Long promptId, int versionNo, String note,
		Long publishUser) {
		PromptVersion version = new PromptVersion();
		version.setPromptId(promptId);
		version.setVersionNo(versionNo);
		version.setPromptCode(target.getPromptCode());
		version.setPromptName(target.getPromptName());
		version.setPromptType(target.getPromptType());
		version.setFixedInstruction(target.getFixedInstruction());
		version.setUserTemplate(target.getUserTemplate());
		version.setVariableSchema(target.getVariableSchema());
		version.setSourceType(VersionSourceType.ROLLBACK.getValue());
		version.setSourceVersionId(target.getId());
		version.setContentHash(promptHash.calculate(target.getPromptCode(), target.getPromptName(),
			target.getFixedInstruction(), target.getUserTemplate(), target.getVariableSchema()));
		version.setChangeNote(note.trim());
		version.setPublishUser(publishUser);
		version.setPublishTime(new Date());
		version.setStatus(1);
		version.setTenantId(target.getTenantId());
		version.setIsDeleted(0);
		return version;
	}

}
