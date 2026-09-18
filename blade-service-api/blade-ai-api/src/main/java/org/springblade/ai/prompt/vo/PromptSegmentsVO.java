/**
 * Copyright (c) 2018-2099, Chill Zhuang 庄骞 (bladejava@qq.com).
 * Licensed under the Apache License, Version 2.0.
 */
package org.springblade.ai.prompt.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/** 生图提示词分段。 @author BladeX */
@Data
public class PromptSegmentsVO implements Serializable {
	@Serial private static final long serialVersionUID = 1L;
	private List<String> subject;
	private List<String> scene;
	private List<String> style;
	private List<String> composition;
	private List<String> lighting;
	private List<String> color;
	private List<String> quality;
}
