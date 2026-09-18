/**
 * Copyright (c) 2018-2099, Chill Zhuang 庄骞 (bladejava@qq.com).
 * Licensed under the Apache License, Version 2.0.
 */
package org.springblade.ai.prompt.reverse.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springblade.ai.config.PromptReverseProperties;
import org.springblade.ai.prompt.constant.PromptReverseResultCode;
import org.springblade.ai.prompt.reverse.exception.TaxonomyCacheMissException;
import org.springblade.ai.prompt.reverse.model.AnalysisPromptContext;
import org.springblade.ai.prompt.reverse.model.PromptReverseContext;
import org.springblade.ai.prompt.reverse.model.PromptStrategy;
import org.springblade.ai.prompt.reverse.model.TaxonomySnapshot;
import org.springblade.ai.prompt.reverse.model.ValidatedImage;
import org.springblade.ai.prompt.vo.PromptReverseVO;
import org.springblade.core.log.exception.ServiceException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;

/** fast-staratlas-ai 图片反推 HTTP 客户端。 @author BladeX */
@Component
public class StarAtlasAiClient {
	private static final String PATH = "/api/v1/prompts/reverse";

	private final PromptReverseProperties properties;
	private final ObjectMapper objectMapper;
	private final RestClient restClient;

	public StarAtlasAiClient(PromptReverseProperties properties, ObjectMapper objectMapper) {
		this.properties = properties;
		this.objectMapper = objectMapper;
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout()).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.getReadTimeout());
		this.restClient = RestClient.builder().requestFactory(requestFactory).build();
	}

	public PromptReverseVO analyze(ValidatedImage image, TaxonomySnapshot snapshot, PromptStrategy strategy) {
		validateConfiguration();
		try {
			AnalysisPromptContext analysisPrompt = new AnalysisPromptContext(strategy.version().getCode(),
				strategy.version().getPromptType(), strategy.version().getVersionId(),
				strategy.version().getVersionNo(), strategy.version().getContentHash());
			PromptReverseContext context = new PromptReverseContext(snapshot.taxonomyHash(),
				analysisPrompt, strategy.messages());
			MultipartBodyBuilder body = new MultipartBodyBuilder();
			body.part("image", imageResource(image), MediaType.parseMediaType(image.mediaType()));
			body.part("context", objectMapper.writeValueAsString(context));
			String response = restClient.post()
				.uri(endpoint())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getFastApiKey().trim())
				.contentType(MediaType.MULTIPART_FORM_DATA)
				.accept(MediaType.APPLICATION_JSON)
				.body(body.build())
				.retrieve()
				.body(String.class);
			if (response == null || response.isBlank()
				|| response.getBytes(StandardCharsets.UTF_8).length > properties.getMaxResponseBytes()) {
				throw new ServiceException(PromptReverseResultCode.OUTPUT_INVALID);
			}
			return objectMapper.readerFor(PromptReverseVO.class)
				.with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
				.readValue(response);
		} catch (RestClientResponseException exception) {
			if (isTaxonomyCacheMiss(exception.getResponseBodyAsString())) {
				throw new TaxonomyCacheMissException();
			}
			throw new ServiceException(PromptReverseResultCode.UPSTREAM_UNAVAILABLE);
		} catch (JsonProcessingException exception) {
			throw new ServiceException(PromptReverseResultCode.OUTPUT_INVALID);
		} catch (RestClientException exception) {
			throw new ServiceException(PromptReverseResultCode.UPSTREAM_UNAVAILABLE);
		} catch (ServiceException | TaxonomyCacheMissException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw new ServiceException(PromptReverseResultCode.UPSTREAM_UNAVAILABLE);
		}
	}

	private ByteArrayResource imageResource(ValidatedImage image) {
		return new ByteArrayResource(image.content()) {
			@Override
			public String getFilename() {
				return image.filename();
			}
		};
	}

	private URI endpoint() {
		String baseUrl = properties.getFastBaseUrl().trim();
		return URI.create((baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl) + PATH);
	}

	private void validateConfiguration() {
		if (properties.getFastBaseUrl() == null || properties.getFastBaseUrl().isBlank()
			|| properties.getFastApiKey() == null || properties.getFastApiKey().isBlank()) {
			throw new ServiceException(PromptReverseResultCode.UPSTREAM_UNAVAILABLE);
		}
	}

	private boolean isTaxonomyCacheMiss(String responseBody) {
		if (responseBody == null || responseBody.isBlank()) {
			return false;
		}
		try {
			JsonNode root = objectMapper.readTree(responseBody);
			return root.findValuesAsText("code").stream().anyMatch("taxonomy_cache_miss"::equals);
		} catch (JsonProcessingException exception) {
			return false;
		}
	}
}
