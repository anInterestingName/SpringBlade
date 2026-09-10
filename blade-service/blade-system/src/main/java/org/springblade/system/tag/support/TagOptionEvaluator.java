/**
 * Copyright (c) 2018-2099, Chill Zhuang 庄骞 (bladejava@qq.com).
 * Licensed under the Apache License, Version 2.0.
 */
package org.springblade.system.tag.support;

import lombok.extern.slf4j.Slf4j;
import org.springblade.system.entity.TagCategory;
import org.springblade.system.entity.TagDefinition;
import org.springblade.system.enums.TagStatus;
import org.springblade.system.vo.TagOptionVO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 根据分类、祖先和自身状态计算有效标签选项。 @author BladeX */
@Slf4j
@Component
public class TagOptionEvaluator {

	public List<TagOptionVO> evaluate(TagCategory category, List<TagDefinition> tags) {
		if (!Objects.equals(category.getStatus(), TagStatus.ENABLED.getValue())) {
			return List.of();
		}
		Map<Long, TagDefinition> tagMap = new HashMap<>();
		tags.forEach(tag -> tagMap.put(tag.getId(), tag));
		List<TagOptionVO> options = new ArrayList<>();
		for (TagDefinition tag : tags) {
			if (!Objects.equals(tag.getStatus(), TagStatus.ENABLED.getValue())) {
				continue;
			}
			PathResult path = validatePath(category, tag, tagMap);
			if (!path.valid() && !path.disabled()) {
				log.warn("标签结构异常 tenantId={}, categoryId={}, tagId={}",
					category.getTenantId(), category.getId(), tag.getId());
			}
			if (!path.valid()) {
				continue;
			}
			TagOptionVO option = new TagOptionVO();
			option.setId(tag.getId());
			option.setTagCode(tag.getTagCode());
			option.setTagName(tag.getTagName());
			option.setParentId(tag.getParentId());
			option.setDepth(tag.getDepth());
			option.setSort(tag.getSort());
			options.add(option);
		}
		return options;
	}

	private PathResult validatePath(TagCategory category, TagDefinition tag, Map<Long, TagDefinition> tagMap) {
		List<Long> parentPath = new ArrayList<>();
		Set<Long> visited = new HashSet<>();
		visited.add(tag.getId());
		Long parentId = tag.getParentId();
		while (parentId != null && parentId != 0L) {
			if (!visited.add(parentId)) return PathResult.INVALID;
			TagDefinition parent = tagMap.get(parentId);
			if (parent == null || !Objects.equals(parent.getCategoryId(), category.getId())) return PathResult.INVALID;
			if (!Objects.equals(parent.getStatus(), TagStatus.ENABLED.getValue())) return PathResult.DISABLED;
			parentPath.addFirst(parent.getId());
			parentId = parent.getParentId();
		}
		String expectedAncestors = parentPath.isEmpty() ? "0" : "0," + String.join(",",
			parentPath.stream().map(String::valueOf).toList());
		boolean valid = Objects.equals(expectedAncestors, tag.getAncestors())
			&& Objects.equals(tag.getDepth(), parentPath.size() + 1);
		return valid ? PathResult.VALID : PathResult.INVALID;
	}

	private record PathResult(boolean valid, boolean disabled) {
		private static final PathResult VALID = new PathResult(true, false);
		private static final PathResult INVALID = new PathResult(false, false);
		private static final PathResult DISABLED = new PathResult(false, true);
	}
}
