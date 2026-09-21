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

import org.springblade.core.loadbalancer.props.BladeLoadBalancerProperties;
import org.springblade.core.loadbalancer.rule.GrayscaleLoadBalancer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClientConfiguration;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClientSpecification;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.context.annotation.Bean;

/**
 * 按 serviceId 隔离的负载均衡自动配置。
 *
 * <p>根上下文只登记命名客户端配置，不创建 ReactorLoadBalancer Bean。
 * 负载均衡器由 {@link BladeLoadBalancerClientConfiguration} 在每个服务专属的
 * LoadBalancer NamedContext 中创建，避免不同服务共享实例供应器。</p>
 *
 * @author SpringBlade
 */
@AutoConfiguration
@AutoConfigureBefore(LoadBalancerClientConfiguration.class)
@ConditionalOnClass({LoadBalancerClientFactory.class, GrayscaleLoadBalancer.class})
@EnableConfigurationProperties(BladeLoadBalancerProperties.class)
@ConditionalOnProperty(value = BladeLoadBalancerProperties.PROPERTIES_PREFIX + ".enabled", matchIfMissing = true)
public class BladeLoadBalancerIsolationAutoConfiguration {

	/**
	 * 注册所有服务共用的命名客户端配置定义。
	 *
	 * <p>配置名必须以 default. 开头，Spring Cloud 才会将其应用到每个 serviceId；
	 * 配置类本身刻意不标注为根上下文配置，详见其类级注释。</p>
	 */
	@Bean
	public LoadBalancerClientSpecification springBladeLoadBalancerClientSpecification() {
		return new LoadBalancerClientSpecification(
			"default.springBladeLoadBalancerClientConfiguration",
			new Class<?>[]{BladeLoadBalancerClientConfiguration.class}
		);
	}

}
