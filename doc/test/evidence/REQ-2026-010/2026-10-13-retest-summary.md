# REQ-2026-010 2026-10-13 重启前实际重跑与修复归档

> 本文保留重启前历史结果。用户重启全部服务后的最新结果为 4 通过、0 失败、21 阻塞，原两项运行缺陷已实测关闭；当前证据见[重启后归档](2026-10-13-after-restart-summary.md)。

- 关联：[需求 0.9](../../../requirements/REQ-2026-010-production-authentication.md)、[设计 0.8](../../../design/DESIGN-REQ-2026-010-production-authentication.md)、[数据库 0.6](../../../database/DB-REQ-2026-010-production-authentication.md)、[测试 0.9](../../../test/TEST-REQ-2026-010-production-authentication.md)。
- 执行由用户本轮明确授权覆盖仓库原“测试由用户执行/不访问真实接口”限制；本轮访问用户已启动的本地目标，未启动或重启服务、Nacos、MySQL、Redis。
- 后端工作区 HEAD：8d4b0eb2，叠加用户未提交修改和本轮修复；JDK 21 / Maven 3.9.6，PowerShell 7.6.6。
- 原套件实际重跑：[25 PASS / 退出码 0](2026-10-13-baseline-suite.log)。用户原执行记录成立；本轮没有把它当作未测试。
- 修正套件执行 UTC 时间：2026-10-13T04:52:06.7109856Z；本轮前缀 `auth-rq10-20261013045057-`。重启前完整用例结果：4 通过、2 失败、19 阻塞、0 不适用、0 未执行，退出码 1。
- 证据：[最终逐项 JSON](2026-10-13-final-suite-results.json)、[最终日志](2026-10-13-final-suite.log)、[目标 15 模块编译](2026-10-13-final-build.log)。日志和 JSON 不包含原始 Token、密码、密钥或连接串。

## 实际修复和已验证断言

| 问题 | 修复 | 实际验证 |
| --- | --- | --- |
| 套件误判 | 原条件缺失不再 PASS；独立病例、有效 CSRF、真实 Token/会话、非零退出码与逐项 JSON | 原 25 PASS 保留，修正后完整用例不伪装通过 |
| 资源用户上下文为空 | Servlet 自动注册禁用，未认证时不占用 OncePerRequest 标记，安全链 Bearer 后桥接；用户 ID 与 tenant 明确 | /user/info 从 500 恢复 200；JWT 基础权限字段、菜单读取得到证据 |
| 刷新整族未撤销 | 新刷新摘要归属表，授权行锁与同客户端兑换校验，旧值重用将授权状态置 0 | 旧值 invalid_grant，后继也被拒绝；并发 code 与独立 refresh 各 4 个请求严格 1 成功、3 invalid_grant |
| BFF 原始 URL 缺失 | 早期全局过滤器使用当前原始 URI 回退，保持内部路径归一化 | /bff/messages 从 500 恢复 200；已登录内部接口 403，未知路径 404 |
| identity 泛型重载转换 | 使用 getClaimAsString 读取 tenant_id，避免推断为 char[] | /bff/identity 从 500 恢复 200，真实 TokenRelay/伪造头断言通过 |
| 协议基本路径 | 实际 code、PKCE、state、UserInfo、client_credentials、scope/audience、撤销内省 | 相关 HTTP 200/400/401/403 已执行，详见 JSON |
| 密码与最低 scope | 新真实密码入口与旧摘要兼容分离；业务最低 service.read；角色/部门字段完整 | 有效 CSRF 的错误密码/越租户拒绝，正常登录通过；迁移/停用夹具未完整 |
| Feign 兼容 | scope 一次登记；传播已验证 JWT；命名条件 + Primary 避免旧拦截器阻止新拦截器；GET 只读白名单及明确降级异常 | 编译通过；当前进程的数据权限 GET 仍 403，重新初始化安全链和后台探针待验证 |

## 数据库和配置动作

- 本地固定 gateway-bff 先保存[scope/audience 前后记录](2026-10-13-local-client-scope-audience.properties)，再只更新这两个字段。密钥、身份标识、TTL 等没有修改；其他客户端没有修改。
- 执行正式升级脚本中新刷新表的具体 CREATE TABLE IF NOT EXISTS 和当前摘要回填；首轮回填 10，重复准备回填 0。未运行整个旧客户端停用/结构迁移。
- 应用账号只补充新表 SELECT、INSERT，已验证该账号可读；全量/升级中的新表 DDL 静态一致。完整空库/存量升级/回滚仍待执行。
- 新客户端种子改为 NULL 密钥/停用，发布由管理员注入外部密钥。网关移除固定凭据默认值并修正服务端口。
- 回滚保留新表和状态，先撤回依赖新表的代码；本地客户端 only-before/after 配置记录不含密钥，恢复前需防止覆盖后续管理员更新。

## 重启前未关闭项（历史）

1. TC-003：客户端管理仍失败；已定位 DataScopeCache 的旧 Feign 失败路径。实际数据权限内部 GET 仍返回 403，源码的白名单、主拦截器和失败语义已调整，但当前一次性安全链需要重新初始化，不能宣称运行修复完成。
2. TC-012：带有效 CSRF 的 BFF 退出仍 500，异常分类为 Redis Session was invalidated。已将失效移动到框架上下文/CSRF 清理之后；当前运行进程是否使用新安全链仍需重新初始化并复测。
3. TC-011、其他阻塞项仍保留：完整客户端 CRUD/迁移/停用/跨租户、不同用户会话、错误 issuer/时间、外部密钥错误启动、故障恢复、容量、全服务/后台 Feign、监控及发布回滚未完整执行。
4. 本轮未运行 mvn test，未执行 clean package；编译不替代验收。需求仍开发中。

## 清理

- 最终登记 17 个 Token，撤销及内省失败 0；使用各自所属客户端，历史指纹记录不能直接用作 Token。
- 最终 8 个本轮会话、16 个确定 UUID 键已确认不存在，失败 0。仅处理本轮 Cookie 所属固定命名空间；不扫描、清空或删除其他会话。到期索引由既有 TTL 机制处理。
- 退出 API 失败仍记为缺陷，精确会话清理成功不替代退出业务通过。
- 临时诊断代码已全部移除。[异常类别取证](runtime-diagnostics.log) 仅含上下文存在与否、异常类别和代码栈位置，没有请求体或凭据。
