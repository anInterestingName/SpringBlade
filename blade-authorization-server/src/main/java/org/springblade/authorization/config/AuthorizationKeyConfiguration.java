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
package org.springblade.authorization.config;

import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springblade.authorization.props.AuthorizationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.util.Assert;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.*;
import java.util.Set;

/**
 * 从外部JWK文件加载固定签名密钥，公开端点只发布公钥。
 *
 * @author SpringBlade
 */
@Configuration(proxyBeanMethods = false)
public class AuthorizationKeyConfiguration {
	@Bean
	public KeyMaterial authorizationKeyMaterial(AuthorizationProperties properties, ResourceLoader loader) {
		Assert.hasText(properties.getJwkSetLocation(), "必须配置持久化JWK文件地址");
		Assert.hasText(properties.getActiveKeyId(), "必须配置活动签名密钥编号");
		Assert.isTrue(properties.getJwkSetLocation().startsWith("file:"), "私钥只能从外部文件加载");
		var resource = loader.getResource(properties.getJwkSetLocation());
		Assert.isTrue(resource.exists() && resource.isReadable(), "签名JWK文件不存在或不可读");
		try {
			validateFilePermissions(resource.getFile().toPath());
		} catch (Exception error) {
			throw new IllegalStateException("签名密钥文件权限不合规");
		}
		try (var input = resource.getInputStream()) {
			JWKSet keys = JWKSet.load(input);
			Assert.isTrue(!keys.getKeys().isEmpty() && keys.getKeys().stream().allMatch(key -> key instanceof RSAKey),
				"JWK集合只能包含RSA签名密钥");
			JWK active = keys.getKeyByKeyId(properties.getActiveKeyId());
			Assert.isTrue(active instanceof RSAKey && active.isPrivate(), "活动密钥必须包含RSA私钥");
			RSAKey signing = (RSAKey) active;
			Assert.isTrue(signing.size() >= 2048, "RSA密钥长度不能小于2048位");
			Assert.isTrue(signing.getAlgorithm() == null || "RS256".equals(signing.getAlgorithm().getName()),
				"活动密钥算法必须为RS256");
			Assert.isTrue(signing.getKeyUse() == null || KeyUse.SIGNATURE.equals(signing.getKeyUse()),
				"活动密钥必须用于签名");
			Assert.isTrue(keys.getKeys().stream().allMatch(key -> key.getKeyID() != null && !key.getKeyID().isBlank()), "所有公钥必须有稳定kid");
			Assert.isTrue(keys.getKeys().stream().map(JWK::getKeyID).distinct().count() == keys.getKeys().size(), "密钥编号不能重复");
			return new KeyMaterial(signing, keys.toPublicJWKSet());
		} catch (Exception exception) {
			throw new IllegalStateException("持久化签名密钥加载失败，请检查文件权限和密钥配置");
		}
	}
	private void validateFilePermissions(Path path) throws java.io.IOException {
		Assert.isTrue(Files.isRegularFile(path) && !Files.isSymbolicLink(path), "签名文件必须为普通文件");
		Path real = path.toRealPath();
		for (Path parent = real.getParent(); parent != null; parent = parent.getParent()) {
			Assert.isTrue(!Files.exists(parent.resolve(".git")), "签名文件不能放在Git仓库内");
		}
		PosixFileAttributeView posix = Files.getFileAttributeView(real, PosixFileAttributeView.class);
		if (posix != null) {
			Assert.isTrue(posix.readAttributes().permissions().stream().noneMatch(permission ->
				permission.name().startsWith("GROUP_") || permission.name().startsWith("OTHERS_")),
				"签名文件只能由所有者访问");
			return;
		}
		AclFileAttributeView acl = Files.getFileAttributeView(real, AclFileAttributeView.class);
		Assert.notNull(acl, "文件系统必须支持权限检查");
		String owner = Files.getOwner(real).getName();
		String current = real.getFileSystem().getUserPrincipalLookupService()
			.lookupPrincipalByName(System.getProperty("user.name")).getName();
		Set<String> allowed = new java.util.HashSet<>(java.util.List.of(owner.toLowerCase(java.util.Locale.ROOT), current.toLowerCase(java.util.Locale.ROOT),
			"nt authority\\system", "builtin\\administrators"));
		for (AclEntry entry : acl.getAcl()) {
			if (entry.type() == AclEntryType.ALLOW && !allowed.contains(entry.principal().getName().toLowerCase(java.util.Locale.ROOT))) {
				Assert.isTrue(entry.permissions().stream().noneMatch(permission -> Set.of(AclEntryPermission.READ_DATA,
					AclEntryPermission.WRITE_DATA, AclEntryPermission.APPEND_DATA, AclEntryPermission.WRITE_ACL,
					AclEntryPermission.WRITE_OWNER, AclEntryPermission.DELETE).contains(permission)), "签名文件ACL授权过宽");
			}
		}
	}

	@Bean
	public JWKSource<SecurityContext> jwkSource(KeyMaterial keys) {
		return new ImmutableJWKSet<>(keys.publicKeys());
	}
	@Bean
	public JwtEncoder jwtEncoder(KeyMaterial keys) {
		return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(keys.signingKey())));
	}
	@Bean
	public JwtDecoder jwtDecoder(JWKSource<SecurityContext> source) {
		return OAuth2AuthorizationServerConfiguration.jwtDecoder(source);
	}
	/** 持久化密钥的运行时引用，禁止输出私钥内容。 */
	public static final class KeyMaterial {
		private final RSAKey signingKey;
		private final JWKSet publicKeys;
		KeyMaterial(RSAKey signingKey, JWKSet publicKeys) {
			this.signingKey = signingKey;
			this.publicKeys = publicKeys;
		}
		RSAKey signingKey() { return signingKey; }
		JWKSet publicKeys() { return publicKeys; }
	}
}
