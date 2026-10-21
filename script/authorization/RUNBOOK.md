# 新认证发布、维护与向前修复

依据：[REQ-2026-010](../../doc/requirements/REQ-2026-010-production-authentication.md)、[设计](../../doc/design/DESIGN-REQ-2026-010-production-authentication.md)、[数据库](../../doc/database/DB-REQ-2026-010-production-authentication.md)、[测试](../../doc/test/TEST-REQ-2026-010-production-authentication.md)。本清单由用户/运维在隔离环境演练后执行；不恢复旧blade-auth，不自动删除未知数据。生产负责人、RTO/RPO、容量和告警阈值待指定。

## 首次发布

1. 关闭公开入口流量和注册开关；保存用户密码、客户端、授权/刷新状态、外部JWK/payload密钥及配置快照。备份须按原有受控方案加密和限制权限；不把密码、Token、私钥、连接串写入普通证据。
2. 在存量库按数据库设计执行对应升级，空库使用全量脚本；核对刷新归属表、用户密码列和账号唯一索引。应用账号启用定时清理时仅额外授予两张协议状态表DELETE，不授予业务表删除。若关闭作业，维护账号按同等expires_at规则定期清理。
3. 配置各环境Nacos Data ID及独立服务客户端；system先发布API和密码/客户端/注册实现。注册默认关闭，普通角色、租户和唯一性检查满足后再开启。
4. 在仓库外创建JWK，所有者为容器运行UID10001且仅所有者可读（例如0400）；Windows仅运行账号/所有者及受控SYSTEM/Administrators授权。权限、kid、算法或长度错误必须拒绝启动。容器只读挂载/run/secrets/authorization-jwks.json；不要拷入镜像。
5. 配置统一同源HTTPS根issuer、BFF回调/login/oauth2/code/blade、前端地址、/oauth2/authorization/blade、TLS证书与可达Saber上游。按实际入口IP显式配置可信代理正则，不使用全网通配。prod Secure=true；拒绝路径型issuer、非HTTPS或跨源地址。
6. 用仓库App/Ingress .env.example在仓库外准备真实.env，权限受控。执行docker compose config --quiet核对插值；不输出完整resolved配置。App镜像CI只发布新授权服务；gateway默认仅127.0.0.1宿主端口供探活，业务经HTTPS入口。BFF、AUTH和system对外端口应按网络边界隔离，system注册仅通过入口访问。
7. 先发布system、授权服务及资源服务，再发布BFF、代理和配套Saber版本。逐一在内部检查/actuator/health/readiness，授权服务应UP且包含db/redis。通过发现/JWKS公钥检查后核对各服务issuer/audience/scope与内部拒绝。
8. 在开放流量前执行测试文档的全部P0及必要回归：真实登录/同意/回调、JSON改密与BCrypt、资料/roles/菜单、注册、写CSRF、退出、Bearer、内部拒绝、同会话并发刷新和故障恢复。前端尚未适配时不得凭后端编译上线。
9. 全部必要验收通过后开启新入口；确认旧auth无进程、容器或发现路由入口。网关明确拒绝/blade-auth，Ingress /blade-auth/返回410。登记版本、时间、审批与无敏感数据的结果。

## 密钥更换与恢复

- 本期文件替换后重启授权服务。新集合包含新私钥和仍有效的旧公钥，保留旧公钥直到旧JWT过期；新kid在所有配置一致后生效。不能将JWK轮换当作payload加密密钥轮换。
- 替换前保留受控快照、停止新令牌流量，检查权限与内容。启动失败保持停流，修复文件/配置并重新启动。验证新签发kid与旧JWT可验签后恢复流量。
- MySQL恢复必须使用匹配原payload的外部加密密钥。Redis恢复按namespace/TTL与配置保护；状态不确定时安全要求重新登录，不绕过state、PKCE或刷新族校验。
- 本期单实例；BFF串行队列不协调多实例。需要多实例时重新设计并独立验收，不能直接扩容宣称无刷新竞争。

## 状态清理与观测

- cleanup-enabled=true、cleanup-interval=30m、cleanup-retention=1d、cleanup-batch-size=1000；可按容量调整，不删除未到期授权/刷新摘要、同意或业务数据。
- 维护账号可先SELECT统计expires_at早于受控截止时间的数量，再在维护窗口逐批执行同条件DELETE并核对数量。不得清空授权表、清空Redis或删除共享租户、未知用户。
- metrics仅允许授权服务本机，可在容器内通过GET /actuator/metrics读取。部署采集应使用受控本机转发或agent，外部入口不代理actuator。
- 告警至少覆盖：readiness异常、协议5xx/拒绝率、登录失败/依赖失败、system依赖延迟、刷新重用、行锁查询延迟、连接池耗尽、清理失败/积压及签名kid变更。环境阈值须经容量演练指定；指标标签只含固定端点、状态、分类和表名。
- 归档故障和恢复耗时、状态数量、精确测试数据清理与证据，密码/Token/私钥/连接串不进入证据。

## 已切流故障与向前修复

1. 在入口停流或维护页阻断认证业务写入，关闭注册；不恢复旧auth或旧网关。
2. 保留当前数据库、Redis namespace、密钥和脱离敏感值的故障摘要。不得恢复切流前快照覆盖已发生的新业务写入。
3. 在隔离副本验证兼容新密码、现有状态表及新Cookie/Token契约的修复，按system/API、SAS、资源服务、BFF/代理、Saber顺序发布。数据恢复如确有必要，按独立维护方案批准。
4. 受影响会话要求重新登录；执行发现、登录/同意、资料/权限、业务读写、注册、退出及内部拒绝用例。readiness/指标/数据一致性及全部必要P0通过后开放流量；否则继续安全停流。
5. 按TC-031记录实际步骤、版本、耗时、数据完整性和清理。部署演练待用户执行，静态Compose解析不等于发布成功。
