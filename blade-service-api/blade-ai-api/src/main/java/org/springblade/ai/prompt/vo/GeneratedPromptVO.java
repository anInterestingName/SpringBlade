/**
 * Copyright (c) 2018-2099, Chill Zhuang 庄骞 (bladejava@qq.com).
 * Licensed under the Apache License, Version 2.0.
 */
package org.springblade.ai.prompt.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 图片反推生成的正向和负向提示词。 @author BladeX */
@Data
public class GeneratedPromptVO implements Serializable {
	@Serial private static final long serialVersionUID = 1L;
	private String language;
	private String positive;
	private String negative;
	private PromptSegmentsVO segments;
}
