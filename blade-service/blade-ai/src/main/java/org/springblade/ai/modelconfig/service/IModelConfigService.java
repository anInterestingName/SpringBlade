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
import org.springblade.ai.modelconfig.dto.ModelCreateDTO;
import org.springblade.ai.modelconfig.dto.ModelUpdateDTO;
import org.springblade.ai.modelconfig.entity.AiModelConfig;
import org.springblade.ai.modelconfig.vo.AiConfigOptionsVO;
import org.springblade.ai.modelconfig.vo.AiModelRuntimeConfigVO;
import org.springblade.ai.modelconfig.vo.ConfigMutationVO;
import org.springblade.ai.modelconfig.vo.ModelDetailVO;
import org.springblade.ai.modelconfig.vo.ModelListVO;
import org.springblade.core.mp.base.BaseService;
import org.springblade.core.mp.support.Query;

import java.util.Map;

/**
 * 模型配置服务接口
 *
 * @author SpringBlade
 */
public interface IModelConfigService extends BaseService<AiModelConfig> {

	/**
	 * 分页查询模型配置
	 */
	IPage<ModelListVO> selectPage(Map<String, Object> params, Query query);

	/**
	 * 获取模型配置详情
	 */
	ModelDetailVO getDetail(Long id);

	/**
	 * 创建模型配置
	 */
	ConfigMutationVO createModel(ModelCreateDTO dto);

	/**
	 * 修改模型配置
	 */
	ConfigMutationVO updateModel(ModelUpdateDTO dto);

	/**
	 * 切换启停状态
	 */
	ConfigMutationVO updateStatus(ConfigStatusDTO dto);

	/**
	 * 逻辑删除模型配置
	 */
	boolean removeModel(ConfigRemoveDTO dto);

	/**
	 * 获取前端下拉选项（预设、协议、能力）
	 */
	AiConfigOptionsVO getOptions();

	/**
	 * 运行时读取完整配置（含凭据）
	 */
	AiModelRuntimeConfigVO getRuntimeConfig(Long modelConfigId);

}
