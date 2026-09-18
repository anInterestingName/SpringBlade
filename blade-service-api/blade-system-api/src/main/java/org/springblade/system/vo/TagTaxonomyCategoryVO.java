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

/** 标签运行时快照分类。 @author BladeX */
@Data
public class TagTaxonomyCategoryVO implements Serializable {
	@Serial private static final long serialVersionUID = 1L;
	private String categoryCode;
	private String categoryName;
	private Integer selectionMode;
	private Integer maxSelectCount;
	private Integer sort;
	private List<TagTaxonomyItemVO> tags = new ArrayList<>();
}
