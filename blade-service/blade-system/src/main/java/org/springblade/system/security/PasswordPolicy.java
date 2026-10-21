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

import org.springblade.core.log.exception.ServiceException;
import java.nio.charset.StandardCharsets;

/**
 * 用户密码写入的长度与复杂度约束，历史默认密码策略保持原约定。
 *
 * @author SpringBlade
 */
public final class PasswordPolicy {
	private PasswordPolicy() { }
	public static void validateEncoding(String password) {
		if (password == null || password.isBlank() || password.getBytes(StandardCharsets.UTF_8).length > 72) {
			throw new ServiceException("密码不能为空且不能超过72个UTF-8字节");
		}
	}
	public static void validateNewPassword(String account, String password) {
		validateEncoding(password);
		if (password.length() < 8 || password.length() > 64 || password.equals(account)
			|| !password.matches("(?s)(?=.*[A-Za-z])(?=.*[0-9]).+")) {
			throw new ServiceException("密码须为8至64位并包含字母和数字，不能与账号相同");
		}
	}
}
