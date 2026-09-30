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
package org.springblade.ai.modelconfig.service;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springblade.ai.modelconfig.constant.AiModelConfigResultCode;
import org.springblade.core.log.exception.ServiceException;
import org.springblade.core.secure.BladeUser;
import org.springblade.core.secure.utils.AuthUtil;
import org.springblade.core.tool.api.R;
import org.springblade.core.tool.utils.StringUtil;
import org.springblade.system.user.feign.IUserClient;
import org.springblade.system.user.vo.UserManagementScopeVO;
import org.springblade.system.user.vo.UserOwnerScopeVO;
import org.springframework.stereotype.Service;

/**
 * 访问控制与三级范围校验服务
 *
 * @author SpringBlade
 */
@Service
@AllArgsConstructor
public class AiConfigAccessService {

	private final IUserClient userClient;

	public enum AccessRole {
		PLATFORM_ADMIN,
		TENANT_ADMIN,
		NORMAL_USER
	}

	@Getter
	@AllArgsConstructor
	public static class UserAccessContext {
		private final Long userId;
		private final String tenantId;
		private final AccessRole role;
	}

	/**
	 * 解析当前操作用户的管理角色上下文
	 */
	public UserAccessContext getCurrentAccessContext() {
		BladeUser user = AuthUtil.getUser();
		if (user == null || user.getUserId() == null) {
			throw new ServiceException(AiModelConfigResultCode.NOT_FOUND);
		}

		// 远程核验当前用户与角色范围
		R<UserManagementScopeVO> scopeResult = userClient.managementScope();
		if (scopeResult == null || !scopeResult.isSuccess() || scopeResult.getData() == null) {
			throw new ServiceException(AiModelConfigResultCode.SERVICE_UNAVAILABLE);
		}

		UserManagementScopeVO scopeVO = scopeResult.getData();
		if (!user.getUserId().equals(scopeVO.getUserId()) || StringUtil.isBlank(scopeVO.getTenantId())
			|| !scopeVO.getTenantId().equals(user.getTenantId())) {
			throw new ServiceException(AiModelConfigResultCode.NOT_FOUND);
		}
		if (Boolean.TRUE.equals(scopeVO.getPlatformAdmin())) {
			return new UserAccessContext(scopeVO.getUserId(), scopeVO.getTenantId(), AccessRole.PLATFORM_ADMIN);
		} else if (Boolean.TRUE.equals(scopeVO.getTenantAdmin())) {
			return new UserAccessContext(scopeVO.getUserId(), scopeVO.getTenantId(), AccessRole.TENANT_ADMIN);
		} else {
			return new UserAccessContext(scopeVO.getUserId(), scopeVO.getTenantId(), AccessRole.NORMAL_USER);
		}
	}

