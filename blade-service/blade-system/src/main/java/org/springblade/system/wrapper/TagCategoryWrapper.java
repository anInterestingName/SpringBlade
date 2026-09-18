/**
 * Copyright (c) 2018-2099, Chill Zhuang 庄骞 (bladejava@qq.com).
 * Licensed under the Apache License, Version 2.0.
 */
package org.springblade.system.wrapper;

import org.springblade.system.entity.TagCategory;
import org.springblade.system.enums.TagSelectionMode;
import org.springblade.system.enums.TagStatus;
import org.springblade.system.vo.TagCategoryDetailVO;
import org.springblade.system.vo.TagCategoryListVO;
import org.springframework.stereotype.Component;

/** 标签分类视图转换器。 @author BladeX */
@Component
public class TagCategoryWrapper {
	public TagCategoryListVO listVO(TagCategoryListVO vo) {
		TagSelectionMode mode = TagSelectionMode.of(vo.getSelectionMode());
		TagStatus status = TagStatus.of(vo.getStatus());
		vo.setSelectionModeName(mode == null ? null : mode.getLabel());
		vo.setStatusName(status == null ? null : status.getLabel());
		return vo;
	}

	public TagCategoryDetailVO detailVO(TagCategory category, long tagCount) {
		TagCategoryDetailVO vo = new TagCategoryDetailVO();
		vo.setId(category.getId());
		vo.setCategoryCode(category.getCategoryCode());
		vo.setCategoryName(category.getCategoryName());
		vo.setSelectionMode(category.getSelectionMode());
		TagSelectionMode mode = TagSelectionMode.of(category.getSelectionMode());
		vo.setSelectionModeName(mode == null ? null : mode.getLabel());
		vo.setMaxSelectCount(category.getMaxSelectCount());
		vo.setSort(category.getSort());
		vo.setRemark(category.getRemark());
		vo.setStatus(category.getStatus());
		TagStatus status = TagStatus.of(category.getStatus());
		vo.setStatusName(status == null ? null : status.getLabel());
		vo.setTagCount(tagCount);
		vo.setCreateTime(category.getCreateTime());
		vo.setUpdateTime(category.getUpdateTime());
		vo.setLockVersion(category.getLockVersion());
		return vo;
	}
}
