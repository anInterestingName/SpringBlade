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
package org.springblade.auth.service;

import org.springblade.auth.dto.RegisterRequest;
import org.springblade.auth.vo.RegisterConfigVO;
import org.springblade.auth.vo.RegisterResultVO;
import org.springblade.core.tool.api.R;

/**
 * 用户自助注册服务。
 *
 * @author Codex
 */
public interface IRegisterService {

	/**
	 * 获取公开注册配置。
	 *
	 * @return 注册配置
	 */
	RegisterConfigVO getConfig();

	/**
	 * 提交用户注册。
	 *
	 * @param request 注册请求
	 * @return 注册结果
	 */
	R<RegisterResultVO> register(RegisterRequest request);
}
