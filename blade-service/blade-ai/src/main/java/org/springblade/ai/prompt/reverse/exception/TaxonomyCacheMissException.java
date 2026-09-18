/**
 * Copyright (c) 2018-2099, Chill Zhuang 庄骞 (bladejava@qq.com).
 * Licensed under the Apache License, Version 2.0.
 */
package org.springblade.ai.prompt.reverse.exception;

/** fast 读取标签快照未命中。 @author BladeX */
public class TaxonomyCacheMissException extends RuntimeException {
	public TaxonomyCacheMissException() {
		super("taxonomy cache miss");
	}
}
