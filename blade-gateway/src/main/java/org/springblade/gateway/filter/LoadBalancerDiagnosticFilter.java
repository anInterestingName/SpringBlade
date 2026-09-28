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
package org.springblade.gateway.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springblade.gateway.provider.RequestProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.ReactiveLoadBalancerClientFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import java.net.URI;
import java.util.Set;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_LOADBALANCER_RESPONSE_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

/**
 * 记录公开接口在网关中的路由与实例选择结果，用于定位跨服务转发。
 * 仅在诊断分支使用，不读取查询参数、请求头或请求体。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoadBalancerDiagnosticFilter implements GlobalFilter, Ordered {

	private static final Set<String> DIAGNOSTIC_PATHS = Set.of(
		"/blade-auth/register/config",
		"/blade-auth/captcha",
		"/blade-system/tenant/info"
	);

	private final LoadBalancerClientFactory clientFactory;

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		String originalPath = RequestProvider.getOriginalRequestPath(exchange);
		if (!DIAGNOSTIC_PATHS.contains(originalPath)) {
			return chain.filter(exchange);
		}

		Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
		URI loadBalancerUrl = exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);
		String expectedServiceId = route == null ? null : route.getUri().getHost();
		String requestId = exchange.getRequest().getId();
		String forwardedPath = exchange.getRequest().getPath().value();
		return chain.filter(exchange).doFinally(signal -> logSelection(exchange, requestId, originalPath,
			forwardedPath, route, loadBalancerUrl, expectedServiceId, signal));
	}

	private void logSelection(ServerWebExchange exchange, String requestId, String originalPath,
		String forwardedPath, Route route, URI loadBalancerUrl, String expectedServiceId, SignalType signal) {
		Response<ServiceInstance> selection = exchange.getAttribute(GATEWAY_LOADBALANCER_RESPONSE_ATTR);
		ServiceInstance instance = selection != null && selection.hasServer() ? selection.getServer() : null;
		URI finalUrl = exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);
		String loadBalancerBean = "unavailable";
		if (selection != null && expectedServiceId != null) {
			try {
				ReactorServiceInstanceLoadBalancer loadBalancer = clientFactory.getInstance(
					expectedServiceId, ReactorServiceInstanceLoadBalancer.class);
				loadBalancerBean = beanIdentity(loadBalancer);
			} catch (RuntimeException exception) {
				loadBalancerBean = "inspection-failed:" + exception.getClass().getSimpleName();
			}
		}

		log.info("lb-diagnostic requestId={} originalPath={} forwardedPath={} routeId={} expectedService={} " +
			"lbUrlHost={} selectedService={} selectedHost={} selectedPort={} finalScheme={} finalHost={} finalPort={} " +
			"loadBalancerBean={} status={} signal={}",
			requestId, originalPath, forwardedPath, route == null ? null : route.getId(), expectedServiceId,
			loadBalancerUrl == null ? null : loadBalancerUrl.getHost(),
			instance == null ? null : instance.getServiceId(), instance == null ? null : instance.getHost(),
			instance == null ? null : instance.getPort(), finalUrl == null ? null : finalUrl.getScheme(),
			finalUrl == null ? null : finalUrl.getHost(), finalUrl == null ? null : finalUrl.getPort(),
			loadBalancerBean, exchange.getResponse().getStatusCode(), signal);
	}

	private static String beanIdentity(Object bean) {
		return bean == null ? "missing" : bean.getClass().getSimpleName() + "@" +
			Integer.toHexString(System.identityHashCode(bean));
	}

	@Override
	public int getOrder() {
		return ReactiveLoadBalancerClientFilter.LOAD_BALANCER_CLIENT_FILTER_ORDER - 1;
	}

}
