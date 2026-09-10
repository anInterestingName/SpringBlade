/**
 * Copyright (c) 2018-2099, Chill Zhuang 庄骞 (bladejava@qq.com).
 * Licensed under the Apache License, Version 2.0.
 */
package org.springblade.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;
import org.springblade.system.entity.TagCategory;
import org.springblade.system.vo.TagCategoryListVO;

import java.util.List;

/** 标签分类 Mapper。 @author BladeX */
public interface TagCategoryMapper extends BaseMapper<TagCategory> {
	List<TagCategoryListVO> selectCategoryPage(IPage<TagCategoryListVO> page, @Param("tenantId") String tenantId,
		@Param("name") String name, @Param("code") String code, @Param("status") Integer status);
	TagCategory selectTenantCategory(@Param("tenantId") String tenantId, @Param("id") Long id);
	TagCategory selectByCodeIncludingDeleted(@Param("tenantId") String tenantId, @Param("code") String code);
	TagCategory selectForUpdate(@Param("tenantId") String tenantId, @Param("id") Long id);
	long countTags(@Param("tenantId") String tenantId, @Param("categoryId") Long categoryId);
	int updateCategory(@Param("category") TagCategory category, @Param("tenantId") String tenantId,
		@Param("expectedLockVersion") Long expectedLockVersion, @Param("updateUser") Long updateUser);
	int updateStatus(@Param("tenantId") String tenantId, @Param("id") Long id,
		@Param("expectedLockVersion") Long expectedLockVersion, @Param("status") Integer status,
		@Param("updateUser") Long updateUser);
	int logicalDelete(@Param("tenantId") String tenantId, @Param("id") Long id,
		@Param("expectedLockVersion") Long expectedLockVersion, @Param("updateUser") Long updateUser);
}
