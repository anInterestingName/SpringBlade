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

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.AllArgsConstructor;
import org.springblade.core.cache.utils.CacheUtil;
import org.springblade.core.log.exception.ServiceException;
import org.springblade.core.tenant.TenantId;
import org.springblade.core.tool.constant.BladeConstant;
import org.springblade.core.tool.utils.Func;
import org.springblade.system.entity.Dept;
import org.springblade.system.entity.Post;
import org.springblade.system.entity.Role;
import org.springblade.system.entity.Tenant;
import org.springblade.system.service.IDeptService;
import org.springblade.system.service.IPostService;
import org.springblade.system.service.IRoleService;
import org.springblade.system.service.ITenantProvisionService;
import org.springblade.system.service.ITenantService;
import org.springblade.system.service.IUserService;
import org.springblade.system.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 租户开通编排服务实现类。
 *
 * @author Chill
 */
@Service
@AllArgsConstructor
public class TenantProvisionServiceImpl implements ITenantProvisionService {

	private final TenantId tenantId;
	private final ITenantService tenantService;
	private final IRoleService roleService;
	private final IDeptService deptService;
	private final IPostService postService;
	private final IUserService userService;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean saveTenant(Tenant tenant) {
		if (Func.isNotEmpty(tenant.getId())) {
			boolean result = tenantService.saveOrUpdate(tenant);
			CacheUtil.clear(CacheUtil.SYS_CACHE);
			return result;
		}

		List<Tenant> tenants = tenantService.list(Wrappers.<Tenant>query().lambda()
			.eq(Tenant::getIsDeleted, BladeConstant.DB_NOT_DELETED));
		List<String> codes = tenants.stream().map(Tenant::getTenantId).collect(Collectors.toList());
		String tenantId = getTenantId(codes);
		tenant.setTenantId(tenantId);

		Role role = new Role();
		role.setTenantId(tenantId);
		role.setParentId(0L);
		role.setRoleName("管理员");
		role.setRoleAlias("admin");
		role.setSort(2);
		role.setIsDeleted(BladeConstant.DB_NOT_DELETED);
		if (!roleService.save(role)) {
			throw new ServiceException("租户默认角色初始化失败");
		}

		Dept dept = new Dept();
		dept.setTenantId(tenantId);
		dept.setParentId(0L);
		dept.setDeptName(tenant.getTenantName());
		dept.setFullName(tenant.getTenantName());
		dept.setSort(2);
		dept.setIsDeleted(BladeConstant.DB_NOT_DELETED);
		if (!deptService.save(dept)) {
			throw new ServiceException("租户默认部门初始化失败");
		}

		Post post = new Post();
		post.setTenantId(tenantId);
		post.setCategory(1);
		post.setPostCode("ceo");
		post.setPostName("首席执行官");
		post.setSort(1);
		if (!postService.save(post)) {
			throw new ServiceException("租户默认岗位初始化失败");
		}

		User user = new User();
		user.setTenantId(tenantId);
		user.setName("admin");
		user.setRealName("admin");
		user.setAccount("admin");
		user.setPassword("admin");
		user.setRoleId(String.valueOf(role.getId()));
		user.setDeptId(String.valueOf(dept.getId()));
		user.setPostId(String.valueOf(post.getId()));
		user.setBirthday(new Date());
		user.setSex(1);
		user.setIsDeleted(BladeConstant.DB_NOT_DELETED);

		if (!tenantService.saveOrUpdate(tenant)) {
			throw new ServiceException("租户信息保存失败");
		}
		if (!userService.submit(user)) {
			throw new ServiceException("租户管理员初始化失败");
		}
		return true;
	}

	/**
	 * 生成不与已有集合冲突的租户编号。
	 */
	private String getTenantId(List<String> codes) {
		String code = tenantId.generate();
		if (codes.contains(code)) {
			return getTenantId(codes);
		}
		return code;
	}

}
