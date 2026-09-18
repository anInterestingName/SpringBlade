/**
 * Copyright (c) 2018-2099, Chill Zhuang 庄骞 (bladejava@qq.com).
 * Licensed under the Apache License, Version 2.0.
 */
package org.springblade.system.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 标签创建参数。 @author BladeX */
@Data
public class TagCreateDTO implements Serializable {
	@Serial private static final long serialVersionUID = 1L;
	@NotNull @JsonSerialize(using = ToStringSerializer.class) private Long categoryId;
	@JsonSerialize(using = ToStringSerializer.class) private Long parentId = 0L;
	@NotBlank @Size(max = 64) private String tagCode;
	@NotBlank @Size(max = 100) private String tagName;
	@Min(0) private Integer sort = 0;
	@Size(max = 500) private String remark;
}
