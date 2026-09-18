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
import org.springblade.system.dto.TagCreateDTO;
import org.springblade.system.dto.TagDeleteDTO;
import org.springblade.system.dto.TagStatusDTO;
import org.springblade.system.dto.TagUpdateDTO;
import org.springblade.system.service.ITagService;
import org.springblade.system.vo.TagDetailVO;
import org.springblade.system.vo.TagListVO;
import org.springblade.system.vo.TagMutationVO;
import org.springblade.system.vo.TagOptionVO;
import org.springblade.system.vo.TagTreeVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 标签管理接口。 @author BladeX */
@RestController
@RequiredArgsConstructor
@RequestMapping("/tag")
@ApiOrder
@Tag(name = "标签管理", description = "租户级标签查询、层级维护与有效选项")
public class TagController extends BladeController {
	private final ITagService tagService;

	@GetMapping("/list")
	@PreAuth(permission = TagPermission.TAG_VIEW)
	@Operation(summary = "标签分页")
	public R<IPage<TagListVO>> list(@RequestParam(required = false) Long categoryId,
		@RequestParam(required = false) Long parentId, @RequestParam(required = false) String name,
		@RequestParam(required = false) String code, @RequestParam(required = false) Integer status, Query query) {
		return R.data(tagService.selectPage(categoryId, parentId, name, code, status, query));
	}

	@GetMapping("/detail")
	@PreAuth(permission = TagPermission.TAG_VIEW)
	@Operation(summary = "标签详情")
	public R<TagDetailVO> detail(@Parameter(description = "标签ID", required = true) @RequestParam Long id) {
		return R.data(tagService.detail(id));
	}

	@GetMapping("/tree")
	@PreAuth(permission = TagPermission.TAG_VIEW)
	@Operation(summary = "标签管理树")
	public R<List<TagTreeVO>> tree(@RequestParam Long categoryId) {
		return R.data(tagService.tree(categoryId));
	}

	@GetMapping("/options")
	@PreAuth(permission = TagPermission.TAG_VIEW)
	@Operation(summary = "有效标签选项")
	public R<List<TagOptionVO>> options(@RequestParam Long categoryId) {
		return R.data(tagService.options(categoryId));
	}

	@PostMapping("/create")
	@PreAuth(permission = TagPermission.TAG_CREATE)
	@Operation(summary = "创建标签")
	public R<TagMutationVO> create(@Valid @RequestBody TagCreateDTO dto) {
		return R.data(tagService.create(dto));
	}

	@PostMapping("/update")
	@PreAuth(permission = TagPermission.TAG_EDIT)
	@Operation(summary = "更新标签")
	public R<TagMutationVO> update(@Valid @RequestBody TagUpdateDTO dto) {
		return R.data(tagService.update(dto));
	}

	@PostMapping("/status")
	@PreAuth(permission = TagPermission.TAG_STATUS)
	@Operation(summary = "变更标签状态")
	public R<TagMutationVO> status(@Valid @RequestBody TagStatusDTO dto) {
		return R.data(tagService.changeStatus(dto));
	}

	@PostMapping("/remove")
	@PreAuth(permission = TagPermission.TAG_DELETE)
	@Operation(summary = "删除叶子标签")
	public R<Boolean> remove(@Valid @RequestBody TagDeleteDTO dto) {
		return R.data(tagService.remove(dto));
	}
}
