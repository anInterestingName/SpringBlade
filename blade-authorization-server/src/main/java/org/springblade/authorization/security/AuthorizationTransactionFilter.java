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
package org.springblade.authorization.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.io.IOException;

/**
 * 将协议兑换和授权状态更新放在同一事务内，持有行锁直到响应生成完成。
 *
 * @author SpringBlade
 */
public class AuthorizationTransactionFilter extends OncePerRequestFilter {
	private final TransactionTemplate transaction;
	public AuthorizationTransactionFilter(PlatformTransactionManager manager) {
		transaction = new TransactionTemplate(manager);
		transaction.setTimeout(30);
	}
	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !"POST".equals(request.getMethod()) || !("/oauth2/token".equals(request.getServletPath())
			|| "/oauth2/revoke".equals(request.getServletPath()) || "/oauth2/authorize".equals(request.getServletPath()));
	}
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
		throws ServletException, IOException {
		ContentCachingResponseWrapper buffered = new ContentCachingResponseWrapper(response);
		try {
			transaction.executeWithoutResult(status -> {
				try {
					chain.doFilter(request, buffered);
				} catch (ServletException | IOException exception) {
					throw new FilterFailure(exception);
				}
			});
			buffered.copyBodyToResponse();
		} catch (FilterFailure exception) {
			if (exception.getCause() instanceof IOException failure) {
				throw failure;
			}
			throw (ServletException) exception.getCause();
		} catch (org.springframework.dao.DataAccessException | org.springframework.transaction.TransactionException exception) {
			if (!response.isCommitted()) {
				response.resetBuffer();
				response.setStatus(500);
				response.setContentType("application/json");
				response.getWriter().write("{\"error\":\"server_error\"}");
			}
		}
	}
	private static class FilterFailure extends RuntimeException {
		FilterFailure(Exception cause) { super(cause); }
	}
}
