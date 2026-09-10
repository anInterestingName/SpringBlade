/**
 * Copyright (c) 2018-2099, Chill Zhuang 庄骞 (bladejava@qq.com).
 * Licensed under the Apache License, Version 2.0.
 */
package org.springblade.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springblade.core.mp.base.BaseService;
import org.springblade.core.mp.support.Query;
import org.springblade.system.dto.TagCreateDTO;
import org.springblade.system.dto.TagDeleteDTO;
import org.springblade.system.dto.TagStatusDTO;
import org.springblade.system.dto.TagUpdateDTO;
import org.springblade.system.entity.TagDefinition;
import org.springblade.system.vo.TagDetailVO;
import org.springblade.system.vo.TagListVO;
import org.springblade.system.vo.TagMutationVO;
import org.springblade.system.vo.TagOptionVO;
import org.springblade.system.vo.TagTreeVO;

import java.util.List;

/** 标签服务。 @author BladeX */
public interface ITagService extends BaseService<TagDefinition> {
	IPage<TagListVO> selectPage(Long categoryId, Long parentId, String name, String code, Integer status, Query query);
	TagDetailVO detail(Long id);
	List<TagTreeVO> tree(Long categoryId);
	List<TagOptionVO> options(Long categoryId);
	TagMutationVO create(TagCreateDTO dto);
	TagMutationVO update(TagUpdateDTO dto);
	TagMutationVO changeStatus(TagStatusDTO dto);
	boolean remove(TagDeleteDTO dto);
}
