# 需求文档索引

本文件是 SpringBlade 后端需求编号、版本、状态及关联设计文档的统一登记入口。历史版本由 Git 保存。

## 使用规则

- 需求编号使用 `REQ-YYYY-NNN`，同一年按登记顺序递增，编号分配后不得删除或复用。
- 文件名中的 `short-name` 使用简短英文 kebab-case。
- 需求、详细设计、数据库设计和测试文档使用同一需求编号与 `short-name`。
- 不涉及数据库时填写“不涉及”，不要创建空数据库设计。
- 状态只使用：待编写、待评审、已确认、设计中、开发中、已验收、已废弃。
- 文档版本从 `0.1` 开始，评审确认后为 `1.0`；状态或版本变化时同步更新本索引。

## 编号分配

| 年份 | 下一个编号 | 最后分配日期 | 维护人 |
| --- | --- | --- | --- |
| 2026 | `REQ-2026-008` | 2026-09-22 | Codex |

> 分配编号后立即将“下一个编号”加一。

## 需求索引

| 需求编号 | 需求名称 | 模块 | 版本 | 状态 | 需求文档 | 数据库设计 | 详细设计 | 测试文档 | 负责人 | 最后更新 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| [REQ-2026-001](requirements/REQ-2026-001-prompt-management.md) | 提示词管理 | AI 提示词管理、内部业务调用 | 0.3 | 开发中 | [需求文档 0.3](requirements/REQ-2026-001-prompt-management.md) | [数据库设计 0.5](database/DB-REQ-2026-001-prompt-management.md) | [详细设计 0.4](design/DESIGN-REQ-2026-001-prompt-management.md) | [测试文档 0.1](test/TEST-REQ-2026-001-prompt-management.md) | 待指定 | 2026-09-09 |
| [REQ-2026-002](requirements/REQ-2026-002-tag-category-management.md) | 标签分类管理 | 标签分类管理、租户共享基础数据 | 0.3 | 开发中 | [需求文档 0.3](requirements/REQ-2026-002-tag-category-management.md) | [数据库设计 0.3](database/DB-REQ-2026-002-tag-category-management.md) | [详细设计 0.3](design/DESIGN-REQ-2026-002-tag-category-management.md) | [测试文档 0.2](test/TEST-REQ-2026-002-tag-category-management.md) | 待指定 | 2026-09-10 |
| [REQ-2026-003](requirements/REQ-2026-003-user-registration.md) | 用户自助注册 | 认证授权、用户管理、Saber 登录 | 0.5 | 开发中 | [需求文档 0.5](requirements/REQ-2026-003-user-registration.md) | [数据库设计 0.4](database/DB-REQ-2026-003-user-registration.md) | [详细设计 0.5](design/DESIGN-REQ-2026-003-user-registration.md) | [测试文档 0.4](test/TEST-REQ-2026-003-user-registration.md) | 待指定 | 2026-09-15 |
| [REQ-2026-004](requirements/REQ-2026-004-image-prompt-reverse.md) | 图片提示词反推与标签化分析 | AI 分析、租户标签快照、提示词生成 | 0.4 | 开发中 | [需求文档 0.4](requirements/REQ-2026-004-image-prompt-reverse.md) | 不涉及 | [详细设计 0.2](design/DESIGN-REQ-2026-004-image-prompt-reverse.md) | [测试文档 0.2](test/TEST-REQ-2026-004-image-prompt-reverse.md) | 待指定 | 2026-09-17 |
| [REQ-2026-005](requirements/REQ-2026-005-prompt-scope-and-publishing.md) | 提示词类型、发布方式与数据权限增强 | AI 提示词管理、权限与租户隔离 | 0.6 | 开发中 | [需求文档 0.6](requirements/REQ-2026-005-prompt-scope-and-publishing.md) | [数据库设计 0.2](database/DB-REQ-2026-005-prompt-scope-and-publishing.md) | [详细设计 0.3](design/DESIGN-REQ-2026-005-prompt-scope-and-publishing.md) | [测试文档 0.2](test/TEST-REQ-2026-005-prompt-scope-and-publishing.md) | 用户 | 2026-09-16 |
| [REQ-2026-006](requirements/REQ-2026-006-compose-deployment.md) | Docker Compose 分层部署与 GitHub Actions 发布 | Docker 镜像、基础设施、应用部署、CI/CD | 0.4 | 开发中 | [需求文档 0.4](requirements/REQ-2026-006-compose-deployment.md) | 不涉及 | [详细设计 0.5](design/DESIGN-REQ-2026-006-compose-deployment.md) | [测试文档 0.5](test/TEST-REQ-2026-006-compose-deployment.md) | 用户 | 2026-09-18 |
| [REQ-2026-007](requirements/REQ-2026-007-loadbalancer-isolation.md) | 跨服务动态路由与负载均衡隔离 | Gateway 动态路由、Feign、公共负载均衡配置 | 0.2 | 开发中 | [需求文档 0.2](requirements/REQ-2026-007-loadbalancer-isolation.md) | 不涉及 | [详细设计 0.2](design/DESIGN-REQ-2026-007-loadbalancer-isolation.md) | [测试文档 0.2](test/TEST-REQ-2026-007-loadbalancer-isolation.md) | 用户 | 2026-09-22 |

## 状态说明

| 状态 | 进入条件 |
| --- | --- |
| 待编写 | 已分配编号，需求正文尚未完成 |
| 待评审 | 需求或设计初稿已完成，等待评审 |
| 已确认 | 需求范围和验收标准已经确认 |
| 设计中 | 正在编写或评审详细设计、数据库设计 |
| 开发中 | 已进入实现、编译或测试阶段 |
| 已验收 | 必要验收标准和回归项均通过 |
| 已废弃 | 需求不再实施，保留编号和原因 |
