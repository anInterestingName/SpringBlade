/**
 * Copyright (c) 2018-2099, Chill Zhuang 庄骞 (bladejava@qq.com).
 * Licensed under the Apache License, Version 2.0.
 */
package org.springblade.system.feign;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springblade.core.secure.annotation.PreAuth;
import org.springblade.core.tool.api.R;
import org.springblade.system.constant.TagPermission;
import org.springblade.system.service.ITagService;
import org.springblade.system.vo.TagTaxonomyVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 标签运行时 Feign 服务端。 @author BladeX */
@Hidden
@RestController
@RequiredArgsConstructor
public class TagRuntimeClient implements ITagRuntimeClient {
	private final ITagService tagService;

	@Override
	@GetMapping(API_PREFIX + "/taxonomy")
	@PreAuth(permission = TagPermission.RUNTIME)
	public R<TagTaxonomyVO> taxonomy() {
		return R.data(tagService.effectiveTaxonomy());
	}
}