	/**
	 * 验证代建或指定的目标所有人归属有效性
	 *
	 * @param context         当前操作人上下文
	 * @param targetTenantId  请求指定的目标租户ID（平台超管必填）
	 * @param targetOwnerUserId 请求指定的目标所有人用户ID
	 * @return 最终确定的 [tenantId, ownerUserId]
	 */
	public ResolvedOwner resolveTargetOwner(UserAccessContext context, String targetTenantId, Long targetOwnerUserId) {
		if (context.getRole() == AccessRole.PLATFORM_ADMIN) {
			if (StringUtil.isBlank(targetTenantId)) {
				throw new ServiceException(AiModelConfigResultCode.OWNER_INVALID);
			}
			if (targetOwnerUserId == null) {
				throw new ServiceException(AiModelConfigResultCode.OWNER_INVALID);
			}
			// 远程核验目标用户是否存在且未删除、状态正常
			R<UserOwnerScopeVO> ownerScopeR = userClient.ownerScope(targetOwnerUserId);
			if (ownerScopeR == null || !ownerScopeR.isSuccess() || ownerScopeR.getData() == null) {
				throw new ServiceException(AiModelConfigResultCode.OWNER_INVALID);
			}
			UserOwnerScopeVO ownerScope = ownerScopeR.getData();
			if (ownerScope.getIsDeleted() != 0 || ownerScope.getStatus() != 1 || !targetTenantId.equals(ownerScope.getTenantId())) {
				throw new ServiceException(AiModelConfigResultCode.OWNER_INVALID);
			}
			return new ResolvedOwner(targetTenantId, targetOwnerUserId);
		} else if (context.getRole() == AccessRole.TENANT_ADMIN) {
			if (StringUtil.isNotBlank(targetTenantId) && !context.getTenantId().equals(targetTenantId)) {
				throw new ServiceException(AiModelConfigResultCode.OWNER_INVALID);
			}
			String finalTenantId = context.getTenantId();
			Long finalOwnerUserId = (targetOwnerUserId != null) ? targetOwnerUserId : context.getUserId();
			if (!finalOwnerUserId.equals(context.getUserId())) {
				// 代建同租户用户，需核实该用户在当前租户有效
				R<UserOwnerScopeVO> ownerScopeR = userClient.ownerScope(finalOwnerUserId);
				if (ownerScopeR == null || !ownerScopeR.isSuccess() || ownerScopeR.getData() == null) {
					throw new ServiceException(AiModelConfigResultCode.OWNER_INVALID);
				}
				UserOwnerScopeVO ownerScope = ownerScopeR.getData();
				if (ownerScope.getIsDeleted() != 0 || ownerScope.getStatus() != 1 || !finalTenantId.equals(ownerScope.getTenantId())) {
					throw new ServiceException(AiModelConfigResultCode.OWNER_INVALID);
				}
			}
			return new ResolvedOwner(finalTenantId, finalOwnerUserId);
		} else {
			// 普通用户：强制本人和当前租户
			if (StringUtil.isNotBlank(targetTenantId) && !context.getTenantId().equals(targetTenantId)) {
				throw new ServiceException(AiModelConfigResultCode.OWNER_INVALID);
			}
			if (targetOwnerUserId != null && !context.getUserId().equals(targetOwnerUserId)) {
				throw new ServiceException(AiModelConfigResultCode.OWNER_INVALID);
			}
			return new ResolvedOwner(context.getTenantId(), context.getUserId());
		}
	}

	/**
	 * 校验对指定资源的可访问性（只读或写前校验）
	 *
	 * @param context        当前操作人上下文
	 * @param resourceTenantId 资源所属租户
	 * @param resourceOwnerId  资源所有人
	 * @param assertTenantId   可选的目标租户断言
	 */
	public void assertResourceAccess(UserAccessContext context, String resourceTenantId, Long resourceOwnerId, String assertTenantId) {
		if (context.getRole() == AccessRole.PLATFORM_ADMIN) {
			if (StringUtil.isNotBlank(assertTenantId) && !assertTenantId.equals(resourceTenantId)) {
				throw new ServiceException(AiModelConfigResultCode.NOT_FOUND);
			}
			return;
		}

		if (context.getRole() == AccessRole.TENANT_ADMIN) {
			if (!context.getTenantId().equals(resourceTenantId)) {
				throw new ServiceException(AiModelConfigResultCode.NOT_FOUND);
			}
			if (StringUtil.isNotBlank(assertTenantId) && !context.getTenantId().equals(assertTenantId)) {
				throw new ServiceException(AiModelConfigResultCode.NOT_FOUND);
			}
			return;
		}

		// 普通用户：租户及本人严格匹配
		if (!context.getTenantId().equals(resourceTenantId) || !context.getUserId().equals(resourceOwnerId)) {
			throw new ServiceException(AiModelConfigResultCode.NOT_FOUND);
		}
		if (StringUtil.isNotBlank(assertTenantId) && !context.getTenantId().equals(assertTenantId)) {
			throw new ServiceException(AiModelConfigResultCode.NOT_FOUND);
		}
	}

	/**
	 * 写操作要求平台超级管理员明确指定目标租户。
	 */
	public void assertResourceWriteAccess(UserAccessContext context, String resourceTenantId, Long resourceOwnerId, String assertTenantId) {
		if (context.getRole() == AccessRole.PLATFORM_ADMIN && StringUtil.isBlank(assertTenantId)) {
			throw new ServiceException(AiModelConfigResultCode.OWNER_INVALID);
		}
		assertResourceAccess(context, resourceTenantId, resourceOwnerId, assertTenantId);
	}

	@Getter
	@AllArgsConstructor
	public static class ResolvedOwner {
		private final String tenantId;
		private final Long ownerUserId;
	}

}
