# OAuth2/OIDC 授权服务器运行说明

实现依据：[需求0.15](../doc/requirements/REQ-2026-010-production-authentication.md)、[设计0.14](../doc/design/DESIGN-REQ-2026-010-production-authentication.md)、[数据库0.10](../doc/database/DB-REQ-2026-010-production-authentication.md)、[测试0.15](../doc/test/TEST-REQ-2026-010-production-authentication.md)。

本期是单实例、固定外部RSA密钥、标准Bearer和BFF会话。旧blade-auth从默认Maven/镜像/Compose退役，旧源代码只用于历史检索；不支持逆向切回旧认证。没有在线轮换、多实例协调、全局退出、即时JWT黑名单或Redis会话应用层加密。

## 服务与入口

授权服务8100、BFF80、system8106、demo8200。授权服务/system通过Nacos Config/Discovery及共享API/Feign交互；BFF默认使用七类固定业务URL，关闭旧发现路由。正式访问统一经过同源HTTPS入口，issuer必须为不带路径和尾斜杠的根地址。

- 标准协议：发现、JWKS、authorize、token、revoke、introspect、userinfo。
- AUTH页面上下文：GET /ui/login-context、GET /ui/consent；POST /login、POST /oauth2/authorize只使用AUTH CSRF。
- BFF：GET /bff/me为subject/issuer/clientId/scope/到期时间摘要，GET /bff/csrf取独立BFF CSRF，POST /bff/logout清当前会话。
- 资料：经TokenRelay访问 /api/blade-system/user/info，包含roles编码，密码永不输出。
- 改密：POST /api/blade-system/user/update-password，JSON为oldPassword/newPassword/confirmPassword，不接受旧query密码契约。
- 公开注册：GET /api/blade-system/user/register/config、GET /api/blade-system/user/register/captcha、POST /api/blade-system/user/register；真实密码、一次性验证码、普通租户角色、成功后主动登录。POST需BFF CSRF。

AUTH_SESSION/BFF_SESSION与AUTH-XSRF-TOKEN/BFF-XSRF-TOKEN独立命名。JSON中的Token由框架掩码处理，使用返回的parameterName/headerName提交，不用原始Cookie替代；生产Cookie强制Secure、HttpOnly、SameSite=Lax。

BFF无登录401/40101；授权客户端丢失或刷新协议失败401/40102并清会话；下游401原样返回。后端不自动重放业务请求，前端403必须阻断。/bff/me、刷新、退出与失效共用会话串行管理器，刷新前重读Redis并即时保存；仅支持单实例队列。

## 凭据、客户端和配置

授权服务按profile导入blade.yaml、blade-{profile}.yaml、blade-authorization-server-{profile}.yaml（DEFAULT_GROUP）；外部本机配置可注入部署变量，全部凭据禁止提交。

| 配置 | 用途 |
| --- | --- |
| AUTHORIZATION_ISSUER / FRONTEND_BASE_URI / BFF_LOGIN_URI | 同源HTTPS根地址、前端与固定BFF发起入口 |
| AUTHORIZATION_DATASOURCE_URL / USERNAME / PASSWORD | 授权状态MySQL受控账号，不与业务管理账号混用 |
| AUTHORIZATION_REDIS_HOST / PORT / PASSWORD / DATABASE | Redis会话和原子限流，namespace隔离 |
| AUTHORIZATION_JWK_SET_LOCATION / ACTIVE_KEY_ID | 仓库外file:JWK及活动kid；启动检查内容和权限 |
| AUTHORIZATION_PAYLOAD_ENCRYPTION_KEY | AES-GCM payload外部加密密钥，至少32字节 |
| AUTHORIZATION_INTERNAL_CLIENT_SECRET | 固定bootstrap客户端密钥，用于system标准服务Token |
| AUTHORIZATION_CLIENT_ID / CLIENT_SECRET / REDIRECT_URI | BFF机密客户端及精确回调 |
| AUTHORIZATION_SERVICE_CLIENT_ID / SECRET / SCOPES | 各资源服务独立后台客户端与最小scope |
| AUTHORIZATION_TRUSTED_PROXY_PATTERN | 显式可信代理IP正则；AUTH/system native，BFF剥离不可信头 |
| AUTHORIZATION_COOKIE_SECURE | prod默认true；代码拒绝false，本地dev可false |

客户端管理只支持none/client_secret_basic和authorization_code/refresh_token/client_credentials。授权码强制S256 PKCE，公共客户端只能授权码且无密钥。BFF申请openid/profile/demo.read/demo.write/service.read，登记audiences应覆盖实际资源服务。显式resource/audience集合必须等于完整登记集合，否则invalid_target；不提供动态目标裁剪。

用户Access Token带user_id/tenant_id，service.read才带account/role_id/dept_id/roles，profile带姓名；ID Token不承载业务资料。所有新密码写入BCrypt，存量SHA-1只在真实密码成功校验后一次迁移，条件更新避免并发覆盖。

## 数据、指标与维护

授权payload采用AES-GCM，刷新摘要归属用于整族重用撤销；JWT离线校验仍有exp前有效边界。Redis凭据存储须独立配置ACL/TLS/TTL及备份保护，MySQL加密不代替Redis保护。

过期清理默认30m间隔、到期后1d保留、每表最多1000行，只处理授权和刷新摘要技术状态。启用时两表需要DELETE权限；可关闭blade.authorization.cleanup-enabled并由维护账号执行。同意与业务数据不自动删除。

readiness检查db/redis；metrics仅允许授权服务本机，采集登录结果、协议请求/拒绝/耗时、system依赖耗时、刷新重用、行锁查询耗时、清理数量/失败及连接池指标。告警、备份恢复与首次停流向前修复按[runbook](../script/authorization/RUNBOOK.md)执行。

## 构建与验收

JDK21：mvn --offline clean compile -DskipTests -pl blade-authorization-server,blade-service/blade-system,blade-gateway,blade-service/blade-demo -am。

本轮13模块编译及App/Ingress Compose静态解析成功，未打包、未启动服务、未运行测试套件或真实接口。历史4通过/0失败/20阻塞/1不适用/6未执行仅是旧运行证据，不能作为本次版本验收。用户执行测试文档全部必要用例，尤其Consent CSRF、改密/注册、同一会话并发刷新、ACL启动、同源代理与故障恢复。详见[实现归档](../doc/test/evidence/REQ-2026-010/2026-10-13-implementation-summary.md)。

启动排序修复：用户日志中的SAS过滤器未登记顺序，已改为框架预登记排序锚点并编译通过；待用户重启并完成TC-032。根因、Nacos连带关闭日志和验证边界见[修复归档](../doc/test/evidence/REQ-2026-010/2026-10-13-filter-order-fix.md)。
