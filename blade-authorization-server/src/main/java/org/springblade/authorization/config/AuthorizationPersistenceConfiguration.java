/*
 * Copyright 2026 SpringBlade contributors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * https://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springblade.authorization.config;

import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.springblade.authorization.props.AuthorizationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

/**
 * 授权存储雪花主键配置，同一数据中心内每个实例使用独立workerId。
 *
 * @author SpringBlade
 */
@Configuration(proxyBeanMethods = false)
public class AuthorizationPersistenceConfiguration {
	@Bean
	public IdentifierGenerator authorizationIdentifierGenerator(AuthorizationProperties properties) {
		Assert.isTrue(properties.getWorkerId() >= 0 && properties.getWorkerId() <= 31
			&& properties.getDatacenterId() >= 0 && properties.getDatacenterId() <= 31,
			"雪花节点编号必须在0至31之间");
		return new DefaultIdentifierGenerator(properties.getWorkerId(), properties.getDatacenterId());
	}
}
