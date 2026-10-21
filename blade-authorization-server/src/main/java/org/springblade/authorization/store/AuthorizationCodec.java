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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.jackson2.OAuth2AuthorizationServerJackson2Module;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Principal;
import java.time.Instant;
import java.util.*;
import org.springblade.authorization.props.AuthorizationProperties;

/**
 * 授权状态的Jackson 2存储编解码，仅使用框架支持的身份类型。
 *
 * @author SpringBlade
 */
@Component
@SuppressWarnings("removal")
public class AuthorizationCodec {
	private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };
	private final ObjectMapper mapper;
	private final SecretKeySpec encryptionKey;
	private final boolean allowLegacyPayload;
	private final SecureRandom random = new SecureRandom();
	public AuthorizationCodec(AuthorizationProperties properties) {
		Assert.hasText(properties.getPayloadEncryptionKey(), "必须配置授权状态加密密钥");
		Assert.isTrue(properties.getPayloadEncryptionKey().getBytes(StandardCharsets.UTF_8).length >= 32,
			"授权状态加密密钥至少32字节");
		this.encryptionKey = new SecretKeySpec(sha256(properties.getPayloadEncryptionKey()), "AES");
		this.allowLegacyPayload = properties.isAllowLegacyPayload();
		mapper = new ObjectMapper();
		mapper.registerModules(SecurityJackson2Modules.getModules(getClass().getClassLoader()));
		mapper.registerModule(new OAuth2AuthorizationServerJackson2Module());
	}
	public String encode(OAuth2Authorization authorization) {
		Map<String, Object> payload = new HashMap<>();
		payload.put("attributes", new HashMap<>(authorization.getAttributes()));
		List<Map<String, Object>> tokens = new ArrayList<>();
		append(tokens, "code", authorization.getToken(OAuth2AuthorizationCode.class));
		append(tokens, "access", authorization.getAccessToken());
		append(tokens, "refresh", authorization.getRefreshToken());
		append(tokens, "id", authorization.getToken(OidcIdToken.class));
		payload.put("tokens", tokens);
		try {
			return encrypt(mapper.writeValueAsString(payload));
		} catch (Exception exception) {
			throw new IllegalStateException("授权状态无法序列化");
		}
	}
	private void append(List<Map<String, Object>> tokens, String kind, OAuth2Authorization.Token<?> token) {
		if (token == null) {
			return;
		}
		Map<String, Object> value = new HashMap<>();
		value.put("kind", kind);
		value.put("value", token.getToken().getTokenValue());
		value.put("issuedAt", token.getToken().getIssuedAt().toString());
		value.put("expiresAt", token.getToken().getExpiresAt() == null ? null : token.getToken().getExpiresAt().toString());
		value.put("metadata", new HashMap<>(token.getMetadata()));
		if (token.getToken() instanceof OAuth2AccessToken access) {
			value.put("scopes", new ArrayList<>(access.getScopes()));
		}
		tokens.add(value);
	}
	@SuppressWarnings("unchecked")
	public OAuth2Authorization decode(String payload, String authorizationId, RegisteredClient client,
		String principal, String grant, String scopes) {
		Map<String, Object> state;
		try {
			String json = payload != null && payload.startsWith("v1:") ? decrypt(payload)
				: (allowLegacyPayload ? payload : null);
			Assert.hasText(json, "授权状态未使用受保护格式");
			state = mapper.readValue(json, MAP);
		} catch (Exception exception) {
			throw new IllegalStateException("授权状态无法反序列化");
		}
		OAuth2Authorization.Builder builder = OAuth2Authorization.withRegisteredClient(client)
			.id(authorizationId).principalName(principal).authorizationGrantType(new AuthorizationGrantType(grant))
			.authorizedScopes(scopes.isBlank() ? Set.of() : new HashSet<>(Arrays.asList(scopes.split(","))))
			.attributes(attributes -> attributes.putAll((Map<String, Object>) state.get("attributes")));
		for (Map<String, Object> value : (List<Map<String, Object>>) state.get("tokens")) {
			String raw = (String) value.get("value");
			Instant issued = Instant.parse((String) value.get("issuedAt"));
			Instant expires = value.get("expiresAt") == null ? null : Instant.parse((String) value.get("expiresAt"));
			Map<String, Object> metadata = (Map<String, Object>) value.get("metadata");
			OAuth2Token token = switch ((String) value.get("kind")) {
				case "code" -> new OAuth2AuthorizationCode(raw, issued, expires);
				case "access" -> new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, raw, issued, expires,
					new HashSet<>((List<String>) value.get("scopes")));
				case "refresh" -> new OAuth2RefreshToken(raw, issued, expires);
				case "id" -> new OidcIdToken(raw, issued, expires,
					(Map<String, Object>) metadata.get(OAuth2Authorization.Token.CLAIMS_METADATA_NAME));
				default -> throw new IllegalStateException("授权令牌类型无效");
			};
			builder.token(token, values -> values.putAll(metadata));
		}
		return builder.build();
	}

	private String encrypt(String plaintext) {
		try {
			byte[] iv = new byte[12];
			random.nextBytes(iv);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(128, iv));
			byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
			byte[] combined = new byte[iv.length + encrypted.length];
			System.arraycopy(iv, 0, combined, 0, iv.length);
			System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
			return "v1:" + Base64.getUrlEncoder().withoutPadding().encodeToString(combined);
		} catch (Exception exception) {
			throw new IllegalStateException("授权状态无法加密");
		}
	}

	private String decrypt(String encoded) {
		try {
			byte[] combined = Base64.getUrlDecoder().decode(encoded.substring(3));
			if (combined.length <= 12) {
				throw new IllegalArgumentException("密文长度无效");
			}
			byte[] iv = Arrays.copyOf(combined, 12);
			byte[] ciphertext = Arrays.copyOfRange(combined, 12, combined.length);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(128, iv));
			return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
		} catch (Exception exception) {
			throw new IllegalStateException("授权状态无法解密");
		}
	}

	private static byte[] sha256(String value) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
		} catch (Exception exception) {
			throw new IllegalStateException("授权状态密钥初始化失败", exception);
		}
	}
	public String tenantId(OAuth2Authorization authorization) {
		Object principal = authorization.getAttribute(Principal.class.getName());
		if (principal instanceof Authentication authentication && authentication.getDetails() instanceof Map<?, ?> details) {
			return (String) details.get("tenantId");
		}
		return null;
	}
}
