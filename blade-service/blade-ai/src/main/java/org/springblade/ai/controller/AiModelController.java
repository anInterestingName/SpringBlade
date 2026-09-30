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
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springblade.ai.modelconfig.constant.AiModelConfigPermission;
import org.springblade.ai.modelconfig.dto.ConfigRemoveDTO;
import org.springblade.ai.modelconfig.dto.ConfigStatusDTO;
import org.springblade.ai.modelconfig.dto.ModelCreateDTO;
import org.springblade.ai.modelconfig.dto.ModelUpdateDTO;
import org.springblade.ai.modelconfig.service.IModelConfigService;
import org.springblade.ai.modelconfig.vo.AiConfigOptionsVO;
import org.springblade.ai.modelconfig.vo.ConfigMutationVO;
import org.springblade.ai.modelconfig.vo.ModelDetailVO;
import org.springblade.ai.modelconfig.vo.ModelListVO;
import org.springblade.core.mp.support.Query;
import org.springblade.core.secure.annotation.PreAuth;
import org.springblade.core.tool.api.R;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.Map;

/**
 * AI大模型配置管理控制器
 *
 * @author SpringBlade
 */
@Validated
@RestController
@AllArgsConstructor
@RequestMapping("/ai")
@Tag(name = "AI模型配置管理", description = "AI模型配置管理与选项接口")
public class AiModelController {

	private final IModelConfigService modelConfigService;

	@GetMapping("/config/options")
	@PreAuth
	@Operation(summary = "获取AI配置选项", description = "获取供应商预设、协议与能力选项集合")
	public R<AiConfigOptionsVO> options() {
		return R.data(modelConfigService.getOptions());
	}

	@GetMapping("/model/list")
	@PreAuth(permission = AiModelConfigPermission.MODEL_VIEW)
	@Operation(summary = "模型配置分页列表", description = "按操作者角色权限范围查询模型配置列表")
	public R<IPage<ModelListVO>> list(@RequestParam Map<String, Object> params, Query query) {
		return R.data(modelConfigService.selectPage(params, query));
	}

	@GetMapping("/model/detail")
	@PreAuth(permission = AiModelConfigPermission.MODEL_VIEW)
	@Operation(summary = "模型配置详情", description = "根据ID获取模型配置详情（含参数定义）")
	public R<ModelDetailVO> detail(@RequestParam("id") Long id) {
		return R.data(modelConfigService.getDetail(id));
	}

	@PostMapping("/model/create")
	@PreAuth(permission = AiModelConfigPermission.MODEL_CREATE)
	@Operation(summary = "创建模型配置", description = "创建模型配置（归属与对应供应商一致）")
	public R<ConfigMutationVO> create(@Valid @RequestBody ModelCreateDTO dto) {
		return R.data(modelConfigService.createModel(dto));
	}

	@PostMapping("/model/update")
	@PreAuth(permission = AiModelConfigPermission.MODEL_EDIT)
	@Operation(summary = "修改模型配置", description = "修改单模型配置信息并进行乐观锁并发控制")
	public R<ConfigMutationVO> update(@Valid @RequestBody ModelUpdateDTO dto) {
		return R.data(modelConfigService.updateModel(dto));
	}

	@PostMapping("/model/status")
	@PreAuth(permission = AiModelConfigPermission.MODEL_EDIT)
	@Operation(summary = "切换模型启停状态", description = "启用或停用模型配置")
	public R<ConfigMutationVO> status(@Valid @RequestBody ConfigStatusDTO dto) {
		return R.data(modelConfigService.updateStatus(dto));
	}

	@PostMapping("/model/remove")
	@PreAuth(permission = AiModelConfigPermission.MODEL_DELETE)
	@Operation(summary = "删除模型配置", description = "逻辑删除模型配置")
	public R<Boolean> remove(@Valid @RequestBody ConfigRemoveDTO dto) {
		return R.data(modelConfigService.removeModel(dto));
	}

}
