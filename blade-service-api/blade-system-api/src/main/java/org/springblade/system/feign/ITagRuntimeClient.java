/**
 * Copyright (c) 2018-2099, Chill Zhuang 庄骞 (bladejava@qq.com).
 * Licensed under the Apache License, Version 2.0.
 */
package org.springblade.system.feign;

import org.springblade.core.launch.constant.AppConstant;
import org.springblade.core.tool.api.R;
import org.springblade.system.vo.TagTaxonomyVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

/** 标签运行时 Feign 契约。 @author BladeX */
@FeignClient(value = AppConstant.APPLICATION_SYSTEM_NAME, fallback = ITagRuntimeClientFallback.class)
public interface ITagRuntimeClient {
	String API_PREFIX = "/feign/client/tag-runtime";

	@GetMapping(API_PREFIX + "/taxonomy")
	R<TagTaxonomyVO> taxonomy();
}
