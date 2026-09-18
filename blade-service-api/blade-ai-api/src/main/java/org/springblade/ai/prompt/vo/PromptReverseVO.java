/**
 * Copyright (c) 2018-2099, Chill Zhuang 庄骞 (bladejava@qq.com).
 * Licensed under the Apache License, Version 2.0.
 */
package org.springblade.ai.prompt.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/** 图片提示词反推固定响应。 @author BladeX */
@Data
public class PromptReverseVO implements Serializable {
	@Serial private static final long serialVersionUID = 1L;
	private String schemaVersion;
	private String taxonomyHash;
	private PromptStrategyVersionVO analysisPrompt;
	private PromptAnalysisVO analysis;
	private List<PromptLabelCategoryVO> labels;
	private GeneratedPromptVO prompt;
	private List<PromptWarningVO> warnings;
}
