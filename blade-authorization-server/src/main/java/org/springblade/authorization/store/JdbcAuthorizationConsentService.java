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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MySQL用户授权同意存储，以客户端和稳定用户主体建立唯一关系。
 *
 * @author SpringBlade
 */
@Service
public class JdbcAuthorizationConsentService implements OAuth2AuthorizationConsentService {
	private final IdentifierGenerator identifiers;
	private final JdbcTemplate jdbc;
	public JdbcAuthorizationConsentService(JdbcTemplate jdbc, IdentifierGenerator identifiers) {
		this.identifiers = identifiers;
		this.jdbc = jdbc;
	}
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void save(OAuth2AuthorizationConsent consent) {
		Assert.notNull(consent, "授权同意不能为空");
		String tenantId = currentTenant(consent.getPrincipalName());
		Assert.hasText(tenantId, "授权同意必须关联当前用户租户");
		String authorities = consent.getAuthorities().stream().map(value -> value.getAuthority()).sorted().collect(Collectors.joining(","));
		int updated = jdbc.update("""
			UPDATE blade_oauth2_authorization_consent SET authorities=?, tenant_id=?, update_time=CURRENT_TIMESTAMP
			WHERE registered_client_id=? AND principal_name=? AND status=1 AND is_deleted=0
			""", authorities, tenantId, consent.getRegisteredClientId(), consent.getPrincipalName());
		if (updated == 0 && !exists(consent)) {
			try {
				jdbc.update("""
					INSERT INTO blade_oauth2_authorization_consent (id, registered_client_id, principal_name, authorities, tenant_id)
					VALUES (?, ?, ?, ?, ?)
					""", identifiers.nextId(consent).longValue(), consent.getRegisteredClientId(), consent.getPrincipalName(), authorities, tenantId);
			} catch (org.springframework.dao.DuplicateKeyException exception) {
				// 仅重试相同业务键的并发创建；主键碰撞不会改写其他用户的同意。
				if (jdbc.update("""
					UPDATE blade_oauth2_authorization_consent SET authorities=?, tenant_id=?, update_time=CURRENT_TIMESTAMP
					WHERE registered_client_id=? AND principal_name=? AND status=1 AND is_deleted=0
					""", authorities, tenantId, consent.getRegisteredClientId(), consent.getPrincipalName()) == 0 && !exists(consent)) {
					throw exception;
				}
			}
		}

	}
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void remove(OAuth2AuthorizationConsent consent) {
		Assert.notNull(consent, "授权同意不能为空");
		jdbc.update("DELETE FROM blade_oauth2_authorization_consent WHERE registered_client_id=? AND principal_name=? AND tenant_id=?",
			consent.getRegisteredClientId(), consent.getPrincipalName(), currentTenant(consent.getPrincipalName()));
	}
	@Override
	public OAuth2AuthorizationConsent findById(String clientId, String principalName) {
		Assert.hasText(clientId, "客户端编号不能为空");
		Assert.hasText(principalName, "用户主体不能为空");
		String tenantId = currentTenant(principalName);
		if (tenantId == null) {
			return null;
		}
		List<OAuth2AuthorizationConsent> rows = jdbc.query("""
			SELECT authorities FROM blade_oauth2_authorization_consent
			WHERE registered_client_id=? AND principal_name=? AND tenant_id=? AND status=1 AND is_deleted=0
			""", (rs, row) -> {
			var builder = OAuth2AuthorizationConsent.withId(clientId, principalName);
			Arrays.stream(rs.getString("authorities").split(",")).filter(value -> !value.isBlank())
				.forEach(value -> builder.authority(new SimpleGrantedAuthority(value)));
			return builder.build();
		}, clientId, principalName, tenantId);
		return rows.isEmpty() ? null : rows.getFirst();
	}

	private boolean exists(OAuth2AuthorizationConsent consent) {
		return Integer.valueOf(1).equals(jdbc.queryForObject(
			"SELECT COUNT(*) FROM blade_oauth2_authorization_consent WHERE registered_client_id=? AND principal_name=? AND status=1 AND is_deleted=0",
			Integer.class, consent.getRegisteredClientId(), consent.getPrincipalName()));
	}

	private String currentTenant(String principalName) {
		var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.getName().equals(principalName)
			|| !(authentication.getDetails() instanceof java.util.Map<?, ?> identity)) {
			return null;
		}
		return (String) identity.get("tenantId");
	}
}
