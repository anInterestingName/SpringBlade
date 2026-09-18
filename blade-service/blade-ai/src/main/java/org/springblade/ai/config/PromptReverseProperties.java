/**
 * Copyright (c) 2018-2099, Chill Zhuang 庄骞 (bladejava@qq.com).
 * Licensed under the Apache License, Version 2.0.
 */
package org.springblade.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** 图片提示词反推配置。 @author BladeX */
@Data
@ConfigurationProperties(prefix = "blade.ai.reverse")
public class PromptReverseProperties {
	private String fastBaseUrl;
	private String fastApiKey;
	private Duration connectTimeout = Duration.ofSeconds(5);
	private Duration readTimeout = Duration.ofSeconds(90);
	private long maxPixels = 40_000_000L;
	private long maxResponseBytes = 1024 * 1024L;
	private Duration taxonomySnapshotTtl = Duration.ofHours(24);
	private Duration taxonomyLockTtl = Duration.ofSeconds(10);
	private Duration taxonomyLockWait = Duration.ofSeconds(2);
	private Duration taxonomyPollInterval = Duration.ofMillis(100);
	private String outputLanguage = "en";
	private String targetEngine = "";
}
