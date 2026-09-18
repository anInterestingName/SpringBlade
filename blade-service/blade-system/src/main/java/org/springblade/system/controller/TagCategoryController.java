/**
 * Copyright (c) 2018-2099, Chill Zhuang 庄骞 (bladejava@qq.com).
 * Licensed under the Apache License, Version 2.0.
 */
package org.springblade.system.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springblade.core.boot.ctrl.BladeController;
import org.springblade.core.mp.support.Query;
import org.springblade.core.secure.annotation.PreAuth;
import org.springblade.core.swagger.annotation.ApiOrder;
import org.springblade.core.tool.api.R;
import org.springblade.system.constant.TagPermission;
import org.springblade.system.dto.TagCategoryCreateDTO;
import org.springblade.system.dto.TagCategoryDeleteDTO;
import org.springblade.system.dto.TagCategoryStatusDTO;
import org.springblade.system.dto.TagCategoryUpdateDTO;
import org.springblade.system.service.ITagCategoryService;
import org.springblade.system.vo.TagCategoryDetailVO;
import org.springblade.system.vo.TagCategoryListVO;
import org.springblade.system.vo.TagCategoryMutationVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 标签分类管理接口。 @author BladeX */
@RestController
@RequiredArgsConstructor
@RequestMapping("/tag-category")
@ApiOrder
@Tag(name = "标签分类管理", description = "租户级标签分类查询与维护")
public class TagCategoryController extends BladeController {
	private final ITagCategoryService categoryService;

	@GetMapping("/list")
	@PreAuth(permission = TagPermission.CATEGORY_VIEW)
	@Operation(summary = "标签分类分页")
	public R<IPage<TagCategoryListVO>> list(@RequestParam(required = false) String name,
		@RequestParam(required = false) String code, @RequestParam(required = false) Integer status, Query query) {
		return R.data(categoryService.selectPage(name, code, status, query));
	}

	@GetMapping("/detail")
	@PreAuth(permission = TagPermission.CATEGORY_VIEW)
	@Operation(summary = "标签分类详情")
	public R<TagCategoryDetailVO> detail(
		@Parameter(description = "分类ID", required = true) @RequestParam Long id) {
		return R.data(categoryService.detail(id));
	}

	@PostMapping("/create")
	@PreAuth(permission = TagPermission.CATEGORY_CREATE)
	@Operation(summary = "创建标签分类")
	public R<TagCategoryMutationVO> create(@Valid @RequestBody TagCategoryCreateDTO dto) {
		return R.data(categoryService.create(dto));
	}

	@PostMapping("/update")
	@PreAuth(permission = TagPermission.CATEGORY_EDIT)
	@Operation(summary = "更新标签分类")
	public R<TagCategoryMutationVO> update(@Valid @RequestBody TagCategoryUpdateDTO dto) {
		return R.data(categoryService.update(dto));
	}

	@PostMapping("/status")
	@PreAuth(permission = TagPermission.CATEGORY_STATUS)
	@Operation(summary = "变更标签分类状态")
	public R<TagCategoryMutationVO> status(@Valid @RequestBody TagCategoryStatusDTO dto) {
		return R.data(categoryService.changeStatus(dto));
	}

	@PostMapping("/remove")
	@PreAuth(permission = TagPermission.CATEGORY_DELETE)
	@Operation(summary = "删除空标签分类")
	public R<Boolean> remove(@Valid @RequestBody TagCategoryDeleteDTO dto) {
		return R.data(categoryService.remove(dto));
	}
}
