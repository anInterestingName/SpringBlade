# 跨服务动态路由与负载均衡隔离需求文档

## 1. 文档信息

| 项目 | 内容 |
| --- | --- |
| 需求名称 | 跨服务动态路由与负载均衡隔离 |
| 需求编号 | REQ-2026-007 |
| 文档版本 | 0.3 |
| 所属模块 | Gateway、Feign、`blade-common` |
| 目标版本/迭代 | SpringBlade 5.0.1 开发阶段 |
| 文档状态 | 开发中 |
| 产品负责人 | 用户 |
| 技术负责人 | Codex |
| 创建日期 | 2026-09-21 |
| 最后更新日期 | 2026-09-24 |
| 关联事项 | [详细设计](../design/DESIGN-REQ-2026-007-loadbalancer-isolation.md)、[测试文档](../test/TEST-REQ-2026-007-loadbalancer-isolation.md)；数据库不涉及 |

## 2. 摘要与目标

### 2.1 摘要

三机部署验证发现 Gateway 的 `blade-auth`、`blade-system`、`blade-ai` 路由会同时指向同一个服务，并按约 29～31 秒周期整体切换；本机开发测试未复现，WSL 容器环境复现。第一版负载均衡 NamedContext 隔离与实例归属保护已部署，但关闭 LoadBalancer 缓存或关闭 Blade 自定义负载均衡后故障仍稳定复现。排查范围据此收敛到 Spring Cloud Gateway 内置反应式发现路由刷新链：它从服务名并发查询各服务实例，再从实例生成路由；Nacos 实例转换会将请求的 `serviceId` 写入转换后的实例，因此实例归属校验不足以定位实例列表来源。尚无证据证明 Nacos SDK 内部存在特定竞态。需求要求 Gateway 路由刷新只依据服务名生成 `lb://serviceId` 路由，不再读取实例列表，同时保留第一版负载均衡隔离作为公共防御措施。

### 2.2 需求目标

1. Gateway 动态路由刷新只读取 `ReactiveDiscoveryClient.getServices()`，不得并发查询各服务的 `getInstances()`。
2. 为每个服务生成独立的 `lb://serviceId` 路由，保留 `/{serviceId}/**` 匹配；服务名前缀由现有 Gateway 过滤器恰好移除一次。
3. 每个 `serviceId` 继续使用独立的负载均衡命名上下文和实例供应器。
4. 保留现有灰度版本、优先 IP 选择及实例归属失败关闭能力。

### 2.3 非目标

- 不修改 Nacos 实例数据、Nginx 路由、服务端口或业务 HTTP 接口。
- 不新增静态服务地址，不以绕过服务发现作为解决方案。
- 不修改服务端或 Feign 契约；本轮根因修复仅重新发布 Gateway。
- 不涉及数据库、租户、权限、审计或脱敏功能。

## 3. 用户故事与范围

| 故事编号 | 优先级 | 用户故事 | 典型场景 | 依赖 |
| --- | --- | --- | --- | --- |
| US-001 | Must | 作为平台调用方，我希望请求始终发送到目标服务，以避免跨服务误路由。 | Gateway 动态路由、Feign 服务调用 | Nacos 服务发现 |
| US-002 | Must | 作为运维人员，我希望发现实例归属不一致时直接失败并记录服务标识，以避免静默串服务。 | 注册刷新、并发首次访问、异常缓存 | 统一日志 |

### 3.1 范围内

- 禁用 Spring Cloud Gateway 内置反应式发现路由 Locator。
- 在 Gateway 内按服务名生成动态路由，不在路由刷新阶段查询实例。
- 排除会在根上下文创建共享负载均衡器的第三方自动配置。
- 在公共模块登记按 `serviceId` 创建的命名客户端配置。
- 复用 Blade 灰度选择规则，并增加实例归属校验。
- 覆盖 Gateway 和 Feign 多服务并发调用验证。

### 3.2 范围外

- Nacos、Spring Cloud、Blade Tool 的整体版本升级。
- Sentinel 日志目录和健康检查白名单异常，作为独立问题处理。

## 4. 业务流程

```mermaid
flowchart TD
    A[路由刷新事件] --> B[ReactiveDiscoveryClient.getServices]
    B --> C[逐个 serviceId 生成 RouteDefinition]
    C --> D[Path /serviceId/**]
    C --> E[现有 Gateway 过滤器移除一次前缀]
    C --> F[URI lb://serviceId]
    G([收到 Gateway 请求]) --> H[按 Path 命中路由]
    H --> I[LoadBalancer 按 serviceId 查询实例]
    I --> J{实例归属一致?}
    J -- 否 --> K[拒绝转发]
    J -- 是 --> L[转发至目标服务]
```

| 编号 | 触发条件 | 系统行为 | 对外结果 | 数据是否改变 |
| --- | --- | --- | --- | :---: |
| EX-001 | 服务列表为空或查询失败 | 不生成错误服务路由，不回退到其他服务 | 对应路由不可用 | 否 |
| EX-002 | 目标服务没有实例 | 不选择其他服务兜底 | 服务不可用 | 否 |
| EX-003 | 选中实例归属其他服务 | 记录期望与实际服务标识并拒绝转发 | 服务不可用 | 否 |

