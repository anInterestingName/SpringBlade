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
package org.springblade.authorization.store;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.springblade.authorization.props.AuthorizationProperties;
import org.springblade.core.tool.utils.DigestUtil;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.Assert;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/**
 * MySQL授权存储，协议写请求内以行锁串行兑换授权码和刷新令牌。
 *
 * @author SpringBlade
 */
@Service
public class JdbcAuthorizationService implements OAuth2AuthorizationService {
	private static final Map<String, String> TOKEN_COLUMNS = Map.of(
		"state", "state_hash", "code", "authorization_code_hash", "access_token", "access_token_hash",
		"refresh_token", "refresh_token_hash", "id_token", "id_token_hash");
	private final MeterRegistry metrics;
	private final IdentifierGenerator identifiers;
	private final JdbcTemplate jdbc;
	private final RegisteredClientRepository clients;
	private final AuthorizationCodec codec;
	private final AuthorizationProperties properties;
	public JdbcAuthorizationService(JdbcTemplate jdbc, RegisteredClientRepository clients,
		AuthorizationCodec codec, AuthorizationProperties properties, IdentifierGenerator identifiers, MeterRegistry metrics) {
		this.identifiers = identifiers;
		this.metrics = metrics;
		this.jdbc = jdbc;
		this.clients = clients;
		this.codec = codec;
		this.properties = properties;
		// 先执行兼容迁移并授予最小 SELECT/INSERT 权限，再发布授权服务。
		jdbc.queryForList("SELECT token_hash, authorization_id FROM blade_oauth2_refresh_token WHERE 1=0");
	}
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void save(OAuth2Authorization authorization) {
		Assert.notNull(authorization, "授权记录不能为空");
		Instant expires = Instant.now().plus(properties.getPendingAuthorizationTtl());
		for (var token : List.of(OAuth2AuthorizationCode.class, OAuth2AccessToken.class, OAuth2RefreshToken.class, OidcIdToken.class)) {
			var stored = authorization.getToken(token);
			if (stored != null && stored.getToken().getExpiresAt() != null && stored.getToken().getExpiresAt().isAfter(expires)) {
				expires = stored.getToken().getExpiresAt();
			}
		}
		Object[] values = {
			codec.encode(authorization), String.join(",", authorization.getAuthorizedScopes()),
			hash(authorization.getAttribute(OAuth2ParameterNames.STATE)),
			tokenHash(authorization.getToken(OAuth2AuthorizationCode.class)), tokenHash(authorization.getAccessToken()),
			tokenHash(authorization.getRefreshToken()), tokenHash(authorization.getToken(OidcIdToken.class)), Timestamp.from(expires),
			authorization.getId(), authorization.getRegisteredClientId(), authorization.getPrincipalName()
		};
		int updated = jdbc.update("""
			UPDATE blade_oauth2_authorization SET payload=?, authorized_scopes=?, state_hash=?, authorization_code_hash=?,
			access_token_hash=?, refresh_token_hash=?, id_token_hash=?, expires_at=?, update_time=CURRENT_TIMESTAMP
			WHERE authorization_id=? AND registered_client_id=? AND principal_name=? AND status=1 AND is_deleted=0
			""", values);
		if (updated == 0 && Integer.valueOf(0).equals(jdbc.queryForObject(
			"SELECT COUNT(*) FROM blade_oauth2_authorization WHERE authorization_id=? AND registered_client_id=? AND principal_name=? AND status=1 AND is_deleted=0",
			Integer.class, authorization.getId(), authorization.getRegisteredClientId(), authorization.getPrincipalName()))) {
			// 插入冲突交由事务回滚，禁止因主键碰撞改写其他授权。
			jdbc.update("""
				INSERT INTO blade_oauth2_authorization
				(id, authorization_id, registered_client_id, principal_name, grant_type, authorized_scopes,
				payload, state_hash, authorization_code_hash, access_token_hash, refresh_token_hash, id_token_hash, expires_at, tenant_id)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", identifiers.nextId(authorization).longValue(), authorization.getId(), authorization.getRegisteredClientId(),
				authorization.getPrincipalName(), authorization.getAuthorizationGrantType().getValue(), values[1], values[0],
				values[2], values[3], values[4], values[5], values[6], values[7], codec.tenantId(authorization));
		}
		rememberRefreshToken(authorization);
	}

	private void rememberRefreshToken(OAuth2Authorization authorization) {
		var refresh = authorization.getRefreshToken();
		if (refresh == null || refresh.getToken().getExpiresAt() == null) {
			return;
		}
		String digest = tokenHash(refresh);
		jdbc.update("""
			INSERT IGNORE INTO blade_oauth2_refresh_token
			(id, token_hash, authorization_id, expires_at, tenant_id)
			VALUES (?, ?, ?, ?, ?)
			""", identifiers.nextId(authorization).longValue(), digest, authorization.getId(),
			Timestamp.from(refresh.getToken().getExpiresAt()), codec.tenantId(authorization));
		List<String> owners = jdbc.queryForList("SELECT authorization_id FROM blade_oauth2_refresh_token WHERE token_hash=?",
			String.class, digest);
		if (owners.size() != 1 || !authorization.getId().equals(owners.getFirst())) {
			throw new IllegalStateException("刷新令牌归属冲突");
		}
	}
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void remove(OAuth2Authorization authorization) {
		Assert.notNull(authorization, "授权记录不能为空");
		jdbc.update("DELETE FROM blade_oauth2_authorization WHERE authorization_id=?", authorization.getId());
	}
	@Override
	public OAuth2Authorization findById(String id) {
		Assert.hasText(id, "授权编号不能为空");
		return query("authorization_id=?", id);
	}
	@Override
	public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
		Assert.hasText(token, "令牌不能为空");
		String digest = hash(token);
		if (tokenType != null) {
			String column = TOKEN_COLUMNS.get(tokenType.getValue());
			OAuth2Authorization authorization = column == null ? null : query(column + "=?"
				+ ("state_hash".equals(column) ? " AND expires_at>CURRENT_TIMESTAMP" : ""), digest);
			if (authorization == null && OAuth2TokenType.REFRESH_TOKEN.equals(tokenType)) {
				revokeReusedRefreshFamily(digest);
			}
			return authorization;
		}
		return query("state_hash=? OR authorization_code_hash=? OR access_token_hash=? OR refresh_token_hash=? OR id_token_hash=?",
			digest, digest, digest, digest, digest);
	}

	/** 仅在同一客户端的真实刷新兑换中处理重用，内省和其他客户端不能触发撤销。 */
	private void revokeReusedRefreshFamily(String digest) {
		if (!TransactionSynchronizationManager.isActualTransactionActive()
			|| !(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)
			|| !"/oauth2/token".equals(attributes.getRequest().getServletPath())
			|| !"refresh_token".equals(attributes.getRequest().getParameter("grant_type"))
			|| !(SecurityContextHolder.getContext().getAuthentication() instanceof OAuth2ClientAuthenticationToken client)
			|| !client.isAuthenticated() || client.getRegisteredClient() == null) {
			return;
		}
		List<String> owners = jdbc.queryForList("""
			SELECT authorization_id FROM blade_oauth2_refresh_token
			WHERE token_hash=? AND expires_at>CURRENT_TIMESTAMP AND status=1 AND is_deleted=0
			""", String.class, digest);
		if (owners.size() != 1) {
			return;
		}
		// 摘要归属记录不可变；授权行锁统一串行刷新与撤销，避免交叉锁顺序。
		OAuth2Authorization current = query("authorization_id=?", owners.getFirst());
		if (current != null && current.getRegisteredClientId().equals(client.getRegisteredClient().getId())) {
			jdbc.update("""
				UPDATE blade_oauth2_authorization SET status=0, update_time=CURRENT_TIMESTAMP
				WHERE authorization_id=? AND registered_client_id=? AND status=1 AND is_deleted=0
				""", current.getId(), current.getRegisteredClientId());
			metrics.counter("blade.authorization.refresh.reuse").increment();
		}
	}
	private OAuth2Authorization query(String filter, Object... arguments) {
		String lock = TransactionSynchronizationManager.isActualTransactionActive() ? " FOR UPDATE" : "";
		long started = System.nanoTime();
		List<OAuth2Authorization> result;
		try { result = jdbc.query("SELECT * FROM blade_oauth2_authorization WHERE status=1 AND is_deleted=0 AND ("
			+ filter + ")" + lock, (rs, row) -> {
				var client = clients.findById(rs.getString("registered_client_id"));
				if (client == null) {
					throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_CLIENT));
				}
				return codec.decode(rs.getString("payload"), rs.getString("authorization_id"), client,
					rs.getString("principal_name"), rs.getString("grant_type"), rs.getString("authorized_scopes"));
			}, arguments);
		if (result.size() > 1) {
			throw new IllegalStateException("授权记录不唯一");
		}
		} finally {
			metrics.timer("blade.authorization.store.query.duration", "lock", lock.isEmpty() ? "none" : "row")
				.record(System.nanoTime() - started, java.util.concurrent.TimeUnit.NANOSECONDS);
		}
		return result.isEmpty() ? null : result.getFirst();
	}
	private String tokenHash(OAuth2Authorization.Token<?> token) {
		return token == null ? null : hash(token.getToken().getTokenValue());
	}
	private String hash(String value) {
		return value == null ? null : DigestUtil.sha256(value);
	}
}
