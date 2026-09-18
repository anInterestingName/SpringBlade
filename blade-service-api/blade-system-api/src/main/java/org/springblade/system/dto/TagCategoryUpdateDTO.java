/**
 * Copyright (c) 2018-2099, Chill Zhuang 庄骞 (bladejava@qq.com).
 * Licensed under the Apache License, Version 2.0.
 */
package org.springblade.system.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 标签分类更新参数。 @author BladeX */
@Data
public class TagCategoryUpdateDTO implements Serializable {
	@Serial private static final long serialVersionUID = 1L;
	@NotNull @JsonSerialize(using = ToStringSerializer.class) private Long id;
	@NotBlank @Size(max = 100) private String categoryName;
	@NotNull private Integer selectionMode;
	@NotNull @Min(1) @Max(100) private Integer maxSelectCount;
	@NotNull @Min(0) private Integer sort;
	@Size(max = 500) private String remark;
	@NotNull @JsonSerialize(using = ToStringSerializer.class) private Long lockVersion;
	@JsonIgnore private boolean immutableFieldAttempted;

	@JsonAnySetter
	public void captureUnknownField(String name, Object value) {
		if ("categoryCode".equals(name) || "category_code".equals(name)
			|| "tenantId".equals(name) || "tenant_id".equals(name)) {
			immutableFieldAttempted = true;
		}
	}
}
