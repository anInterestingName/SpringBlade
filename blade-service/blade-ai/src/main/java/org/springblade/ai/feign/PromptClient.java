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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springblade.ai.prompt.constant.PromptPermission;
import org.springblade.ai.prompt.dto.PromptRenderRequest;
import org.springblade.ai.prompt.feign.IPromptClient;
import org.springblade.ai.prompt.service.IPromptRenderService;
import org.springblade.ai.prompt.vo.PromptRenderVO;
import org.springblade.core.secure.annotation.PreAuth;
import org.springblade.core.tool.api.R;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 提示词运行时 Feign 服务端。 @author BladeX */
@Hidden
@RestController
@RequiredArgsConstructor
public class PromptClient implements IPromptClient {
	private final IPromptRenderService renderService;

	@Override
	@PostMapping(API_PREFIX + "/render")
	@PreAuth(permission = PromptPermission.RUNTIME)
	public R<PromptRenderVO> render(@Valid @RequestBody PromptRenderRequest request) {
		return R.data(renderService.render(request));
	}
}
