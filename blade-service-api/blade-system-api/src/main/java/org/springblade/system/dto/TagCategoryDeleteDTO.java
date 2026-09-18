/**
 * Copyright (c) 2018-2099, Chill Zhuang 庄骞 (bladejava@qq.com).
 * Licensed under the Apache License, Version 2.0.
 */
package org.springblade.system.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 标签分类删除参数。 @author BladeX */
@Data
public class TagCategoryDeleteDTO implements Serializable {
	@Serial private static final long serialVersionUID = 1L;
	@NotNull @JsonSerialize(using = ToStringSerializer.class) private Long id;
	@NotNull @JsonSerialize(using = ToStringSerializer.class) private Long lockVersion;
}
