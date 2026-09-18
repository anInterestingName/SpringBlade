/**
 * Copyright (c) 2018-2099, Chill Zhuang 庄骞 (bladejava@qq.com).
 * Licensed under the Apache License, Version 2.0.
 */
package org.springblade.ai.prompt.reverse.support;

import org.springblade.ai.prompt.constant.PromptReverseResultCode;
import org.springblade.ai.prompt.reverse.model.PromptStrategy;
import org.springblade.ai.prompt.reverse.model.TaxonomySnapshot;
import org.springblade.ai.prompt.vo.GeneratedPromptVO;
import org.springblade.ai.prompt.vo.PromptAnalysisVO;
import org.springblade.ai.prompt.vo.PromptLabelCategoryVO;
import org.springblade.ai.prompt.vo.PromptLabelSelectionVO;
import org.springblade.ai.prompt.vo.PromptReverseVO;
import org.springblade.ai.prompt.vo.PromptSegmentsVO;
import org.springblade.ai.prompt.vo.PromptStrategyVersionVO;
import org.springblade.ai.prompt.vo.PromptWarningVO;
import org.springblade.core.log.exception.ServiceException;
import org.springblade.system.enums.TagSelectionMode;
import org.springblade.system.vo.TagTaxonomyCategoryVO;
import org.springblade.system.vo.TagTaxonomyItemVO;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** fast 返回结构和受控标签二次校验。 @author BladeX */
@Component
public class PromptReverseResultValidator {

	public PromptReverseVO validate(PromptReverseVO result, TaxonomySnapshot snapshot, PromptStrategy strategy) {
		if (result == null || !"1.0".equals(result.getSchemaVersion())
			|| !snapshot.taxonomyHash().equals(result.getTaxonomyHash())
			|| !sameVersion(result.getAnalysisPrompt(), strategy.version())) {
			throw invalid();
		}
		validateAnalysis(result.getAnalysis());
		validatePrompt(result.getPrompt());
		validateWarnings(result.getWarnings());
		validateLabels(result.getLabels(), snapshot);
		result.setTaxonomyHash(snapshot.taxonomyHash());
		result.setAnalysisPrompt(strategy.version());
		return result;
	}

	private void validateLabels(List<PromptLabelCategoryVO> labels, TaxonomySnapshot snapshot) {
		if (labels == null || snapshot.taxonomy() == null || snapshot.taxonomy().getCategories() == null) {
			throw invalid();
		}
		Map<String, TagTaxonomyCategoryVO> categories = new HashMap<>();
		for (TagTaxonomyCategoryVO category : snapshot.taxonomy().getCategories()) {
			if (category == null || blank(category.getCategoryCode())
				|| categories.put(category.getCategoryCode(), category) != null) {
				throw invalid();
			}
		}
		Set<String> returnedCategories = new HashSet<>();
		for (PromptLabelCategoryVO labelCategory : labels) {
			if (labelCategory == null || blank(labelCategory.getCategoryCode())
				|| !returnedCategories.add(labelCategory.getCategoryCode())) {
				throw invalid();
			}
			TagTaxonomyCategoryVO category = categories.get(labelCategory.getCategoryCode());
			List<PromptLabelSelectionVO> selections = labelCategory.getSelections();
			if (category == null || selections == null || selections.isEmpty()
				|| category.getMaxSelectCount() == null || selections.size() > category.getMaxSelectCount()
				|| (Objects.equals(category.getSelectionMode(), TagSelectionMode.SINGLE.getValue())
				&& selections.size() > 1) || category.getTags() == null) {
				throw invalid();
			}
			Map<String, TagTaxonomyItemVO> tags = new HashMap<>();
			for (TagTaxonomyItemVO tag : category.getTags()) {
				if (tag == null || blank(tag.getTagCode()) || tags.put(tag.getTagCode(), tag) != null) {
					throw invalid();
				}
			}
			Set<String> returnedTags = new HashSet<>();
			for (PromptLabelSelectionVO selection : selections) {
				if (selection == null || blank(selection.getTagCode()) || !returnedTags.add(selection.getTagCode())
					|| selection.getConfidence() == null || !Double.isFinite(selection.getConfidence())
					|| selection.getConfidence() < 0 || selection.getConfidence() > 1 || blank(selection.getReason())) {
					throw invalid();
				}
				TagTaxonomyItemVO tag = tags.get(selection.getTagCode());
				if (tag == null) {
					throw invalid();
				}
				selection.setTagName(tag.getTagName());
			}
			labelCategory.setCategoryName(category.getCategoryName());
		}
	}

	private void validateAnalysis(PromptAnalysisVO analysis) {
		if (analysis == null || blank(analysis.getSummary()) || blank(analysis.getSubject())
			|| blank(analysis.getScene()) || analysis.getDetails() == null
			|| analysis.getUnmatchedFeatures() == null || analysis.getUncertainFeatures() == null) {
			throw invalid();
		}
	}

	private void validatePrompt(GeneratedPromptVO prompt) {
		if (prompt == null || blank(prompt.getLanguage()) || blank(prompt.getPositive())
			|| blank(prompt.getNegative())) {
			throw invalid();
		}
		PromptSegmentsVO segments = prompt.getSegments();
		if (segments == null || segments.getSubject() == null || segments.getScene() == null
			|| segments.getStyle() == null || segments.getComposition() == null
			|| segments.getLighting() == null || segments.getColor() == null || segments.getQuality() == null) {
			throw invalid();
		}
	}

	private void validateWarnings(List<PromptWarningVO> warnings) {
		if (warnings == null) {
			throw invalid();
		}
		for (PromptWarningVO warning : warnings) {
			if (warning == null || blank(warning.getCode()) || blank(warning.getMessage())) {
				throw invalid();
			}
		}
	}

	private boolean sameVersion(PromptStrategyVersionVO actual, PromptStrategyVersionVO expected) {
		return actual != null && expected != null
			&& Objects.equals(actual.getCode(), expected.getCode())
			&& Objects.equals(actual.getPromptType(), expected.getPromptType())
			&& Objects.equals(actual.getVersionId(), expected.getVersionId())
			&& Objects.equals(actual.getVersionNo(), expected.getVersionNo())
			&& Objects.equals(actual.getContentHash(), expected.getContentHash());
	}

	private boolean blank(String value) {
		return value == null || value.isBlank();
	}

	private ServiceException invalid() {
		return new ServiceException(PromptReverseResultCode.OUTPUT_INVALID);
	}
}
