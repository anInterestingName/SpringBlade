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
package org.springblade.system.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springblade.core.tool.api.IResultCode;

/** 标签分类业务错误码。 @author BladeX */
@Getter
@AllArgsConstructor
public enum TagResultCode implements IResultCode {
	TAG_CATEGORY_NOT_FOUND(48101, "标签分类不存在"),
	TAG_NOT_FOUND(48102, "标签不存在"),
	TAG_CATEGORY_CODE_DUPLICATE(48103, "标签分类编码已存在"),
	TAG_CODE_DUPLICATE(48104, "标签编码已存在"),
	TAG_SELECTION_RULE_INVALID(48105, "标签分类选择规则不合法"),
	TAG_IMMUTABLE_FIELD(48106, "不可变字段不允许修改"),
	TAG_PARENT_INVALID(48107, "父标签不合法"),
	TAG_CYCLE_DETECTED(48108, "标签层级存在循环"),
	TAG_DEPTH_EXCEEDED(48109, "标签层级超过最大深度"),
	TAG_CATEGORY_NOT_EMPTY(48110, "标签分类下仍存在标签"),
	TAG_HAS_CHILDREN(48111, "标签下仍存在子标签"),
	TAG_CONFLICT(48112, "数据已被其他操作修改，请刷新后重试"),
	TAG_STATUS_INVALID(48113, "标签状态不合法"),
	TAG_CODE_INVALID(48114, "标签编码格式不合法"),
	TAG_RUNTIME_UNAVAILABLE(48115, "标签运行时服务暂不可用");

	private final int code;
	private final String message;
}
