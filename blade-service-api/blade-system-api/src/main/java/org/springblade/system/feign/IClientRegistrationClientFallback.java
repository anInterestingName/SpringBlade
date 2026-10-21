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

import org.springblade.core.tool.api.R;
import org.springblade.system.vo.ClientRegistrationVO;
import org.springframework.stereotype.Component;

/**
 * 客户端注册服务降级，依赖失败不返回有效客户端。
 *
 * @author SpringBlade
 */
@Component
public class IClientRegistrationClientFallback implements IClientRegistrationClient {
	@Override
	public R<ClientRegistrationVO> byClientId(String clientId) {
		return R.fail(503, "客户端注册服务不可用");
	}
	@Override
	public R<ClientRegistrationVO> byId(String id) {
		return R.fail(503, "客户端注册服务不可用");
	}
}
