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
package org.springblade.auth.service.impl;

import lombok.AllArgsConstructor;
import org.springblade.auth.config.RegistrationProperties;
import org.springblade.auth.dto.RegisterRequest;
import org.springblade.auth.service.IRegisterService;
import org.springblade.auth.utils.TokenUtil;
import org.springblade.auth.vo.RegisterConfigVO;
import org.springblade.auth.vo.RegisterResultVO;
import org.springblade.common.cache.CacheNames;
import org.springblade.core.redis.cache.BladeRedis;
import org.springblade.core.secure.props.BladeAuthProperties;
import org.springblade.core.tool.api.R;
import org.springblade.core.tool.utils.DigestUtil;
import org.springblade.core.tool.utils.Func;
import org.springblade.core.tool.utils.WebUtil;
import org.springblade.system.user.constant.RegisterResultCode;
import org.springblade.system.user.dto.UserRegisterCommand;
import org.springblade.system.user.feign.IUserClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 用户自助注册服务实现。
 *
 * @author Codex
 */
@Service
@AllArgsConstructor
public class RegisterServiceImpl implements IRegisterService {

	private static final String REGISTER_RATE_KEY = "blade:auth::blade:registration:rate:";
	private static final String ACCOUNT_PATTERN = "[A-Za-z0-9._-]+";
	private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).+$");

	private final RegistrationProperties properties;
	private final BladeAuthProperties authProperties;
	private final BladeRedis bladeRedis;
	private final IUserClient userClient;

	@Override
	public RegisterConfigVO getConfig() {
		RegisterConfigVO config = new RegisterConfigVO();
		config.setEnabled(isRegistrationEnabled());
		config.setDefaultTenantId(defaultTenantId());
		config.setCaptchaEnabled(properties.isCaptchaEnabled());
		config.setAccountMinLength(properties.getAccountMinLength());
		config.setAccountMaxLength(properties.getAccountMaxLength());
		config.setPasswordMinLength(properties.getPasswordMinLength());
		config.setPasswordMaxLength(properties.getPasswordMaxLength());
		return config;
	}

	@Override
	public R<RegisterResultVO> register(RegisterRequest request) {
		if (!isRegistrationEnabled()) {
			return R.fail(RegisterResultCode.REGISTRATION_DISABLED);
		}
		if (request == null) {
			return R.fail(RegisterResultCode.REGISTER_REQUEST_INVALID);
		}

		String tenantId = resolveTenantId(request.getTenantId());
		String account = trim(request.getAccount());
		if (!validAccount(account) || !StringUtils.hasText(request.getCaptchaKey()) || !StringUtils.hasText(request.getCaptchaCode())) {
			return R.fail(RegisterResultCode.REGISTER_REQUEST_INVALID);
		}

		String name = trim(request.getName());
		if (!StringUtils.hasText(name)) {
			name = account;
		}
		if (name.length() > 32) {
			return R.fail(RegisterResultCode.REGISTER_REQUEST_INVALID);
		}

		if (!allowRegistration(tenantId, account)) {
			return R.fail(RegisterResultCode.REGISTER_RATE_LIMITED);
		}

		String captcha = Func.toStr(bladeRedis.getAndDel(CacheNames.CAPTCHA_KEY + request.getCaptchaKey()));
		if (!StringUtils.hasText(captcha) || !captcha.equalsIgnoreCase(request.getCaptchaCode().trim())) {
			return R.fail(RegisterResultCode.CAPTCHA_INVALID);
		}

		String password;
		String confirmPassword;
		if (!StringUtils.hasText(request.getPassword()) || !StringUtils.hasText(request.getConfirmPassword())) {
			return R.fail(RegisterResultCode.REGISTER_REQUEST_INVALID);
		}
		try {
			password = TokenUtil.decryptPassword(request.getPassword(), authProperties.getPublicKey(), authProperties.getPrivateKey());
			confirmPassword = TokenUtil.decryptPassword(request.getConfirmPassword(), authProperties.getPublicKey(), authProperties.getPrivateKey());
		} catch (RuntimeException exception) {
			return R.fail(RegisterResultCode.REGISTER_REQUEST_INVALID);
		}
		if (!validPassword(account, password) || !password.equals(confirmPassword)) {
			return R.fail(RegisterResultCode.REGISTER_REQUEST_INVALID);
		}

		UserRegisterCommand command = new UserRegisterCommand();
		command.setTenantId(tenantId);
		command.setAccount(account);
		command.setName(name);
		command.setPasswordDigest(DigestUtil.encrypt(password));

		R<Boolean> result;
		try {
			result = userClient.register(command);
		} catch (RuntimeException exception) {
			return R.fail(RegisterResultCode.REGISTER_SERVICE_UNAVAILABLE);
		}
		if (result == null) {
			return R.fail(RegisterResultCode.REGISTER_SERVICE_UNAVAILABLE);
		}
		if (!result.isSuccess() || !Boolean.TRUE.equals(result.getData())) {
			if (isKnownBusinessFailure(result.getCode())) {
				return R.fail(result.getCode(), result.getMsg());
			}
			return R.fail(RegisterResultCode.REGISTER_SERVICE_UNAVAILABLE);
		}

		return R.data(new RegisterResultVO(tenantId, account, "LOGIN"));
	}

	private boolean isRegistrationEnabled() {
		return properties.isEnabled() && properties.isCaptchaEnabled();
	}

	private String defaultTenantId() {
		String tenantId = trim(properties.getDefaultTenantId());
		return StringUtils.hasText(tenantId) ? tenantId : TokenUtil.DEFAULT_TENANT_ID;
	}

	private String resolveTenantId(String tenantId) {
		String resolved = trim(tenantId);
		return StringUtils.hasText(resolved) ? resolved : defaultTenantId();
	}

	private boolean validAccount(String account) {
		int length = account.length();
		return length >= properties.getAccountMinLength()
			&& length <= properties.getAccountMaxLength()
			&& account.matches(ACCOUNT_PATTERN);
	}

	private boolean validPassword(String account, String password) {
		if (!StringUtils.hasText(password)) {
			return false;
		}
		int length = password.length();
		return length >= properties.getPasswordMinLength()
			&& length <= properties.getPasswordMaxLength()
			&& !password.equals(account)
			&& PASSWORD_PATTERN.matcher(password).matches();
	}

	private boolean allowRegistration(String tenantId, String account) {
		RegistrationProperties.RateLimit limit = properties.getRateLimit();
		String ip = Func.toStr(WebUtil.getIP(), "unknown");
		String normalizedAccount = account.toLowerCase(Locale.ROOT);
		return allow(REGISTER_RATE_KEY + "ip:" + ip, limit.getIpWindowSeconds(), limit.getIpMaxAttempts())
			&& allow(REGISTER_RATE_KEY + "tenant:" + tenantId, limit.getTenantWindowSeconds(), limit.getTenantMaxAttempts())
			&& allow(REGISTER_RATE_KEY + "account:" + tenantId + ':' + normalizedAccount,
			limit.getAccountWindowSeconds(), limit.getAccountMaxAttempts());
	}

	private boolean allow(String key, long windowSeconds, long maxAttempts) {
		if (windowSeconds <= 0 || maxAttempts <= 0) {
			return false;
		}
		Long current = bladeRedis.incr(key);
		if (current != null && current == 1L) {
			bladeRedis.expire(key, Duration.ofSeconds(windowSeconds));
		}
		return current != null && current <= maxAttempts;
	}

	private boolean isKnownBusinessFailure(int code) {
		return code == RegisterResultCode.REGISTER_REQUEST_INVALID.getCode()
			|| code == RegisterResultCode.TENANT_INVALID.getCode()
			|| code == RegisterResultCode.REGISTRATION_ROLE_UNAVAILABLE.getCode()
			|| code == RegisterResultCode.ACCOUNT_DUPLICATE.getCode()
			|| code == RegisterResultCode.REGISTER_FAILED.getCode();
	}

	private String trim(String value) {
		return value == null ? "" : value.trim();
	}
}
