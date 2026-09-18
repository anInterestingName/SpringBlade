/**
 * Copyright (c) 2018-2099, Chill Zhuang 庄骞 (bladejava@qq.com).
 * Licensed under the Apache License, Version 2.0.
 */
package org.springblade.ai.prompt.reverse.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springblade.ai.prompt.reverse.client.StarAtlasAiClient;
import org.springblade.ai.prompt.reverse.exception.TaxonomyCacheMissException;
import org.springblade.ai.prompt.reverse.model.PromptStrategy;
import org.springblade.ai.prompt.reverse.model.TaxonomySnapshot;
import org.springblade.ai.prompt.reverse.model.ValidatedImage;
import org.springblade.ai.prompt.reverse.service.IPromptReverseService;
import org.springblade.ai.prompt.reverse.service.PromptStrategyService;
import org.springblade.ai.prompt.reverse.service.TaxonomySnapshotService;
import org.springblade.ai.prompt.reverse.support.ImageUploadValidator;
import org.springblade.ai.prompt.reverse.support.PromptReverseResultValidator;
import org.springblade.ai.prompt.service.IPromptService;
import org.springblade.ai.prompt.vo.PromptReverseVO;
import org.springblade.core.secure.utils.SecureUtil;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.TimeUnit;

/** 图片提示词反推编排实现。 @author BladeX */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptReverseServiceImpl implements IPromptReverseService {
	private final IPromptService promptService;
	private final ImageUploadValidator imageValidator;
	private final PromptStrategyService strategyService;
	private final TaxonomySnapshotService taxonomyService;
	private final StarAtlasAiClient starAtlasAiClient;
	private final PromptReverseResultValidator resultValidator;

	@Override
	public PromptReverseVO reverse(MultipartFile image) {
		long start = System.nanoTime();
		String tenantId = promptService.currentTenantId();
		ValidatedImage validatedImage = imageValidator.validate(image);
		PromptStrategy strategy = strategyService.current();
		TaxonomySnapshot snapshot = taxonomyService.current(tenantId);
		PromptReverseVO result;
		try {
			result = starAtlasAiClient.analyze(validatedImage, snapshot, strategy);
		} catch (TaxonomyCacheMissException exception) {
			snapshot = taxonomyService.refresh(tenantId);
			result = starAtlasAiClient.analyze(validatedImage, snapshot, strategy);
		}
		PromptReverseVO validated = resultValidator.validate(result, snapshot, strategy);
		log.info("图片反推完成 tenantId={} operatorId={} taxonomyHash={} promptVersionId={} promptVersionNo={} elapsedMs={}",
			tenantId, SecureUtil.getUserId(), snapshot.taxonomyHash(), strategy.version().getVersionId(),
			strategy.version().getVersionNo(), TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
		return validated;
	}
}
