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

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springblade.ai.modelconfig.constant.AiModelConfigResultCode;
import org.springblade.ai.modelconfig.dto.ConfigRemoveDTO;
import org.springblade.ai.modelconfig.dto.ConfigStatusDTO;
import org.springblade.ai.modelconfig.dto.ProviderCreateDTO;
import org.springblade.ai.modelconfig.dto.ProviderUpdateDTO;
import org.springblade.ai.modelconfig.entity.AiModelConfig;
import org.springblade.ai.modelconfig.entity.AiProviderConfig;
import org.springblade.ai.modelconfig.mapper.AiModelConfigMapper;
import org.springblade.ai.modelconfig.mapper.AiProviderConfigMapper;
import org.springblade.ai.modelconfig.service.AiConfigAccessService;
import org.springblade.ai.modelconfig.service.IProviderConfigService;
import org.springblade.ai.modelconfig.vo.ConfigMutationVO;
import org.springblade.ai.modelconfig.vo.ProviderDetailVO;
import org.springblade.ai.modelconfig.vo.ProviderListVO;
import org.springblade.core.log.exception.ServiceException;
import org.springblade.core.mp.base.BaseServiceImpl;
import org.springblade.core.mp.support.Condition;
import org.springblade.core.mp.support.Query;
import org.springblade.core.tenant.TenantConstant;
import org.springblade.core.tenant.TenantUtil;
import org.springblade.core.tool.api.R;
import org.springblade.core.tool.utils.BeanUtil;
import org.springblade.core.tool.utils.Func;
import org.springblade.core.tool.utils.StringUtil;
import org.springblade.system.feign.IDictClient;
import org.springblade.system.vo.DictOptionVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 供应商接入配置服务实现
 *
 * @author SpringBlade
 */
@Slf4j
@Service
@AllArgsConstructor
public class ProviderConfigServiceImpl extends BaseServiceImpl<AiProviderConfigMapper, AiProviderConfig> implements IProviderConfigService {

	private final AiConfigAccessService accessService;
	private final AiModelConfigMapper modelConfigMapper;
	private final IDictClient dictClient;

	@Override
	public IPage<ProviderListVO> selectPage(Map<String, Object> params, Query query) {
		AiConfigAccessService.UserAccessContext context = accessService.getCurrentAccessContext();
		Map<String, Object> queryMap = new HashMap<>(params);

		if (context.getRole() == AiConfigAccessService.AccessRole.PLATFORM_ADMIN) {
			// 超级管理员可查全局，也可指定租户/所有人筛选
			return TenantUtil.ignore(() -> baseMapper.selectProviderPage(Condition.getPage(query), queryMap));
		} else if (context.getRole() == AiConfigAccessService.AccessRole.TENANT_ADMIN) {
			// 租户管理员限定本租户
			queryMap.put("tenantId", context.getTenantId());
			return TenantUtil.ignore(() -> baseMapper.selectProviderPage(Condition.getPage(query), queryMap));
		} else {
			// 普通用户限定本租户与本人
			queryMap.put("tenantId", context.getTenantId());
			queryMap.put("ownerUserId", context.getUserId());
			return TenantUtil.ignore(() -> baseMapper.selectProviderPage(Condition.getPage(query), queryMap));
		}
	}

