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

import org.springblade.core.tool.api.R;
import org.springblade.system.user.dto.UserAuthenticationCommand;
import org.springblade.system.user.vo.UserAuthenticationVO;
import org.springframework.stereotype.Component;

/**
 * 用户认证服务降级，禁止将依赖失败作为有效身份。
 *
 * @author SpringBlade
 */
@Component
public class IUserAuthenticationClientFallback implements IUserAuthenticationClient {
	@Override
	public R<UserAuthenticationVO> authenticate(UserAuthenticationCommand command) {
		return R.fail(503, "用户认证服务不可用");
	}
	@Override
	public R<UserAuthenticationVO> user(String userId) {
		return R.fail(503, "用户认证服务不可用");
	}
}
