# 跨服务负载均衡实例隔离测试文档

## 1. 文档信息

| 项目 | 内容 |
| --- | --- |
| 测试编号 | TEST-REQ-2026-007 |
| 关联需求 | [REQ-2026-007](../requirements/REQ-2026-007-loadbalancer-isolation.md) |
| 关联详细设计 | [DESIGN-REQ-2026-007](../design/DESIGN-REQ-2026-007-loadbalancer-isolation.md) |
| 关联数据库设计 | 不涉及 |
| 文档版本 | 0.1 |
| 文档状态 | 执行中 |
| 测试负责人 | 用户 / Codex |
| 测试日期 | 2026-09-21 至 2026-09-22 |

## 2. 测试范围与依据

- 测试目标：验证 Gateway 与 Feign 按 `serviceId` 隔离实例供应器，异常实例失败关闭，现有灰度配置兼容。
- 范围内：公共模块编译与打包、自动配置资源、Gateway 动态路由、Feign 多服务调用、配置开关、实例刷新与并发。
- 范围外：业务接口功能、数据库、租户、权限、Sentinel 日志目录和健康检查白名单。
- 验收依据：`AC-001` 至 `AC-004`、`BR-001` 至 `BR-003`。

## 3. 测试环境与准备

| 项目 | 内容 |
| --- | --- |
| 后端版本/提交 | 待本次提交后填写 |
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
- 实际结果：使用 JDK 21 执行 `mvn clean package -DskipTests -pl blade-gateway,blade-auth,blade-service/blade-system -am`，11 个 Reactor 模块全部 BUILD SUCCESS。
- 结果：通过
- 证据/缺陷：构建日志；首次使用 JDK 17 执行时因不支持 release 21 失败，切换项目要求的 JDK 21 后通过，不属于代码缺陷。

### TC-002 自动配置产物完整性

- 优先级：P0
- 关联需求/验收标准：REQ-001 / AC-001
- 测试层级：静态检查
- 前置条件：`blade-common` 已打包。
- 步骤：检查 JAR 中的 AutoConfiguration imports、`spring.factories` 和四个负载均衡实现类；检查依赖树只有一套 LoadBalancer/Nacos 版本。
- 预期结果：过滤器与本地自动配置均进入产物，第三方依赖保留且无版本分裂。
- 清理：不涉及。
- 实际结果：`blade-common.jar` 包含四个实现类、AutoConfiguration imports 与 ImportFilter 注册；依赖树保持 `blade-starter-loadbalancer 5.0.1`、Spring Cloud 5.0.2、Nacos Client 3.2.2 单一版本。
- 结果：通过
- 证据/缺陷：JAR 清单、`spring.factories`、AutoConfiguration imports 和 Maven dependency tree。

### TC-003 Gateway 多服务连续与并发路由

- 优先级：P0
- 关联需求/验收标准：REQ-001 / AC-001、AC-002
- 测试层级：Gateway/Nacos 集成
- 前置条件：auth 和 system 分别注册唯一端口，Gateway 使用本次镜像。
- 输入数据：`GET /blade-system/tenant/info` 与 `GET /blade-auth/captcha`。
- 步骤：先交替连续请求不少于 100 轮，再并发请求不少于 1000 次；期间触发一次 Nacos 实例列表刷新。
- 预期结果：两个接口持续同时成功；auth 日志无 `/tenant/info`，system 日志无 `/captcha`；刷新前后无整体切换。
- 清理：不涉及。
- 实际结果：待执行
- 结果：未执行
- 证据/缺陷：无

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
- 步骤：启动 Gateway，检查 LoadBalancer Bean 类型并调用 auth/system。
- 预期结果：使用 Spring Cloud 默认负载均衡器；应用启动成功；两个接口路由正确。
- 清理：恢复测试配置。
- 实际结果：待执行
- 结果：未执行
- 证据/缺陷：无

## 5. 专项检查

- [ ] 多 `serviceId` 首次并发创建 NamedContext。
- [ ] Nacos 实例新增、下线与缓存刷新。
- [ ] 灰度版本头存在、不存在和无匹配实例。
- [ ] 优先 IP 匹配与无匹配回退。
- [ ] 错误实例失败关闭且不泄露地址、Token 或请求体。
- [ ] 关闭自定义负载均衡后默认实现可用。

## 6. 验收覆盖矩阵

| 验收标准 | 测试用例 | 结果 | 说明 |
| --- | --- | --- | --- |
| AC-001 | TC-001、TC-002、TC-003、TC-004 | 未执行 | 构建、Gateway、Feign |
| AC-002 | TC-003、TC-004 | 未执行 | 实例刷新和并发 |
| AC-003 | TC-005 | 通过 | 失败关闭 |
| AC-004 | TC-006 | 未执行 | 默认实现回退 |

## 7. 缺陷与汇总

| 缺陷编号 | 关联用例 | 严重级别 | 描述 | 状态 |
| --- | --- | --- | --- | --- |
| - | - | - | 暂无 | - |

| 指标 | 数量 |
| --- | ---: |
| 用例总数 | 6 |
| 通过 | 3 |
| 失败 | 0 |
| 阻塞 | 0 |
| 不适用 | 0 |
| 未执行 | 3 |

- 测试结论：待执行。
- 未完成项：TC-003、TC-004 和 TC-006 待真实 Nacos、Gateway 和 Feign 环境执行。
- 遗留风险：真实 Nacos 刷新、Gateway 并发和 Feign 多服务调用尚未验收。
- 数据清理结果：不涉及业务数据。

## 8. 变更记录

| 日期 | 版本 | 变更内容 | 修改人 |
| --- | --- | --- | --- |
| 2026-09-21 | 0.1 | 创建测试计划与覆盖矩阵 | Codex |