## 5. 功能需求与验收标准

### 5.1 REQ-001 Gateway 动态路由隔离

- 关联用户故事：`US-001`
- 优先级：Must
- 处理规则：Gateway 路由刷新只消费服务名，为每个非空且去重后的 `serviceId` 生成固定路由；实例查询延迟到请求转发阶段处理。

- `AC-001`：Given Nacos 注册 `blade-auth`、`blade-system` 与 `blade-ai`，When Gateway 刷新动态路由，Then 分别生成 `lb://blade-auth`、`lb://blade-system` 与 `lb://blade-ai`，且刷新阶段不调用任一服务的 `getInstances()`。
- `AC-002`：Given Nacos 定时心跳确实触发 Gateway 路由刷新，When 连续跨越至少三个约 30 秒周期交替请求三个服务，Then 每个请求只到达目标服务，不发生整体切换或交叉路由。
- `AC-005`：Given Gateway 已启动且动态路由已缓存，When Nacos 新注册一个服务并在之后下线，Then 不重启 Gateway 即可在预期刷新周期内分别新增、移除对应路由，且刷新阶段不查询各服务实例。

### 5.2 REQ-002 负载均衡实例隔离与失败关闭

- 关联用户故事：`US-002`
- 优先级：Must
- 处理规则：负载均衡器必须在目标服务的 NamedContext 内创建；选中实例必须再次校验 `ServiceInstance.serviceId`，不一致时返回空选择结果并记录期望、实际服务标识，不记录地址、凭据或请求体。

- `AC-003`：Given 负载均衡组件返回归属错误的实例，When 执行转发，Then 请求不得到达错误服务并返回服务不可用。
- `AC-004`：Given `blade.loadbalancer.enabled=false`，When Gateway 使用自定义动态路由并创建负载均衡上下文，Then 使用 Spring Cloud 默认负载均衡器，路由刷新仍不查询实例且应用可正常启动。

## 6. 业务规则

| 规则编号 | 规则 | 适用范围 | 违反时行为 |
| --- | --- | --- | --- |
| BR-001 | 路由刷新只能调用 `getServices()`，禁止按服务并发调用 `getInstances()` | Gateway | 路由实现不予发布 |
| BR-002 | 每个服务路由必须使用 `lb://serviceId` 和 `/{serviceId}/**`；现有过滤器仅移除一次服务名前缀 | Gateway | 路由或下游路径校验失败 |
| BR-003 | 实例 `serviceId` 必须等于请求目标 `serviceId`，忽略大小写 | Gateway、Feign | 拒绝转发 |
| BR-004 | 禁止无实例时回退到其他服务 | Gateway、Feign | 返回服务不可用 |
| BR-005 | 灰度版本和优先 IP 规则保持现有语义 | 已启用灰度的调用 | 按现有规则返回空结果 |
| BR-006 | 关闭内置 Discovery Locator 后仍须保留路由刷新事件 | Gateway | 不得以静态路由表的持续成功代替动态刷新验收 |

状态迁移不涉及。

## 7. 认证与访问边界

本需求不改变认证、租户、数据权限、API Scope 或资源归属规则。错误路由必须在认证与业务处理前被阻断；日志不得记录 Token、密钥、连接串或完整请求体。

## 8. 页面与交互要求

不涉及页面。调用成功响应保持原接口行为；无实例或归属校验失败沿用 Spring Cloud 服务不可用语义。

## 9. 质量要求与影响

| 类别 | 要求 | 验证标准 |
| --- | --- | --- |
| 可靠性 | 动态路由刷新与多服务并发调用不得串用目标服务 | 确认刷新事件实际发生，跨越至少三个周期连续请求无串服务，并验证服务增删 |
| 安全 | 错误实例必须失败关闭 | 错误服务无请求日志 |
| 兼容性 | 保留现有灰度和优先 IP 配置 | 原配置无需迁移 |

| 影响项 | 是否涉及 | 需求层说明 | 关联设计 |
| --- | :---: | --- | --- |
| 新增/修改数据实体 | 否 | 不涉及数据库 | 不涉及 |
| 新增/修改 API | 否 | HTTP 与 Feign 签名不变 | [详细设计](../design/DESIGN-REQ-2026-007-loadbalancer-isolation.md) |
| 配置或部署变化 | 是 | Gateway 关闭内置发现 Locator 并启用本地实现；公共防御配置保留 | [详细设计](../design/DESIGN-REQ-2026-007-loadbalancer-isolation.md) |
| 外部依赖 | 是 | 继续使用 Blade Tool 5.0.1 与 Spring Cloud 5.0.2 | [详细设计](../design/DESIGN-REQ-2026-007-loadbalancer-isolation.md) |

## 10. 验证记录与外部依据

下表区分实际观察、用户反馈和静态分析；未提供请求数量、状态码或日志证据的反馈不替代完整测试用例结果。

