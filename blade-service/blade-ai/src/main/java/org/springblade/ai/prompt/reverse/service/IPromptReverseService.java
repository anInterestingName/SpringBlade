/**
 * Copyright (c) 2018-2099, Chill Zhuang 庄骞 (bladejava@qq.com).
 * Licensed under the Apache License, Version 2.0.
 */
package org.springblade.ai.prompt.reverse.service;

import org.springblade.ai.prompt.vo.PromptReverseVO;
import org.springframework.web.multipart.MultipartFile;

/** 图片提示词反推服务。 @author BladeX */
public interface IPromptReverseService {
	PromptReverseVO reverse(MultipartFile image);
}