	@Override
	public ProviderDetailVO getDetail(Long id) {
		AiConfigAccessService.UserAccessContext context = accessService.getCurrentAccessContext();
		AiProviderConfig entity = TenantUtil.ignore(() -> baseMapper.selectProviderById(id));
		if (entity == null) {
			throw new ServiceException(AiModelConfigResultCode.NOT_FOUND);
		}

		// 检查操作者是否有权查看该资源
		accessService.assertResourceAccess(context, entity.getTenantId(), entity.getOwnerUserId(), null);

		ProviderDetailVO vo = BeanUtil.copyProperties(entity, ProviderDetailVO.class);
		if (vo != null) {
			vo.setCredentialConfigured(StringUtil.isNotBlank(entity.getApiKeyValue()));
		}
		return vo;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public ConfigMutationVO createProvider(ProviderCreateDTO dto) {
		validateBaseUrl(dto.getBaseUrl());
		validatePresetDictId(dto.getPresetDictId());
		if (dto.getStatus() != null && dto.getStatus() != 0 && dto.getStatus() != 1) {
			throw new ServiceException(AiModelConfigResultCode.OPTION_INVALID);
		}

		AiConfigAccessService.UserAccessContext context = accessService.getCurrentAccessContext();
		AiConfigAccessService.ResolvedOwner owner = accessService.resolveTargetOwner(context, dto.getTenantId(), dto.getOwnerUserId());

		AiProviderConfig entity = new AiProviderConfig();
		entity.setProviderCode(dto.getProviderCode().trim());
		entity.setProviderName(dto.getProviderName().trim());
		entity.setPresetDictId(dto.getPresetDictId());
		entity.setBaseUrl(dto.getBaseUrl().trim());
		entity.setApiKeyValue(dto.getApiKeyValue());
		entity.setTenantId(owner.getTenantId());
		entity.setOwnerUserId(owner.getOwnerUserId());
		entity.setStatus(dto.getStatus() != null && dto.getStatus() == 1 ? 1 : 0);
		entity.setLockVersion(0L);

		// 校验唯一性
		Long count = TenantUtil.ignore(() -> baseMapper.selectCount(Wrappers.<AiProviderConfig>query().lambda()
			.eq(AiProviderConfig::getTenantId, owner.getTenantId())
			.eq(AiProviderConfig::getOwnerUserId, owner.getOwnerUserId())
			.eq(AiProviderConfig::getProviderCode, entity.getProviderCode())
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
	public ConfigMutationVO updateProvider(ProviderUpdateDTO dto) {
		validateBaseUrl(dto.getBaseUrl());
		validatePresetDictId(dto.getPresetDictId());

		AiConfigAccessService.UserAccessContext context = accessService.getCurrentAccessContext();
		AiProviderConfig oldEntity = TenantUtil.ignore(() -> baseMapper.selectProviderById(dto.getId()));
		if (oldEntity == null) {
			throw new ServiceException(AiModelConfigResultCode.NOT_FOUND);
		}

		accessService.assertResourceWriteAccess(context, oldEntity.getTenantId(), oldEntity.getOwnerUserId(), dto.getTenantId());

		if (!oldEntity.getLockVersion().equals(dto.getLockVersion())) {
			throw new ServiceException(AiModelConfigResultCode.CONFLICT);
		}

		oldEntity.setProviderName(dto.getProviderName().trim());
		oldEntity.setPresetDictId(dto.getPresetDictId());
		oldEntity.setBaseUrl(dto.getBaseUrl().trim());
		if (dto.getApiKeyValue() != null) {
			if (StringUtil.isBlank(dto.getApiKeyValue())) {
				throw new ServiceException(AiModelConfigResultCode.OPTION_INVALID);
			}
			oldEntity.setApiKeyValue(dto.getApiKeyValue());
		}
		long newVersion = oldEntity.getLockVersion() + 1;
		oldEntity.setLockVersion(newVersion);

		int updated = TenantUtil.ignore(() -> baseMapper.update(oldEntity, Wrappers.<AiProviderConfig>update().lambda()
			.eq(AiProviderConfig::getId, oldEntity.getId())
			.eq(AiProviderConfig::getLockVersion, dto.getLockVersion())
			.eq(AiProviderConfig::getIsDeleted, 0)
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
		AiProviderConfig oldEntity = TenantUtil.ignore(() -> baseMapper.selectProviderById(dto.getId()));
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

		int updated = TenantUtil.ignore(() -> baseMapper.update(oldEntity, Wrappers.<AiProviderConfig>update().lambda()
			.eq(AiProviderConfig::getId, oldEntity.getId())
			.eq(AiProviderConfig::getLockVersion, dto.getLockVersion())
			.eq(AiProviderConfig::getIsDeleted, 0)
		));
		if (updated == 0) {
			throw new ServiceException(AiModelConfigResultCode.CONFLICT);
		}

		return new ConfigMutationVO(oldEntity.getId(), newVersion);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean removeProvider(ConfigRemoveDTO dto) {
		AiConfigAccessService.UserAccessContext context = accessService.getCurrentAccessContext();
		AiProviderConfig oldEntity = TenantUtil.ignore(() -> baseMapper.selectProviderByIdForUpdate(dto.getId()));
		if (oldEntity == null) {
			throw new ServiceException(AiModelConfigResultCode.NOT_FOUND);
		}

		accessService.assertResourceWriteAccess(context, oldEntity.getTenantId(), oldEntity.getOwnerUserId(), dto.getTenantId());

		if (!oldEntity.getLockVersion().equals(dto.getLockVersion())) {
			throw new ServiceException(AiModelConfigResultCode.CONFLICT);
		}

		// 检查是否有未删除的关联模型
		Long activeModels = TenantUtil.ignore(() -> modelConfigMapper.selectCount(Wrappers.<AiModelConfig>query().lambda()
			.eq(AiModelConfig::getProviderId, oldEntity.getId())
			.eq(AiModelConfig::getIsDeleted, 0)
		));
		if (activeModels != null && activeModels > 0) {
			throw new ServiceException(AiModelConfigResultCode.HAS_MODELS);
		}

		// 执行逻辑删除
		int updated = TenantUtil.ignore(() -> baseMapper.delete(Wrappers.<AiProviderConfig>update().lambda()
			.eq(AiProviderConfig::getId, oldEntity.getId())
			.eq(AiProviderConfig::getLockVersion, dto.getLockVersion())
			.eq(AiProviderConfig::getIsDeleted, 0)
		));
		if (updated == 0) {
			throw new ServiceException(AiModelConfigResultCode.CONFLICT);
		}

		return true;
	}

	private void validateBaseUrl(String url) {
		if (StringUtil.isBlank(url)) {
			throw new ServiceException(AiModelConfigResultCode.OPTION_INVALID);
		}
		try {
			URI uri = URI.create(url.trim());
			if (uri.getHost() == null || uri.getScheme() == null || (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme()))) {
				throw new ServiceException(AiModelConfigResultCode.OPTION_INVALID);
			}
			if (StringUtil.isNotBlank(uri.getUserInfo())) {
				// 禁止URL中内嵌用户信息
				throw new ServiceException(AiModelConfigResultCode.OPTION_INVALID);
			}
		} catch (Exception e) {
			throw new ServiceException(AiModelConfigResultCode.OPTION_INVALID);
		}
	}

	private void validatePresetDictId(Long presetDictId) {
		if (presetDictId == null) {
			return;
		}
		R<List<DictOptionVO>> optionsR = dictClient.getOptions("ai_provider_preset");
		if (!optionsR.isSuccess() || Func.isEmpty(optionsR.getData())) {
			throw new ServiceException(AiModelConfigResultCode.SERVICE_UNAVAILABLE);
		}
		boolean match = optionsR.getData().stream().anyMatch(opt -> presetDictId.equals(opt.getId()) && opt.getIsDeleted() == 0);
		if (!match) {
			throw new ServiceException(AiModelConfigResultCode.OPTION_INVALID);
		}
	}

}
