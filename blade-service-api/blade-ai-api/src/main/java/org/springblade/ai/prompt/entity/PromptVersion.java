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
package org.springblade.ai.prompt.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springblade.core.mp.base.TenantEntity;

import java.io.Serial;
import java.util.Date;

/**
 * 提示词不可变发布版本实体。
 *
 * @author BladeX
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("blade_ai_prompt_version")
@Schema(description = "提示词发布版本")
public class PromptVersion extends TenantEntity {

	@Serial
	private static final long serialVersionUID = 1L;

	@TableId(value = "id", type = IdType.ASSIGN_ID)
	@JsonSerialize(using = ToStringSerializer.class)
	@Schema(description = "主键")
	private Long id;

	@JsonSerialize(using = ToStringSerializer.class)
	@Schema(description = "提示词ID")
	private Long promptId;

	@Schema(description = "版本号")
	private Integer versionNo;

	@Schema(description = "稳定编码快照")
	private String promptCode;

	@Schema(description = "名称快照")
	private String promptName;

	@Schema(description = "固定指令快照")
	private String fixedInstruction;

	@Schema(description = "用户模板快照")
	private String userTemplate;

	@Schema(description = "变量定义快照JSON")
	private String variableSchema;

	@Schema(description = "来源类型")
	private Integer sourceType;

	@JsonSerialize(using = ToStringSerializer.class)
	@Schema(description = "回滚来源版本ID")
	private Long sourceVersionId;

	@JsonSerialize(using = ToStringSerializer.class)
	@Schema(description = "来源草稿修订号")
	private Long sourceDraftRevision;

	@Schema(description = "内容SHA-256")
	private String contentHash;

	@Schema(description = "变更说明")
	private String changeNote;

	@JsonSerialize(using = ToStringSerializer.class)
	@Schema(description = "发布人")
	private Long publishUser;

	@Schema(description = "发布时间")
	private Date publishTime;

}
