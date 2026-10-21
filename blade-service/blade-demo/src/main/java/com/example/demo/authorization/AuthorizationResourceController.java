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
package com.example.demo.authorization;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springblade.core.tool.api.R;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * OAuth2 资源端验证入口，只返回身份摘要，不操作 demo 业务数据。
 *
 * @author BladeX
 */
@RestController
@RequestMapping("/authorization")
@Tag(name = "OAuth2 资源端验证")
public class AuthorizationResourceController {

	@GetMapping("/public")
	@Operation(summary = "验证公开入口")
	public R<Map<String, String>> publicEndpoint() {
		return R.data(Map.of("service", "blade-demo", "mode", "authorization"));
	}

	@GetMapping("/messages")
	@Operation(summary = "验证 demo.read 权限")
	public R<Map<String, Object>> messages(@AuthenticationPrincipal Jwt jwt) {
		return R.data(Map.of("service", "blade-demo", "subject", jwt.getSubject(),
			"audience", jwt.getAudience(), "scope", jwt.getClaimAsStringList("scope"),
			"message", "OAuth2 JWT 资源端验证成功"));
	}

	@GetMapping("/restricted")
	@Operation(summary = "验证缺少 demo.write 权限时拒绝访问")
	public R<String> restricted() {
		return R.data("已通过 demo.write 权限校验");
	}

	@GetMapping("/callback")
	@Operation(summary = "授权码验证回调提示")
	public R<String> callback() {
		return R.data("请校验回调地址中的 state，并使用 code 与原始 code_verifier 换取令牌；本接口不保存授权码。");
	}

	@GetMapping("/identity")
	@Operation(summary = "验证标准 Token 下游校验")
	public R<Map<String, Object>> identity(@AuthenticationPrincipal Jwt jwt) {
		return R.data(Map.of("service", "blade-demo", "subject", jwt.getSubject(),
			"tenantId", String.valueOf(jwt.getClaimAsString("tenant_id")), "mode", "TOKEN",
			"audience", jwt.getAudience(), "scope", jwt.getClaimAsStringList("scope")));
	}

}
