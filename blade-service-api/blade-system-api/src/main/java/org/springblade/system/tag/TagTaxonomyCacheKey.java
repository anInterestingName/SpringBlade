/**
 * Copyright (c) 2018-2099, Chill Zhuang 庄骞 (bladejava@qq.com).
 * Licensed under the Apache License, Version 2.0.
 */
package org.springblade.system.tag;

/** 标签运行时快照 Redis Key。 @author BladeX */
public final class TagTaxonomyCacheKey {
	private static final String PREFIX = "ai:prompt-reverse:taxonomy:";

	private TagTaxonomyCacheKey() {
	}

	public static String current(String tenantId) {
		return PREFIX + "tenant:" + tenantId + ":current";
	}

	public static String lock(String tenantId) {
		return PREFIX + "tenant:" + tenantId + ":lock";
	}

	public static String generation(String tenantId) {
		return PREFIX + "tenant:" + tenantId + ":generation";
	}

	public static String snapshot(String taxonomyHash) {
		return PREFIX + "snapshot:" + taxonomyHash;
	}
}
