# SpringBlade 三服务器 Compose 部署文档

## 1. 文档状态

本文档基于 2026-09-18 的三机评估记录生成，当前只生成部署文件和操作说明，不连接服务器、不拉取镜像、不启动或停止容器。真实部署前必须重新确认服务器规格、当前端口占用、云安全组和现有中间件状态。

本方案面向测试环境，采用公网地址互通和云安全组白名单，不要求额外建设 WireGuard/Tailscale。公网传输没有私网加密能力，禁止使用生产凭据和生产数据。

## 2. 服务分配

| 服务器 | 记录中的资源 | 部署服务 | 目录 |
| --- | --- | --- | --- |
| 上海 | 4C / 3.6 GiB，可用约 1.0 GiB；已有 MySQL、Redis、Nacos、Sentinel、Saber | `gateway`、Nginx入口及现有中间件 | `/opt/springblade/app/gateway`、`/opt/springblade/ingress` |
| 东京 | 2C / 1.9 GiB，可用约 1.4 GiB；记录中未安装 Docker/Java | `auth`、`system` | `/opt/springblade/app/auth`、`/opt/springblade/app/system` |
| 硅谷 | 2C / 3.6 GiB，可用约 2.6 GiB；已有 Sub2API、PostgreSQL、Redis、Caddy、Saber | `ai`、`admin`、`log` | `/opt/springblade/app/ai`、`/opt/springblade/app/admin`、`/opt/springblade/app/log` |

这是当前资源条件下的最低风险分配，不是高可用方案。东京 2 GiB 同时运行 `auth + system` 余量较小，正式部署前应确认是否升级到至少 4 GiB或准备可接受的 swap 和降载策略。

## 3. JVM 与容器资源

| 服务 | 容器端口 | `Xms` | `Xmx` | 容器内存上限 | CPU |
| --- | ---: | ---: | ---: | ---: | ---: |
| `gateway` | 80 | 128m | 256m | 640m | 1.0 |
| `auth` | 8100 | 128m | 256m | 512m | 0.75 |
| `system` | 8106 | 192m | 384m | 768m | 1.0 |
| `ai` | 8107 | 256m | 512m | 896m | 1.0 |
| `admin` | 7002 | 128m | 256m | 512m | 0.5 |
| `log` | 8103 | 128m | 256m | 512m | 0.5 |

所有 Compose 模板默认使用 G1、`MaxGCPauseMillis=200`、`Xss512k`、`MaxMetaspaceSize=160m`、`ReservedCodeCacheSize=96m` 和 `ExitOnOutOfMemoryError`。Gateway 使用 128m Direct Memory，AI 使用 128m，其余服务使用 64m。JVM 参数可通过目标服务 `.env` 中的 `JAVA_TOOL_OPTIONS` 覆盖，但必须同步调整 `mem_limit`，不能只扩大堆。

## 4. 跨主机通信模型

### 4.1 原有单机网络不跨服务器

`springblade-backend` 和 `springblade-ingress` 是 Docker 本机 bridge 网络，不能让东京或硅谷容器解析上海的 `springblade-nacos`、`springblade-redis` 等别名。因此六个应用 Compose 不依赖跨服务器 external network：

- 上海 Gateway 与上海 Nginx 继续共用本机 `springblade-ingress`，Nginx 通过 `blade-gateway:80` 转发。
- 东京、硅谷应用通过上海公网地址访问 Nacos、MySQL、Redis、Sentinel。
- 各应用使用 `SPRING_CLOUD_NACOS_DISCOVERY_IP` 和 `SPRING_CLOUD_NACOS_DISCOVERY_PORT` 向 Nacos 注册宿主机公网地址和映射端口，不能注册容器的 `172.x` 地址。
- 服务间 Feign 调用通过 Nacos 返回的公网地址和宿主机端口完成。

### 4.2 上海中间件前置条件

现有中间件 Compose 默认绑定 `127.0.0.1`，远端应用启动前必须在上海服务器的真实 `.env` 中改为可被白名单访问的地址。建议优先绑定上海服务器实际公网网卡地址；如果云主机网络要求绑定 `0.0.0.0`，必须同时收紧云安全组：

```dotenv
# /opt/springblade/middleware/mysql/.env
MYSQL_BIND_ADDRESS=<SHANGHAI_PUBLIC_IP>

# /opt/springblade/middleware/redis/.env
REDIS_BIND_ADDRESS=<SHANGHAI_PUBLIC_IP>

# /opt/springblade/middleware/nacos/.env
NACOS_BIND_ADDRESS=<SHANGHAI_PUBLIC_IP>

# /opt/springblade/middleware/sentinel/.env
SENTINEL_BIND_ADDRESS=<SHANGHAI_PUBLIC_IP>
```

Nacos 客户端需要同时访问 `8848` 和 `9848`。单节点测试不需要向业务服务器开放 `7848`、`9849`。MySQL、Redis、Nacos 和 Sentinel 不得对全网开放。

### 4.3 测试环境安全组建议

