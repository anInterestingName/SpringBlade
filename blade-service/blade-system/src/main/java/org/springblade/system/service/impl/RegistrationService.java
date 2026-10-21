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
package org.springblade.system.service.impl;

import com.wf.captcha.SpecCaptcha;
import jakarta.servlet.http.HttpServletRequest;
import org.springblade.system.security.PasswordPolicy;
import org.springblade.core.log.exception.ServiceException;
import org.springblade.core.redis.cache.BladeRedis;
import org.springblade.core.tool.api.R;
import org.springblade.system.config.RegistrationProperties;
import org.springblade.system.service.IUserService;
import org.springblade.system.user.constant.RegisterResultCode;
import org.springblade.system.user.dto.UserRegisterCommand;
import org.springblade.system.user.dto.UserRegisterRequest;
import org.springblade.system.user.vo.RegisterConfigVO;
import org.springblade.system.user.vo.RegisterResultVO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * system 同域注册编排：一次性验证码、原子限流、可信来源及本地用户事务。
 *
 * @author SpringBlade
 */
@Service
public class RegistrationService {
	private static final String PREFIX = "blade:system:registration:";
	private static final DefaultRedisScript<Long> RATE = new DefaultRedisScript<>(
		"local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],ARGV[1]); end; return n", Long.class);
	private final RegistrationProperties properties;
	private final IUserService users;
	private final StringRedisTemplate redis;
	private final BladeRedis bladeRedis;
	public RegistrationService(RegistrationProperties properties, IUserService users, StringRedisTemplate redis, BladeRedis bladeRedis) {
		this.properties = properties; this.users = users; this.redis = redis; this.bladeRedis = bladeRedis;
	}
	public RegisterConfigVO config() {
		RegisterConfigVO config = new RegisterConfigVO();
		config.setEnabled(enabled()); config.setCaptchaEnabled(true); config.setDefaultTenantId(properties.getDefaultTenantId());
		config.setAccountMinLength(properties.getAccountMinLength()); config.setAccountMaxLength(properties.getAccountMaxLength());
		config.setPasswordMinLength(properties.getPasswordMinLength()); config.setPasswordMaxLength(properties.getPasswordMaxLength());
		return config;
	}
	public R<Map<String, String>> captcha(HttpServletRequest request) {
		if (!enabled()) return R.fail(RegisterResultCode.REGISTRATION_DISABLED);
		try {
			var limit = properties.getRateLimit();
			if (!allow(PREFIX+"captcha:ip:"+request.getRemoteAddr(), limit.getIpWindowSeconds(), limit.getIpMaxAttempts())) {
				return R.fail(RegisterResultCode.REGISTER_RATE_LIMITED);
			}
			SpecCaptcha captcha = new SpecCaptcha(130,48,5);
			String key = UUID.randomUUID().toString();
			bladeRedis.setEx(PREFIX+"captcha:"+key,captcha.text().toLowerCase(Locale.ROOT),5L,TimeUnit.MINUTES);
			return R.data(Map.of("key",key,"image",captcha.toBase64()));
		} catch (RuntimeException error) { return R.fail(RegisterResultCode.REGISTER_SERVICE_UNAVAILABLE); }
	}
	public R<RegisterResultVO> register(UserRegisterRequest request, HttpServletRequest servletRequest) {
		if (!enabled()) return R.fail(RegisterResultCode.REGISTRATION_DISABLED);
		if (request == null) return R.fail(RegisterResultCode.REGISTER_REQUEST_INVALID);
		String tenant = trim(request.getTenantId()); if (tenant.isEmpty()) tenant=properties.getDefaultTenantId();
		String account = trim(request.getAccount()); String name=trim(request.getName()); if (name.isEmpty()) name=account;
		if (tenant == null || !tenant.matches("[0-9]{6,12}") || !account.matches("[A-Za-z0-9._-]{4,32}")
			|| account.length()<properties.getAccountMinLength() || account.length()>properties.getAccountMaxLength()
			|| name.length()>32 || request.getPassword()==null || !request.getPassword().equals(request.getConfirmPassword())
			|| request.getPassword().length()<properties.getPasswordMinLength() || request.getPassword().length()>properties.getPasswordMaxLength()
			|| request.getCaptchaKey()==null || !request.getCaptchaKey().matches("[0-9a-fA-F-]{36}")
			|| request.getCaptchaCode()==null || request.getCaptchaCode().length()>20) {
			return R.fail(RegisterResultCode.REGISTER_REQUEST_INVALID);
		}
		try { PasswordPolicy.validateNewPassword(account,request.getPassword()); }
		catch (ServiceException error) { return R.fail(RegisterResultCode.REGISTER_REQUEST_INVALID); }
		try {
			var limit=properties.getRateLimit();
			if (!allow(PREFIX+"rate:ip:"+servletRequest.getRemoteAddr(),limit.getIpWindowSeconds(),limit.getIpMaxAttempts())
				|| !allow(PREFIX+"rate:tenant:"+tenant,limit.getTenantWindowSeconds(),limit.getTenantMaxAttempts())
				|| !allow(PREFIX+"rate:account:"+tenant+":"+account.toLowerCase(Locale.ROOT),limit.getAccountWindowSeconds(),limit.getAccountMaxAttempts())) {
				return R.fail(RegisterResultCode.REGISTER_RATE_LIMITED);
			}
			Object code=bladeRedis.getAndDel(PREFIX+"captcha:"+request.getCaptchaKey());
			if (!(code instanceof String expected) || !expected.equalsIgnoreCase(request.getCaptchaCode().trim())) return R.fail(RegisterResultCode.CAPTCHA_INVALID);
			UserRegisterCommand command=new UserRegisterCommand();
			command.setTenantId(tenant); command.setAccount(account); command.setName(name); command.setPassword(request.getPassword());
			if (!users.register(command)) return R.fail(RegisterResultCode.REGISTER_FAILED);
			return R.data(new RegisterResultVO(tenant,account,"LOGIN"));
		} catch (ServiceException error) {
			if (error.getResultCode() instanceof RegisterResultCode code) return R.fail(code);
			return R.fail(RegisterResultCode.REGISTER_SERVICE_UNAVAILABLE);
		} catch (RuntimeException error) { return R.fail(RegisterResultCode.REGISTER_SERVICE_UNAVAILABLE); }
	}
	private boolean enabled() { return properties.isEnabled() && properties.isCaptchaEnabled(); }
	private boolean allow(String key,long seconds,long max) {
		if (seconds<=0 || max<=0) return false;
		Long current=redis.execute(RATE,List.of(key),String.valueOf(seconds)); return current != null && current<=max;
	}
	private String trim(String value) { return value==null ? "" : value.trim(); }
}
