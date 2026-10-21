# BFF 构建与静态检查摘要

- 日期：2026-10-08。
- 环境：JDK 21、Maven 3.9.6，当前工作区；没有 Git 提交。
- 验证范围：网关、认证服务、demo 及 Maven reactor 依赖。

| 实际执行命令 | 实际结果 |
| --- | --- |
| mvn -pl blade-gateway,blade-authorization-server,blade-service/blade-demo -am compile -DskipTests | BUILD SUCCESS |
| mvn clean package -DskipTests -pl blade-authorization-server,blade-gateway,blade-service/blade-demo -am | BUILD SUCCESS；全部 9 个 reactor 项目 SUCCESS，Tests are skipped |
| node --check blade-gateway/src/main/resources/static/bff-poc/app.js | 退出码 0 |
| mvn package -DskipTests -pl blade-gateway,blade-authorization-server -am | BUILD SUCCESS；本地凭据导入配置已包含在常规 jar 中 |
| mvn package -DskipTests -pl blade-gateway,blade-service/blade-demo -am | BUILD SUCCESS；移除旧 bootstrap starter 后的常规打包 |
| mvn package -DskipTests -pl blade-authorization-server -am | BUILD SUCCESS；授权请求缓存和直接登录回跳修复 |

静态检查了 jar 的 Start-Class：

- 网关：org.springblade.gateway.GateWayApplication。
- 认证服务：org.springblade.authorization.AuthorizationServerApplication。
- demo：com.example.demo.DemoApplication。

本地凭据补充检查：使用 JDK 21 和 Spring ResourceLoader 读取用户目录配置，确认共用客户端密钥、账号密码均非空，网关的共用文件不包含用户密码。两文件 ACL 限定当前用户访问；jar 中仅有外部文件导入地址，没有打包凭据文件。未记录实际凭据值。

日志启动缺陷定位：用户报告 demo 无法解析 blade.env。依赖图确认网关直接引入 bootstrap starter，demo 经 blade-core-boot / blade-core-cloud 传递引入。Spring Cloud PropertyUtils 的 bootstrapEnabled 判断包含 Marker 存在条件，因此 false 配置不能关闭该 starter；依赖内 bootstrap.yml 的日志配置使用 blade.env。当前已移除对应依赖并重新打包，待用户重新加载 Maven 后复测。

403 提示缺陷：用户提供的[截图](http-403-before.png)显示 HTTP 403，却提示未返回 JSON。demo 的鉴权失败已补充统一 401/403 JSON 并保留 Bearer 响应头；页面按状态码提供兜底提示。再次执行网关和 demo 的 package -DskipTests 结果为 BUILD SUCCESS，更新后的 JavaScript 语法检查退出码 0；运行复测未执行。

登录回跳缺陷：用户输入账号密码后出现 404，[截图](login-devtools-404-before.png)显示跳转到 Chrome appspecific 探测地址并带 continue 参数。认证服务两条安全链现共用仅缓存 GET 授权端点的请求缓存，探测请求允许匿名访问但没有业务映射；没有授权请求时的登录成功地址配置为 BFF 登录发起入口。授权服务打包成功，并确认编译产物包含新的缓存配置和 bffLoginUri 属性。没有访问真实登录接口。

AI 没有运行 Maven 测试、启动服务或调用接口。用户报告的启动、权限提示和登录回跳缺陷已记录，修复后的运行证据待用户补充。完整待执行项见[测试文档](../../TEST-REQ-2026-009-bff-validation.md)。

## 前端认证替换阶段

2026-10-08，前后端同名分支 `codex/authorization-frontend-poc`，均未提交。用户确认前阶段最小链路通过；本阶段新增页面未运行，历史逐用例结果不据此改写。

| 实际执行命令 | 结果 |
| --- | --- |
| JDK 21：mvn -o compile -DskipTests -pl blade-authorization-server,blade-gateway,blade-service/blade-demo -am | BUILD SUCCESS，9 个 reactor 项目 SUCCESS |
| JDK 21：mvn -o package -DskipTests -pl blade-authorization-server,blade-gateway,blade-service/blade-demo -am | BUILD SUCCESS，Tests are skipped |
| Saber：pnpm run type-check | 退出码 0，vue-tsc 与 Node 配置 tsc 均通过 |
| Saber：pnpm run build:prod | 退出码 0；2078 个模块转换，包含新增 login/consent/verify 页面产物 |

首轮后端编译暴露 Servlet CsrfConfigurer 没有 WebFlux 的 accessDeniedHandler 方法，已改为 ExceptionHandlingConfigurer 的拒绝处理器后编译通过；源代码核对确认 Servlet CSRF 会使用该处理器。前端依赖目录在沙箱内出现 EPERM，授权执行工程检查后成功。构建存在依赖 PURE 注解、既有动态/静态混合导入和大 chunk 提示，不影响完成。

