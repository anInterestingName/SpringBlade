/**
 * Copyright (c) 2018-2099, Chill Zhuang 庄骞 (bladejava@qq.com).
 * Licensed under the Apache License, Version 2.0.
 */
package org.springblade.ai.prompt.reverse.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springblade.ai.config.PromptReverseProperties;
import org.springblade.ai.prompt.constant.PromptReverseResultCode;
import org.springblade.ai.prompt.reverse.model.TaxonomySnapshot;
import org.springblade.core.log.exception.ServiceException;
import org.springblade.core.tool.api.R;
import org.springblade.system.feign.ITagRuntimeClient;
import org.springblade.system.tag.TagTaxonomyCacheKey;
import org.springblade.system.vo.TagTaxonomyVO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** 当前租户标签 Redis 快照读取与构建。 @author BladeX */
@Service
@RequiredArgsConstructor
public class TaxonomySnapshotService {
	private static final DefaultRedisScript<Long> RELEASE_LOCK = new DefaultRedisScript<>(
		"if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
		Long.class);
	private static final DefaultRedisScript<Long> PUBLISH_SNAPSHOT = new DefaultRedisScript<>(
		"local generation = redis.call('get', KEYS[1]) or '0'; "
			+ "if generation ~= ARGV[1] then return 0 end; "
			+ "redis.call('set', KEYS[2], ARGV[2], 'PX', ARGV[3]); "
			+ "redis.call('set', KEYS[3], ARGV[4], 'PX', ARGV[3]); return 1",
		Long.class);

	private final StringRedisTemplate redisTemplate;
	private final ITagRuntimeClient tagRuntimeClient;
	private final ObjectMapper objectMapper;
	private final PromptReverseProperties properties;

	public TaxonomySnapshot current(String tenantId) {
		try {
			TaxonomySnapshot cached = loadCurrent(tenantId);
			if (cached != null) {
				return cached;
			}
			return buildWithLock(tenantId);
		} catch (ServiceException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw new ServiceException(PromptReverseResultCode.TAXONOMY_UNAVAILABLE);
		}
	}

	public TaxonomySnapshot refresh(String tenantId) {
		try {
			redisTemplate.delete(TagTaxonomyCacheKey.current(tenantId));
			return buildWithLock(tenantId);
		} catch (ServiceException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw new ServiceException(PromptReverseResultCode.TAXONOMY_UNAVAILABLE);
		}
	}

	private TaxonomySnapshot buildWithLock(String tenantId) {
		String lockKey = TagTaxonomyCacheKey.lock(tenantId);
		String token = UUID.randomUUID().toString();
		Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, token, properties.getTaxonomyLockTtl());
		if (!Boolean.TRUE.equals(acquired)) {
			return awaitSnapshot(tenantId);
		}
		try {
			TaxonomySnapshot cached = loadCurrent(tenantId);
			if (cached != null) {
				return cached;
			}
			for (int attempt = 0; attempt < 3; attempt++) {
				String generation = generation(tenantId);
				TagTaxonomyVO taxonomy = fetchTaxonomy();
				if (taxonomy.getCategories() == null || taxonomy.getCategories().stream()
					.allMatch(category -> category.getTags() == null || category.getTags().isEmpty())) {
					throw new ServiceException(PromptReverseResultCode.TAXONOMY_EMPTY);
				}
				String json = writeJson(taxonomy);
				String hash = hash(json);
				if (publish(tenantId, generation, hash, json)) {
					return new TaxonomySnapshot(hash, taxonomy);
				}
			}
			throw new ServiceException(PromptReverseResultCode.TAXONOMY_UNAVAILABLE);
		} finally {
			releaseLock(lockKey, token);
		}
	}

	private TaxonomySnapshot awaitSnapshot(String tenantId) {
		long deadline = System.nanoTime() + positive(properties.getTaxonomyLockWait()).toNanos();
		Duration interval = positive(properties.getTaxonomyPollInterval());
		while (System.nanoTime() < deadline) {
			try {
				Thread.sleep(Math.max(1L, interval.toMillis()));
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new ServiceException(PromptReverseResultCode.TAXONOMY_UNAVAILABLE, exception);
			}
			TaxonomySnapshot cached = loadCurrent(tenantId);
			if (cached != null) {
				return cached;
			}
		}
		throw new ServiceException(PromptReverseResultCode.TAXONOMY_UNAVAILABLE);
	}

	private TaxonomySnapshot loadCurrent(String tenantId) {
		String currentKey = TagTaxonomyCacheKey.current(tenantId);
		String hash = redisTemplate.opsForValue().get(currentKey);
		if (hash == null || hash.isBlank()) {
			return null;
		}
		String json = redisTemplate.opsForValue().get(TagTaxonomyCacheKey.snapshot(hash));
		if (json == null || json.isBlank()) {
			redisTemplate.delete(currentKey);
			return null;
		}
		if (!hash.equals(hash(json))) {
			redisTemplate.delete(currentKey);
			throw new ServiceException(PromptReverseResultCode.TAXONOMY_UNAVAILABLE);
		}
		try {
			return new TaxonomySnapshot(hash, objectMapper.readValue(json, TagTaxonomyVO.class));
		} catch (JsonProcessingException exception) {
			redisTemplate.delete(currentKey);
			throw new ServiceException(PromptReverseResultCode.TAXONOMY_UNAVAILABLE);
		}
	}

	private TagTaxonomyVO fetchTaxonomy() {
		R<TagTaxonomyVO> result = tagRuntimeClient.taxonomy();
		if (result == null || !result.isSuccess() || result.getData() == null) {
			throw new ServiceException(PromptReverseResultCode.TAXONOMY_UNAVAILABLE);
		}
		return result.getData();
	}

	private String writeJson(TagTaxonomyVO taxonomy) {
		try {
			return objectMapper.writeValueAsString(taxonomy);
		} catch (JsonProcessingException exception) {
			throw new ServiceException(PromptReverseResultCode.TAXONOMY_UNAVAILABLE);
		}
	}

	private String generation(String tenantId) {
		String value = redisTemplate.opsForValue().get(TagTaxonomyCacheKey.generation(tenantId));
		return value == null ? "0" : value;
	}

	private boolean publish(String tenantId, String generation, String hash, String json) {
		long ttlMillis = positive(properties.getTaxonomySnapshotTtl()).toMillis();
		Long published = redisTemplate.execute(PUBLISH_SNAPSHOT,
			List.of(TagTaxonomyCacheKey.generation(tenantId), TagTaxonomyCacheKey.snapshot(hash),
				TagTaxonomyCacheKey.current(tenantId)), generation, json, String.valueOf(ttlMillis), hash);
		return Long.valueOf(1L).equals(published);
	}

	private String hash(String json) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8));
			return "sha256:" + HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 unavailable", exception);
		}
	}

	private void releaseLock(String lockKey, String token) {
		try {
			redisTemplate.execute(RELEASE_LOCK, Collections.singletonList(lockKey), token);
		} catch (RuntimeException ignored) {
			// 锁具有短 TTL，释放失败时不覆盖原始业务异常。
		}
	}

	private Duration positive(Duration duration) {
		return duration == null || duration.isNegative() || duration.isZero() ? Duration.ofMillis(1) : duration;
	}
}
