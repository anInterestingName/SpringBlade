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
package org.springblade.ai.prompt.feign;

import org.springblade.ai.prompt.constant.PromptResultCode;
import org.springblade.ai.prompt.dto.PromptRenderRequest;
import org.springblade.ai.prompt.vo.PromptRenderVO;
import org.springblade.core.tool.api.R;
import org.springframework.stereotype.Component;

/** 提示词 Feign 降级实现。 @author BladeX */
@Component
public class IPromptClientFallback implements IPromptClient {
	@Override
	public R<PromptRenderVO> render(PromptRenderRequest request) {
		return R.fail(PromptResultCode.PROMPT_SERVICE_UNAVAILABLE);
	}
}
