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

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springblade.core.cache.constant.CacheConstant;
import org.springblade.core.cache.utils.CacheUtil;
import org.springblade.core.log.exception.ServiceException;
import org.springblade.core.tool.node.ForestNodeMerger;
import org.springblade.core.tool.utils.Func;
import org.springblade.core.tool.utils.StringPool;
import org.springblade.system.entity.Dict;
import org.springblade.system.mapper.DictMapper;
import org.springblade.system.service.IDictService;
import org.springblade.system.vo.DictOptionVO;
import org.springblade.system.vo.DictVO;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 服务实现类
 *
 * @author Chill
 */
@Service
public class DictServiceImpl extends ServiceImpl<DictMapper, Dict> implements IDictService {

	@Override
	public IPage<DictVO> selectDictPage(IPage<DictVO> page, DictVO dict) {
		return page.setRecords(baseMapper.selectDictPage(page, dict));
	}

	@Override
	public List<DictVO> tree() {
		return ForestNodeMerger.merge(baseMapper.tree());
	}

	@Override
	public String getValue(String code, Integer dictKey) {
		return Func.toStr(baseMapper.getValue(code, dictKey), StringPool.EMPTY);
	}

	@Override
	public List<Dict> getList(String code) {
		return baseMapper.getList(code);
	}

	@Override
	public List<DictOptionVO> getOptions(String code) {
		if (Func.isEmpty(code)) {
			return Collections.emptyList();
		}
		// 查询当前字典分类下 dict_key != -1（排除根节点）且未逻辑删除的有效子项
		List<Dict> list = list(Wrappers.<Dict>query().lambda()
			.eq(Dict::getCode, code)
			.ne(Dict::getDictKey, -1)
			.eq(Dict::getIsDeleted, 0)
			.orderByAsc(Dict::getSort)
			.orderByAsc(Dict::getId));
		if (Func.isEmpty(list)) {
			return Collections.emptyList();
		}
		return list.stream().map(dict -> {
			DictOptionVO vo = new DictOptionVO();
			vo.setId(dict.getId());
			vo.setCode(dict.getCode());
			vo.setDictKey(dict.getDictKey());
			vo.setDictValue(dict.getDictValue());
			vo.setSort(dict.getSort());
			vo.setIsDeleted(dict.getIsDeleted());
			return vo;
		}).collect(Collectors.toList());
	}

	@Override
	public boolean submit(Dict dict) {
		CacheUtil.clear(CacheConstant.DICT_CACHE);
		LambdaQueryWrapper<Dict> lqw = Wrappers.<Dict>query().lambda().eq(Dict::getCode, dict.getCode()).eq(Dict::getDictKey, dict.getDictKey());
		Long cnt = baseMapper.selectCount((Func.isEmpty(dict.getId())) ? lqw : lqw.notIn(Dict::getId, dict.getId()));
		if (cnt > 0) {
			throw new ServiceException("当前字典键值已存在!");
		}
		return saveOrUpdate(dict);
	}

	@Override
	public boolean removeDict(List<Long> ids) {
		CacheUtil.clear(CacheConstant.DICT_CACHE);
		return removeByIds(ids);
	}

}
