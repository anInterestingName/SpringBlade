/**
 * Copyright (c) 2018-2099, Chill Zhuang 庄骞 (bladejava@qq.com).
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springblade.ai.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springblade.ai.prompt.constant.PromptPermission;
import org.springblade.ai.prompt.dto.PromptCopyDTO;
import org.springblade.ai.prompt.dto.PromptCreateDTO;
import org.springblade.ai.prompt.dto.PromptDeleteDTO;
import org.springblade.ai.prompt.dto.PromptDisableDTO;
import org.springblade.ai.prompt.dto.PromptPreviewDTO;
import org.springblade.ai.prompt.dto.PromptPublishDTO;
import org.springblade.ai.prompt.dto.PromptRollbackDTO;
import org.springblade.ai.prompt.dto.PromptUpdateDTO;
import org.springblade.ai.prompt.service.IPromptPublishService;
import org.springblade.ai.prompt.service.IPromptRenderService;
import org.springblade.ai.prompt.service.IPromptService;
import org.springblade.ai.prompt.vo.PromptDetailVO;
import org.springblade.ai.prompt.vo.PromptListVO;
import org.springblade.ai.prompt.vo.PromptMutationVO;
import org.springblade.ai.prompt.vo.PromptRenderVO;
import org.springblade.ai.prompt.vo.PromptVersionVO;
import org.springblade.core.boot.ctrl.BladeController;
import org.springblade.core.mp.support.Query;
import org.springblade.core.secure.annotation.PreAuth;
import org.springblade.core.swagger.annotation.ApiOrder;
import org.springblade.core.tool.api.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 提示词管理接口。 @author BladeX */
@RestController
@RequiredArgsConstructor
@RequestMapping("/prompt")
@ApiOrder
@Tag(name = "提示词管理", description = "提示词草稿、发布版本和渲染预览")
public class PromptController extends BladeController {
	private final IPromptService promptService;
	private final IPromptPublishService publishService;
	private final IPromptRenderService renderService;

	@GetMapping("/list")
	@PreAuth(permission = PromptPermission.VIEW)
	@Operation(summary = "提示词分页")
	public R<IPage<PromptListVO>> list(@RequestParam(required = false) String name,
		@RequestParam(required = false) String code, @RequestParam(required = false) Integer status,
		@RequestParam(required = false) String promptType,
		@RequestParam(required = false) Integer publishMode, Query query) {
		return R.data(promptService.selectPage(name, code, status, promptType, publishMode, query));
	}

	@GetMapping("/detail")
	@PreAuth(permission = PromptPermission.VIEW)
	@Operation(summary = "提示词详情")
	public R<PromptDetailVO> detail(@Parameter(description = "提示词ID", required = true) @RequestParam Long id) {
		return R.data(promptService.detail(id));
	}

	@PostMapping("/create")
	@PreAuth(permission = PromptPermission.CREATE)
	@Operation(summary = "创建提示词草稿")
	public R<PromptMutationVO> create(@Valid @RequestBody PromptCreateDTO dto) {
		return R.data(promptService.create(dto));
	}

	@PostMapping("/update")
	@PreAuth(permission = PromptPermission.EDIT)
	@Operation(summary = "更新提示词草稿")
	public R<PromptMutationVO> update(@Valid @RequestBody PromptUpdateDTO dto) {
		return R.data(promptService.update(dto));
	}

	@PostMapping("/copy")
	@PreAuth(permission = PromptPermission.COPY)
	@Operation(summary = "复制提示词草稿")
	public R<PromptMutationVO> copy(@Valid @RequestBody PromptCopyDTO dto) {
		return R.data(promptService.copy(dto));
	}

	@PostMapping("/remove")
	@PreAuth(permission = PromptPermission.DELETE)
	@Operation(summary = "删除从未发布的提示词草稿")
	public R<Boolean> remove(@Valid @RequestBody PromptDeleteDTO dto) {
		return R.data(promptService.remove(dto));
	}

	@PostMapping("/preview")
	@PreAuth(permission = PromptPermission.PREVIEW)
	@Operation(summary = "无状态渲染预览")
	public R<PromptRenderVO> preview(@Valid @RequestBody PromptPreviewDTO dto) {
		return R.data(renderService.preview(dto));
	}

	@PostMapping("/publish")
	@PreAuth(permission = PromptPermission.PUBLISH)
	@Operation(summary = "发布提示词版本")
	public R<PromptMutationVO> publish(@Valid @RequestBody PromptPublishDTO dto) {
		return R.data(publishService.publish(dto));
	}

	@PostMapping("/disable")
	@PreAuth(permission = PromptPermission.DISABLE)
	@Operation(summary = "停用提示词")
	public R<PromptMutationVO> disable(@Valid @RequestBody PromptDisableDTO dto) {
		return R.data(publishService.disable(dto));
	}

	@GetMapping("/version/list")
	@PreAuth(permission = PromptPermission.VIEW)
	@Operation(summary = "发布版本分页")
	public R<IPage<PromptVersionVO>> versionList(@RequestParam Long promptId, Query query) {
		return R.data(promptService.versionPage(promptId, query));
	}

	@GetMapping("/version/detail")
	@PreAuth(permission = PromptPermission.VIEW)
	@Operation(summary = "发布版本详情")
	public R<PromptVersionVO> versionDetail(@RequestParam Long promptId, @RequestParam Long versionId) {
		return R.data(promptService.versionDetail(promptId, versionId));
	}

	@PostMapping("/rollback")
	@PreAuth(permission = PromptPermission.ROLLBACK)
	@Operation(summary = "基于历史版本回滚")
	public R<PromptMutationVO> rollback(@Valid @RequestBody PromptRollbackDTO dto) {
		return R.data(publishService.rollback(dto));
	}
}
