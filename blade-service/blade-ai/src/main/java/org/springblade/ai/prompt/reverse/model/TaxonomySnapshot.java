/**
 * Copyright (c) 2018-2099, Chill Zhuang 庄骞 (bladejava@qq.com).
 * Licensed under the Apache License, Version 2.0.
 */
package org.springblade.ai.prompt.reverse.model;

import org.springblade.system.vo.TagTaxonomyVO;

/** Redis 标签快照及其内容摘要。 @author BladeX */
public record TaxonomySnapshot(String taxonomyHash, TagTaxonomyVO taxonomy) {
}
