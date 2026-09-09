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

/**
 * 提示词草稿与发布状态实体。
 *
 * @author BladeX
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("blade_ai_prompt")
@Schema(description = "提示词")
public class Prompt extends TenantEntity {

	@Serial
	private static final long serialVersionUID = 1L;

	@TableId(value = "id", type = IdType.ASSIGN_ID)
	@JsonSerialize(using = ToStringSerializer.class)
	@Schema(description = "主键")
	private Long id;

	@Schema(description = "稳定编码")
	private String promptCode;

	@Schema(description = "提示词名称")
	private String promptName;

	@Schema(description = "当前草稿固定指令")
	private String fixedInstruction;

	@Schema(description = "当前草稿用户模板")
	private String userTemplate;

	@Schema(description = "当前草稿变量定义JSON")
	private String variableSchema;

	@Schema(description = "草稿修订号")
	private Long draftRevision;

	@Schema(description = "是否存在未发布草稿")
	private Boolean draftDirty;

	@JsonSerialize(using = ToStringSerializer.class)
	@Schema(description = "当前发布版本ID")
	private Long currentVersionId;

	@Schema(description = "当前发布版本号")
	private Integer currentVersionNo;

	@JsonSerialize(using = ToStringSerializer.class)
	@Schema(description = "聚合并发版本")
	private Long lockVersion;

}
