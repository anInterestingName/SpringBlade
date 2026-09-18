/**
 * Copyright (c) 2018-2099, Chill Zhuang 庄骞 (bladejava@qq.com).
 * Licensed under the Apache License, Version 2.0.
 */
package org.springblade.system.tag.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springblade.system.tag.TagTaxonomyCacheKey;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/** 标签事务提交后的运行时快照指针失效器。 @author BladeX */
@Slf4j
@Component
@RequiredArgsConstructor
public class TagTaxonomyCacheInvalidator {
	private static final DefaultRedisScript<Long> INVALIDATE = new DefaultRedisScript<>(
		"local generation = redis.call('incr', KEYS[1]); redis.call('del', KEYS[2]); return generation",
		Long.class);

	private final StringRedisTemplate redisTemplate;

	public void invalidateAfterCommit(String tenantId) {
		Runnable invalidation = () -> invalidate(tenantId);
		if (TransactionSynchronizationManager.isSynchronizationActive()
			&& TransactionSynchronizationManager.isActualTransactionActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					invalidation.run();
				}
			});
		} else {
			invalidation.run();
		}
	}

	private void invalidate(String tenantId) {
		try {
			redisTemplate.execute(INVALIDATE, List.of(TagTaxonomyCacheKey.generation(tenantId),
				TagTaxonomyCacheKey.current(tenantId)));
		} catch (RuntimeException exception) {
			log.error("标签快照指针失效失败 tenantId={} errorType={}", tenantId,
				exception.getClass().getSimpleName());
		}
	}
}
