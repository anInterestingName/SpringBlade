/**
 * Copyright (c) 2018-2099, Chill Zhuang 庄骞 (bladejava@qq.com).
 * Licensed under the Apache License, Version 2.0.
 */
package org.springblade.system.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 标签更新参数。 @author BladeX */
@Data
public class TagUpdateDTO implements Serializable {
	@Serial private static final long serialVersionUID = 1L;
	@NotNull @JsonSerialize(using = ToStringSerializer.class) private Long id;
	@JsonSerialize(using = ToStringSerializer.class) private Long parentId = 0L;
	@NotBlank @Size(max = 100) private String tagName;
	@NotNull @Min(0) private Integer sort;
	@Size(max = 500) private String remark;
	@NotNull @JsonSerialize(using = ToStringSerializer.class) private Long lockVersion;
	@JsonIgnore private boolean immutableFieldAttempted;

	@JsonAnySetter
	public void captureUnknownField(String name, Object value) {
		if ("tagCode".equals(name) || "tag_code".equals(name)
			|| "categoryId".equals(name) || "category_id".equals(name)
			|| "tenantId".equals(name) || "tenant_id".equals(name)
			|| "ancestors".equals(name) || "depth".equals(name)) {
			immutableFieldAttempted = true;
		}
	}
}
