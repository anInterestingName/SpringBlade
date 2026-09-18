/**
 * Copyright (c) 2018-2099, Chill Zhuang 庄骞 (bladejava@qq.com).
 * Licensed under the Apache License, Version 2.0.
 */
package org.springblade.ai.prompt.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 图片分析策略版本摘要。 @author BladeX */
@Data
public class PromptStrategyVersionVO implements Serializable {
	@Serial private static final long serialVersionUID = 1L;
	private String code;
	private String promptType;
	@JsonSerialize(using = ToStringSerializer.class) private Long versionId;
	private Integer versionNo;
	private String contentHash;
}
