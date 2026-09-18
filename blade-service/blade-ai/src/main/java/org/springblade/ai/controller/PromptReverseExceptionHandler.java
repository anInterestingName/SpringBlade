/**
 * Copyright (c) 2018-2099, Chill Zhuang 庄骞 (bladejava@qq.com).
 * Licensed under the Apache License, Version 2.0.
 */
package org.springblade.ai.controller;

import org.springblade.ai.prompt.constant.PromptReverseResultCode;
import org.springblade.core.tool.api.R;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** 图片反推 multipart 容器级异常映射。 @author BladeX */
@RestControllerAdvice(assignableTypes = PromptReverseController.class)
public class PromptReverseExceptionHandler {
	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public R<Void> handleMaxUploadSize(MaxUploadSizeExceededException exception) {
		return R.fail(PromptReverseResultCode.IMAGE_INVALID);
	}
}
