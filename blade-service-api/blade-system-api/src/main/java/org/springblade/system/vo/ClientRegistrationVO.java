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
package org.springblade.system.vo;

import lombok.Data;
import lombok.ToString;

/**
 * 内部客户端注册数据，密钥摘要只供授权服务器校验。
 *
 * @author SpringBlade
 */
@Data
public class ClientRegistrationVO {
	private String id;
	private String clientId;
	private String clientName;
	@ToString.Exclude
	private String clientSecretHash;
	private String clientAuthenticationMethods;
	private String grantTypes;
	private String redirectUris;
	private String scopes;
	private String audiences;
	private Integer accessTokenTtl;
	private Integer refreshTokenTtl;
	private Boolean requireProofKey;
	private Boolean requireAuthorizationConsent;
	private Boolean reuseRefreshTokens;
}
