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
import org.springblade.ai.modelconfig.dto.ProviderCreateDTO;
import org.springblade.ai.modelconfig.dto.ProviderUpdateDTO;
import org.springblade.ai.modelconfig.service.IProviderConfigService;
import org.springblade.ai.modelconfig.vo.ConfigMutationVO;
import org.springblade.ai.modelconfig.vo.ProviderDetailVO;
import org.springblade.ai.modelconfig.vo.ProviderListVO;
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
 * AI大模型供应商接入配置管理控制器
 *
 * @author SpringBlade
 */
@Validated
@RestController
@AllArgsConstructor
@RequestMapping("/ai/provider")
@Tag(name = "AI供应商配置管理", description = "AI供应商配置管理接口")
public class AiProviderController {

	private final IProviderConfigService providerConfigService;

	@GetMapping("/list")
	@PreAuth(permission = AiModelConfigPermission.PROVIDER_VIEW)
	@Operation(summary = "供应商配置分页列表", description = "按操作者角色权限范围查询供应商配置列表（不含凭据原值）")
	public R<IPage<ProviderListVO>> list(@RequestParam Map<String, Object> params, Query query) {
		return R.data(providerConfigService.selectPage(params, query));
	}

	@GetMapping("/detail")
	@PreAuth(permission = AiModelConfigPermission.PROVIDER_VIEW)
	@Operation(summary = "供应商配置详情", description = "根据ID获取供应商配置详情（不含凭据原值）")
	public R<ProviderDetailVO> detail(@RequestParam("id") Long id) {
		return R.data(providerConfigService.getDetail(id));
	}

	@PostMapping("/create")
	@PreAuth(permission = AiModelConfigPermission.PROVIDER_CREATE)
	@Operation(summary = "创建供应商配置", description = "创建供应商配置，超级/租户管理员可代建")
	public R<ConfigMutationVO> create(@Valid @RequestBody ProviderCreateDTO dto) {
		return R.data(providerConfigService.createProvider(dto));
	}

	@PostMapping("/update")
	@PreAuth(permission = AiModelConfigPermission.PROVIDER_EDIT)
	@Operation(summary = "修改供应商配置", description = "修改供应商配置信息并进行乐观锁并发控制")
	public R<ConfigMutationVO> update(@Valid @RequestBody ProviderUpdateDTO dto) {
		return R.data(providerConfigService.updateProvider(dto));
	}

	@PostMapping("/status")
	@PreAuth(permission = AiModelConfigPermission.PROVIDER_EDIT)
	@Operation(summary = "切换供应商启停状态", description = "启用或停用供应商配置")
	public R<ConfigMutationVO> status(@Valid @RequestBody ConfigStatusDTO dto) {
		return R.data(providerConfigService.updateStatus(dto));
	}

	@PostMapping("/remove")
	@PreAuth(permission = AiModelConfigPermission.PROVIDER_DELETE)
	@Operation(summary = "删除供应商配置", description = "逻辑删除供应商配置（存在未删除模型时拒绝）")
	public R<Boolean> remove(@Valid @RequestBody ConfigRemoveDTO dto) {
		return R.data(providerConfigService.removeProvider(dto));
	}

}
