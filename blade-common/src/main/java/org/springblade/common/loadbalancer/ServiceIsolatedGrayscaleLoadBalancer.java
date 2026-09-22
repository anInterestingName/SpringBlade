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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.EmptyResponse;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import reactor.core.publisher.Mono;

/**
 * 带 serviceId 归属校验的灰度负载均衡器。
 *
 * <p>保留 Blade 原有灰度与优先 IP 选择规则；若底层组件异常返回其他服务实例，
 * 本层以空响应拒绝转发，使调用方得到服务不可用，而不是把请求发送到错误服务。</p>
 *
 * @author SpringBlade
 */
@Slf4j
@RequiredArgsConstructor
public class ServiceIsolatedGrayscaleLoadBalancer implements ReactorServiceInstanceLoadBalancer {

	private final String serviceId;
	private final ReactorServiceInstanceLoadBalancer delegate;

	@Override
	public Mono<Response<ServiceInstance>> choose(Request request) {
		return delegate.choose(request).map(this::validateServiceInstance);
	}

	private Response<ServiceInstance> validateServiceInstance(Response<ServiceInstance> response) {
		if (!response.hasServer()) {
			return response;
		}
		ServiceInstance instance = response.getServer();
		String actualServiceId = instance == null ? null : instance.getServiceId();
		if (!serviceId.equalsIgnoreCase(actualServiceId)) {
			log.error("负载均衡实例归属不匹配，拒绝转发，expectedServiceId={}, actualServiceId={}",
				serviceId, actualServiceId);
			return new EmptyResponse();
		}
		return response;
	}

}
