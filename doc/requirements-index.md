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
| 2026 | `REQ-2026-012` | 2026-10-21 | Codex |

> 分配编号后立即将“下一个编号”加一。

## 需求索引

| 需求编号 | 需求名称 | 模块 | 版本 | 状态 | 需求文档 | 数据库设计 | 详细设计 | 测试文档 | 负责人 | 最后更新 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| [REQ-2026-001](requirements/REQ-2026-001-prompt-management.md) | 提示词管理 | AI 提示词管理、内部业务调用 | 0.3 | 开发中 | [需求文档 0.3](requirements/REQ-2026-001-prompt-management.md) | [数据库设计 0.5](database/DB-REQ-2026-001-prompt-management.md) | [详细设计 0.4](design/DESIGN-REQ-2026-001-prompt-management.md) | [测试文档 0.1](test/TEST-REQ-2026-001-prompt-management.md) | 待指定 | 2026-09-09 |
| [REQ-2026-002](requirements/REQ-2026-002-tag-category-management.md) | 标签分类管理 | 标签分类管理、租户共享基础数据 | 0.3 | 开发中 | [需求文档 0.3](requirements/REQ-2026-002-tag-category-management.md) | [数据库设计 0.3](database/DB-REQ-2026-002-tag-category-management.md) | [详细设计 0.3](design/DESIGN-REQ-2026-002-tag-category-management.md) | [测试文档 0.2](test/TEST-REQ-2026-002-tag-category-management.md) | 待指定 | 2026-09-10 |
| [REQ-2026-003](requirements/REQ-2026-003-user-registration.md) | 用户自助注册 | 用户管理、网关、Saber 登录 | 0.9 | 开发中 | [需求文档 0.9](requirements/REQ-2026-003-user-registration.md) | [数据库设计 0.6](database/DB-REQ-2026-003-user-registration.md) | [详细设计 0.9](design/DESIGN-REQ-2026-003-user-registration.md) | [测试文档 0.8](test/TEST-REQ-2026-003-user-registration.md) | 待指定 | 2026-10-13 |
| [REQ-2026-004](requirements/REQ-2026-004-image-prompt-reverse.md) | 图片提示词反推与标签化分析 | AI 分析、租户标签快照、提示词生成 | 0.4 | 开发中 | [需求文档 0.4](requirements/REQ-2026-004-image-prompt-reverse.md) | 不涉及 | [详细设计 0.2](design/DESIGN-REQ-2026-004-image-prompt-reverse.md) | [测试文档 0.2](test/TEST-REQ-2026-004-image-prompt-reverse.md) | 待指定 | 2026-09-17 |
| [REQ-2026-005](requirements/REQ-2026-005-prompt-scope-and-publishing.md) | 提示词类型、发布方式与数据权限增强 | AI 提示词管理、权限与租户隔离 | 0.6 | 开发中 | [需求文档 0.6](requirements/REQ-2026-005-prompt-scope-and-publishing.md) | [数据库设计 0.2](database/DB-REQ-2026-005-prompt-scope-and-publishing.md) | [详细设计 0.3](design/DESIGN-REQ-2026-005-prompt-scope-and-publishing.md) | [测试文档 0.2](test/TEST-REQ-2026-005-prompt-scope-and-publishing.md) | 用户 | 2026-09-16 |
| REQ-2026-006 | 旧版部署文档撤回；编号不复用，部署实现仍保留 | 部署文档 | 0.4（历史） | 已废弃 | 已移除 | 不涉及 | 已移除 | 已移除 | 用户 | 2026-09-29 |
| [REQ-2026-007](requirements/REQ-2026-007-deployment-standardization.md) | 前后端容器部署规范化 | Docker Compose、部署教程、Saber 协同 | 0.5 | 开发中 | [需求文档 0.5](requirements/REQ-2026-007-deployment-standardization.md) | 不涉及 | [详细设计 0.5](design/DESIGN-REQ-2026-007-deployment-standardization.md) | [测试文档 0.5](test/TEST-REQ-2026-007-deployment-standardization.md) | 用户 | 2026-10-13 |
| [REQ-2026-008](requirements/REQ-2026-008-ai-model-configuration.md) | 大模型供应商与模型配置管理 | blade-ai、blade-ai-api、Saber 协同 | 0.9 | 开发中 | [需求文档 0.9](requirements/REQ-2026-008-ai-model-configuration.md) | [数据库设计 0.4](database/DB-REQ-2026-008-ai-model-configuration.md) | [详细设计 0.4](design/DESIGN-REQ-2026-008-ai-model-configuration.md) | [测试文档 0.3](test/TEST-REQ-2026-008-ai-model-configuration.md) | 待指定 | 2026-09-30 |
| REQ-2026-009 | BFF 最小化验证与前端认证页面 | blade-gateway、blade-authorization-server、blade-demo、Saber | 0.8 | 开发中 | 延续既有不编写约定，依据本次会话确认方案；[前端说明](../../Saber/doc/authorization-poc.md) | 本项认证时间修复不涉及结构变更；既有持久化脚本见[实际方案](../blade-authorization-server/README.md) | 延续既有不编写约定；[实际方案](../blade-authorization-server/README.md) | [测试文档 0.8](test/TEST-REQ-2026-009-bff-validation.md) | 待指定 | 2026-10-09 |
| [REQ-2026-010](requirements/REQ-2026-010-production-authentication.md) | 生产级 OAuth2/OIDC 认证中心与 BFF 正式化 | blade-authorization-server、blade-gateway、blade-system、资源服务、Feign、Saber 协同 | 0.15 | 开发中 | [需求文档 0.15](requirements/REQ-2026-010-production-authentication.md) | [数据库设计 0.10](database/DB-REQ-2026-010-production-authentication.md) | [详细设计 0.14](design/DESIGN-REQ-2026-010-production-authentication.md) | [测试文档 0.15](test/TEST-REQ-2026-010-production-authentication.md) | 待指定 | 2026-10-13 |
| [REQ-2026-011](requirements/REQ-2026-011-single-tenant-permissions.md) | 默认租户运行与业务权限统一 | 认证中心、网关、system、资源服务、Feign、Saber 协同 | 1.0 | 已确认 | [需求文档 1.0](requirements/REQ-2026-011-single-tenant-permissions.md) | 待设计评估；默认租户业务数据无需合并，权限元数据、服务角色及客户端与服务账号绑定影响待确定 | 待编写 | 待编写 | 用户 | 2026-10-21 |

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
