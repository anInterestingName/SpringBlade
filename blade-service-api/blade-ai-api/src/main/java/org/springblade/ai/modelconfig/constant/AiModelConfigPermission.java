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

/**
 * 大模型与供应商配置权限常量
 *
 * @author SpringBlade
 */
public interface AiModelConfigPermission {

	String PROVIDER_VIEW = "ai:model:provider:view";
	String PROVIDER_CREATE = "ai:model:provider:create";
	String PROVIDER_EDIT = "ai:model:provider:edit";
	String PROVIDER_DELETE = "ai:model:provider:delete";

	String MODEL_VIEW = "ai:model:config:view";
	String MODEL_CREATE = "ai:model:config:create";
	String MODEL_EDIT = "ai:model:config:edit";
	String MODEL_DELETE = "ai:model:config:delete";

	String RUNTIME_READ = "ai:model:runtime:read";

}
