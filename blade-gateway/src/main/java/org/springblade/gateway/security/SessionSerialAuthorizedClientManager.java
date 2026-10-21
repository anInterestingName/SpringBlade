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
package org.springblade.gateway.security;

import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * 单实例内按会话串行刷新与退出，先持久化或失效会话再放行下一项。
 * 已订阅兑换不因客户端取消而中断；后台不使用线程阻塞锁。
 *
 * @author SpringBlade
 */
public final class SessionSerialAuthorizedClientManager implements ReactiveOAuth2AuthorizedClientManager {
	private final ReactiveOAuth2AuthorizedClientManager delegate;
	private final Map<String, Mono<Void>> tails = new HashMap<>();
	public SessionSerialAuthorizedClientManager(ReactiveOAuth2AuthorizedClientManager delegate) { this.delegate = delegate; }
	@Override
	public Mono<OAuth2AuthorizedClient> authorize(OAuth2AuthorizeRequest request) {
		return Mono.deferContextual(context -> {
			ServerWebExchange exchange = request.getAttribute(ServerWebExchange.class.getName());
			if (exchange == null) exchange = context.getOrDefault(ServerWebExchange.class, null);
			if (exchange == null) return Mono.error(new IllegalStateException("BFF 授权缺少当前请求"));
			ServerWebExchange current = exchange;
			return current.getSession().flatMap(session -> serialized(session.getId(), () -> {
				if (session.isExpired()) return Mono.empty();
				OAuth2AuthorizeRequest fresh = OAuth2AuthorizeRequest.withClientRegistrationId(request.getClientRegistrationId())
					.principal(request.getPrincipal()).attributes(attributes -> {
						attributes.putAll(request.getAttributes());
						attributes.put(ServerWebExchange.class.getName(), current);
					}).build();
				return delegate.authorize(fresh).timeout(Duration.ofSeconds(15)).onErrorMap(TimeoutException.class,
					error -> new OAuth2AuthorizationException(new OAuth2Error("temporarily_unavailable")));
			}));
		});
	}
	public Mono<Void> invalidate(ServerWebExchange exchange) {
		return exchange.getSession().flatMap(session -> serialized(session.getId(),
			() -> session.invalidate().thenReturn(Boolean.TRUE))).then();
	}
	private <T> Mono<T> serialized(String id, Supplier<Mono<T>> action) {
		synchronized (tails) {
			Mono<Void> previous = tails.getOrDefault(id, Mono.empty());
			AtomicReference<Mono<Void>> ownTail = new AtomicReference<>();
			Mono<T> result = previous.then(Mono.defer(action)).doFinally(signal -> {
				synchronized (tails) { tails.remove(id, ownTail.get()); }
			}).cache();
			Mono<Void> tail = result.then().onErrorResume(error -> Mono.empty()).cache();
			ownTail.set(tail);
			tails.put(id, tail);
			return result;
		}
	}
}
