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

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.server.WebSessionServerOAuth2AuthorizedClientRepository;
import org.springframework.session.ReactiveSessionRepository;
import org.springframework.session.Session;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.util.Map;

/**
 * 会话授权客户端使用原框架格式，串行刷新前从 Redis 重读，保存后立即持久化。
 *
 * @author SpringBlade
 */
public final class BffSessionAuthorizedClientRepository implements ServerOAuth2AuthorizedClientRepository {
	private static final String ATTRIBUTE = WebSessionServerOAuth2AuthorizedClientRepository.class.getName() + ".AUTHORIZED_CLIENTS";
	private final ServerOAuth2AuthorizedClientRepository delegate = new WebSessionServerOAuth2AuthorizedClientRepository();
	private final ReactiveSessionRepository<? extends Session> sessions;
	public BffSessionAuthorizedClientRepository(ReactiveSessionRepository<? extends Session> sessions) { this.sessions = sessions; }
	@Override
	public <T extends OAuth2AuthorizedClient> Mono<T> loadAuthorizedClient(String id, Authentication principal, ServerWebExchange exchange) {
		return exchange.getSession().flatMap(local -> sessions.findById(local.getId()).flatMap(stored -> {
			Map<String, OAuth2AuthorizedClient> clients = stored.getAttribute(ATTRIBUTE);
			if (clients == null) local.getAttributes().remove(ATTRIBUTE);
			else local.getAttributes().put(ATTRIBUTE, new java.util.HashMap<>(clients));
			return delegate.<T>loadAuthorizedClient(id, principal, exchange);
		}));
	}
	@Override
	public Mono<Void> saveAuthorizedClient(OAuth2AuthorizedClient client, Authentication principal, ServerWebExchange exchange) {
		return delegate.saveAuthorizedClient(client, principal, exchange).then(exchange.getSession().flatMap(session -> {
			if (session.isExpired()) return Mono.error(new IllegalStateException("BFF 会话已失效"));
			return session.save();
		}));
	}
	@Override
	public Mono<Void> removeAuthorizedClient(String id, Authentication principal, ServerWebExchange exchange) {
		return delegate.removeAuthorizedClient(id, principal, exchange).then(exchange.getSession().flatMap(session ->
			session.isExpired() ? Mono.empty() : session.save()));
	}
}
