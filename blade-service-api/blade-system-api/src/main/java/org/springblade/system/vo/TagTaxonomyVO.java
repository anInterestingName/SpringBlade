/**
 * Copyright (c) 2018-2099, Chill Zhuang 庄骞 (bladejava@qq.com).
 * Licensed under the Apache License, Version 2.0.
 */
package org.springblade.system.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** 当前租户有效标签体系。 @author BladeX */
@Data
public class TagTaxonomyVO implements Serializable {
	@Serial private static final long serialVersionUID = 1L;
	private String schemaVersion = "1.0";
	private List<TagTaxonomyCategoryVO> categories = new ArrayList<>();
}
