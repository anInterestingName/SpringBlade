/**
 * Copyright (c) 2018-2099, Chill Zhuang 庄骞 (bladejava@qq.com).
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springblade.ai.modelconfig.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springblade.ai.modelconfig.constant.AiModelConfigResultCode;
import org.springblade.ai.modelconfig.dto.ConfigRemoveDTO;
import org.springblade.ai.modelconfig.dto.ConfigStatusDTO;
import org.springblade.ai.modelconfig.dto.ModelCreateDTO;
import org.springblade.ai.modelconfig.dto.ModelUpdateDTO;
import org.springblade.ai.modelconfig.entity.AiModelConfig;
import org.springblade.ai.modelconfig.entity.AiProviderConfig;
import org.springblade.ai.modelconfig.mapper.AiModelConfigMapper;
import org.springblade.ai.modelconfig.mapper.AiProviderConfigMapper;
import org.springblade.ai.modelconfig.service.AiConfigAccessService;
import org.springblade.ai.modelconfig.service.IModelConfigService;
import org.springblade.ai.modelconfig.vo.AiConfigOptionsVO;
import org.springblade.ai.modelconfig.vo.AiModelRuntimeConfigVO;
import org.springblade.ai.modelconfig.vo.ConfigMutationVO;
import org.springblade.ai.modelconfig.vo.ModelDetailVO;
import org.springblade.ai.modelconfig.vo.ModelListVO;
import org.springblade.core.log.exception.ServiceException;
import org.springblade.core.mp.base.BaseServiceImpl;
import org.springblade.core.mp.support.Condition;
import org.springblade.core.mp.support.Query;
import org.springblade.core.tenant.TenantUtil;
import org.springblade.core.tool.api.R;
import org.springblade.core.tool.jackson.JsonUtil;
import org.springblade.core.tool.utils.BeanUtil;
import org.springblade.core.tool.utils.Func;
import org.springblade.core.tool.utils.StringUtil;
import org.springblade.system.feign.IDictClient;
import org.springblade.system.vo.DictOptionVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 模型配置服务实现
 *
 * @author SpringBlade
 */
@Slf4j
@Service
@AllArgsConstructor
public class ModelConfigServiceImpl extends BaseServiceImpl<AiModelConfigMapper, AiModelConfig> implements IModelConfigService {

	private final AiConfigAccessService accessService;
	private final AiProviderConfigMapper providerConfigMapper;
	private final IDictClient dictClient;

	@Override
	public IPage<ModelListVO> selectPage(Map<String, Object> params, Query query) {
		AiConfigAccessService.UserAccessContext context = accessService.getCurrentAccessContext();
		Map<String, Object> queryMap = new HashMap<>(params);

		if (context.getRole() == AiConfigAccessService.AccessRole.PLATFORM_ADMIN) {
			return TenantUtil.ignore(() -> baseMapper.selectModelPage(Condition.getPage(query), queryMap));
		} else if (context.getRole() == AiConfigAccessService.AccessRole.TENANT_ADMIN) {
			queryMap.put("tenantId", context.getTenantId());
			return TenantUtil.ignore(() -> baseMapper.selectModelPage(Condition.getPage(query), queryMap));
		} else {
			queryMap.put("tenantId", context.getTenantId());
			queryMap.put("ownerUserId", context.getUserId());
			return TenantUtil.ignore(() -> baseMapper.selectModelPage(Condition.getPage(query), queryMap));
		}
	}