| 编号 | 来源 | 已知结果 | 结论边界 |
| --- | --- | --- | --- |
| V-001 | 三机/WSL 容器复查 | `blade-auth`、`blade-system`、`blade-ai` 请求约每 29～31 秒同时落到同一服务；本机开发未复现 | 证明故障与部署条件和路由刷新周期相关，不证明操作系统是根因 |
| V-002 | 第一版修复后的 A/B | 关闭 LoadBalancer 缓存时 99/100 次异常；关闭 Blade 自定义负载均衡时 90/90 次异常 | 两项开关均不能作为该故障的解决或回滚措施 |
| V-003 | 用户于 2026-09-23 确认 | `codex/req-2026-007-gateway-route-validation` 分支修复代码可用；`0285b37c` 删除重复裁剪后，404 与双重裁剪已消失 | 尚未归档完整 HTTP 状态码、请求数量、跨周期及服务增删证据，需求继续保持“开发中” |
| V-004 | 依赖字节码与仓库配置静态检查 | 当前 Nacos Discovery 心跳发布器可由 `spring.cloud.nacos.discovery.heart-beat.enabled=true` 或内置 Locator 开关启用；验证分支关闭了内置 Locator，未显式设置前一开关 | 定时刷新是否仍发生及新服务能否自动进入路由表，尚待运行时检查 |

公开资料用于佐证机制和选择验证方向，不等同于本项目或当前版本的同一缺陷已获官方确认：

| 资料 | 与本需求的关系 |
| --- | --- |
| [Spring Cloud Alibaba #1967](https://github.com/alibaba/spring-cloud-alibaba/issues/1967) | 记录 Nacos Watch 每 30 秒发布心跳、Gateway 随之刷新路由缓存；报告的是性能影响，不是跨服务串路由 |
| [Spring Cloud Alibaba #2868](https://github.com/alibaba/spring-cloud-alibaba/issues/2868) | 讨论动态路由只取服务名、避免周期性拉取全部实例，与本分支方案方向一致 |
| [Spring Cloud Alibaba PR #3308](https://github.com/alibaba/spring-cloud-alibaba/pull/3308) | 已合并的独立心跳开关设计，说明 `spring.cloud.nacos.discovery.heart-beat.enabled` 默认关闭；当前版本仍须核对实际装配和刷新事件 |
| [Spring Cloud Gateway #1514](https://github.com/spring-cloud/spring-cloud-gateway/issues/1514) | 较旧版本中 Gateway 启动后新服务不进入路由表的案例；提示必须单独验证服务增删，不可直接推定为本项目根因 |

待完成：确认心跳发布器与 `RefreshRoutesEvent` 实际运行；执行 `AC-002`、`AC-005` 的跨周期及服务增删测试；归档 Gateway 运行时路由与 Feign 结果。关联用例见[测试文档](../test/TEST-REQ-2026-007-loadbalancer-isolation.md)。

## 11. 风险、评审与变更

| 编号 | 类型 | 内容 | 状态/结论 |
| --- | --- | --- | --- |
| ITEM-001 | 风险 | 命名客户端配置若被根上下文扫描会再次引入共享状态 | 通过独立非组件配置类和构建检查控制 |
| ITEM-002 | 风险 | 仅编译不能替代真实 Nacos 并发验证 | 保持开发中，待真实环境执行 |
| ITEM-003 | 已确认问题 | 内置 `DiscoveryClientRouteDefinitionLocator` 在刷新阶段对服务执行并发 `getInstances()` | 禁用内置实现，以只读服务名的本地 Locator 替代 |
| ITEM-004 | 已排除项 | LoadBalancer 缓存或 Blade 自定义负载均衡导致串服务 | 两组线上 A/B 分别出现 99/100 与 90/90 次异常，均排除 |
| ITEM-005 | 待验证风险 | 关闭内置 Discovery Locator 可能同时停用 Nacos 心跳发布器，使动态路由在服务增删后不刷新 | 静态条件已确认；按 AC-005 进行运行时验证，必要时独立启用心跳 |

- [x] 目标、范围、异常流程与验收标准明确。
- [x] 数据库、API、认证、租户和配置影响已识别。
- [x] 每项 Must 需求均有可执行验收标准。
- [ ] 新 Gateway 镜像跨越至少三个 Watch 周期验收完成。
- [ ] 新服务注册与下线后的路由增删验收完成。
- [ ] 真实环境 Feign 验收完成。

| 日期 | 版本 | 变更内容 | 原因 | 影响范围 | 修改人 |
| --- | --- | --- | --- | --- | --- |
| 2026-09-21 | 0.1 | 初稿并进入开发 | 修复跨服务负载均衡实例串用 | Gateway、Feign、公共配置 | Codex |
| 2026-09-22 | 0.2 | 根据线上复查新增 Gateway 动态路由隔离要求，保留第一版公共防御 | 第一版修复已部署但故障仍按 Nacos Watch 周期复现 | Gateway、公共配置与验收范围 | Codex |
| 2026-09-24 | 0.3 | 记录用户确认的 404/双裁修复、公开案例及动态刷新待验证项 | 核对验证分支与 Nacos 心跳装配影响 | 需求、设计、测试和索引 | Codex |
