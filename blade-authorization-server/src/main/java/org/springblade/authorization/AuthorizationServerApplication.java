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
package org.springblade.authorization;

import org.springblade.core.cloud.client.BladeCloudApplication;
import org.springblade.core.launch.BladeApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * OAuth2/OIDC 授权服务器启动器，通过公共 LauncherServiceImpl 统一初始化环境和 Nacos。
 *
 * @author BladeX
 */
@BladeCloudApplication
@SpringBootApplication(excludeName = {
	"org.springblade.common.security.config.StandardTokenResourceServerAutoConfiguration",
	"org.springblade.common.security.feign.ServiceTokenFeignConfiguration"
})
public class AuthorizationServerApplication {
	private static final String APPLICATION_NAME = "blade-authorization-server";

	public static void main(String[] args) {
		BladeApplication.run(APPLICATION_NAME, AuthorizationServerApplication.class, args);
	}

}
