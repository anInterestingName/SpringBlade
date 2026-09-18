/**
 * Copyright (c) 2018-2099, Chill Zhuang 庄骞 (bladejava@qq.com).
 * Licensed under the Apache License, Version 2.0.
 */
package org.springblade.system.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 有效标签选项。 @author BladeX */
@Data
public class TagOptionVO implements Serializable {
	@Serial private static final long serialVersionUID = 1L;
	@JsonSerialize(using = ToStringSerializer.class) private Long id;
	private String tagCode;
	private String tagName;
	@JsonSerialize(using = ToStringSerializer.class) private Long parentId;
	private Integer depth;
	private Integer sort;
}
