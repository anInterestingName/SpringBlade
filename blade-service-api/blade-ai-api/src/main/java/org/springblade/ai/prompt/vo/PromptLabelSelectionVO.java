/**
 * Copyright (c) 2018-2099, Chill Zhuang 庄骞 (bladejava@qq.com).
 * Licensed under the Apache License, Version 2.0.
 */
package org.springblade.ai.prompt.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 图片反推命中的标签。 @author BladeX */
@Data
public class PromptLabelSelectionVO implements Serializable {
	@Serial private static final long serialVersionUID = 1L;
	private String tagCode;
	private String tagName;
	private Double confidence;
	private String reason;
}
