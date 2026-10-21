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

import io.micrometer.core.instrument.MeterRegistry;
import org.springblade.authorization.props.AuthorizationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.Instant;

/**
 * 分批维护已过期的协议状态，不删除业务数据和授权同意。
 *
 * @author SpringBlade
 */
@Configuration(proxyBeanMethods=false)
@EnableScheduling
public class AuthorizationMaintenance {
	private final JdbcTemplate jdbc;
	private final AuthorizationProperties properties;
	private final MeterRegistry metrics;
	public AuthorizationMaintenance(JdbcTemplate jdbc,AuthorizationProperties properties,MeterRegistry metrics) {
		this.jdbc=jdbc;this.properties=properties;this.metrics=metrics;
	}
	@Scheduled(fixedDelayString="${blade.authorization.cleanup-interval:30m}",initialDelayString="${blade.authorization.cleanup-interval:30m}")
	@Transactional(rollbackFor=Exception.class)
	public void cleanup() {
		if (!properties.isCleanupEnabled()) return;
		Timestamp cutoff=Timestamp.from(Instant.now().minus(properties.getCleanupRetention()));
		try {
			int authorizations=jdbc.update("DELETE FROM blade_oauth2_authorization WHERE expires_at<? LIMIT ?",cutoff,properties.getCleanupBatchSize());
			int refresh=jdbc.update("DELETE FROM blade_oauth2_refresh_token WHERE expires_at<? LIMIT ?",cutoff,properties.getCleanupBatchSize());
			metrics.counter("blade.authorization.cleanup.rows","table","authorization").increment(authorizations);
			metrics.counter("blade.authorization.cleanup.rows","table","refresh").increment(refresh);
		} catch (RuntimeException error) {
			metrics.counter("blade.authorization.cleanup.failures").increment();
			throw new IllegalStateException("授权状态维护失败");
		}
	}
}
