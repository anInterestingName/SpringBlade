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

/** 标签分类权限常量。 @author BladeX */
public interface TagPermission {
	String CATEGORY_VIEW = "system:tag-category:view";
	String CATEGORY_CREATE = "system:tag-category:create";
	String CATEGORY_EDIT = "system:tag-category:edit";
	String CATEGORY_STATUS = "system:tag-category:status";
	String CATEGORY_DELETE = "system:tag-category:delete";
	String TAG_VIEW = "system:tag:view";
	String TAG_CREATE = "system:tag:create";
	String TAG_EDIT = "system:tag:edit";
	String TAG_STATUS = "system:tag:status";
	String TAG_DELETE = "system:tag:delete";
	String RUNTIME = "system:tag:runtime";
}
