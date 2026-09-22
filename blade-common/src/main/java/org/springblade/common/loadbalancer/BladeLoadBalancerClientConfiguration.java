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
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.loadbalancer.core.ReactorLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.util.Assert;

/**
 * 单个 serviceId 的负载均衡客户端配置。
 *
 * <p>本类由 LoadBalancerClientFactory 显式注册到命名子上下文，禁止添加
 * {@code @Configuration}、{@code @Component} 或自动配置注解，否则会被根上下文扫描，
 * 再次造成不同服务共享同一个 ServiceInstanceListSupplier。</p>
 *
 * @author SpringBlade
 */
public class BladeLoadBalancerClientConfiguration {

	/**
	 * 为当前命名上下文创建绑定到固定 serviceId 的灰度负载均衡器。
	 */
	@Bean
	public ReactorLoadBalancer<ServiceInstance> reactorServiceInstanceLoadBalancer(
		Environment environment,
		LoadBalancerClientFactory loadBalancerClientFactory,
		BladeLoadBalancerProperties bladeLoadBalancerProperties) {
		String serviceId = LoadBalancerClientFactory.getName(environment);
		Assert.hasText(serviceId, "LoadBalancer serviceId must not be empty");
		GrayscaleLoadBalancer delegate = new GrayscaleLoadBalancer(
			loadBalancerClientFactory.getLazyProvider(serviceId, ServiceInstanceListSupplier.class),
			bladeLoadBalancerProperties
		);
		return new ServiceIsolatedGrayscaleLoadBalancer(serviceId, delegate);
	}

}
