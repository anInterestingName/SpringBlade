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
package org.springblade.system.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springblade.core.tool.api.R;
import org.springblade.system.service.impl.RegistrationService;
import org.springblade.system.user.dto.UserRegisterRequest;
import org.springblade.system.user.vo.RegisterConfigVO;
import org.springblade.system.user.vo.RegisterResultVO;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/**
 * 与管理 Controller 隔离的公开注册端点，只返回非敏感业务结果。
 *
 * @author SpringBlade
 */
@RestController
@RequestMapping("/user/register")
@Tag(name="用户自助注册")
public class RegistrationController {
	private final RegistrationService registration;
	public RegistrationController(RegistrationService registration) { this.registration=registration; }
	@GetMapping("/config")
	@Operation(summary="读取公开注册开关和规则")
	public ResponseEntity<R<RegisterConfigVO>> config() { return noStore(R.data(registration.config())); }
	@GetMapping("/captcha")
	@Operation(summary="获取一次性注册图形验证码")
	public ResponseEntity<R<Map<String,String>>> captcha(HttpServletRequest request) { return noStore(registration.captcha(request)); }
	@PostMapping
	@Operation(summary="使用真实密码注册普通租户用户")
	public ResponseEntity<R<RegisterResultVO>> register(@RequestBody UserRegisterRequest request,HttpServletRequest servletRequest) {
		return noStore(registration.register(request,servletRequest));
	}
	private <T> ResponseEntity<R<T>> noStore(R<T> result) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result); }
}
