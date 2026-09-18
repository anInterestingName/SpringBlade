/**
 * Copyright (c) 2018-2099, Chill Zhuang 庄骞 (bladejava@qq.com).
 * Licensed under the Apache License, Version 2.0.
 */
package org.springblade.system.feign;

import org.springblade.core.tool.api.R;
import org.springblade.system.constant.TagResultCode;
import org.springblade.system.vo.TagTaxonomyVO;
import org.springframework.stereotype.Component;

/** 标签运行时 Feign 降级实现。 @author BladeX */
@Component
public class ITagRuntimeClientFallback implements ITagRuntimeClient {
	@Override
	public R<TagTaxonomyVO> taxonomy() {
		return R.fail(TagResultCode.TAG_RUNTIME_UNAVAILABLE);
	}
}
