# REQ-2026-010 构建证据

## 执行信息

- 日期：2026-10-09
- 工作区：`D:\workspace\project\SpringBlade`
- JDK/Maven：按仓库约定使用 JDK 21 和系统 Maven
- 执行范围：`blade-authorization-server`、`blade-service/blade-system`、`blade-gateway`、`blade-service/blade-demo` 及其依赖模块

## 结果

### 编译

命令：

```powershell
mvn compile -DskipTests -pl blade-authorization-server,blade-service/blade-system,blade-gateway,blade-service/blade-demo -am
```

结果：`BUILD SUCCESS`。授权服务、system、网关、demo 及依赖模块完成编译；未运行 Maven 测试，未启动服务。

### 打包

命令：

```powershell
mvn clean package -DskipTests -pl blade-authorization-server,blade-service/blade-system,blade-gateway,blade-service/blade-demo -am
```

结果：阻塞。Maven clean/package 需要删除或写入现有 `target` 文件，工作区文件被占用，出现 `Failed to delete ... target\blade-system.jar` 和 `Problem creating output file ... blade-authorization-server.jar`。该结果不能解释为代码编译失败；释放占用进程后必须重新执行。

## 边界

本证据只证明构建命令的实际结果，不证明 HTTP、OAuth2/OIDC、数据库、Redis、权限、租户、并发、重启、密钥轮换或生产发布验收。

## 2026-10-10 标准 Token 改造复核

命令：

```powershell
mvn -Dmaven.compiler.useIncrementalCompilation=false -DskipTests compile -pl blade-token-security -am
```

结果：`BUILD SUCCESS`。公共 Resource Server、Feign `client_credentials` 和 BladeUser 适配模块在 JDK 21 下完成编译。

授权服务和 system 的同范围编译仍被既有 `target/classes` 及 Maven 本地仓库文件拒绝访问阻塞，未取得这两个模块本次改造的完整编译证据；未运行 Maven 测试，未启动服务。

为排除工作区文件锁影响，将当前源码复制到可写临时工作区后执行：

```powershell
mvn -o -DskipTests compile -pl blade-authorization-server,blade-service/blade-system,blade-gateway,blade-service/blade-demo -am
```

结果：`BUILD SUCCESS`。授权服务、system、网关、demo、`blade-token-security` 及依赖模块在 JDK 21 下完成编译；未运行 Maven 测试，未启动服务。该结果证明源码编译通过，不替代真实依赖、HTTP、数据库、Redis、权限和发布验收。

随后在同一临时工作区执行全部 service/ops 模块编译：

```powershell
mvn -o -DskipTests compile -pl blade-service/blade-ai,blade-service/blade-desk,blade-service/blade-log,blade-service/blade-system,blade-service/blade-demo,blade-ops/blade-admin,blade-ops/blade-develop,blade-ops/blade-report,blade-ops/blade-resource,blade-ops/blade-seata-order,blade-ops/blade-seata-storage -am
```

结果：`BUILD SUCCESS`。全部资源服务及运维服务通过编译；未运行 Maven 测试，未启动服务。

## 2026-10-10 Nacos Discovery/Config 改造复核

命令：

```powershell
mvn compile -DskipTests -pl blade-authorization-server,blade-service/blade-system,blade-gateway -am
```

环境：JDK 21.0.12 / Maven 3.9.6，当前工作区。

结果：`BUILD SUCCESS`。授权服务改用公共 `BladeApplication.run`/`LauncherServiceImpl` 启动链路，按 profile 使用标准 Nacos Data ID；Nacos Discovery、LoadBalancer、Feign 服务发现、共享内部契约白名单和保留客户端标识校验均完成编译；未运行 Maven 测试，未启动 Nacos、数据库、Redis 或任何服务。

本次复核同时确认 `@BladeCloudApplication`、公共 Feign/Fallback 扫描和授权服务全量 Blade 公共运行时依赖完成编译；未启动应用，完整自动配置与协议安全链的运行时组合仍需真实环境验证。

## 2026-10-13 通用安全配置并入 blade-common 重构复核

- 改造内容：
  1. 彻底移除根目录下异类模块 `blade-token-security`，其 OAuth2 Resource Server 自动装配、Feign 客户端凭据拦截器、TokenBladeUserFilter 请求属性桥接以及属性配置类完整并入 `blade-common`（包名：`org.springblade.common.security`）。
  2. 依赖管理：在 `blade-common/pom.xml` 中引入 `spring-security-oauth2-resource-server`、`spring-security-oauth2-jose`、`spring-security-oauth2-client`、`spring-cloud-openfeign-core`；根 `pom.xml` 与 `blade-service/pom.xml` 移除对 `blade-token-security` 的引用。
  3. 隔离与安全边界：
     - 网关通过 `@ConditionalOnWebApplication(type = SERVLET)` 条件天然忽略 Servlet 资源服务安全链。
     - 资源服务自动装配要求 `@ConditionalOnProperty(prefix = "blade.authorization.resource", name = "enabled", havingValue = "true")`；在 `blade-authorization-server` 中显式配置 `blade.authorization.resource.enabled: false`，避免授权协议端点与通用资源服务链发生 `@Order` 拦截冲突与公钥拉取死锁。

命令：

```powershell
mvn -DskipTests compile
```

环境：JDK 21.0.12.1 / Maven 3.9.6，当前工作区。

结果：`BUILD SUCCESS`（Reactor 全量 26 个模块全部编译通过，耗时约 13s）；未运行 Maven 测试，未启动真实服务。证明将通用安全适配并入 `blade-common` 后的代码架构与依赖组织完全自洽。

## 2026-10-13 全量资源服务接入与网关 BFF 路由扩展复核

- 改造内容：
  1. `blade-common` 安全底座增强：`ResourceTokenProperties` 支持 `skipUrl` 列表，`StandardTokenResourceServerAutoConfiguration` 支持动态放行路径配置。
  2. `blade-ops` 与 `blade-service`：全量业务微服务与运维服务（`blade-system`、`blade-desk`、`blade-ai`、`blade-log`、`blade-demo`、`blade-resource`、`blade-develop`、`blade-report`、`blade-admin`）均已由公共 `blade-common` 自动装配标准 Resource Server 安全过滤与 `BladeUser` 桥接。
  3. `blade-gateway`：在 `BffProperties` 和 `BffConfiguration` 中为所有微服务注册 BFF 路由，去除上游敏感凭据头并挂载 `sessionRelay`（TokenRelay 过滤器），实现请求自动附加 Bearer Token。
  4. 数据脚本与 Nacos 配置：`blade.mysql.upgrade.5.0.1.oauth2-persistence.sql` 与 `blade.mysql.all.create.sql` 补充 `gateway-bff` 覆盖全量微服务的 audience 种子数据；`doc/nacos/blade.yaml` 同步补充标准 skip-url 放行配置。

命令：

```powershell
mvn compile -DskipTests -pl blade-common,blade-service/blade-system,blade-service/blade-desk,blade-service/blade-ai,blade-service/blade-log,blade-service/blade-demo,blade-ops/blade-resource,blade-ops/blade-develop,blade-ops/blade-report,blade-ops/blade-admin,blade-gateway -am
```

环境：JDK 21.0.12.1 / Maven 3.9.6，当前工作区。

结果：`BUILD SUCCESS`（Reactor 22 个模块编译通过，耗时 3.3s）；未运行 Maven 测试，未启动服务。证实代码层改动语法与依赖关系完全正确。