下表中的“来源”应使用实际服务器公网 `/32`，不能使用 `0.0.0.0/0`：

| 节点 | 端口 | 来源 |
| --- | --- | --- |
| 上海 | 80、443 | 测试用户公网或 CDN/反向代理地址 |
| 上海 | 3306、6379、8848、9848、8858 | 东京、硅谷公网 IP |
| 上海 | 18080 | 仅运维 SSH 隧道，不加入公网白名单 |
| 东京 | 8100 | 上海公网 IP；如需同机诊断可保留本机 |
| 东京 | 8106 | 上海、硅谷公网 IP |
| 硅谷 | 8107 | 上海公网 IP；按 AI 调用方需要增加东京 |
| 硅谷 | 7002 | 上海、东京公网 IP |
| 硅谷 | 8103 | 上海、东京公网 IP |
| 所有节点 | SSH | 固定运维出口 IP |

以上只描述应用通信方向，最终规则以安全组和现有 Caddy/Saber 端口占用核对结果为准。应用端口若绑定 `0.0.0.0`，安全组仍必须限制来源。

## 5. 仓库文件与目标目录

仓库中的应用模板位于 `script/docker/app/`：

```text
script/docker/app/
├── gateway/compose.yml  -> 上海，project: springblade-gateway
├── auth/compose.yml     -> 东京，project: springblade-auth
├── system/compose.yml   -> 东京，project: springblade-system
├── ai/compose.yml       -> 硅谷，project: springblade-ai
├── admin/compose.yml    -> 硅谷，project: springblade-admin
└── log/compose.yml      -> 硅谷，project: springblade-log
```

每个目录还包含 `.env.example`。服务器上只创建真实 `.env`，不把 `.env` 回传或提交到 Git：

```text
/opt/springblade/app/<service>/
├── compose.yml
└── .env
```

复制文件时保持每个服务的 Compose project 独立。不要重新创建已经删除的聚合 `app/compose.yml`，不要在一个服务器上执行其他节点的 Compose 文件。

## 6. 环境文件填写规则

### 6.1 所有服务

六个 `.env.example` 都需要填写：

- `REGISTRY`、`IMAGE_NAMESPACE`、`IMAGE_TAG`：GHCR 地址、组织/用户名和不可变 Git SHA 标签。
- `BLADE_NACOS_ADDR`：`<SHANGHAI_PUBLIC_IP>:8848`。
- `BLADE_NACOS_NAMESPACE`：默认 `prod`。
- `BLADE_NACOS_USERNAME`、`BLADE_NACOS_PASSWORD`：Nacos 真实账号，不使用默认弱密码。
- `BLADE_SENTINEL_ADDR`：`<SHANGHAI_PUBLIC_IP>:8858`。
- `DISCOVERY_IP`、`DISCOVERY_PORT`：当前服务所在服务器公网地址和宿主机映射端口。
- `JAVA_TOOL_OPTIONS`、服务资源上限：默认值已经按本记录设置，只有经过容量评估后才覆盖。

### 6.2 需要数据库和 Redis 的服务

`auth`、`system`、`ai`、`log` 还需要填写：

- `BLADE_REDIS_HOST`、`BLADE_REDIS_PORT`、`BLADE_REDIS_PASSWORD`、`BLADE_REDIS_DATABASE`。
- `BLADE_DATASOURCE_URL`、`BLADE_DATASOURCE_USERNAME`、`BLADE_DATASOURCE_PASSWORD`。
- JDBC URL 必须指向上海 MySQL；模板已包含 `allowPublicKeyRetrieval=true`，实际参数需与现有 MySQL 账号策略确认。
- `BLADE_TOKEN_SIGN_KEY`、`BLADE_TOKEN_CRYPTO_KEY` 必须与 Gateway 和 Nacos 公共配置一致。

`auth` 另外需要 `BLADE_OAUTH2_PUBLIC_KEY` 和 `BLADE_OAUTH2_PRIVATE_KEY`，必须使用同一套 SM2 密钥并通过服务器 `.env` 提供。

### 6.3 AI 服务

`ai/.env` 还需要填写 `BLADE_AI_REVERSE_FAST_BASE_URL`、`BLADE_AI_REVERSE_FAST_API_KEY` 和 `BLADE_AI_REVERSE_TARGET_ENGINE`。没有启用图片反推时可以保持为空，但必须明确确认该功能不在本次验收范围内。

### 6.4 社交登录与域名

`auth` 的 `social.domain` 和第三方 OAuth 客户端配置位于 Nacos 配置，Compose 不覆盖这些值。实际域名、HTTPS 终止位置、GitHub/Gitee/微信/QQ/钉钉回调地址必须在导入 Nacos 配置前确认。

## 7. 后续人工部署顺序

以下是部署人员后续可执行的顺序，本次任务没有执行这些命令：