	@Override
	public ModelDetailVO getDetail(Long id) {
		AiConfigAccessService.UserAccessContext context = accessService.getCurrentAccessContext();
		AiModelConfig entity = TenantUtil.ignore(() -> baseMapper.selectModelById(id));
		if (entity == null) {
			throw new ServiceException(AiModelConfigResultCode.NOT_FOUND);
		}

		accessService.assertResourceAccess(context, entity.getTenantId(), entity.getOwnerUserId(), null);

		ModelDetailVO vo = BeanUtil.copyProperties(entity, ModelDetailVO.class);
		if (vo != null) {
			if (StringUtil.isNotBlank(entity.getInputTypes())) {
				vo.setInputTypes(JsonUtil.parse(entity.getInputTypes(), new TypeReference<List<String>>() {}));
			}
			if (StringUtil.isNotBlank(entity.getReasoningEfforts())) {
				vo.setReasoningEfforts(JsonUtil.parse(entity.getReasoningEfforts(), new TypeReference<Map<String, String>>() {}));
			}
		}
		return vo;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public ConfigMutationVO createModel(ModelCreateDTO dto) {
		validateModelParams(dto.getApiProtocol(), dto.getCapabilityType(), dto.getContextWindow(), dto.getInputTypes(), dto.getReasoningEfforts());
		if (dto.getStatus() != null && dto.getStatus() != 0 && dto.getStatus() != 1) {
			throw new ServiceException(AiModelConfigResultCode.OPTION_INVALID);
		}

		AiConfigAccessService.UserAccessContext context = accessService.getCurrentAccessContext();

		// 检查并锁定所属供应商，同时校验供应商存在且未被删除
		AiProviderConfig provider = TenantUtil.ignore(() -> providerConfigMapper.selectProviderByIdForUpdate(dto.getProviderId()));
		if (provider == null) {
			throw new ServiceException(AiModelConfigResultCode.PROVIDER_UNAVAILABLE);
		}

		// 校验操作者是否有权管理目标供应商
		accessService.assertResourceWriteAccess(context, provider.getTenantId(), provider.getOwnerUserId(), dto.getTenantId());

		AiModelConfig entity = new AiModelConfig();
		entity.setProviderId(provider.getId());
		entity.setModelCode(dto.getModelCode().trim());
		entity.setModelName(dto.getModelName().trim());
		entity.setUpstreamModelId(dto.getUpstreamModelId().trim());
		entity.setApiProtocol(dto.getApiProtocol().trim());
		entity.setCapabilityType(dto.getCapabilityType().trim());
		entity.setContextWindow(dto.getContextWindow());
		entity.setInputTypes(JsonUtil.toJson(dto.getInputTypes()));
		entity.setReasoningEfforts(Func.isNotEmpty(dto.getReasoningEfforts()) ? JsonUtil.toJson(dto.getReasoningEfforts()) : null);
		entity.setTenantId(provider.getTenantId());
		entity.setOwnerUserId(provider.getOwnerUserId());
		entity.setStatus(dto.getStatus() != null && dto.getStatus() == 1 ? 1 : 0);
		entity.setLockVersion(0L);

		// 校验唯一性
		Long count = TenantUtil.ignore(() -> baseMapper.selectCount(Wrappers.<AiModelConfig>query().lambda()
			.eq(AiModelConfig::getTenantId, entity.getTenantId())
			.eq(AiModelConfig::getOwnerUserId, entity.getOwnerUserId())
			.eq(AiModelConfig::getModelCode, entity.getModelCode())
		));
		if (count != null && count > 0) {
			throw new ServiceException(AiModelConfigResultCode.DUPLICATE_CODE);
		}

		try {
			boolean saved = TenantUtil.ignore(() -> baseMapper.insert(entity) > 0);
			if (!saved) {
				throw new ServiceException(AiModelConfigResultCode.SERVICE_UNAVAILABLE);
			}
		} catch (DuplicateKeyException e) {
			throw new ServiceException(AiModelConfigResultCode.DUPLICATE_CODE);
		}

		return new ConfigMutationVO(entity.getId(), entity.getLockVersion());
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public ConfigMutationVO updateModel(ModelUpdateDTO dto) {
		validateModelParams(dto.getApiProtocol(), dto.getCapabilityType(), dto.getContextWindow(), dto.getInputTypes(), dto.getReasoningEfforts());

		AiConfigAccessService.UserAccessContext context = accessService.getCurrentAccessContext();
		AiModelConfig oldEntity = TenantUtil.ignore(() -> baseMapper.selectModelById(dto.getId()));
		if (oldEntity == null) {
			throw new ServiceException(AiModelConfigResultCode.NOT_FOUND);
		}

		accessService.assertResourceWriteAccess(context, oldEntity.getTenantId(), oldEntity.getOwnerUserId(), dto.getTenantId());

		if (!oldEntity.getLockVersion().equals(dto.getLockVersion())) {
			throw new ServiceException(AiModelConfigResultCode.CONFLICT);
		}

		oldEntity.setModelName(dto.getModelName().trim());
		oldEntity.setUpstreamModelId(dto.getUpstreamModelId().trim());
		oldEntity.setApiProtocol(dto.getApiProtocol().trim());
		oldEntity.setCapabilityType(dto.getCapabilityType().trim());
		oldEntity.setContextWindow(dto.getContextWindow());
		oldEntity.setInputTypes(JsonUtil.toJson(dto.getInputTypes()));
		oldEntity.setReasoningEfforts(Func.isNotEmpty(dto.getReasoningEfforts()) ? JsonUtil.toJson(dto.getReasoningEfforts()) : null);

		long newVersion = oldEntity.getLockVersion() + 1;
		oldEntity.setLockVersion(newVersion);

		int updated = TenantUtil.ignore(() -> baseMapper.update(oldEntity, Wrappers.<AiModelConfig>update().lambda()
			.eq(AiModelConfig::getId, oldEntity.getId())
			.eq(AiModelConfig::getLockVersion, dto.getLockVersion())
			.eq(AiModelConfig::getIsDeleted, 0)
		));
		if (updated == 0) {
			throw new ServiceException(AiModelConfigResultCode.CONFLICT);
		}

		return new ConfigMutationVO(oldEntity.getId(), newVersion);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public ConfigMutationVO updateStatus(ConfigStatusDTO dto) {
		if (dto.getStatus() == null || (dto.getStatus() != 0 && dto.getStatus() != 1)) {
			throw new ServiceException(AiModelConfigResultCode.OPTION_INVALID);
		}

		AiConfigAccessService.UserAccessContext context = accessService.getCurrentAccessContext();
		AiModelConfig oldEntity = TenantUtil.ignore(() -> baseMapper.selectModelById(dto.getId()));
		if (oldEntity == null) {
			throw new ServiceException(AiModelConfigResultCode.NOT_FOUND);
		}

		accessService.assertResourceWriteAccess(context, oldEntity.getTenantId(), oldEntity.getOwnerUserId(), dto.getTenantId());

		if (!oldEntity.getLockVersion().equals(dto.getLockVersion())) {
			throw new ServiceException(AiModelConfigResultCode.CONFLICT);
		}

		long newVersion = oldEntity.getLockVersion() + 1;
		oldEntity.setStatus(dto.getStatus());
		oldEntity.setLockVersion(newVersion);

		int updated = TenantUtil.ignore(() -> baseMapper.update(oldEntity, Wrappers.<AiModelConfig>update().lambda()
			.eq(AiModelConfig::getId, oldEntity.getId())
			.eq(AiModelConfig::getLockVersion, dto.getLockVersion())
			.eq(AiModelConfig::getIsDeleted, 0)
		));
		if (updated == 0) {
			throw new ServiceException(AiModelConfigResultCode.CONFLICT);
		}

		return new ConfigMutationVO(oldEntity.getId(), newVersion);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean removeModel(ConfigRemoveDTO dto) {
		AiConfigAccessService.UserAccessContext context = accessService.getCurrentAccessContext();
		AiModelConfig oldEntity = TenantUtil.ignore(() -> baseMapper.selectModelById(dto.getId()));
		if (oldEntity == null) {
			throw new ServiceException(AiModelConfigResultCode.NOT_FOUND);
		}

		accessService.assertResourceWriteAccess(context, oldEntity.getTenantId(), oldEntity.getOwnerUserId(), dto.getTenantId());

		if (!oldEntity.getLockVersion().equals(dto.getLockVersion())) {
			throw new ServiceException(AiModelConfigResultCode.CONFLICT);
		}

		int updated = TenantUtil.ignore(() -> baseMapper.delete(Wrappers.<AiModelConfig>update().lambda()
			.eq(AiModelConfig::getId, oldEntity.getId())
			.eq(AiModelConfig::getLockVersion, dto.getLockVersion())
			.eq(AiModelConfig::getIsDeleted, 0)
		));
		if (updated == 0) {
			throw new ServiceException(AiModelConfigResultCode.CONFLICT);
		}

		return true;
	}

	@Override
	public AiConfigOptionsVO getOptions() {
		R<List<DictOptionVO>> presetR = dictClient.getOptions("ai_provider_preset");
		R<List<DictOptionVO>> protocolR = dictClient.getOptions("ai_model_api_protocol");
		R<List<DictOptionVO>> capabilityR = dictClient.getOptions("ai_model_capability");

		if (!presetR.isSuccess() || !protocolR.isSuccess() || !capabilityR.isSuccess()) {
			throw new ServiceException(AiModelConfigResultCode.SERVICE_UNAVAILABLE);
		}

		AiConfigOptionsVO vo = new AiConfigOptionsVO();

		List<AiConfigOptionsVO.PresetOptionVO> presetList = new ArrayList<>();
		if (Func.isNotEmpty(presetR.getData())) {
			for (DictOptionVO d : presetR.getData()) {
				AiConfigOptionsVO.PresetOptionVO opt = new AiConfigOptionsVO.PresetOptionVO();
				opt.setId(d.getId());
				opt.setValue(d.getDictValue());
				presetList.add(opt);
			}
		}
		vo.setProviderPresets(presetList);

		List<AiConfigOptionsVO.SimpleOptionVO> protocolList = new ArrayList<>();
		if (Func.isNotEmpty(protocolR.getData())) {
			for (DictOptionVO d : protocolR.getData()) {
				AiConfigOptionsVO.SimpleOptionVO opt = new AiConfigOptionsVO.SimpleOptionVO();
				opt.setValue(d.getDictValue());
				protocolList.add(opt);
			}
		}
		vo.setApiProtocols(protocolList);

		List<AiConfigOptionsVO.SimpleOptionVO> capabilityList = new ArrayList<>();
		if (Func.isNotEmpty(capabilityR.getData())) {
			for (DictOptionVO d : capabilityR.getData()) {
				AiConfigOptionsVO.SimpleOptionVO opt = new AiConfigOptionsVO.SimpleOptionVO();
				opt.setValue(d.getDictValue());
				capabilityList.add(opt);
			}
		}
		vo.setCapabilityTypes(capabilityList);

		return vo;
	}

	@Override
	public AiModelRuntimeConfigVO getRuntimeConfig(Long modelConfigId) {
		if (modelConfigId == null) {
			throw new ServiceException(AiModelConfigResultCode.NOT_FOUND);
		}

		// 运行读取内部按 ID 局部忽略租户过滤
		AiModelConfig model = TenantUtil.ignore(() -> baseMapper.selectModelById(modelConfigId));
		if (model == null || model.getIsDeleted() != 0) {
			throw new ServiceException(AiModelConfigResultCode.NOT_FOUND);
		}

		if (model.getStatus() != 1) {
			throw new ServiceException(AiModelConfigResultCode.CONFIG_UNAVAILABLE);
		}

		AiProviderConfig provider = TenantUtil.ignore(() -> providerConfigMapper.selectProviderById(model.getProviderId()));
		if (provider == null || provider.getIsDeleted() != 0) {
			throw new ServiceException(AiModelConfigResultCode.PROVIDER_UNAVAILABLE);
		}
		if (!model.getTenantId().equals(provider.getTenantId()) || !model.getOwnerUserId().equals(provider.getOwnerUserId())) {
			throw new ServiceException(AiModelConfigResultCode.PROVIDER_UNAVAILABLE);
		}

		if (provider.getStatus() != 1 || StringUtil.isBlank(provider.getApiKeyValue())) {
			throw new ServiceException(AiModelConfigResultCode.CONFIG_UNAVAILABLE);
		}

		AiModelRuntimeConfigVO vo = new AiModelRuntimeConfigVO();
		vo.setModelConfigId(model.getId());
		vo.setProviderId(provider.getId());
		vo.setTenantId(model.getTenantId());
		vo.setOwnerUserId(model.getOwnerUserId());
		vo.setBaseUrl(provider.getBaseUrl());
		vo.setApiKeyValue(provider.getApiKeyValue());
		vo.setUpstreamModelId(model.getUpstreamModelId());
		vo.setApiProtocol(model.getApiProtocol());
		vo.setCapabilityType(model.getCapabilityType());
		vo.setContextWindow(model.getContextWindow());

		if (StringUtil.isNotBlank(model.getInputTypes())) {
			vo.setInputTypes(JsonUtil.parse(model.getInputTypes(), new TypeReference<List<String>>() {}));
		}
		if (StringUtil.isNotBlank(model.getReasoningEfforts())) {
			vo.setReasoningEfforts(JsonUtil.parse(model.getReasoningEfforts(), new TypeReference<Map<String, String>>() {}));
		}

		return vo;
	}

	private void validateModelParams(String apiProtocol, String capabilityType, Integer contextWindow, List<String> inputTypes, Map<String, String> reasoningEfforts) {
		if (contextWindow != null && contextWindow <= 0) {
			throw new ServiceException(AiModelConfigResultCode.OPTION_INVALID);
		}

		// 校验协议
		R<List<DictOptionVO>> protocolR = dictClient.getOptions("ai_model_api_protocol");
		if (!protocolR.isSuccess() || Func.isEmpty(protocolR.getData())) {
			throw new ServiceException(AiModelConfigResultCode.SERVICE_UNAVAILABLE);
		}
		boolean protocolValid = protocolR.getData().stream().anyMatch(d -> d.getDictValue().equalsIgnoreCase(apiProtocol));
		if (!protocolValid) {
			throw new ServiceException(AiModelConfigResultCode.OPTION_INVALID);
		}

		// 校验能力与输入模态
		R<List<DictOptionVO>> capR = dictClient.getOptions("ai_model_capability");
		if (!capR.isSuccess() || Func.isEmpty(capR.getData())) {
			throw new ServiceException(AiModelConfigResultCode.SERVICE_UNAVAILABLE);
		}
		Set<String> validCaps = capR.getData().stream().map(d -> d.getDictValue().toLowerCase()).collect(Collectors.toSet());
		if (!validCaps.contains(capabilityType.toLowerCase())) {
			throw new ServiceException(AiModelConfigResultCode.OPTION_INVALID);
		}

		if (Func.isEmpty(inputTypes)) {
			throw new ServiceException(AiModelConfigResultCode.OPTION_INVALID);
		}
		Set<String> set = new HashSet<>();
		for (String in : inputTypes) {
			if (StringUtil.isBlank(in) || !validCaps.contains(in.trim().toLowerCase()) || !set.add(in.trim().toLowerCase())) {
				// 元素为空、不在字典项中或有重复
				throw new ServiceException(AiModelConfigResultCode.OPTION_INVALID);
			}
		}

		if (Func.isNotEmpty(reasoningEfforts)) {
			for (Map.Entry<String, String> entry : reasoningEfforts.entrySet()) {
				if (StringUtil.isBlank(entry.getKey()) || StringUtil.isBlank(entry.getValue())) {
					throw new ServiceException(AiModelConfigResultCode.OPTION_INVALID);
				}
			}
		}
	}

}
