/**
 * Copyright (c) 2018-2099, Chill Zhuang 庄骞 (bladejava@qq.com).
 * Licensed under the Apache License, Version 2.0.
 */
package org.springblade.system.tag.support;

import org.springblade.core.log.exception.ServiceException;
import org.springblade.system.constant.TagResultCode;
import org.springblade.system.entity.TagDefinition;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

/** 标签父子关系、循环和深度校验器。 @author BladeX */
@Component
public class TagTreeValidator {
	public static final int MAX_DEPTH = 8;

	public Placement resolvePlacement(Long parentId, Long categoryId, Long movingTagId,
		Map<Long, TagDefinition> tags) {
		long resolvedParentId = parentId == null ? 0L : parentId;
		if (resolvedParentId == 0L) {
			return new Placement("0", 1);
		}
		TagDefinition parent = tags.get(resolvedParentId);
		if (parent == null || !Objects.equals(parent.getCategoryId(), categoryId)) {
			throw new ServiceException(TagResultCode.TAG_PARENT_INVALID);
		}
		if (movingTagId != null && (Objects.equals(parent.getId(), movingTagId)
			|| containsId(parent.getAncestors(), movingTagId))) {
			throw new ServiceException(TagResultCode.TAG_CYCLE_DETECTED);
		}
		int depth = parent.getDepth() + 1;
		if (depth > MAX_DEPTH) {
			throw new ServiceException(TagResultCode.TAG_DEPTH_EXCEEDED);
		}
		return new Placement(parent.getAncestors() + "," + parent.getId(), depth);
	}

	public void validateSubtreeDepth(TagDefinition current, int newDepth, Iterable<TagDefinition> tags) {
		int delta = newDepth - current.getDepth();
		for (TagDefinition tag : tags) {
			if (containsId(tag.getAncestors(), current.getId()) && tag.getDepth() + delta > MAX_DEPTH) {
				throw new ServiceException(TagResultCode.TAG_DEPTH_EXCEEDED);
			}
		}
	}

	public boolean containsId(String ancestors, Long id) {
		if (ancestors == null || id == null) {
			return false;
		}
		String value = String.valueOf(id);
		return Arrays.stream(ancestors.split(",")).anyMatch(value::equals);
	}

	public record Placement(String ancestors, int depth) {
	}
}
