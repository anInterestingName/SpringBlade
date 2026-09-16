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

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springblade.ai.prompt.dto.PromptCopyDTO;
import org.springblade.ai.prompt.dto.PromptCreateDTO;
import org.springblade.ai.prompt.dto.PromptDeleteDTO;
import org.springblade.ai.prompt.dto.PromptUpdateDTO;
import org.springblade.ai.prompt.entity.Prompt;
import org.springblade.ai.prompt.vo.PromptDetailVO;
import org.springblade.ai.prompt.vo.PromptListVO;
import org.springblade.ai.prompt.vo.PromptMutationVO;
import org.springblade.ai.prompt.vo.PromptVersionVO;
import org.springblade.core.mp.base.BaseService;
import org.springblade.core.mp.support.Query;

/** 提示词管理服务。 @author BladeX */
public interface IPromptService extends BaseService<Prompt> {
	IPage<PromptListVO> selectPage(String name, String code, Integer status, String promptType,
		Integer publishMode, Query query);
	PromptDetailVO detail(Long id);
	PromptMutationVO create(PromptCreateDTO dto);
	PromptMutationVO update(PromptUpdateDTO dto);
	PromptMutationVO copy(PromptCopyDTO dto);
	boolean remove(PromptDeleteDTO dto);
	IPage<PromptVersionVO> versionPage(Long promptId, Query query);
	PromptVersionVO versionDetail(Long promptId, Long versionId);
	Prompt getTenantPrompt(Long id);
	Prompt getTenantPromptByCode(String code);
	String currentTenantId();
}
