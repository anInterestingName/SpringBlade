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
package org.springblade.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springblade.core.mp.base.BaseEntity;
import java.io.Serial;

/**
 * OAuth2/OIDC 客户端主数据，由现有应用管理维护。
 *
 * @author SpringBlade
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("blade_client")
@Schema(description = "OAuth2/OIDC 客户端")
public class AuthClient extends BaseEntity {
	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "客户端标识")
	private String clientId;

	@Schema(description = "客户端名称")
	private String clientName;

	@Schema(description = "客户端密钥，仅接收写入")
	@TableField(updateStrategy = FieldStrategy.ALWAYS)
	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	@ToString.Exclude
	private String clientSecret;

	@Schema(description = "客户端认证方式，逗号分隔")
	private String clientAuthenticationMethods;

	@Schema(description = "授权类型，逗号分隔")
	private String grantTypes;

	@Schema(description = "精确回调地址，逗号分隔")
	private String redirectUris;

	@Schema(description = "授权范围，逗号分隔")
	private String scopes;

	@Schema(description = "目标资源服务，逗号分隔")
	private String audiences;

	@Schema(description = "访问令牌有效期，秒")
	private Integer accessTokenTtl;

	@Schema(description = "刷新令牌有效期，秒")
	private Integer refreshTokenTtl;

	@Schema(description = "要求 PKCE")
	private Boolean requireProofKey;

	@Schema(description = "要求用户确认授权")
	private Boolean requireAuthorizationConsent;

	@Schema(description = "复用刷新令牌")
	private Boolean reuseRefreshTokens;
}
