/**
 * Copyright (c) 2018-2099, Chill Zhuang 庄骞 (bladejava@qq.com).
 * Licensed under the Apache License, Version 2.0.
 */
package org.springblade.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springblade.core.mp.base.BaseService;
import org.springblade.core.mp.support.Query;
import org.springblade.system.dto.TagCategoryCreateDTO;
import org.springblade.system.dto.TagCategoryDeleteDTO;
import org.springblade.system.dto.TagCategoryStatusDTO;
import org.springblade.system.dto.TagCategoryUpdateDTO;
import org.springblade.system.entity.TagCategory;
import org.springblade.system.vo.TagCategoryDetailVO;
import org.springblade.system.vo.TagCategoryListVO;
import org.springblade.system.vo.TagCategoryMutationVO;

/** 标签分类服务。 @author BladeX */
public interface ITagCategoryService extends BaseService<TagCategory> {
	IPage<TagCategoryListVO> selectPage(String name, String code, Integer status, Query query);
	TagCategoryDetailVO detail(Long id);
	TagCategoryMutationVO create(TagCategoryCreateDTO dto);
	TagCategoryMutationVO update(TagCategoryUpdateDTO dto);
	TagCategoryMutationVO changeStatus(TagCategoryStatusDTO dto);
	boolean remove(TagCategoryDeleteDTO dto);
}
