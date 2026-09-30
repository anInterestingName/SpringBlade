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
package org.springblade.ai.modelconfig.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;
import org.springblade.ai.modelconfig.entity.AiModelConfig;
import org.springblade.ai.modelconfig.vo.ModelListVO;

import java.util.Map;

/**
 * 模型配置Mapper
 *
 * @author SpringBlade
 */
public interface AiModelConfigMapper extends BaseMapper<AiModelConfig> {

	/**
	 * 分页查询模型配置列表
	 *
	 * @param page   分页对象
	 * @param params 参数
	 * @return 分页列表
	 */
	IPage<ModelListVO> selectModelPage(IPage<ModelListVO> page, @Param("param") Map<String, Object> params);

	/**
	 * 查询单个模型配置实体
	 *
	 * @param id 主键ID
	 * @return 模型配置实体
	 */
	AiModelConfig selectModelById(@Param("id") Long id);

}
