# 跨服务动态路由与负载均衡隔离测试文档

## 1. 文档信息

| 项目 | 内容 |
| --- | --- |
| 测试编号 | TEST-REQ-2026-007 |
| 关联需求 | [REQ-2026-007](../requirements/REQ-2026-007-loadbalancer-isolation.md) |
| 关联详细设计 | [DESIGN-REQ-2026-007](../design/DESIGN-REQ-2026-007-loadbalancer-isolation.md) |
| 关联数据库设计 | 不涉及 |
| 文档版本 | 0.3 |
| 文档状态 | 执行中 |
| 测试负责人 | 用户 / Codex |
| 测试日期 | 2026-09-21 至 2026-09-24 |

## 2. 测试范围与依据

- 测试目标：验证 Gateway 只按服务名生成动态路由，刷新阶段不查询实例；同时验证 Gateway 与 Feign 按 `serviceId` 隔离实例供应器、异常实例失败关闭及灰度配置兼容。
- 范围内：Gateway 与公共模块编译打包、动态路由产物、运行时 Locator Bean、Feign 多服务调用、配置开关、Nacos Watch 刷新与并发。
- 范围外：业务接口功能、数据库、租户、权限、Sentinel 日志目录和健康检查白名单。
- 验收依据：`AC-001` 至 `AC-005`、`BR-001` 至 `BR-006`。

## 3. 测试环境与准备

| 项目 | 内容 |
| --- | --- |
| 后端版本/提交 | 验证分支 `0285b37c`；完整接口测试使用的镜像摘要待归档 |
| JDK / Maven | JDK 21 / 系统 Maven |
| MySQL | 不涉及 |
| Nacos / Redis | Nacos 3.2.2；Redis 不涉及本次路由选择 |
| 目标服务 | 上海 Gateway/Nacos、东京 auth/system、硅谷其他服务 |
| 测试身份 | 公开登录前接口；不记录 Token |
| 测试数据 | 只读请求，无业务数据写入和清理 |

## 4. 测试用例

### TC-001 公共模块与受影响服务构建

- 优先级：P0
- 关联需求/验收标准：REQ-001 / AC-001
- 测试层级：构建
- 前置条件：JDK 21、Maven 依赖可用。
- 步骤：执行目标模块 `clean package -DskipTests`，覆盖 Gateway、auth、system 及依赖模块。
- 预期结果：BUILD SUCCESS；无重复 Bean、类型不匹配或自动配置编译错误。
- 清理：不涉及。
- 实际结果：第一版使用 JDK 21 执行 `mvn clean package -DskipTests -pl blade-gateway,blade-auth,blade-service/blade-system -am`，11 个 Reactor 模块全部 BUILD SUCCESS。本轮使用 JDK 21 执行 `mvn -pl blade-gateway -am -DskipTests compile` 和 `mvn -pl blade-gateway -am -DskipTests package`，SpringBlade、blade-common、blade-gateway 3 个 Reactor 模块均 BUILD SUCCESS。
- 结果：通过
- 证据/缺陷：构建日志；首次使用 JDK 17 执行时因不支持 release 21 失败，切换项目要求的 JDK 21 后通过，不属于代码缺陷。

### TC-002 自动配置与动态路由产物完整性

- 优先级：P0
- 关联需求/验收标准：REQ-001 / AC-001
- 测试层级：静态检查
- 前置条件：`blade-common` 已打包。
- 步骤：检查 `blade-common` JAR 中的 AutoConfiguration imports、`spring.factories` 和四个负载均衡实现类；检查 Gateway JAR 中包含本地 Locator 类，`bootstrap.yml` 的内置 Locator 开关为关闭。
- 预期结果：第一版过滤器与本地自动配置保持完整；Gateway 本地 Locator 和关闭内置 Locator 的配置进入产物。
- 清理：不涉及。
- 实际结果：`blade-common.jar` 包含四个实现类、AutoConfiguration imports 与 ImportFilter 注册；依赖树保持 `blade-starter-loadbalancer 5.0.1`、Spring Cloud 5.0.2、Nacos Client 3.2.2 单一版本。`blade-gateway.jar` 包含 `ServiceDiscoveryRouteDefinitionLocator.class`，归档内 `bootstrap.yml` 已设置 `spring.cloud.gateway.server.webflux.discovery.locator.enabled=false`。
- 结果：通过
- 证据/缺陷：JAR 清单、`spring.factories`、AutoConfiguration imports 和 Maven dependency tree。

### TC-003 Gateway 多服务连续与并发路由

