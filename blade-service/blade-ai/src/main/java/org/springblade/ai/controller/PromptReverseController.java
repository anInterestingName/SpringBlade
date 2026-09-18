/**
 * Copyright (c) 2018-2099, Chill Zhuang 庄骞 (bladejava@qq.com).
 * Licensed under the Apache License, Version 2.0.
 */
package org.springblade.ai.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springblade.ai.prompt.constant.PromptPermission;
import org.springblade.ai.prompt.constant.PromptReverseResultCode;
import org.springblade.ai.prompt.reverse.service.IPromptReverseService;
import org.springblade.ai.prompt.vo.PromptReverseVO;
import org.springblade.core.boot.ctrl.BladeController;
import org.springblade.core.log.exception.ServiceException;
import org.springblade.core.secure.annotation.PreAuth;
import org.springblade.core.swagger.annotation.ApiOrder;
import org.springblade.core.tool.api.R;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** 图片提示词反推接口。 @author BladeX */
@RestController
@RequiredArgsConstructor
@RequestMapping("/prompt")
@ApiOrder
@Tag(name = "图片提示词反推", description = "单图视觉分析、租户标签选择和生图提示词生成")
public class PromptReverseController extends BladeController {
	private final IPromptReverseService reverseService;

	@PostMapping(value = "/reverse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuth(permission = PromptPermission.REVERSE)
	@Operation(summary = "反推图片提示词")
	public R<PromptReverseVO> reverse(
		@RequestPart(value = "image", required = false) List<MultipartFile> images) {
		if (images == null || images.size() != 1) {
			throw new ServiceException(PromptReverseResultCode.IMAGE_INVALID);
		}
		return R.data(reverseService.reverse(images.getFirst()));
	}
}
