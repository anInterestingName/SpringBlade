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
package org.springblade.ai.prompt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;
import org.springblade.ai.prompt.entity.Prompt;
import org.springblade.core.datascope.annotation.DataAuth;
import org.springblade.core.datascope.enums.DataScopeEnum;

/** 提示词主记录 Mapper。 @author BladeX */
public interface PromptMapper extends BaseMapper<Prompt> {
	@DataAuth(type = DataScopeEnum.CUSTOM, value = "where 1 = 0")
	IPage<Prompt> selectScopePage(IPage<Prompt> page, @Param("tenantId") String tenantId,
		@Param("name") String name, @Param("code") String code, @Param("status") Integer status,
		@Param("promptType") String promptType, @Param("publishMode") Integer publishMode,
		@Param("ownerUserId") Long ownerUserId);
	@DataAuth(type = DataScopeEnum.CUSTOM, value = "where 1 = 0")
	Prompt selectScopePrompt(@Param("tenantId") String tenantId, @Param("id") Long id,
		@Param("ownerUserId") Long ownerUserId);
	Prompt selectTenantPrompt(@Param("tenantId") String tenantId, @Param("id") Long id);
	Prompt selectTenantPromptByCode(@Param("tenantId") String tenantId, @Param("code") String code);
	Prompt selectTenantPromptByCodeIncludingDeleted(@Param("tenantId") String tenantId, @Param("code") String code);
	Prompt selectForUpdate(@Param("tenantId") String tenantId, @Param("id") Long id);
	int updateDraft(@Param("prompt") Prompt prompt, @Param("tenantId") String tenantId,
		@Param("ownerUserId") Long ownerUserId, @Param("expectedLockVersion") Long expectedLockVersion);
	int updateAutoPublishedDraft(@Param("prompt") Prompt prompt, @Param("tenantId") String tenantId,
		@Param("ownerUserId") Long ownerUserId, @Param("expectedLockVersion") Long expectedLockVersion,
		@Param("versionId") Long versionId, @Param("versionNo") Integer versionNo);
	int updatePublishedState(@Param("tenantId") String tenantId, @Param("id") Long id,
		@Param("ownerUserId") Long ownerUserId, @Param("expectedLockVersion") Long expectedLockVersion,
		@Param("versionId") Long versionId,
		@Param("versionNo") Integer versionNo, @Param("draftDirty") boolean draftDirty,
		@Param("updateUser") Long updateUser);
	int updateDisabledState(@Param("tenantId") String tenantId, @Param("id") Long id,
		@Param("ownerUserId") Long ownerUserId, @Param("expectedLockVersion") Long expectedLockVersion,
		@Param("updateUser") Long updateUser);
	int logicalDelete(@Param("tenantId") String tenantId, @Param("id") Long id,
		@Param("ownerUserId") Long ownerUserId, @Param("expectedLockVersion") Long expectedLockVersion,
		@Param("updateUser") Long updateUser);
}
