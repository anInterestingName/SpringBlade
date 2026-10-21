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
package org.springblade.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * BFF 网关启动，使用本地路由与Redis共享会话。
 *
 * @author Chill
 */
@SpringBootApplication(excludeName = {
	"org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
	"org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration",
	"org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
	"org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration",
	"com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration"
})
@EnableDiscoveryClient
public class GateWayApplication {

	public static void main(String[] args) {
		// 从实际自动配置候选项排除当前链路不使用的框架基础设施。
		boolean legacyRouting = Boolean.parseBoolean(System.getenv().getOrDefault("GATEWAY_LEGACY_ROUTING_ENABLED", "false"));
		List<String> prefixes = List.of("org.springblade.", "com.alibaba.", "de.codecentric.boot.admin.client.");
		String exclusions = legacyRouting ? "" : StreamSupport.stream(ImportCandidates.load(AutoConfiguration.class,
			GateWayApplication.class.getClassLoader()).spliterator(), false)
			.filter(name -> prefixes.stream().anyMatch(name::startsWith)).collect(Collectors.joining(","));
		SpringApplication application = new SpringApplication(GateWayApplication.class);
		application.setDefaultProperties(Map.of("spring.autoconfigure.exclude", exclusions,
			"spring.cloud.bootstrap.enabled", "false"));
		application.run(args);
	}

}
