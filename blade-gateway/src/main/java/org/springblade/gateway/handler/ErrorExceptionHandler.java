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

package org.springblade.gateway.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springblade.core.tool.api.R;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 异常处理
 *
 * @author Chill
 */
@Order(-1)
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class ErrorExceptionHandler implements ErrorWebExceptionHandler {

	private final ObjectMapper objectMapper;

	@Override
	public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
		ServerHttpResponse response = exchange.getResponse();

		if (response.isCommitted()) {
			return Mono.error(ex);
		}

		response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
		response.getHeaders().setCacheControl("no-store");
		response.setStatusCode(ex instanceof ResponseStatusException error
			? error.getStatusCode() : HttpStatus.INTERNAL_SERVER_ERROR);

		return response.writeWith(Mono.fromSupplier(() -> {
			DataBufferFactory bufferFactory = response.bufferFactory();
			try {
				int status = 500;
				if (response.getStatusCode() != null) {
					status = response.getStatusCode().value();
				}
				// 授权回调含 code/state，错误响应不回显请求 URI、查询串或上游异常内容。
				R<?> result = R.fail(status, status == 404 ? "接口不存在" : "请求处理失败，请检查服务状态");
				return bufferFactory.wrap(objectMapper.writeValueAsBytes(result));
			} catch (JsonProcessingException e) {
				return bufferFactory.wrap(new byte[0]);
			}
		}));
	}
}
