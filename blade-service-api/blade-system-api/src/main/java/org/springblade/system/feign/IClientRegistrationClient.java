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

import org.springblade.core.launch.constant.AppConstant;
import org.springblade.core.tool.api.R;
import org.springblade.system.vo.ClientRegistrationVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * 授权服务器通过标准服务 Token 使用的客户端注册查询契约。
 *
 * @author SpringBlade
 */
@FeignClient(value = AppConstant.APPLICATION_SYSTEM_NAME, contextId = "clientRegistrationClient",
	fallback = IClientRegistrationClientFallback.class)
public interface IClientRegistrationClient {
	String API_PREFIX = "/feign/client/client-registration";

	@GetMapping(API_PREFIX + "/by-client-id")
	R<ClientRegistrationVO> byClientId(@RequestParam("clientId") String clientId);

	@GetMapping(API_PREFIX + "/by-id")
	R<ClientRegistrationVO> byId(@RequestParam("id") String id);
}
