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

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springblade.core.tool.api.R;
import org.springblade.system.entity.AuthClient;
import org.springblade.system.service.IAuthClientService;
import org.springblade.system.vo.ClientRegistrationVO;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.BeanUtils;

/**
 * 从应用管理读取有效的 OAuth2/OIDC 客户端注册配置。
 *
 * @author SpringBlade
 */
@Hidden
@RestController
@RequiredArgsConstructor
public class ClientRegistrationClient implements IClientRegistrationClient {
	private final IAuthClientService clients;

	@Override
	@GetMapping(API_PREFIX + "/by-client-id")
	public R<ClientRegistrationVO> byClientId(@RequestParam("clientId") String clientId) {
		return registration(clients.getOne(Wrappers.<AuthClient>lambdaQuery()
			.eq(AuthClient::getClientId, clientId).eq(AuthClient::getStatus, 1).eq(AuthClient::getIsDeleted, 0)));
	}

	@Override
	@GetMapping(API_PREFIX + "/by-id")
	public R<ClientRegistrationVO> byId(@RequestParam("id") String id) {
		if (id == null || !id.matches("[1-9][0-9]{0,18}")) {
			return R.fail(404, "客户端不存在或已停用");
		}
		try {
			return registration(clients.getOne(Wrappers.<AuthClient>lambdaQuery()
				.eq(AuthClient::getId, Long.parseLong(id)).eq(AuthClient::getStatus, 1).eq(AuthClient::getIsDeleted, 0)));
		} catch (NumberFormatException exception) {
			return R.fail(404, "客户端不存在或已停用");
		}
	}

	private R<ClientRegistrationVO> registration(AuthClient client) {
		if (client == null) {
			return R.fail(404, "客户端不存在或已停用");
		}
		ClientRegistrationVO registration = new ClientRegistrationVO();
		BeanUtils.copyProperties(client, registration, "id", "clientSecret");
		registration.setId(client.getId().toString());
		registration.setClientSecretHash(client.getClientSecret());
		return R.data(registration);
	}
}
