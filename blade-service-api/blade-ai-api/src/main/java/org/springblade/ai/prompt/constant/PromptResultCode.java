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
package org.springblade.ai.prompt.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springblade.core.tool.api.IResultCode;

import java.io.Serial;

/**
 * 提示词业务错误码。
 *
 * @author BladeX
 */
@Getter
@AllArgsConstructor
public enum PromptResultCode implements IResultCode {

	PROMPT_NOT_FOUND(48001, "提示词不存在"),
	PROMPT_CODE_DUPLICATE(48002, "提示词编码已存在"),
	PROMPT_CODE_IMMUTABLE(48003, "提示词编码创建后不可修改"),
	PROMPT_CONFLICT(48004, "提示词已被其他操作修改，请刷新后重试"),
	PROMPT_TEMPLATE_INVALID(48005, "提示词模板不合法"),
	PROMPT_VARIABLE_INVALID(48006, "提示词变量不合法"),
	PROMPT_NOT_PUBLISHED(48007, "提示词尚未发布"),
	PROMPT_DISABLED(48008, "提示词已停用"),
	PROMPT_DELETE_FORBIDDEN(48009, "存在发布历史的提示词不允许删除"),
	PROMPT_ROLLBACK_TARGET_INVALID(48010, "回滚目标版本不合法"),
	PROMPT_SERVICE_UNAVAILABLE(48011, "提示词服务暂不可用");

	private final int code;
	private final String message;

	public IResultCode detail(String detail) {
		return new DetailResultCode(code, message + "：" + detail);
	}

	private record DetailResultCode(int code, String message) implements IResultCode {
		@Serial
		private static final long serialVersionUID = 1L;

		@Override
		public int getCode() {
			return code;
		}

		@Override
		public String getMessage() {
			return message;
		}
	}

}
