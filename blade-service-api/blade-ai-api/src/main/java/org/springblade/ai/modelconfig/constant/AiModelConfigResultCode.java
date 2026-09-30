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
package org.springblade.ai.modelconfig.constant;

import lombok.Getter;
import org.springblade.core.tool.api.IResultCode;

/**
 * 大模型与供应商配置业务错误码
 *
 * @author SpringBlade
 */
@Getter
public enum AiModelConfigResultCode implements IResultCode {

	/**
	 * 目标不存在、已删除或不可见
	 */
	NOT_FOUND(48401, "目标不存在或无权查看"),

	/**
	 * 目标用户或租户归属无效
	 */
	OWNER_INVALID(48402, "目标用户或租户归属无效"),

	/**
	 * 供应商不存在、已删除或归属不符
	 */
	PROVIDER_UNAVAILABLE(48403, "供应商不存在、已删除或归属不符"),

	/**
	 * 当前归属范围内管理编码重复
	 */
	DUPLICATE_CODE(48404, "当前所有人范围内管理编码已存在"),

	/**
	 * 供应商下仍有未删除模型
	 */
	HAS_MODELS(48405, "供应商下仍存在有效模型，请先处理关联模型"),

	/**
	 * 乐观版本过期或写入状态冲突
	 */
	CONFLICT(48406, "配置已被其他人修改，请刷新后重试"),

	/**
	 * 协议、能力、预设或模型参数无效
	 */
	OPTION_INVALID(48407, "协议、能力、预设或模型参数无效"),

	/**
	 * 运行配置停用或缺少凭据
	 */
	CONFIG_UNAVAILABLE(48408, "大模型或供应商已停用，或未配置有效接入凭据"),

	/**
	 * 依赖服务失败及降级
	 */
	SERVICE_UNAVAILABLE(48409, "模型配置服务暂时不可用");

	private final int code;
	private final String message;

	AiModelConfigResultCode(int code, String message) {
		this.code = code;
		this.message = message;
	}

}
