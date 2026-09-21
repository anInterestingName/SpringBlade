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
package org.springblade.common.loadbalancer;

import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;

/**
 * Blade 负载均衡自动配置过滤器。
 *
 * <p>第三方配置同时在根上下文创建负载均衡器并注册为命名客户端配置，
 * 在多个服务并发路由时可能让不同 serviceId 共享同一实例供应器。
 * 此过滤器只排除该自动配置，依赖包中的属性和灰度选择规则仍继续复用。</p>
 *
 * @author SpringBlade
 */
public class BladeLoadBalancerAutoConfigurationImportFilter implements AutoConfigurationImportFilter {

	private static final String BLADE_LOAD_BALANCER_CONFIGURATION =
		"org.springblade.core.loadbalancer.config.BladeLoadBalancerConfiguration";

	@Override
	public boolean[] match(String[] autoConfigurationClasses, AutoConfigurationMetadata autoConfigurationMetadata) {
		boolean[] matches = new boolean[autoConfigurationClasses.length];
		for (int i = 0; i < autoConfigurationClasses.length; i++) {
			matches[i] = !BLADE_LOAD_BALANCER_CONFIGURATION.equals(autoConfigurationClasses[i]);
		}
		return matches;
	}

}
