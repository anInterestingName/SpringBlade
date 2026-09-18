/**
 * Copyright (c) 2018-2099, Chill Zhuang 庄骞 (bladejava@qq.com).
 * Licensed under the Apache License, Version 2.0.
 */
package org.springblade.ai.prompt.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/** 图片视觉分析结果。 @author BladeX */
@Data
public class PromptAnalysisVO implements Serializable {
	@Serial private static final long serialVersionUID = 1L;
	private String summary;
	private String subject;
	private String scene;
	private List<String> details;
	private List<String> unmatchedFeatures;
	private List<String> uncertainFeatures;
}
