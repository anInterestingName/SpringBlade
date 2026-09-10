/**
 * Copyright (c) 2018-2099, Chill Zhuang 庄骞 (bladejava@qq.com).
 * Licensed under the Apache License, Version 2.0.
 */
package org.springblade.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;
import org.springblade.system.entity.TagDefinition;
import org.springblade.system.vo.TagListVO;

import java.util.List;

/** 标签 Mapper。 @author BladeX */
public interface TagMapper extends BaseMapper<TagDefinition> {
	List<TagListVO> selectTagPage(IPage<TagListVO> page, @Param("tenantId") String tenantId,
		@Param("categoryId") Long categoryId, @Param("parentId") Long parentId,
		@Param("name") String name, @Param("code") String code, @Param("status") Integer status);
	TagDefinition selectTenantTag(@Param("tenantId") String tenantId, @Param("id") Long id);
	TagDefinition selectByCodeIncludingDeleted(@Param("tenantId") String tenantId,
		@Param("categoryId") Long categoryId, @Param("code") String code);
	TagDefinition selectForUpdate(@Param("tenantId") String tenantId, @Param("id") Long id);
	List<TagDefinition> selectByCategory(@Param("tenantId") String tenantId, @Param("categoryId") Long categoryId);
	long countChildren(@Param("tenantId") String tenantId, @Param("categoryId") Long categoryId,
		@Param("parentId") Long parentId);
	int updateTag(@Param("tag") TagDefinition tag, @Param("tenantId") String tenantId,
		@Param("expectedLockVersion") Long expectedLockVersion, @Param("updateUser") Long updateUser);
	int updateStructure(@Param("tenantId") String tenantId, @Param("categoryId") Long categoryId,
		@Param("id") Long id, @Param("ancestors") String ancestors, @Param("depth") Integer depth,
		@Param("expectedLockVersion") Long expectedLockVersion, @Param("updateUser") Long updateUser);
	int updateStatus(@Param("tenantId") String tenantId, @Param("categoryId") Long categoryId,
		@Param("id") Long id, @Param("expectedLockVersion") Long expectedLockVersion,
		@Param("status") Integer status, @Param("updateUser") Long updateUser);
	int logicalDelete(@Param("tenantId") String tenantId, @Param("categoryId") Long categoryId,
		@Param("id") Long id, @Param("expectedLockVersion") Long expectedLockVersion,
		@Param("updateUser") Long updateUser);
}