1. 在上海确认 MySQL、Redis、Nacos、Sentinel 现有数据和容器状态，先备份并修改绑定地址。
2. 在云安全组放行最小跨主机规则，确认东京和硅谷可以访问上海 `3306/6379/8848/9848/8858`。
3. 确认 Nacos `prod` namespace、`DEFAULT_GROUP`、`blade.yaml` 和 `blade-prod.yaml` 已存在，且敏感值不会写入 Git。
4. 在东京安装或确认 Docker Engine 与 Compose v2，先校验 `system`，再校验 `auth`。
5. 在硅谷安装或确认 Docker Engine 与 Compose v2，按 `ai`、`log`、`admin` 顺序校验。
6. 在上海创建 `springblade-ingress` external network，校验 Gateway 和 Nginx 的入口网络。
7. 先部署 `system`，确认注册和数据库连接，再部署 `auth`。两者同在东京，但仍通过 Nacos 注册地址通信。
8. 部署 `ai`、`log`、`admin`，检查 Nacos 注册地址不是容器 IP。
9. 最后部署 Gateway 和 Nginx，验证网关能从 Nacos 发现 `auth`、`system`、`ai`、`log`、`admin`。
10. 逐项做登录、后台接口、AI 反推、日志写入、服务注册和回滚验收。

单个服务的配置校验命令如下，只解析 Compose，不拉取镜像、不启动容器：

```bash
docker compose -p springblade-<service> --env-file .env -f compose.yml config --quiet
```

通过校验后才允许由运维人员执行该服务自己的 `pull` 和 `up -d`。日常发布或回滚只修改目标服务的 `IMAGE_TAG`，不得执行其他服务或中间件 project 的 `down`、`rm`、`prune`。

## 8. 回滚边界

应用回滚仅将目标目录 `.env` 的 `IMAGE_TAG` 改为上一稳定 SHA，再对该服务执行独立 Compose 的拉取和重建。应用回滚不自动回滚 MySQL SQL、Nacos 配置、Redis 数据、AI 外部服务、Caddy、Saber 或其他应用。

如果新版本改变数据库结构或 Nacos 配置，必须先确认向后兼容和对应回滚方案，不能把镜像回滚误认为数据回滚。

## 9. 部署前需要用户提供的信息

请在实际部署前提供或确认以下信息。凭据、Token、私钥和 API Key 不要直接发到聊天，可在服务器上按模板填写：

### 9.1 服务器与网络

1. 上海、东京、硅谷三台服务器的最终公网 IP；如有私网/VPC 地址，同时提供私网地址和是否改用私网。
2. 三台服务器的 SSH 用户、Docker 执行权限、操作系统版本、Docker Engine/Compose v2 是否已安装。
3. 三台服务器当前 CPU、可用内存、swap、磁盘余量，以及现有 Docker 容器和端口占用；特别是东京是否能承受 `auth + system`。
4. 腾讯云安全组入站和出站规则，确认是否允许跨地域 TCP 连接和固定 `/32` 白名单。
5. 是否继续采用公网测试方案；如果改用私网，需要提供私网网段、路由和 DNS/主机名解析方案。

### 9.2 中间件与数据库

6. 上海现有 MySQL、Redis、Nacos、Sentinel 的实际容器名、版本、绑定地址和数据目录，不要只提供逻辑服务名。
7. MySQL `blade` 和 `nacos_config` 的连接地址、端口、账号和密码；确认是否允许东京/硅谷访问 `3306`。
8. Redis 地址、端口、密码和 database；确认是否允许东京/硅谷访问 `6379`。
9. Nacos 地址、8848/9848 端口、管理员账号、应用账号、`prod` namespace 和配置导入状态。
10. Sentinel 地址和 8858 端口；如果测试阶段不启用 Sentinel，请明确允许将应用地址改为空或关闭相关能力。

### 9.3 镜像与发布

11. GHCR 的组织/用户名、镜像是否私有、服务器只读 `read:packages` 凭据准备方式。
12. 六个镜像要部署的 Git SHA 标签；不要使用 `latest`。
13. 是否要求通过代理、镜像加速或指定 Registry 地址拉取镜像。

### 9.4 应用配置与密钥

14. `BLADE_TOKEN_SIGN_KEY`、`BLADE_TOKEN_CRYPTO_KEY`、SM2 公钥和私钥；确认所有相关服务使用同一套值。
15. 社交登录供应商的 Client ID、Client Secret、域名和回调地址；凭据只在服务器侧填写。
16. AI 反推 Fast 服务地址、API Key 和目标引擎；不启用时确认验收范围。
17. 对外域名、TLS 证书来源、证书终止位置、HTTP 到 HTTPS 跳转要求和真实客户端 IP 传递要求。
18. Gateway、Auth、System、AI、Admin、Log 是否需要额外的管理端点白名单、访问日志保留周期和磁盘配额。

## 10. 当前未执行项

- 未连接三台服务器。
- 未修改上海中间件绑定地址或安全组。
- 未安装 Docker/Java，未拉取 GHCR 镜像。
- 未执行 `docker compose pull`、`up`、`down`、`rm` 或 `prune`。
- 未验证跨主机 Nacos 注册、Feign 调用、登录、AI、日志、备份和回滚。
