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
package org.springblade.system.user.feign;

import org.springblade.core.launch.constant.AppConstant;
import org.springblade.core.tool.api.R;
import org.springblade.system.user.dto.UserAuthenticationCommand;
import org.springblade.system.user.vo.UserAuthenticationVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * 授权服务器通过标准服务 Token 使用的用户认证契约。
 *
 * @author SpringBlade
 */
@FeignClient(value = AppConstant.APPLICATION_SYSTEM_NAME, contextId = "userAuthenticationClient",
	fallback = IUserAuthenticationClientFallback.class)
public interface IUserAuthenticationClient {
	String API_PREFIX = "/feign/client/user-authentication";

	@PostMapping(API_PREFIX + "/authenticate")
	R<UserAuthenticationVO> authenticate(@RequestBody UserAuthenticationCommand command);

	@GetMapping(API_PREFIX + "/user")
	R<UserAuthenticationVO> user(@RequestParam("userId") String userId);
}
