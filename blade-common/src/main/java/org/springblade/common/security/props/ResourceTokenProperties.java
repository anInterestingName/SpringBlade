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
package org.springblade.common.security.props;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 资源服务标准 JWT 校验配置。
 *
 * @author SpringBlade
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "blade.authorization.resource")
public class ResourceTokenProperties {

	private boolean enabled = true;
	private String issuer;
	private String jwkSetUri;
	private String audience;
	/** 通用业务资源的最低授权范围，业务角色和数据权限仍由原有规则检查。 */
	private String requiredScope = "service.read";
	private java.util.List<String> skipUrl = new java.util.ArrayList<>();
}
