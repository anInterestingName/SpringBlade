# 授权服务安全过滤器排序启动缺陷

- 需求：REQ-2026-010；缺陷：AUTH-010-STARTUP-001；用例：TC-032。
- 用户日志：2026-10-13 23:20:06，创建 authorizationServerSecurityFilterChain 失败。
- 根因错误：The Filter class org.springframework.security.oauth2.server.authorization.web.OAuth2AuthorizationEndpointFilter does not have a registered order。
- 根因：安全链在 http.build() 前，以 SAS 专属过滤器作为排序锚点；该顺序要到 SAS configurer.configure 阶段才注册。客户端认证过滤器也有同类问题，需要同时修复。
- 连带现象：上下文初始化失败后的 Nacos nacosGracefulShutdownDelegate 关闭异常，不是最初启动原因。
- 修复：Consent CsrfFilter 改到 LogoutFilter 之前，保留独立 OncePerRequest 名称；协议校验过滤器改到 BasicAuthenticationFilter 之后。两个锚点均由 Spring Security 预先登记。
- 源码依据：当前 Spring Security 7.1.0 将 SAS 客户端认证放在 AbstractPreAuthenticatedProcessingFilter 之后，授权/Token/撤销/内省处理放在 AuthorizationFilter 之后。修复保留 CSRF 在授权处理前、协议校验在客户端认证后及协议处理前的顺序。
- 验证：使用与用户启动相同的D:/Java/jdk-21.0.12.1执行mvn --offline compile -DskipTests -pl blade-authorization-server -am；6模块成功、退出码0，[完整日志](2026-10-13-filter-order-build.log)。已核对Spring Security7.1.0源码的预登记锚点和SAS配置时序；文档静态检查单独记录。遵循仓库约定，不启动服务、不运行测试或访问真实接口。
- 状态：代码已修复，待用户重启验证。编译成功不能代替启动和 Consent CSRF 回归。
- 文档静态检查：6份文档、148条相对文件链接、表格/围栏/编码检查通过；32项用例唯一编号及4通过/1失败/20阻塞/1不适用/6未执行汇总一致；需求0.15、设计0.14、数据库0.10、测试0.15与开发中索引一致。
- API、数据库和配置：不变；不新增依赖，不涉及运行数据清理。
