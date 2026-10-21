/*
 * Copyright 2026 SpringBlade contributors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * https://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springblade.system.feign;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springblade.core.tool.api.R;
import org.springblade.system.service.ITenantService;
import org.springblade.system.service.IUserService;
import org.springblade.system.user.dto.UserAuthenticationCommand;
import org.springblade.system.user.entity.User;
import org.springblade.system.user.entity.UserInfo;
import org.springblade.system.user.feign.IUserAuthenticationClient;
import org.springblade.system.user.vo.UserAuthenticationVO;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;

/**
 * 复用系统用户和租户的标准 Token 认证入口。
 *
 * @author SpringBlade
 */
@Hidden
@RestController
@RequiredArgsConstructor
public class UserAuthenticationClient implements IUserAuthenticationClient {
	private final IUserService users;
	private final ITenantService tenants;

	@Override
	@PostMapping(API_PREFIX + "/authenticate")
	public R<UserAuthenticationVO> authenticate(@Valid @RequestBody UserAuthenticationCommand command) {
		if (tenants.getActiveByTenantId(command.getTenantId()) == null) {
			return R.fail(401, "账号或密码错误");
		}
		return identity(users.authenticateUser(command.getTenantId(), command.getAccount(), command.getPassword()));
	}

	@Override
	@GetMapping(API_PREFIX + "/user")
	public R<UserAuthenticationVO> user(@RequestParam("userId") String userId) {
		if (userId == null || !userId.matches("[1-9][0-9]{0,18}")) {
			return R.fail(401, "用户身份无效");
		}
		try {
			return identity(users.userInfo(Long.parseLong(userId)));
		} catch (NumberFormatException exception) {
			return R.fail(401, "用户身份无效");
		}
	}

	private R<UserAuthenticationVO> identity(UserInfo info) {
		User user = info == null ? null : info.getUser();
		if (user == null || user.getId() == null || !Integer.valueOf(1).equals(user.getStatus())
			|| !Integer.valueOf(0).equals(user.getIsDeleted())
			|| tenants.getActiveByTenantId(user.getTenantId()) == null) {
			return R.fail(401, "用户身份无效");
		}
		UserAuthenticationVO identity = new UserAuthenticationVO();
		identity.setUserId(user.getId().toString());
		identity.setTenantId(user.getTenantId());
		identity.setAccount(user.getAccount());
		identity.setName(user.getName());
		identity.setRoleId(user.getRoleId());
		identity.setDeptId(user.getDeptId());
		identity.setRoles(info.getRoles() == null ? new ArrayList<>() : new ArrayList<>(info.getRoles()));
		return R.data(identity);
	}
}
