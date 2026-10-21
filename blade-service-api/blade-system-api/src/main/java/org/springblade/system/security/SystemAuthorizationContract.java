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
package org.springblade.system.security;

import org.springblade.core.launch.constant.AppConstant;

import java.util.Set;

/**
 * 授权服务访问 system 的固定 OAuth2 内部契约白名单。
 *
 * <p>部署地址、Nacos namespace 和密钥属于运行环境配置；客户端身份、目标资源和接口 scope
 * 是跨服务契约，必须由 API 模块统一约束，避免每个服务分别声明一套不兼容的环境变量。</p>
 *
 * @author SpringBlade
 */
public final class SystemAuthorizationContract {

	public static final String SERVICE_ID = AppConstant.APPLICATION_SYSTEM_NAME;
	public static final String CLIENT_ID = "authorization-server-system";
	public static final String BOOTSTRAP_CLIENT_ID_NUM = "2108451995948937299";
	public static final String AUDIENCE = AppConstant.APPLICATION_SYSTEM_NAME;

	public static final String USER_AUTHENTICATE_SCOPE = "system.user.authenticate";
	public static final String USER_READ_SCOPE = "system.user.read";
	public static final String CLIENT_READ_SCOPE = "system.client.read";

	public static final Set<String> ALLOWED_SCOPES = Set.of(
		USER_AUTHENTICATE_SCOPE, USER_READ_SCOPE, CLIENT_READ_SCOPE);

	private SystemAuthorizationContract() {
	}
}
