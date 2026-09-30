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
package org.springblade.ai.feign;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.AllArgsConstructor;
import org.springblade.ai.modelconfig.constant.AiModelConfigPermission;
import org.springblade.ai.modelconfig.feign.IAiModelConfigClient;
import org.springblade.ai.modelconfig.service.IModelConfigService;
import org.springblade.ai.modelconfig.vo.AiModelRuntimeConfigVO;
import org.springblade.core.secure.annotation.PreAuth;
import org.springblade.core.tool.api.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 大模型配置内部运行读取Feign服务端实现
 *
 * @author SpringBlade
 */
@Hidden
@RestController
@AllArgsConstructor
public class AiModelConfigClient implements IAiModelConfigClient {

	private final IModelConfigService modelConfigService;

	@Override
	@GetMapping(API_PREFIX + "/runtime")
	@PreAuth(permission = AiModelConfigPermission.RUNTIME_READ)
	public R<AiModelRuntimeConfigVO> getRuntimeConfig(Long modelConfigId) {
		return R.data(modelConfigService.getRuntimeConfig(modelConfigId));
	}

}
