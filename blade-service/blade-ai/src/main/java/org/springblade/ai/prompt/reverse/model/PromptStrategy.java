/**
 * Copyright (c) 2018-2099, Chill Zhuang 庄骞 (bladejava@qq.com).
 * Licensed under the Apache License, Version 2.0.
 */
package org.springblade.ai.prompt.reverse.model;

import org.springblade.ai.prompt.vo.PromptMessageVO;
import org.springblade.ai.prompt.vo.PromptStrategyVersionVO;

import java.util.List;

/** 当前发布的图片分析策略。 @author BladeX */
public record PromptStrategy(PromptStrategyVersionVO version, List<PromptMessageVO> messages) {
}
