/**
 * Copyright (c) 2018-2099, Chill Zhuang 庄骞 (bladejava@qq.com).
 * Licensed under the Apache License, Version 2.0.
 */
package org.springblade.ai.prompt.reverse.support;

import lombok.RequiredArgsConstructor;
import org.springblade.ai.config.PromptReverseProperties;
import org.springblade.ai.prompt.constant.PromptReverseResultCode;
import org.springblade.ai.prompt.reverse.model.ValidatedImage;
import org.springframework.boot.servlet.autoconfigure.MultipartProperties;
import org.springblade.core.log.exception.ServiceException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;

/** 图片魔数、尺寸和像素上限校验。 @author BladeX */
@Component
@RequiredArgsConstructor
public class ImageUploadValidator {
	private final PromptReverseProperties properties;
	private final MultipartProperties multipartProperties;

	public ValidatedImage validate(MultipartFile file) {
		long maxImageBytes = multipartProperties.getMaxFileSize().toBytes();
		if (file == null || file.isEmpty() || file.getSize() <= 0
			|| exceedsLimit(file.getSize(), maxImageBytes)) {
			throw invalid();
		}
		try {
			byte[] content = file.getBytes();
			if (content.length == 0 || exceedsLimit(content.length, maxImageBytes)) {
				throw invalid();
			}
			ImageFormat format = detect(content);
			Dimensions dimensions = dimensions(content, format);
			long pixels = (long) dimensions.width() * dimensions.height();
			if (dimensions.width() <= 0 || dimensions.height() <= 0 || pixels > properties.getMaxPixels()) {
				throw invalid();
			}
			return new ValidatedImage(content, format.mediaType, "image." + format.extension,
				dimensions.width(), dimensions.height());
		} catch (ServiceException exception) {
			throw exception;
		} catch (IOException | RuntimeException exception) {
			throw new ServiceException(PromptReverseResultCode.IMAGE_INVALID, exception);
		}
	}

	private ImageFormat detect(byte[] content) {
		if (content.length >= 24 && unsigned(content[0]) == 0x89 && content[1] == 'P'
			&& content[2] == 'N' && content[3] == 'G' && unsigned(content[4]) == 0x0D
			&& unsigned(content[5]) == 0x0A && unsigned(content[6]) == 0x1A && unsigned(content[7]) == 0x0A) {
			return ImageFormat.PNG;
		}
		if (content.length >= 4 && unsigned(content[0]) == 0xFF && unsigned(content[1]) == 0xD8) {
			return ImageFormat.JPEG;
		}
		if (content.length >= 30 && ascii(content, 0, "RIFF") && ascii(content, 8, "WEBP")) {
			return ImageFormat.WEBP;
		}
		throw invalid();
	}

	private Dimensions dimensions(byte[] content, ImageFormat format) throws IOException {
		return switch (format) {
			case PNG -> verifiedDimensions(content, bigEndianInt(content, 16), bigEndianInt(content, 20));
			case JPEG -> {
				Dimensions parsed = jpegDimensions(content);
				yield verifiedDimensions(content, parsed.width(), parsed.height());
			}
			case WEBP -> {
				Dimensions parsed = webpDimensions(content);
				yield verifiedDimensions(content, parsed.width(), parsed.height());
			}
		};
	}

