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
import java.util.Date;

/** 标签分类列表视图。 @author BladeX */
@Data
public class TagCategoryListVO implements Serializable {
	@Serial private static final long serialVersionUID = 1L;
	@JsonSerialize(using = ToStringSerializer.class) private Long id;
	private String categoryCode;
	private String categoryName;
	private Integer selectionMode;
	private String selectionModeName;
	private Integer maxSelectCount;
	private Integer sort;
	private Integer status;
	private String statusName;
	private Long tagCount;
	private Date createTime;
	private Date updateTime;
	@JsonSerialize(using = ToStringSerializer.class) private Long lockVersion;
}