静态核对 Spring Security 7.1 源码：自定义登录页面取消默认 HTML；授权确认采用服务端生成的 state，对应当前身份、客户端和原申请 scope。登录仍走原生表单与 CSRF；授权确认 POST 沿用框架协议校验。blade-core-tool 的自动配置清单已核对并在独立认证服务排除，避免加载工具模块 Redis/MVC 配置。打包后的认证服务不含 blade-core-launch、Redis starter、Nacos、bootstrap、JDBC/MySQL 依赖；本地两份凭据文件没有覆盖前端/回调地址的配置项，只检查项名和数量，没有输出凭据值。

文档检查：前后端相对链接可达；15 个用例编号唯一、必要字段齐全，汇总为通过 1、历史失败 3、未执行 11，其他状态 0；无模板占位符。git diff --check 通过。前端改动仅新增认证页面/API、三个精确路由及守卫分支、Vite 代理和说明，未新增 Avue/Ant Design、客户端密钥、Token 持久化或生产部署变更。

本阶段没有启动后端或 Vite、访问真实接口、执行浏览器测试或 Maven 测试。构建不能证明登录、授权拒绝、刷新、Cookie、代理或响应式验收通过；全部新增用例待用户执行。

## 认证服务启动路径修复

2026-10-08 用户提供 BeanCreationException：loginSecurityFilterChain 创建失败，pattern must start with a /，登记为 DEF-009-004。

核对 Spring Security 7.1.0 的 AbstractAuthenticationFilterConfigurer.loginPage 源码：该方法会立即调用 updateAuthenticationDefaults；loginProcessingUrl 尚未设置时，直接把 loginPage 值作为匹配路径。原配置先给出绝对前端 URL，导致 POST 路径匹配器校验失败，后面的 loginProcessingUrl("/login") 尚未执行。修复使用 loginPage("/login")，由已有 GET /login Controller 302 跳转到固定前端 URL；POST /login、失败处理和登录成功恢复授权的逻辑保持现有契约。

实际执行 JDK 21：`mvn -o package -DskipTests -pl blade-authorization-server -am`，退出码 0，BUILD SUCCESS，Tests are skipped。本轮只有后端安全配置与配套文档变化，不重复前端构建。未启动认证服务或访问接口，启动复测和登录回归仍待用户执行。

## 2026-10-09 密码认证时间修复

用户提供授权码兑换日志：`JwtGenerator.getAuthenticationTime` 第 243 行抛出 `authenticationTime cannot be null`；对应 Security 7.1.0。该版本从 `Authentication.getAuthorities()` 中的 `FactorGrantedAuthority.issuedAt` 获取认证时间，自定义 Provider 原权限列表只有 `SimpleGrantedAuthority` 业务角色。

修复在系统用户密码、有效身份与租户校验成功后，复制业务角色到 `List<GrantedAuthority>`，加入 `FactorGrantedAuthority.fromAuthority(PASSWORD_AUTHORITY)`，再创建成功的认证对象。业务 User、稳定主体、tenantId 与 JWT roles 来源保持现有契约。时间在登录成功时产生，未在 JWT 定制器或兑换时补造。

静态源码核对：Spring Security 7.1.0 默认密码 Provider 同样添加该因子；现有 `SecurityJackson2Modules.getModules` 注册包含认证因子 MixIn 的 CoreJackson2Module，且在 JavaTimeModule 可用时注册该模块。授权服务当前 jar 包含 jackson-datatype-jsr310，现有 AuthorizationCodec 保存/恢复完整授权 attributes。该检查仅支持序列化方案判断，不能证明真实 Redis/MySQL 往返已经通过。

| 实际执行命令 | 实际结果 |
| --- | --- |
| JDK 21.0.12 / Maven 3.9.6：mvn -o package -DskipTests -pl blade-authorization-server -am | 退出码 0，BUILD SUCCESS；5 个 reactor 项目 SUCCESS，授权服务重新编译 14 个源文件，Tests are skipped |

构建生成 `blade-authorization-server/target/blade-authorization-server.jar`。本项未增加依赖或修改 SQL、Nacos、Feign、网关、前端和资源服务，不重复其构建；运行回归覆盖登录、auth_time、持久化往返、再次授权、刷新和 scope 拒绝。

未启动服务、访问真实接口或执行 Maven 测试。旧会话不会因服务重启自动补齐认证因子，应在新无痕窗口重新登录；TC-016 的用户报告失败保留，修复后复测与 TC-017 均待用户执行，需求仍为开发中。证据记录不包含密码、Token、Cookie、内部凭据或完整授权上下文。
