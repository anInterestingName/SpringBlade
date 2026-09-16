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
package org.springblade.ai.prompt.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/** 提示词列表摘要。 @author BladeX */
@Data
public class PromptListVO implements Serializable {
	@Serial private static final long serialVersionUID = 1L;
	@JsonSerialize(using = ToStringSerializer.class) private Long id;
	private String promptCode;
	private String promptName;
	private String promptType;
	private String promptTypeName;
	private Integer publishMode;
	private String publishModeName;
	private Integer status;
	private String statusName;
	private Integer currentVersionNo;
	private Boolean draftDirty;
	@JsonSerialize(using = ToStringSerializer.class) private Long lockVersion;
	@JsonSerialize(using = ToStringSerializer.class) private Long createUser;
	@JsonSerialize(using = ToStringSerializer.class) private Long updateUser;
	private Date createTime;
	private Date updateTime;
	private PromptActionVO actions;
}
