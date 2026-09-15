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
package org.springblade.system.user.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springblade.core.tool.api.IResultCode;

/**
 * 用户自助注册业务错误码。
 *
 * @author Codex
 */
@Getter
@AllArgsConstructor
public enum RegisterResultCode implements IResultCode {
	REGISTRATION_DISABLED(48301, "当前暂未开放注册"),
	REGISTER_REQUEST_INVALID(48302, "注册信息不合法"),
	CAPTCHA_INVALID(48303, "验证码无效，请重新获取"),
	REGISTER_RATE_LIMITED(48304, "操作过于频繁，请稍后再试"),
	TENANT_INVALID(48305, "租户不存在或暂不可注册"),
	REGISTRATION_ROLE_UNAVAILABLE(48306, "当前租户暂不可注册，请联系管理员"),
	ACCOUNT_DUPLICATE(48307, "当前账号已被使用"),
	REGISTER_SERVICE_UNAVAILABLE(48308, "注册服务暂不可用，请稍后重试"),
	REGISTER_FAILED(48309, "注册失败，请稍后重试");

	private final int code;
	private final String message;
}
