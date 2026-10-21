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

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springblade.core.log.exception.ServiceException;
import org.springblade.core.mp.base.BaseServiceImpl;
import org.springblade.system.entity.AuthClient;
import org.springblade.system.mapper.AuthClientMapper;
import org.springblade.system.security.SystemAuthorizationContract;
import org.springblade.system.service.IAuthClientService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * OAuth2/OIDC 应用管理，保存时校验协议约束并单向编码密钥。
 *
 * @author SpringBlade
 */
@Service
public class AuthClientServiceImpl extends BaseServiceImpl<AuthClientMapper, AuthClient> implements IAuthClientService {
	private final BCryptPasswordEncoder secrets = new BCryptPasswordEncoder();

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean save(AuthClient client) {
		prepare(client, null);
		return super.save(client);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean updateById(AuthClient client) {
		AuthClient existing = client.getId() == null ? null : getById(client.getId());
		if (existing == null) {
			throw new ServiceException("客户端不存在");
		}
		prepare(client, existing);
		return super.updateById(client);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean saveOrUpdate(AuthClient client) {
		return client.getId() == null ? save(client) : updateById(client);
	}

	private void prepare(AuthClient client, AuthClient existing) {
		String submittedSecret = client.getClientSecret();
		if (existing != null) {
			BeanWrapperImpl incoming = new BeanWrapperImpl(client);
			BeanWrapperImpl stored = new BeanWrapperImpl(existing);
			for (var property : BeanUtils.getPropertyDescriptors(AuthClient.class)) {
				String name = property.getName();
				if (incoming.isWritableProperty(name) && incoming.getPropertyValue(name) == null) {
					incoming.setPropertyValue(name, stored.getPropertyValue(name));
				}
			}
			if (!existing.getClientId().equals(client.getClientId())) {
				throw new ServiceException("客户端标识创建后不可修改");
			}
		}
		if (!StringUtils.hasText(client.getClientId()) || !client.getClientId().matches("[A-Za-z0-9._-]{1,48}")) {
			throw new ServiceException("客户端标识格式无效");
		}
		if (SystemAuthorizationContract.CLIENT_ID.equals(client.getClientId())) {
			throw new ServiceException("客户端标识为系统保留值");
		}
		if (!StringUtils.hasText(client.getClientName()) || client.getClientName().length() > 100) {
			throw new ServiceException("客户端名称不能为空且不能超过100字符");
		}
		Set<String> methods = values(client.getClientAuthenticationMethods());
		Set<String> grants = values(client.getGrantTypes());
		Set<String> scopes = values(client.getScopes());
		Set<String> audiences = values(client.getAudiences());
		Set<String> redirects = values(client.getRedirectUris());
		if (methods.size() != 1 || !Set.of("none", "client_secret_basic").containsAll(methods)
			|| grants.isEmpty() || !Set.of("authorization_code", "refresh_token", "client_credentials").containsAll(grants)) {
			throw new ServiceException("客户端认证方式或授权类型无效");
		}
		if (grants.contains("refresh_token") && !grants.contains("authorization_code")) {
			throw new ServiceException("刷新令牌必须与授权码模式同时配置");
		}
		if (methods.contains("none") && !grants.equals(Set.of("authorization_code"))) {
			throw new ServiceException("公共客户端只允许授权码与PKCE模式");
		}
		if (grants.contains("authorization_code") && redirects.isEmpty()) {
			throw new ServiceException("授权码模式必须配置回调地址");
		}
		for (String redirect : redirects) {
			try {
				URI uri = URI.create(redirect);
				boolean loopback = uri.getHost() != null && Set.of("localhost", "127.0.0.1", "[::1]").contains(uri.getHost());
				if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
					|| redirect.contains("*") || !("https".equals(uri.getScheme())
					|| ("http".equals(uri.getScheme()) && loopback))) {
					throw new IllegalArgumentException();
				}
			} catch (IllegalArgumentException exception) {
				throw new ServiceException("回调地址必须是精确HTTPS地址，本地回环地址可使用HTTP");
			}
		}
		if (scopes.isEmpty() || audiences.isEmpty() || scopes.stream().anyMatch(value -> !value.matches("[\\x21\\x23-\\x5B\\x5D-\\x7E]+"))) {
			throw new ServiceException("授权范围和目标资源不能为空，scope须符合OAuth2格式");
		}
		if (client.getAccessTokenTtl() == null) {
			client.setAccessTokenTtl(300);
		}
		if (client.getRefreshTokenTtl() == null) {
			client.setRefreshTokenTtl(1800);
		}
		if (client.getRequireProofKey() == null) {
			client.setRequireProofKey(true);
		}
		if (client.getRequireAuthorizationConsent() == null) {
			client.setRequireAuthorizationConsent(true);
		}
		if (client.getReuseRefreshTokens() == null) {
			client.setReuseRefreshTokens(false);
		}
		if (client.getAccessTokenTtl() <= 0 || client.getAccessTokenTtl() > 86400
			|| client.getRefreshTokenTtl() <= 0 || client.getRefreshTokenTtl() > 2592000
			|| (grants.contains("authorization_code") && !client.getRequireProofKey())) {
			throw new ServiceException("令牌有效期无效，授权码模式必须启用PKCE");
		}
		if (client.getReuseRefreshTokens()) {
			throw new ServiceException("刷新令牌必须采用轮换模式");
		}
		if (methods.contains("none")) {
			if (StringUtils.hasText(submittedSecret)) {
				throw new ServiceException("公共客户端不能设置密钥");
			}
			client.setClientSecret(null);
		} else if (StringUtils.hasText(submittedSecret)) {
			if (submittedSecret.length() < 16 || submittedSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72 || submittedSecret.startsWith("{")) {
				throw new ServiceException("客户端密钥须为16至72字符的原始密钥");
			}
			client.setClientSecret("{bcrypt}" + secrets.encode(submittedSecret));
		} else if (existing == null || (!StringUtils.hasText(existing.getClientSecret()) || !existing.getClientSecret().startsWith("{bcrypt}"))) {
			throw new ServiceException("机密客户端必须设置密钥");
		}
		client.setClientAuthenticationMethods(String.join(",", methods));
		client.setGrantTypes(String.join(",", grants));
		client.setScopes(String.join(",", scopes));
		client.setAudiences(String.join(",", audiences));
		client.setRedirectUris(String.join(",", redirects));
		if (client.getStatus() == null) {
			client.setStatus(1);
		}
		if (!Set.of(0, 1).contains(client.getStatus())) {
			throw new ServiceException("客户端状态无效");
		}
		if (count(Wrappers.<AuthClient>lambdaQuery().eq(AuthClient::getClientId, client.getClientId())
			.ne(existing != null, AuthClient::getId, client.getId())) > 0) {
			throw new ServiceException("客户端标识已存在");
		}
	}

	private Set<String> values(String value) {
		Set<String> result = new LinkedHashSet<>();
		if (StringUtils.hasText(value)) {
			Arrays.stream(value.split(",")).map(String::trim).filter(StringUtils::hasText).forEach(result::add);
		}
		return result;
	}
}
