/**
 * Copyright (c) 2018-2099, Chill Zhuang 庄骞 (bladejava@qq.com).
 * Licensed under the Apache License, Version 2.0.
 */
package org.springblade.ai.prompt.reverse.model;

import org.springblade.ai.prompt.vo.PromptMessageVO;

import java.util.List;

/** 发送给 fast-staratlas-ai 的可信请求上下文。 @author BladeX */
public record PromptReverseContext(String taxonomyHash, AnalysisPromptContext analysisPrompt,
	List<PromptMessageVO> messages) {
}