- 优先级：P0
- 关联需求/验收标准：REQ-001 / AC-001、AC-002
- 测试层级：Gateway/Nacos 集成
- 前置条件：auth、system 和 ai 分别注册唯一地址及端口，Gateway 使用本次验证分支镜像。
- 输入数据：`GET /blade-system/tenant/info`、`GET /blade-auth/captcha` 与 `GET /blade-ai/v3/api-docs`。
- 步骤：先确认心跳与 `RefreshRoutesEvent` 实际发生，再交替连续请求三个入口不少于 100 轮且持续至少 100 秒，并发请求不少于 1000 次；覆盖至少三个实际刷新周期。
- 预期结果：三个入口持续同时命中各自服务；auth 日志无 `/tenant/info`，各服务日志无其他服务入口；刷新前后无整体切换。
- 清理：不涉及。
- 实际结果：第一版修复镜像已执行基线复查，故障仍按 29～31 秒周期出现，auth、system、ai 会同时落到同一服务。用户于 2026-09-23 确认验证分支的 404 和双重裁剪已修复；尚未提供本用例要求的 100 轮、1000 次并发、实际心跳周期及服务端日志证据。
- 结果：未执行
- 证据/缺陷：用户反馈为阶段性证据；完整请求结果与日志待归档，不能据此判定本用例通过。

### TC-004 Feign 多服务实例隔离

- 优先级：P0
- 关联需求/验收标准：REQ-001 / AC-001、AC-002
- 测试层级：Feign 集成
- 前置条件：选择一个同时调用两个服务的测试入口，依赖实例均健康。
- 步骤：并发交替触发两个 Feign Client，核对客户端结果与服务端访问日志。
- 预期结果：每个 Client 只访问声明的服务；业务失败与降级结果不被误判为成功。
- 清理：使用只读入口；若需写数据，另行定义唯一前缀和精确清理步骤。
- 实际结果：待执行
- 结果：未执行
- 证据/缺陷：无

### TC-005 错误实例失败关闭

- 优先级：P0
- 关联需求/验收标准：REQ-002 / AC-003
- 测试层级：负载均衡组件
- 前置条件：测试环境可注入 `serviceId` 不匹配的 `ServiceInstance`。
- 步骤：期望服务设为 `blade-system`，让委托选择器返回 `blade-auth` 实例并发起调用。
- 预期结果：返回空选择/服务不可用；错误服务未收到请求；日志只含 expected/actual serviceId。
- 清理：移除测试实例供应器。
- 实际结果：在构建后的运行时类路径中注入 `blade-auth` 错误实例和 `blade-system` 正确实例；错误实例返回 `EmptyResponse` 且 `hasServer=false`，正确实例正常返回 `blade-system`。
- 结果：通过
- 证据/缺陷：JShell 断言通过；ERROR 日志只包含 expected/actual serviceId。

### TC-006 关闭自定义负载均衡

- 优先级：P1
- 关联需求/验收标准：REQ-002 / AC-004
- 测试层级：配置/启动
- 前置条件：测试实例设置 `blade.loadbalancer.enabled=false`。
- 步骤：启动 Gateway，检查 LoadBalancer Bean 类型、本地动态路由 Locator，并调用 auth/system/ai。
- 预期结果：使用 Spring Cloud 默认负载均衡器；本地动态路由仍生效且刷新阶段不查询实例；三个入口路由正确。
- 清理：恢复测试配置。
- 实际结果：前序 A/B 在尚未引入本地动态路由时执行，90 次请求全部异常，证明 Blade 自定义负载均衡不是根因；本轮组合场景待执行。
- 结果：未执行
- 证据/缺陷：前序 A/B 记录；不得将关闭该开关作为回滚措施。

### TC-007 本地 Locator 静态行为

- 优先级：P0
- 关联需求/验收标准：REQ-001 / AC-001
- 测试层级：静态检查
- 前置条件：Gateway 已使用 JDK 21 编译。
- 步骤：检查最终源码及已有字节码记录，确认本地 Locator 调用 `getServices()`、不引用 `getInstances()`；检查 Path、`lb://serviceId` 构造和最终源码不再添加 `StripPrefix`。
- 预期结果：只调用 `getServices()`；每个非空去重服务名生成一条目标明确的路由，前缀仅由现有 `RequestFilter` 裁剪一次。
- 清理：不涉及。
- 实际结果：此前构建的字节码检查输出 `NO_GET_INSTANCES_REFERENCE`；静态核对最终提交 `0285b37c` 的 Locator 源码仍只使用 `getServices()`，且已删除 `StripPrefix` 构造。最终提交的字节码尚未重新归档。
- 结果：通过
- 证据/缺陷：`2d2dce95` 的源码与字节码记录、`0285b37c` 的源码差异；最终产物检查仍需补充。

### TC-008 运行时 Locator 唯一性

- 优先级：P0
- 关联需求/验收标准：REQ-001 / AC-001、AC-002
- 测试层级：Gateway/Nacos 集成
- 前置条件：Gateway 使用本次验证分支镜像，Actuator 或 JVM 诊断工具可用。
- 步骤：检查 `RouteDefinitionLocator` Bean 与动态路由，确认内置 `DiscoveryClientRouteDefinitionLocator` 未装配、本地 `ServiceDiscoveryRouteDefinitionLocator` 已装配，每个服务只有一条发现路由。
- 预期结果：不存在内置发现 Locator Bean；auth/system/ai 路由分别指向自身 `lb://serviceId`，没有重复路由。
- 清理：不涉及。
- 实际结果：待执行
- 结果：未执行
- 证据/缺陷：无

