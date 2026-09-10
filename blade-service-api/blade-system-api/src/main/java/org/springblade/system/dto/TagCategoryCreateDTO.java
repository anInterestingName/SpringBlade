/**
 * Copyright (c) 2018-2099, Chill Zhuang 庄骞 (bladejava@qq.com).
 * Licensed under the Apache License, Version 2.0.
 */
package org.springblade.system.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 标签分类创建参数。 @author BladeX */
@Data
public class TagCategoryCreateDTO implements Serializable {
	@Serial private static final long serialVersionUID = 1L;
	@NotBlank @Size(max = 64) private String categoryCode;
	@NotBlank @Size(max = 100) private String categoryName;
	@NotNull private Integer selectionMode;
	@NotNull @Min(1) @Max(100) private Integer maxSelectCount;
	@Min(0) private Integer sort = 0;
	@Size(max = 500) private String remark;
}
