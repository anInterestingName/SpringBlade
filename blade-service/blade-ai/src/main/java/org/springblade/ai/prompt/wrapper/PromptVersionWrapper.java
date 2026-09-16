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
import org.springblade.ai.prompt.entity.PromptVersion;
import org.springblade.ai.prompt.enums.PromptType;
import org.springblade.ai.prompt.enums.VersionSourceType;
import org.springblade.ai.prompt.vo.PromptVersionVO;
import org.springframework.stereotype.Component;

/** 提示词版本视图转换器。 @author BladeX */
@Component
@RequiredArgsConstructor
public class PromptVersionWrapper {
	private final PromptSchemaCodec schemaCodec;

	public PromptVersionVO entityVO(PromptVersion version) {
		if (version == null) {
			return null;
		}
		PromptVersionVO vo = new PromptVersionVO();
		vo.setId(version.getId());
		vo.setPromptId(version.getPromptId());
		vo.setVersionNo(version.getVersionNo());
		vo.setPromptCode(version.getPromptCode());
		vo.setPromptName(version.getPromptName());
		PromptType type = PromptType.of(version.getPromptType());
		vo.setPromptType(version.getPromptType());
		vo.setPromptTypeName(type == null ? null : type.getLabel());
		vo.setFixedInstruction(version.getFixedInstruction());
		vo.setUserTemplate(version.getUserTemplate());
		vo.setVariables(schemaCodec.decode(version.getVariableSchema()));
		vo.setSourceType(version.getSourceType());
		vo.setSourceTypeName(VersionSourceType.of(version.getSourceType()).getLabel());
		vo.setSourceVersionId(version.getSourceVersionId());
		vo.setSourceDraftRevision(version.getSourceDraftRevision());
		vo.setContentHash(version.getContentHash());
		vo.setChangeNote(version.getChangeNote());
		vo.setPublishUser(version.getPublishUser());
		vo.setPublishTime(version.getPublishTime());
		return vo;
	}
}
