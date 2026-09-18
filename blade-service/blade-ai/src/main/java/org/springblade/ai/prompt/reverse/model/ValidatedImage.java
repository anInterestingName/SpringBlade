/**
 * Copyright (c) 2018-2099, Chill Zhuang 庄骞 (bladejava@qq.com).
 * Licensed under the Apache License, Version 2.0.
 */
package org.springblade.ai.prompt.reverse.model;

/** 已通过服务端校验的图片。 @author BladeX */
public record ValidatedImage(byte[] content, String mediaType, String filename, int width, int height) {
}
