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
package org.springblade.gateway.route;

import lombok.RequiredArgsConstructor;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.cloud.gateway.handler.predicate.PathRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.net.URI;

import static org.springframework.cloud.gateway.handler.predicate.RoutePredicateFactory.PATTERN_KEY;
import static org.springframework.cloud.gateway.support.NameUtils.normalizeRoutePredicateName;

/**
 * 基于服务名生成动态路由
 *
 * <p>
 * 路由刷新只读取服务名，不并发查询各服务实例。实例解析统一留到请求转发阶段由 Spring Cloud LoadBalancer 完成，
 * 避免服务发现刷新期间的实例视图影响其他服务的路由定义。
 * </p>
 *
 * @author Chill
 */
@Component
@RequiredArgsConstructor
public class ServiceDiscoveryRouteDefinitionLocator implements RouteDefinitionLocator {

	private final ReactiveDiscoveryClient discoveryClient;

	@Override
	public Flux<RouteDefinition> getRouteDefinitions() {
		return discoveryClient.getServices()
			.filter(StringUtils::hasText)
			.distinct()
			.map(this::buildRouteDefinition);
	}

	private RouteDefinition buildRouteDefinition(String serviceId) {
		RouteDefinition routeDefinition = new RouteDefinition();
		routeDefinition.setId(discoveryClient.getClass().getSimpleName() + "_" + serviceId);
		routeDefinition.setUri(URI.create("lb://" + serviceId));
		routeDefinition.getPredicates().add(buildPathPredicate(serviceId));
		return routeDefinition;
	}

	private PredicateDefinition buildPathPredicate(String serviceId) {
		PredicateDefinition predicate = new PredicateDefinition();
		predicate.setName(normalizeRoutePredicateName(PathRoutePredicateFactory.class));
		predicate.addArg(PATTERN_KEY, "/" + serviceId + "/**");
		return predicate;
	}

}
