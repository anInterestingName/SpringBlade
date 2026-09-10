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
package org.springblade.system.entity;

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

/** 标签定义实体。 @author BladeX */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("blade_tag")
@Schema(description = "标签")
public class TagDefinition extends TenantEntity {
	@Serial private static final long serialVersionUID = 1L;
	@TableId(value = "id", type = IdType.ASSIGN_ID)
	@JsonSerialize(using = ToStringSerializer.class) private Long id;
	@JsonSerialize(using = ToStringSerializer.class) private Long categoryId;
	@JsonSerialize(using = ToStringSerializer.class) private Long parentId;
	private String ancestors;
	private Integer depth;
	private String tagCode;
	private String tagName;
	private Integer sort;
	private String remark;
	@JsonSerialize(using = ToStringSerializer.class) private Long lockVersion;
}
