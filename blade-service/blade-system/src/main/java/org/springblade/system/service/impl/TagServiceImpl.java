/**
 * Copyright (c) 2018-2099, Chill Zhuang 庄骞 (bladejava@qq.com).
 * Licensed under the Apache License, Version 2.0.
 */
package org.springblade.system.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.springblade.core.log.exception.ServiceException;
import org.springblade.core.mp.base.BaseServiceImpl;
import org.springblade.core.mp.support.Condition;
import org.springblade.core.mp.support.Query;
import org.springblade.core.secure.utils.SecureUtil;
import org.springblade.core.tool.api.ResultCode;
import org.springblade.system.constant.TagResultCode;
import org.springblade.system.dto.TagCreateDTO;
import org.springblade.system.dto.TagDeleteDTO;
import org.springblade.system.dto.TagStatusDTO;
import org.springblade.system.dto.TagUpdateDTO;
import org.springblade.system.entity.TagCategory;
import org.springblade.system.entity.TagDefinition;
import org.springblade.system.enums.TagStatus;
import org.springblade.system.mapper.TagCategoryMapper;
import org.springblade.system.mapper.TagMapper;
import org.springblade.system.service.ITagService;
import org.springblade.system.tag.support.TagOptionEvaluator;
import org.springblade.system.tag.support.TagTreeValidator;
import org.springblade.system.vo.TagDetailVO;
import org.springblade.system.vo.TagListVO;
import org.springblade.system.vo.TagMutationVO;
import org.springblade.system.vo.TagOptionVO;
import org.springblade.system.vo.TagTreeVO;
import org.springblade.system.wrapper.TagWrapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** 标签服务实现。 @author BladeX */
@Service
@RequiredArgsConstructor
public class TagServiceImpl extends BaseServiceImpl<TagMapper, TagDefinition> implements ITagService {
	private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{0,63}$");

	private final TagCategoryMapper categoryMapper;
	private final TagTreeValidator treeValidator;
	private final TagOptionEvaluator optionEvaluator;
	private final TagWrapper tagWrapper;

	@Override
	public IPage<TagListVO> selectPage(Long categoryId, Long parentId, String name, String code,
		Integer status, Query query) {
		validateOptionalStatus(status);
		IPage<TagListVO> page = Condition.getPage(query);
		page.setRecords(baseMapper.selectTagPage(page, currentTenantId(), categoryId, parentId,
			trimToNull(name), normalizeOptionalCode(code), status).stream().map(tagWrapper::listVO).toList());
		return page;
	}

	@Override
	public TagDetailVO detail(Long id) {
		return tagWrapper.detailVO(requireTag(currentTenantId(), id));
	}

	@Override
	public List<TagTreeVO> tree(Long categoryId) {
		String tenantId = currentTenantId();
		requireCategory(tenantId, categoryId);
		List<TagDefinition> tags = baseMapper.selectByCategory(tenantId, categoryId);
		Map<Long, TagTreeVO> nodes = new HashMap<>();
		tags.forEach(tag -> nodes.put(tag.getId(), tagWrapper.treeVO(tag)));
		List<TagTreeVO> roots = new ArrayList<>();
		for (TagDefinition tag : tags) {
			TagTreeVO node = nodes.get(tag.getId());
			if (tag.getParentId() == null || tag.getParentId() == 0L) {
				roots.add(node);
			} else {
				TagTreeVO parent = nodes.get(tag.getParentId());
				if (parent != null) parent.getChildren().add(node);
			}
		}
		return roots;
	}