	private Dimensions verifiedDimensions(byte[] content, int expectedWidth, int expectedHeight) throws IOException {
		validateDimensions(expectedWidth, expectedHeight);
		try (MemoryCacheImageInputStream stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(content))) {
			Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
			if (!readers.hasNext()) {
				throw invalid();
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(stream, true, true);
				int width = reader.getWidth(0);
				int height = reader.getHeight(0);
				if (width != expectedWidth || height != expectedHeight) {
					throw invalid();
				}
				BufferedImage image = reader.read(0);
				if (image == null || image.getWidth() != width || image.getHeight() != height) {
					throw invalid();
				}
				return new Dimensions(width, height);
			} finally {
				reader.dispose();
			}
		}
	}

	private void validateDimensions(int width, int height) {
		if (width <= 0 || height <= 0 || (long) width * height > properties.getMaxPixels()) {
			throw invalid();
		}
	}

	private boolean exceedsLimit(long size, long limit) {
		return limit > 0 && size > limit;
	}

	private Dimensions jpegDimensions(byte[] content) {
		int offset = 2;
		while (offset + 3 < content.length) {
			if (unsigned(content[offset]) != 0xFF) {
				throw invalid();
			}
			while (offset < content.length && unsigned(content[offset]) == 0xFF) {
				offset++;
			}
			if (offset >= content.length) {
				break;
			}
			int marker = unsigned(content[offset++]);
			if (marker == 0xD9 || marker == 0xDA) {
				break;
			}
			if (marker == 0x01 || marker >= 0xD0 && marker <= 0xD7) {
				continue;
			}
			if (offset + 1 >= content.length) {
				throw invalid();
			}
			int length = unsignedShort(content, offset);
			if (length < 2 || offset + length > content.length) {
				throw invalid();
			}
			if (isStartOfFrame(marker)) {
				if (length < 7) {
					throw invalid();
				}
				return new Dimensions(unsignedShort(content, offset + 5), unsignedShort(content, offset + 3));
			}
			offset += length;
		}
		throw invalid();
	}

	private Dimensions webpDimensions(byte[] content) {
		long declaredSize = Integer.toUnsignedLong(littleEndianInt(content, 4)) + 8L;
		if (declaredSize > content.length) {
			throw invalid();
		}
		int chunkSize = littleEndianInt(content, 16);
		if (chunkSize < 0 || 20L + chunkSize > content.length) {
			throw invalid();
		}
		if (ascii(content, 12, "VP8X") && chunkSize >= 10) {
			return new Dimensions(1 + littleEndian24(content, 24), 1 + littleEndian24(content, 27));
		}
		if (ascii(content, 12, "VP8L") && chunkSize >= 5 && unsigned(content[20]) == 0x2F) {
			int b1 = unsigned(content[21]);
			int b2 = unsigned(content[22]);
			int b3 = unsigned(content[23]);
			int b4 = unsigned(content[24]);
			int width = 1 + (((b2 & 0x3F) << 8) | b1);
			int height = 1 + (((b4 & 0x0F) << 10) | (b3 << 2) | ((b2 & 0xC0) >> 6));
			return new Dimensions(width, height);
		}
		if (ascii(content, 12, "VP8 ") && chunkSize >= 10 && unsigned(content[23]) == 0x9D
			&& unsigned(content[24]) == 0x01 && unsigned(content[25]) == 0x2A) {
			int width = littleEndianShort(content, 26) & 0x3FFF;
			int height = littleEndianShort(content, 28) & 0x3FFF;
			return new Dimensions(width, height);
		}
		throw invalid();
	}

	private boolean isStartOfFrame(int marker) {
		return marker >= 0xC0 && marker <= 0xC3 || marker >= 0xC5 && marker <= 0xC7
			|| marker >= 0xC9 && marker <= 0xCB || marker >= 0xCD && marker <= 0xCF;
	}

	private boolean ascii(byte[] bytes, int offset, String value) {
		if (offset < 0 || offset + value.length() > bytes.length) {
			return false;
		}
		for (int index = 0; index < value.length(); index++) {
			if (unsigned(bytes[offset + index]) != value.charAt(index)) {
				return false;
			}
		}
		return true;
	}

	private int unsigned(byte value) {
		return value & 0xFF;
	}

	private int unsignedShort(byte[] bytes, int offset) {
		return unsigned(bytes[offset]) << 8 | unsigned(bytes[offset + 1]);
	}

	private int littleEndianShort(byte[] bytes, int offset) {
		return unsigned(bytes[offset]) | unsigned(bytes[offset + 1]) << 8;
	}

	private int bigEndianInt(byte[] bytes, int offset) {
		return unsigned(bytes[offset]) << 24 | unsigned(bytes[offset + 1]) << 16
			| unsigned(bytes[offset + 2]) << 8 | unsigned(bytes[offset + 3]);
	}

	private int littleEndianInt(byte[] bytes, int offset) {
		return unsigned(bytes[offset]) | unsigned(bytes[offset + 1]) << 8
			| unsigned(bytes[offset + 2]) << 16 | unsigned(bytes[offset + 3]) << 24;
	}

	private int littleEndian24(byte[] bytes, int offset) {
		return unsigned(bytes[offset]) | unsigned(bytes[offset + 1]) << 8 | unsigned(bytes[offset + 2]) << 16;
	}

	private ServiceException invalid() {
		return new ServiceException(PromptReverseResultCode.IMAGE_INVALID);
	}

	private enum ImageFormat {
		PNG("image/png", "png"),
		JPEG("image/jpeg", "jpg"),
		WEBP("image/webp", "webp");

		private final String mediaType;
		private final String extension;

		ImageFormat(String mediaType, String extension) {
			this.mediaType = mediaType;
			this.extension = extension;
		}
	}

	private record Dimensions(int width, int height) {
	}
}
