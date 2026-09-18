/**
 * Copyright (c) 2018-2099, Chill Zhuang 庄骞 (bladejava@qq.com).
 * Licensed under the Apache License, Version 2.0.
 */
package org.springblade.ai.prompt.reverse.model;

/** 发送给 fast-staratlas-ai 的分析策略版本摘要。 @author BladeX */
public record AnalysisPromptContext(String code, String promptType, Long versionId, Integer versionNo,
	String contentHash) {
}