	@Override
	public List<TagOptionVO> options(Long categoryId) {
		String tenantId = currentTenantId();
		TagCategory category = requireCategory(tenantId, categoryId);
		return optionEvaluator.evaluate(category, baseMapper.selectByCategory(tenantId, categoryId));
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public TagMutationVO create(TagCreateDTO dto) {
		String tenantId = currentTenantId();
		TagCategory category = requireLockedCategory(tenantId, dto.getCategoryId());
		List<TagDefinition> tags = baseMapper.selectByCategory(tenantId, category.getId());
		Map<Long, TagDefinition> tagMap = toMap(tags);
		TagTreeValidator.Placement placement = treeValidator.resolvePlacement(dto.getParentId(), category.getId(), null, tagMap);
		String code = normalizeCode(dto.getTagCode());
		if (baseMapper.selectByCodeIncludingDeleted(tenantId, category.getId(), code) != null) {
			throw new ServiceException(TagResultCode.TAG_CODE_DUPLICATE);
		}
		TagDefinition tag = new TagDefinition();
		tag.setTenantId(tenantId);
		tag.setCategoryId(category.getId());
		tag.setParentId(dto.getParentId() == null ? 0L : dto.getParentId());
		tag.setAncestors(placement.ancestors());
		tag.setDepth(placement.depth());
		tag.setTagCode(code);
		tag.setTagName(dto.getTagName().trim());
		tag.setSort(dto.getSort() == null ? 0 : dto.getSort());
		tag.setRemark(trimToNull(dto.getRemark()));
		tag.setLockVersion(0L);
		tag.setStatus(TagStatus.ENABLED.getValue());
		tag.setIsDeleted(0);
		try {
			save(tag);
		} catch (DuplicateKeyException exception) {
			throw new ServiceException(TagResultCode.TAG_CODE_DUPLICATE, exception);
		}
		return mutation(tag.getId(), tag.getStatus(), tag.getLockVersion());
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public TagMutationVO update(TagUpdateDTO dto) {
		if (dto.isImmutableFieldAttempted()) {
			throw new ServiceException(TagResultCode.TAG_IMMUTABLE_FIELD);
		}
		String tenantId = currentTenantId();
		TagDefinition snapshot = requireTag(tenantId, dto.getId());
		requireLockedCategory(tenantId, snapshot.getCategoryId());
		TagDefinition current = requireLockedTag(tenantId, dto.getId());
		if (!Objects.equals(current.getLockVersion(), dto.getLockVersion())) {
			throw new ServiceException(TagResultCode.TAG_CONFLICT);
		}
		List<TagDefinition> tags = baseMapper.selectByCategory(tenantId, current.getCategoryId());
		TagTreeValidator.Placement placement = treeValidator.resolvePlacement(dto.getParentId(), current.getCategoryId(),
			current.getId(), toMap(tags));
		treeValidator.validateSubtreeDepth(current, placement.depth(), tags);
		boolean structureChanged = !Objects.equals(current.getParentId(), dto.getParentId() == null ? 0L : dto.getParentId());

		TagDefinition update = new TagDefinition();
		update.setId(current.getId());
		update.setCategoryId(current.getCategoryId());
		update.setParentId(dto.getParentId() == null ? 0L : dto.getParentId());
		update.setAncestors(placement.ancestors());
		update.setDepth(placement.depth());
		update.setTagName(dto.getTagName().trim());
		update.setSort(dto.getSort());
		update.setRemark(trimToNull(dto.getRemark()));
		Long updateUser = currentUserId();
		if (baseMapper.updateTag(update, tenantId, dto.getLockVersion(), updateUser) != 1) {
			throw new ServiceException(TagResultCode.TAG_CONFLICT);
		}
		if (structureChanged) {
			updateDescendantPaths(tenantId, current, placement, tags, updateUser);
		}
		return mutation(current.getId(), current.getStatus(), current.getLockVersion() + 1);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public TagMutationVO changeStatus(TagStatusDTO dto) {
		TagStatus target = requireStatus(dto.getStatus());
		String tenantId = currentTenantId();
		TagDefinition snapshot = requireTag(tenantId, dto.getId());
		requireLockedCategory(tenantId, snapshot.getCategoryId());
		TagDefinition current = requireLockedTag(tenantId, dto.getId());
		if (Objects.equals(current.getStatus(), target.getValue())) {
			return mutation(current.getId(), current.getStatus(), current.getLockVersion());
		}
		if (!Objects.equals(current.getLockVersion(), dto.getLockVersion())
			|| baseMapper.updateStatus(tenantId, current.getCategoryId(), current.getId(), dto.getLockVersion(),
			target.getValue(), currentUserId()) != 1) {
			throw new ServiceException(TagResultCode.TAG_CONFLICT);
		}
		return mutation(current.getId(), target.getValue(), current.getLockVersion() + 1);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean remove(TagDeleteDTO dto) {
		String tenantId = currentTenantId();
		TagDefinition snapshot = requireTag(tenantId, dto.getId());
		requireLockedCategory(tenantId, snapshot.getCategoryId());
		TagDefinition current = requireLockedTag(tenantId, dto.getId());
		if (!Objects.equals(current.getLockVersion(), dto.getLockVersion())) {
			throw new ServiceException(TagResultCode.TAG_CONFLICT);
		}
		if (baseMapper.countChildren(tenantId, current.getCategoryId(), current.getId()) > 0) {
			throw new ServiceException(TagResultCode.TAG_HAS_CHILDREN);
		}
		if (baseMapper.logicalDelete(tenantId, current.getCategoryId(), current.getId(),
			dto.getLockVersion(), currentUserId()) != 1) {
			throw new ServiceException(TagResultCode.TAG_CONFLICT);
		}
		return true;
	}

	private void updateDescendantPaths(String tenantId, TagDefinition current,
		TagTreeValidator.Placement placement, List<TagDefinition> tags, Long updateUser) {
		String oldPrefix = current.getAncestors() + "," + current.getId();
		String newPrefix = placement.ancestors() + "," + current.getId();
		int depthDelta = placement.depth() - current.getDepth();
		tags.stream().filter(tag -> treeValidator.containsId(tag.getAncestors(), current.getId()))
			.sorted(Comparator.comparing(TagDefinition::getDepth)).forEach(tag -> {
				if (!tag.getAncestors().startsWith(oldPrefix)) {
					throw new ServiceException(TagResultCode.TAG_PARENT_INVALID);
				}
				String newAncestors = newPrefix + tag.getAncestors().substring(oldPrefix.length());
				if (baseMapper.updateStructure(tenantId, current.getCategoryId(), tag.getId(), newAncestors,
					tag.getDepth() + depthDelta, tag.getLockVersion(), updateUser) != 1) {
					throw new ServiceException(TagResultCode.TAG_CONFLICT);
				}
			});
	}

	private Map<Long, TagDefinition> toMap(List<TagDefinition> tags) {
		Map<Long, TagDefinition> map = new HashMap<>();
		tags.forEach(tag -> map.put(tag.getId(), tag));
		return map;
	}

	private TagCategory requireCategory(String tenantId, Long id) {
		TagCategory category = categoryMapper.selectTenantCategory(tenantId, id);
		if (category == null) throw new ServiceException(TagResultCode.TAG_CATEGORY_NOT_FOUND);
		return category;
	}

	private TagCategory requireLockedCategory(String tenantId, Long id) {
		TagCategory category = categoryMapper.selectForUpdate(tenantId, id);
		if (category == null) throw new ServiceException(TagResultCode.TAG_CATEGORY_NOT_FOUND);
		return category;
	}

	private TagDefinition requireTag(String tenantId, Long id) {
		TagDefinition tag = baseMapper.selectTenantTag(tenantId, id);
		if (tag == null) throw new ServiceException(TagResultCode.TAG_NOT_FOUND);
		return tag;
	}

	private TagDefinition requireLockedTag(String tenantId, Long id) {
		TagDefinition tag = baseMapper.selectForUpdate(tenantId, id);
		if (tag == null) throw new ServiceException(TagResultCode.TAG_NOT_FOUND);
		return tag;
	}

	private TagStatus requireStatus(Integer value) {
		TagStatus status = TagStatus.of(value);
		if (status == null) throw new ServiceException(TagResultCode.TAG_STATUS_INVALID);
		return status;
	}

	private void validateOptionalStatus(Integer value) {
		if (value != null) requireStatus(value);
	}

	private TagMutationVO mutation(Long id, Integer status, Long lockVersion) {
		TagMutationVO vo = new TagMutationVO();
		vo.setId(id);
		vo.setStatus(status);
		vo.setLockVersion(lockVersion);
		return vo;
	}

	private String normalizeCode(String code) {
		String normalized = code == null ? null : code.trim().toLowerCase(Locale.ROOT);
		if (normalized == null || !CODE_PATTERN.matcher(normalized).matches()) {
			throw new ServiceException(TagResultCode.TAG_CODE_INVALID);
		}
		return normalized;
	}

	private String normalizeOptionalCode(String code) {
		return code == null ? null : code.trim().toLowerCase(Locale.ROOT);
	}

	private String trimToNull(String value) {
		if (value == null) return null;
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private String currentTenantId() {
		String tenantId = SecureUtil.getTenantId();
		if (!SecureUtil.hasAuth() || tenantId == null || tenantId.isBlank()) {
			throw new ServiceException(ResultCode.UN_AUTHORIZED);
		}
		return tenantId;
	}

	private Long currentUserId() {
		Long userId = SecureUtil.getUserId();
		if (userId == null) throw new ServiceException(ResultCode.UN_AUTHORIZED);
		return userId;
	}
}
