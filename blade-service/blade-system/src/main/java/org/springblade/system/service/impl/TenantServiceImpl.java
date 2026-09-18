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
package org.springblade.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springblade.core.cache.utils.CacheUtil;
import org.springblade.core.mp.base.BaseServiceImpl;
import org.springblade.core.mp.support.Condition;
import org.springblade.core.mp.support.Query;
import org.springblade.core.secure.utils.SecureUtil;
import org.springblade.core.tool.constant.BladeConstant;
import org.springblade.system.entity.Tenant;
import org.springblade.system.mapper.TenantMapper;
import org.springblade.system.service.ITenantService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 服务实现类
 *
 * @author Chill
 */
@Service
public class TenantServiceImpl extends BaseServiceImpl<TenantMapper, Tenant> implements ITenantService {

	@Override
	public IPage<Tenant> selectTenantPage(IPage<Tenant> page, Tenant tenant) {
		return page.setRecords(baseMapper.selectTenantPage(page, tenant));
	}

	@Override
	public Tenant getByTenantId(String tenantId) {
		return getOne(Wrappers.<Tenant>query().lambda().eq(Tenant::getTenantId, tenantId));
	}

	@Override
	public Tenant getActiveByTenantId(String tenantId) {
		return getOne(Wrappers.<Tenant>query().lambda()
			.eq(Tenant::getTenantId, tenantId)
			.eq(Tenant::getStatus, 1)
			.eq(Tenant::getIsDeleted, BladeConstant.DB_NOT_DELETED));
	}

	@Override
	public boolean removeTenant(List<Long> ids) {
		boolean result = deleteLogic(ids);
		// 租户删除后清理系统缓存，避免 SysCache 中残留已删除租户导致游客注册等场景误放行
		CacheUtil.clear(CacheUtil.SYS_CACHE);
		return result;
	}

	@Override
	public Tenant getDetail(Tenant tenant) {
		QueryWrapper<Tenant> queryWrapper = Condition.getQueryWrapper(tenant);
		applyTenantScope(queryWrapper);
		return getOne(queryWrapper);
	}

	@Override
	public IPage<Tenant> selectPage(Map<String, Object> tenant, Query query) {
		QueryWrapper<Tenant> queryWrapper = Condition.getQueryWrapper(tenant, Tenant.class);
		applyTenantScope(queryWrapper);
		return page(Condition.getPage(query), queryWrapper);
	}

	@Override
	public List<Tenant> selectList(Tenant tenant) {
		QueryWrapper<Tenant> queryWrapper = Condition.getQueryWrapper(tenant);
		applyTenantScope(queryWrapper);
		return list(queryWrapper);
	}

	/**
	 * 统一的租户范围过滤：超管放行，其他用户强制限定为当前会话租户。
	 */
	private void applyTenantScope(QueryWrapper<Tenant> queryWrapper) {
		if (!SecureUtil.isAdministrator()) {
			queryWrapper.lambda().eq(Tenant::getTenantId, SecureUtil.getTenantId());
		}
	}

}
