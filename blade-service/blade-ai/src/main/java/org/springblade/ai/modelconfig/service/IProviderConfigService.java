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
package org.springblade.ai.modelconfig.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springblade.ai.modelconfig.dto.ConfigRemoveDTO;
import org.springblade.ai.modelconfig.dto.ConfigStatusDTO;
import org.springblade.ai.modelconfig.dto.ProviderCreateDTO;
import org.springblade.ai.modelconfig.dto.ProviderUpdateDTO;
import org.springblade.ai.modelconfig.entity.AiProviderConfig;
import org.springblade.ai.modelconfig.vo.ConfigMutationVO;
import org.springblade.ai.modelconfig.vo.ProviderDetailVO;
import org.springblade.ai.modelconfig.vo.ProviderListVO;
import org.springblade.core.mp.base.BaseService;
import org.springblade.core.mp.support.Query;

import java.util.Map;

/**
 * 供应商接入配置服务接口
 *
 * @author SpringBlade
 */
public interface IProviderConfigService extends BaseService<AiProviderConfig> {

	/**
	 * 分页查询供应商配置
	 */
	IPage<ProviderListVO> selectPage(Map<String, Object> params, Query query);

	/**
	 * 获取供应商配置详情
	 */
	ProviderDetailVO getDetail(Long id);

	/**
	 * 创建供应商配置
	 */
	ConfigMutationVO createProvider(ProviderCreateDTO dto);

	/**
	 * 修改供应商配置
	 */
	ConfigMutationVO updateProvider(ProviderUpdateDTO dto);

	/**
	 * 切换启停状态
	 */
	ConfigMutationVO updateStatus(ConfigStatusDTO dto);

	/**
	 * 逻辑删除供应商配置
	 */
	boolean removeProvider(ConfigRemoveDTO dto);

}
