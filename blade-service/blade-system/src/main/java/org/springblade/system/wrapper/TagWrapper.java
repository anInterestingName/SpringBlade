/**
 * Copyright (c) 2018-2099, Chill Zhuang 庄骞 (bladejava@qq.com).
 * Licensed under the Apache License, Version 2.0.
 */
package org.springblade.system.wrapper;

import org.springblade.system.entity.TagDefinition;
import org.springblade.system.enums.TagStatus;
import org.springblade.system.vo.TagDetailVO;
import org.springblade.system.vo.TagListVO;
import org.springblade.system.vo.TagTreeVO;
import org.springframework.stereotype.Component;

/** 标签视图转换器。 @author BladeX */
@Component
public class TagWrapper {
	public TagListVO listVO(TagListVO vo) {
		TagStatus status = TagStatus.of(vo.getStatus());
		vo.setStatusName(status == null ? null : status.getLabel());
		return vo;
	}

	public TagDetailVO detailVO(TagDefinition tag) {
		TagDetailVO vo = new TagDetailVO();
		vo.setId(tag.getId());
		vo.setCategoryId(tag.getCategoryId());
		vo.setParentId(tag.getParentId());
		vo.setAncestors(tag.getAncestors());
		vo.setDepth(tag.getDepth());
		vo.setTagCode(tag.getTagCode());
		vo.setTagName(tag.getTagName());
		vo.setSort(tag.getSort());
		vo.setRemark(tag.getRemark());
		vo.setStatus(tag.getStatus());
		TagStatus status = TagStatus.of(tag.getStatus());
		vo.setStatusName(status == null ? null : status.getLabel());
		vo.setCreateTime(tag.getCreateTime());
		vo.setUpdateTime(tag.getUpdateTime());
		vo.setLockVersion(tag.getLockVersion());
		return vo;
	}

	public TagTreeVO treeVO(TagDefinition tag) {
		TagTreeVO vo = new TagTreeVO();
		vo.setId(tag.getId());
		vo.setCategoryId(tag.getCategoryId());
		vo.setParentId(tag.getParentId());
		vo.setAncestors(tag.getAncestors());
		vo.setTagCode(tag.getTagCode());
		vo.setTagName(tag.getTagName());
		vo.setDepth(tag.getDepth());
		vo.setSort(tag.getSort());
		vo.setRemark(tag.getRemark());
		vo.setStatus(tag.getStatus());
		TagStatus status = TagStatus.of(tag.getStatus());
		vo.setStatusName(status == null ? null : status.getLabel());
		vo.setLockVersion(tag.getLockVersion());
		return vo;
	}
}