### TC-009 服务增删后的动态路由刷新

- 优先级：P0
- 关联需求/验收标准：REQ-001 / AC-005
- 测试层级：Gateway/Nacos 集成
- 目标模块与入口：Gateway 动态路由定义与 Nacos 服务注册；通过路由列表或 JVM 诊断观察，不调用业务 HTTP 入口。
- 依赖：确认 Gateway 正在运行、心跳事件与 `RefreshRoutesEvent` 可观察，测试服务拥有唯一服务名和地址。
- 前置条件：隔离测试服务尚未注册，Gateway 已缓存现有路由；不得操作共享或生产服务。
- 测试数据：带唯一前缀的隔离服务名，执行前记录 Gateway 路由列表与对应容器标识。
- 步骤：记录当前路由；注册隔离测试服务；等待至少两个实际刷新周期并检查路由列表；将该测试服务下线；再次等待至少两个刷新周期并检查其路由消失及原有服务路由未改变。
- 可观察预期：心跳/刷新事件确实发生；新增路由的 ID、Path 与 `lb://serviceId` 只指向隔离服务；下线后该路由移除，原有服务路由保持各自目标。不得以固定时间内旧路由持续正常代替刷新证据。
- 证据：事件时间、注册前后及下线后的路由列表；不记录 Token 或敏感请求体。
- 清理：仅注销本用例注册的隔离测试服务并确认路由消失，不删除未知或共享服务实例。
- 实际结果：待执行。
- 结果：未执行

## 5. 专项检查

- [x] 本地 Locator 字节码不引用 `getInstances()`。
- [x] Gateway JAR 中内置 Locator 开关为关闭。
- [ ] 运行时仅装配本地发现 Locator。
- [ ] 多 `serviceId` 首次并发创建 NamedContext。
- [ ] Nacos 实例新增、下线与缓存刷新。
- [ ] 关闭内置 Locator 后，确认 Nacos 心跳与 `RefreshRoutesEvent` 仍实际发生。
- [ ] 灰度版本头存在、不存在和无匹配实例。
- [ ] 优先 IP 匹配与无匹配回退。
- [ ] 错误实例失败关闭且不泄露地址、Token 或请求体。
- [ ] 关闭自定义负载均衡后默认实现可用。

## 6. 验收覆盖矩阵

| 验收标准 | 测试用例 | 结果 | 说明 |
| --- | --- | --- | --- |
| AC-001 | TC-001、TC-002、TC-003、TC-004、TC-007、TC-008 | 未执行 | 构建、路由定义、Gateway、Feign |
| AC-002 | TC-003、TC-004、TC-008 | 未执行 | Watch 刷新和并发 |
| AC-003 | TC-005 | 通过 | 失败关闭 |
| AC-004 | TC-006 | 未执行 | 默认实现回退 |
| AC-005 | TC-009 | 未执行 | 新增和下线服务后的路由刷新 |

## 7. 缺陷与汇总

| 缺陷编号 | 关联用例 | 严重级别 | 描述 | 状态 |
| --- | --- | --- | --- | --- |
| DEF-001 | TC-003 | 阻断 | 第一版负载均衡隔离上线后仍按 29～31 秒周期串服务；关闭缓存为 99/100 次异常，关闭 Blade 负载均衡为 90/90 次异常 | 已实现 Gateway 路由修复，待线上验证 |

| 指标 | 数量 |
| --- | ---: |
| 用例总数 | 9 |
| 通过 | 4 |
| 失败 | 0 |
| 阻塞 | 0 |
| 不适用 | 0 |
| 未执行 | 5 |

- 测试结论：待执行。
- 未完成项：TC-003、TC-004、TC-006、TC-008 和 TC-009 待真实 Nacos、Gateway 和 Feign 环境执行；TC-003 中用户确认的 404/双裁修复尚需补充状态码及访问日志证据。
- 遗留风险：关闭内置 Locator 后 Nacos 心跳可能不再装配；原有服务持续可用不能证明服务增删后的路由会自动刷新。
- 数据清理结果：不涉及业务数据。

## 8. 变更记录

| 日期 | 版本 | 变更内容 | 修改人 |
| --- | --- | --- | --- |
| 2026-09-21 | 0.1 | 创建测试计划与覆盖矩阵 | Codex |
| 2026-09-22 | 0.2 | 归档第一版线上失败与 A/B 证据，新增 Gateway 本地 Locator 的产物、字节码和运行时验证 | Codex |
| 2026-09-24 | 0.3 | 记录用户确认的路径修复，纠正 StripPrefix 静态记录，并增加服务增删后的刷新用例 | Codex |
