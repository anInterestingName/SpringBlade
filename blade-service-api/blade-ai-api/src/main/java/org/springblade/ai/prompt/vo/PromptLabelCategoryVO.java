/**
 * Copyright (c) 2018-2099, Chill Zhuang 庄骞 (bladejava@qq.com).
 * Licensed under the Apache License, Version 2.0.
 */
package org.springblade.ai.prompt.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/** 图片反推标签分类结果。 @author BladeX */
@Data
public class PromptLabelCategoryVO implements Serializable {
	@Serial private static final long serialVersionUID = 1L;
	private String categoryCode;
	private String categoryName;
	private List<PromptLabelSelectionVO> selections;
}
