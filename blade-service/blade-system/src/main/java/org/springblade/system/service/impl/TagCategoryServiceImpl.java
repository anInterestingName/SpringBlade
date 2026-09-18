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
import org.springblade.system.dto.TagCategoryCreateDTO;
import org.springblade.system.dto.TagCategoryDeleteDTO;
import org.springblade.system.dto.TagCategoryStatusDTO;
import org.springblade.system.dto.TagCategoryUpdateDTO;
import org.springblade.system.entity.TagCategory;
import org.springblade.system.enums.TagSelectionMode;
import org.springblade.system.enums.TagStatus;
import org.springblade.system.mapper.TagCategoryMapper;
import org.springblade.system.service.ITagCategoryService;
import org.springblade.system.tag.support.TagTaxonomyCacheInvalidator;
import org.springblade.system.vo.TagCategoryDetailVO;
import org.springblade.system.vo.TagCategoryListVO;
import org.springblade.system.vo.TagCategoryMutationVO;
import org.springblade.system.wrapper.TagCategoryWrapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** 标签分类服务实现。 @author BladeX */
@Service
@RequiredArgsConstructor
public class TagCategoryServiceImpl extends BaseServiceImpl<TagCategoryMapper, TagCategory>
	implements ITagCategoryService {

	private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{0,63}$");
	private final TagCategoryWrapper categoryWrapper;
	private final TagTaxonomyCacheInvalidator taxonomyCacheInvalidator;

	@Override
	public IPage<TagCategoryListVO> selectPage(String name, String code, Integer status, Query query) {
		validateOptionalStatus(status);
		IPage<TagCategoryListVO> page = Condition.getPage(query);
		page.setRecords(baseMapper.selectCategoryPage(page, currentTenantId(), trimToNull(name),
			normalizeOptionalCode(code), status).stream().map(categoryWrapper::listVO).toList());
		return page;
	}

	@Override
	public TagCategoryDetailVO detail(Long id) {
		String tenantId = currentTenantId();
		TagCategory category = requireCategory(tenantId, id);
		return categoryWrapper.detailVO(category, baseMapper.countTags(tenantId, id));
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public TagCategoryMutationVO create(TagCategoryCreateDTO dto) {
		String tenantId = currentTenantId();
		String code = normalizeCode(dto.getCategoryCode());
		validateSelectionRule(dto.getSelectionMode(), dto.getMaxSelectCount());
		if (baseMapper.selectByCodeIncludingDeleted(tenantId, code) != null) {
			throw new ServiceException(TagResultCode.TAG_CATEGORY_CODE_DUPLICATE);
		}
		TagCategory category = new TagCategory();
		category.setTenantId(tenantId);
		category.setCategoryCode(code);
		category.setCategoryName(dto.getCategoryName().trim());
		category.setSelectionMode(dto.getSelectionMode());
		category.setMaxSelectCount(dto.getMaxSelectCount());
		category.setSort(defaultSort(dto.getSort()));
		category.setRemark(trimToNull(dto.getRemark()));
		category.setLockVersion(0L);
		category.setStatus(TagStatus.ENABLED.getValue());
		category.setIsDeleted(0);
		try {
			save(category);
		} catch (DuplicateKeyException exception) {
			throw new ServiceException(TagResultCode.TAG_CATEGORY_CODE_DUPLICATE, exception);
		}
		taxonomyCacheInvalidator.invalidateAfterCommit(tenantId);
		return mutation(category.getId(), category.getStatus(), category.getLockVersion());
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public TagCategoryMutationVO update(TagCategoryUpdateDTO dto) {
		if (dto.isImmutableFieldAttempted()) {
			throw new ServiceException(TagResultCode.TAG_IMMUTABLE_FIELD);
		}
		String tenantId = currentTenantId();
		TagCategory existing = requireLockedCategory(tenantId, dto.getId());
		if (!Objects.equals(existing.getLockVersion(), dto.getLockVersion())) {
			throw new ServiceException(TagResultCode.TAG_CONFLICT);
		}
		validateSelectionRule(dto.getSelectionMode(), dto.getMaxSelectCount());
		TagCategory update = new TagCategory();
		update.setId(dto.getId());
		update.setCategoryName(dto.getCategoryName().trim());
		update.setSelectionMode(dto.getSelectionMode());
		update.setMaxSelectCount(dto.getMaxSelectCount());
		update.setSort(dto.getSort());
		update.setRemark(trimToNull(dto.getRemark()));
		if (baseMapper.updateCategory(update, tenantId, dto.getLockVersion(), currentUserId()) != 1) {
			throw new ServiceException(TagResultCode.TAG_CONFLICT);
		}
		taxonomyCacheInvalidator.invalidateAfterCommit(tenantId);
		return mutation(existing.getId(), existing.getStatus(), existing.getLockVersion() + 1);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public TagCategoryMutationVO changeStatus(TagCategoryStatusDTO dto) {
		TagStatus target = requireStatus(dto.getStatus());
		String tenantId = currentTenantId();
		TagCategory existing = requireLockedCategory(tenantId, dto.getId());
		if (Objects.equals(existing.getStatus(), target.getValue())) {
			return mutation(existing.getId(), existing.getStatus(), existing.getLockVersion());
		}
		if (!Objects.equals(existing.getLockVersion(), dto.getLockVersion())
			|| baseMapper.updateStatus(tenantId, dto.getId(), dto.getLockVersion(), target.getValue(), currentUserId()) != 1) {
			throw new ServiceException(TagResultCode.TAG_CONFLICT);
		}
		taxonomyCacheInvalidator.invalidateAfterCommit(tenantId);
		return mutation(existing.getId(), target.getValue(), existing.getLockVersion() + 1);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean remove(TagCategoryDeleteDTO dto) {
		String tenantId = currentTenantId();
		TagCategory existing = requireLockedCategory(tenantId, dto.getId());
		if (!Objects.equals(existing.getLockVersion(), dto.getLockVersion())) {
			throw new ServiceException(TagResultCode.TAG_CONFLICT);
		}
		if (baseMapper.countTags(tenantId, dto.getId()) > 0) {
			throw new ServiceException(TagResultCode.TAG_CATEGORY_NOT_EMPTY);
		}
		if (baseMapper.logicalDelete(tenantId, dto.getId(), dto.getLockVersion(), currentUserId()) != 1) {
			throw new ServiceException(TagResultCode.TAG_CONFLICT);
		}
		taxonomyCacheInvalidator.invalidateAfterCommit(tenantId);
		return true;
	}

	private TagCategory requireCategory(String tenantId, Long id) {
		TagCategory category = baseMapper.selectTenantCategory(tenantId, id);
		if (category == null) throw new ServiceException(TagResultCode.TAG_CATEGORY_NOT_FOUND);
		return category;
	}

	private TagCategory requireLockedCategory(String tenantId, Long id) {
		TagCategory category = baseMapper.selectForUpdate(tenantId, id);
		if (category == null) throw new ServiceException(TagResultCode.TAG_CATEGORY_NOT_FOUND);
		return category;
	}

	private void validateSelectionRule(Integer selectionMode, Integer maxSelectCount) {
		TagSelectionMode mode = TagSelectionMode.of(selectionMode);
		if (mode == null || maxSelectCount == null || maxSelectCount < 1 || maxSelectCount > 100
			|| mode == TagSelectionMode.SINGLE && maxSelectCount != 1) {
			throw new ServiceException(TagResultCode.TAG_SELECTION_RULE_INVALID);
		}
	}

	private TagStatus requireStatus(Integer value) {
		TagStatus status = TagStatus.of(value);
		if (status == null) throw new ServiceException(TagResultCode.TAG_STATUS_INVALID);
		return status;
	}

	private void validateOptionalStatus(Integer value) {
		if (value != null) requireStatus(value);
	}

	private TagCategoryMutationVO mutation(Long id, Integer status, Long lockVersion) {
		TagCategoryMutationVO vo = new TagCategoryMutationVO();
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

	private int defaultSort(Integer sort) {
		return sort == null ? 0 : sort;
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
