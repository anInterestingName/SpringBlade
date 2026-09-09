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
import org.springblade.ai.prompt.dto.PromptVariableDTO;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** 提示词详情。 @author BladeX */
@Data
public class PromptDetailVO implements Serializable {
	@Serial private static final long serialVersionUID = 1L;
	@JsonSerialize(using = ToStringSerializer.class) private Long id;
	private String promptCode;
	private String promptName;
	private String fixedInstruction;
	private String userTemplate;
	private List<PromptVariableDTO> variables = new ArrayList<>();
	@JsonSerialize(using = ToStringSerializer.class) private Long draftRevision;
	private Boolean draftDirty;
	private Integer status;
	private String statusName;
	@JsonSerialize(using = ToStringSerializer.class) private Long currentVersionId;
	private Integer currentVersionNo;
	@JsonSerialize(using = ToStringSerializer.class) private Long lockVersion;
	private Date createTime;
	private Date updateTime;
	private PromptVersionVO currentVersion;
	private PromptActionVO actions;
	private List<PromptValidationIssueVO> warnings = new ArrayList<>();
}
