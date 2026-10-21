# REQ-2026-010 2026-10-13 重启后复测归档

- 关联：[需求 0.10](../../../requirements/REQ-2026-010-production-authentication.md)、[设计 0.9](../../../design/DESIGN-REQ-2026-010-production-authentication.md)、[数据库 0.7](../../../database/DB-REQ-2026-010-production-authentication.md)、[测试 0.10](../../../test/TEST-REQ-2026-010-production-authentication.md)。
- 用户确认已经重启全部服务并授权复测；AI 本轮没有启动或重启服务与基础设施。
- 入口：script/authorization/test-suite-req-2026-010.ps1，携带 RunMavenBuild；JDK 21、Maven 3.9.6、PowerShell 7.6.6，后端 HEAD 8d4b0eb2 加工作区修改。
- 完成时间：2026-10-13T06:35:37.8763492Z（北京时间 14:35:37）；本轮唯一前缀 auth-rq10-20261013063454-。
- 实际访问用户既有本地授权服务、网关/BFF、system、demo，以及它们所用 MySQL/Redis；完整独立夹具尚未全部准备。
- 结果：25 项中 4 通过、0 失败、21 阻塞、0 不适用、0 未执行，退出码 2。阻塞表示完整验收仍缺步骤/夹具，并非已执行断言失败。
- 证据：[逐项 JSON](2026-10-13-after-restart-suite-results.json)、[完整日志](2026-10-13-after-restart-suite.log)。原 25 PASS 与重启前 4/2/19 均保留，见[历史归档](2026-10-13-retest-summary.md)。

## 缺陷复测

| 缺陷 | 本轮实际结果 | 状态与完整验收边界 |
| --- | --- | --- |
| AUTH-010-RETEST-001 客户端管理 500 | 管理员 /client/list HTTP 200，业务 success=true | 已关闭；TC-003 仍待完整 CRUD、非法配置、重复键及数据库副作用夹具 |
| AUTH-010-RETEST-002 BFF 退出 500 | 缺失 CSRF=403；有效 CSRF 退出=200；退出后 /bff/me=401；重复退出验证成功；同用户另一会话仍 200 | 已关闭；TC-011 不同用户、TC-012 拒绝同意/过期回调恢复仍待执行 |

本轮真实授权码/state/S256 PKCE、UserInfo、BFF 回调、服务 Token 的 scope/audience、Refresh Token 轮换及重用后后继拒绝、撤销内省再次通过。真实 code 和独立 refresh 各四个同时请求，均严格一个 200、三个 invalid_grant。代表性旧网关路由、内部端点 403、未知路由 404、TokenRelay、伪造头拒绝、BladeUser 与角色菜单也取得本轮证据。

目标 15 模块离线编译再次通过。TC-001 仍缺 clean package；编译不替代业务、数据库、权限或故障验收。套件报告中的 TC-014“其他未启动服务”没有启动检查依据，已修正脚本文字为“其他资源服务的路由及全站回归待执行”，原输出作为真实历史不改写。

## 剩余验收与清理

- 21 个阻塞用例逐项理由保留在主测试文档和 JSON，主要缺完整客户端 CRUD、旧密码迁移/停用/跨租户与不同用户、有效签名的错误 issuer/时间、隔离启动/故障恢复、全服务与后台 Feign、完整落盘/指标、容量及发布回滚。
- 本次未重复执行客户端配置同步或数据库迁移，未删除未知归属授权/会话，未切换部署版本；不涉及审计、脱敏建设。
- 17 个本轮登记 Token 已按所属客户端撤销并内省确认，失败 0；证据仅保存 SHA-256 指纹。
- 9 个本轮 Cookie 归属会话、18 个固定 namespace/UUID 精确键确认不存在，失败 0，不扫描或清空其他会话。
- 需求、设计、数据库验证记录、测试和索引已同步；必要验收尚有阻塞，保持开发中。
