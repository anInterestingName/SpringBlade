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
package org.springblade.ai.prompt.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 发布版本来源。
 *
 * @author BladeX
 */
@Getter
@AllArgsConstructor
public enum VersionSourceType {
	PUBLISH(1, "手工发布"),
	ROLLBACK(2, "回滚发布"),
	AUTO_PUBLISH(3, "自动发布");

	private final int value;
	private final String label;

	public static VersionSourceType of(Integer value) {
		if (value != null) {
			for (VersionSourceType sourceType : values()) {
				if (sourceType.value == value) {
					return sourceType;
				}
			}
		}
		return PUBLISH;
	}
}
