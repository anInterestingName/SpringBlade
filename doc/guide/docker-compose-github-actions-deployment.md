# SpringBlade Docker Compose 与 GitHub Actions 部署教程

> 本文保留单机中间件和入口的基础部署说明。六个应用拆分到上海、东京、硅谷三台服务器时，优先阅读[`SpringBlade 三服务器 Compose 部署文档`](three-server-compose-deployment.md)；该文档覆盖跨主机地址、端口白名单、JVM资源和六套应用 Compose。

## 1. 部署范围

本教程用于在单台Linux服务器部署SpringBlade 5.0.1。每个中间件使用独立目录和独立Compose project，数据绑定到宿主机目录。

| 层次 | Compose project | 内容 |
| --- | --- | --- |
| 数据库 | `springblade-mysql` | MySQL 8.4 |
| 缓存 | `springblade-redis` | Redis 7 |
| 注册配置中心 | `springblade-nacos` | Nacos 3.2.2 |
| 流量治理 | `springblade-sentinel` | Sentinel Dashboard 1.8.0 |
| 应用 | `springblade-<service>` | 每个SpringBlade微服务独立project；六服务三机模板见三服务器文档 |
| 入口 | `springblade-ingress` | Nginx |

本方案不部署Seata Server、MinIO、消息队列、Elasticsearch和监控平台，不提供中间件集群高可用。

## 2. 服务器目录

统一使用 `/opt/springblade`：

```text
/opt/springblade/
├── middleware/
│   ├── deploy.sh
│   ├── mysql/
│   │   ├── compose.yml
│   │   ├── .env
│   │   ├── conf/
│   │   │   └── my.cnf
│   │   ├── init/
│   │   │   ├── init-databases.sh
│   │   │   └── nacos.mysql-schema.sql
│   │   ├── sql/
│   │   │   └── blade/
│   │   │       ├── blade.mysql.all.create.sql
│   │   │       └── blade.mysql.upgrade.*.sql
│   │   ├── data/
│   │   └── backup/
│   ├── redis/
│   │   ├── compose.yml
│   │   ├── .env
│   │   ├── conf/
│   │   │   └── redis.conf
│   │   ├── data/
│   │   └── backup/
│   ├── nacos/
│   │   ├── compose.yml
│   │   ├── .env
│   │   ├── conf/
│   │   │   └── application.properties
│   │   ├── data/
│   │   └── logs/
│   └── sentinel/
│       ├── compose.yml
│       ├── .env
│       ├── data/
│       └── logs/
├── app/
│   ├── README.md
│   ├── gateway/
│   │   ├── compose.yml
│   │   └── .env
│   └── service-name/
│       ├── compose.yml
│       └── .env
└── ingress/
    ├── compose.yml
    ├── nginx.conf
    └── .env
```

目录用途：

| 目录 | 用途 |
| --- | --- |
| `data` | 服务持久化数据 |
| `logs` | Nacos等服务日志 |
| `conf` | 只读挂载的服务配置 |
| `init` | MySQL首次空目录初始化脚本 |
| `sql` | 业务全量与升级SQL |
| `backup` | 数据库和缓存备份 |

## 3. 网络与通信

### 3.1 Docker网络

创建两个external network：

```bash
docker network inspect springblade-backend >/dev/null 2>&1 \
  || docker network create springblade-backend

docker network inspect springblade-ingress >/dev/null 2>&1 \
  || docker network create springblade-ingress
```

| 网络 | 服务 |
| --- | --- |
| `springblade-backend` | 单机模式中的 MySQL、Redis、Nacos、Sentinel 和同机应用 |
| `springblade-ingress` | Nginx、Gateway |

这是单机基础设施的本机网络模型。三机模式下 Docker bridge 不跨服务器，Gateway只加入上海本机的`springblade-ingress`；东京和硅谷应用使用宿主机地址和Nacos注册信息访问远端依赖。Nginx不能连接`springblade-backend`，不能直接访问数据库、缓存和注册中心。

### 3.2 网络别名

| 服务 | 固定网络别名 | 端口 |
| --- | --- | ---: |
| MySQL | `springblade-mysql` | 3306 |
| Redis | `springblade-redis` | 6379 |
| Nacos Server | `springblade-nacos` | 8848 |
| Nacos gRPC | `springblade-nacos` | 9848 |
| Sentinel | `springblade-sentinel` | 8858 |
| Gateway | `blade-gateway` | 80 |

容器之间只使用网络别名通信，不使用固定容器IP、宿主机公网IP或宿主机映射端口。

### 3.3 通信关系

| 调用方 | 目标 | 地址 |
| --- | --- | --- |
| Nacos | MySQL | `springblade-mysql:3306` |
| 应用 | MySQL | `springblade-mysql:3306` |
| 应用 | Redis | `springblade-redis:6379` |
| 应用 | Nacos | `springblade-nacos:8848` |
| Nacos客户端 | Nacos gRPC | `springblade-nacos:9848` |
| 应用 | Sentinel | `springblade-sentinel:8858` |
| Nginx | Gateway | `blade-gateway:80` |

### 3.4 宿主机端口

| 服务 | 容器端口 | 宿主机绑定 |
| --- | ---: | --- |
| MySQL | 3306 | `127.0.0.1:3306` |
| Redis | 6379 | `127.0.0.1:6379` |
| Nacos Server | 8848 | `127.0.0.1:8848` |
| Nacos gRPC | 9848 | `127.0.0.1:9848` |
| Nacos Console | 8080 | `127.0.0.1:18080` |
| Sentinel | 8858 | `127.0.0.1:8858` |
| Gateway | 80 | `127.0.0.1:8080` |
| Nginx HTTP | 80 | `0.0.0.0:80` |
| Nginx HTTPS | 443 | `0.0.0.0:443` |

Nacos Console使用宿主机`18080`，避免与Gateway调试端口`8080`冲突。单机模式腾讯云安全组只对外开放SSH和实际使用的80/443；三机模式的额外白名单规则见三服务器文档。

## 4. 服务器准备

### 4.1 环境要求

- Linux x86_64服务器。
- Docker Engine和Docker Compose v2。
- `curl`、`openssl`、`tar`和`rsync`。
- 部署用户可以执行Docker命令并写入`/opt/springblade`。
- 服务器可以拉取Docker Hub、Nacos镜像和GHCR应用镜像。

检查环境：

```bash
docker version
docker compose version
curl --version
```

### 4.2 创建目录

```bash
sudo mkdir -p \
  /opt/springblade/middleware/mysql/{conf,init,sql/blade,data,backup} \
  /opt/springblade/middleware/redis/{conf,data,backup} \
  /opt/springblade/middleware/nacos/{conf,data,logs} \
  /opt/springblade/middleware/sentinel/{data,logs} \
  /opt/springblade/app/gateway \
  /opt/springblade/ingress

sudo chown -R "$USER":"$USER" /opt/springblade
chmod 700 /opt/springblade/middleware/mysql/backup
chmod 700 /opt/springblade/middleware/redis/backup
```

`.env`创建后统一设置：

```bash
chmod 600 /opt/springblade/middleware/*/.env
chmod 600 /opt/springblade/app/*/.env
chmod 600 /opt/springblade/ingress/.env
```

### 4.3 数据目录权限

不要使用`chmod 777`。先检查镜像运行用户，再设置对应目录所有权：

```bash
docker run --rm --entrypoint id mysql:8.4 mysql
docker run --rm --entrypoint id redis:7-alpine redis
docker run --rm --entrypoint id nacos/nacos-server:v3.2.2
```

根据命令返回的UID/GID设置`mysql/data`、`redis/data`、`nacos/data`和`nacos/logs`。

## 5. MySQL

### 5.1 参数

`/opt/springblade/middleware/mysql/.env`：

```dotenv
TZ=Asia/Shanghai
MYSQL_BIND_ADDRESS=127.0.0.1
MYSQL_PORT=3306
MYSQL_ROOT_PASSWORD=<强随机密码>
BLADE_MYSQL_PASSWORD=<业务库密码>
NACOS_MYSQL_PASSWORD=<Nacos数据库密码>
```

生成随机密码：

```bash
openssl rand -base64 32
```

### 5.2 挂载

```yaml
volumes:
  - ./data:/var/lib/mysql
  - ./conf/my.cnf:/etc/mysql/conf.d/springblade.cnf:ro
  - ./init/init-databases.sh:/docker-entrypoint-initdb.d/10-init-databases.sh:ro
  - ./init/nacos.mysql-schema.sql:/opt/springblade/bootstrap/nacos.mysql-schema.sql:ro
```

Nacos Schema只作为初始化脚本的输入挂载到`/opt/springblade/bootstrap`，不得直接以`.sql`文件放入`/docker-entrypoint-initdb.d`，否则官方entrypoint会在脚本导入后再次执行Schema。

MySQL加入`springblade-backend`，网络别名为`springblade-mysql`。

### 5.3 启动

```bash
cd /opt/springblade/middleware/mysql
docker compose -p springblade-mysql --env-file .env -f compose.yml config --quiet
docker compose -p springblade-mysql --env-file .env -f compose.yml pull
docker compose -p springblade-mysql --env-file .env -f compose.yml up -d
docker compose -p springblade-mysql --env-file .env -f compose.yml ps
```

首次空`data`目录启动时自动创建：

| 数据库 | 用户 | 用途 |
| --- | --- | --- |
| `blade` | `blade` | SpringBlade业务数据 |
| `nacos_config` | `nacos` | Nacos数据 |

Nacos Schema自动初始化，业务全量SQL不自动执行。

### 5.4 初始化新业务库

仅在确认`blade`为空库时执行：

```bash
cd /opt/springblade/middleware/mysql

docker compose -p springblade-mysql --env-file .env -f compose.yml \
  exec -T mysql sh -c \
  'exec mysql -ublade -p"$BLADE_MYSQL_PASSWORD" blade' \
  < ./sql/blade/blade.mysql.all.create.sql
```

已有数据库禁止执行全量脚本，只允许在备份后执行匹配版本的升级脚本。

## 6. Redis

### 6.1 参数

`/opt/springblade/middleware/redis/.env`：

```dotenv
TZ=Asia/Shanghai
REDIS_BIND_ADDRESS=127.0.0.1
REDIS_PORT=6379
REDIS_PASSWORD=<强随机密码>
```

Redis配置启用AOF和密码认证。数据目录挂载：

```yaml
volumes:
  - ./data:/data
  - ./conf/redis.conf:/usr/local/etc/redis/redis.conf:ro
```

Redis加入`springblade-backend`，网络别名为`springblade-redis`。

### 6.2 启动

```bash
cd /opt/springblade/middleware/redis
docker compose -p springblade-redis --env-file .env -f compose.yml config --quiet
docker compose -p springblade-redis --env-file .env -f compose.yml pull
docker compose -p springblade-redis --env-file .env -f compose.yml up -d
docker compose -p springblade-redis --env-file .env -f compose.yml ps
```

## 7. Nacos

### 7.1 参数

`/opt/springblade/middleware/nacos/.env`：

```dotenv
TZ=Asia/Shanghai
NACOS_BIND_ADDRESS=127.0.0.1
NACOS_SERVER_PORT=8848
NACOS_GRPC_PORT=9848
NACOS_CONSOLE_HOST_PORT=18080

MYSQL_SERVICE_HOST=springblade-mysql
MYSQL_SERVICE_PORT=3306
MYSQL_SERVICE_DB_NAME=nacos_config
MYSQL_SERVICE_USER=nacos
MYSQL_SERVICE_PASSWORD=<与MySQL配置一致>

NACOS_AUTH_IDENTITY_KEY=<随机值>
NACOS_AUTH_IDENTITY_VALUE=<随机值>
NACOS_AUTH_TOKEN=<Base64且至少32字节的随机值>
NACOS_ADMIN_USERNAME=nacos
NACOS_ADMIN_PASSWORD=<强随机管理员密码>
```

生成鉴权参数：

```bash
openssl rand -hex 16
openssl rand -hex 16
openssl rand -base64 32
openssl rand -hex 24
```

数据和配置挂载：

```yaml
volumes:
  - ./data:/home/nacos/data
  - ./logs:/home/nacos/logs
  - ./conf/application.properties:/home/nacos/conf/application.properties:ro
```

Nacos加入`springblade-backend`，网络别名为`springblade-nacos`。

Nacos官方镜像启动脚本启用了shell xtrace。Compose需要用包装入口将xtrace输出定向到`/dev/null`，避免鉴权Token和身份密钥出现在容器日志：

```yaml
entrypoint:
  - bash
  - -c
  - exec 9>/dev/null; export BASH_XTRACEFD=9; exec bash /home/nacos/bin/docker-startup.sh
```

### 7.2 启动

确认MySQL健康后执行：

```bash
cd /opt/springblade/middleware/nacos
docker compose -p springblade-nacos --env-file .env -f compose.yml config --quiet
docker compose -p springblade-nacos --env-file .env -f compose.yml pull
docker compose -p springblade-nacos --env-file .env -f compose.yml up -d
docker compose -p springblade-nacos --env-file .env -f compose.yml ps
docker compose -p springblade-nacos --env-file .env -f compose.yml logs --tail=100 nacos
```

### 7.3 控制台和配置

通过SSH隧道访问控制台：

```bash
ssh -L 18080:127.0.0.1:18080 <部署用户>@<服务器地址>
```

浏览器访问`http://127.0.0.1:18080`。

Nacos 3首次启动时需要初始化管理员用户`nacos`。使用`.env`中的`NACOS_ADMIN_PASSWORD`完成初始化，不使用默认弱密码；该密码只保存在服务器，文件权限保持`600`。

创建namespace：

| 字段 | 值 |
| --- | --- |
| Namespace ID | `prod` |
| Namespace Name | `prod` |

在`prod` namespace和`DEFAULT_GROUP`创建：

| Data ID | 类型 | 来源 |
| --- | --- | --- |
| `blade.yaml` | YAML | `doc/nacos/blade.yaml` |
| `blade-prod.yaml` | YAML | `doc/nacos/blade-prod.yaml` |

真实数据库和Redis凭据不写入Nacos配置文件，通过应用`.env`注入。

## 8. Sentinel

`/opt/springblade/middleware/sentinel/.env`：

```dotenv
TZ=Asia/Shanghai
SENTINEL_BIND_ADDRESS=127.0.0.1
SENTINEL_PORT=8858
```

Sentinel加入`springblade-backend`，网络别名为`springblade-sentinel`。

```bash
cd /opt/springblade/middleware/sentinel
docker compose -p springblade-sentinel --env-file .env -f compose.yml config --quiet
docker compose -p springblade-sentinel --env-file .env -f compose.yml pull
docker compose -p springblade-sentinel --env-file .env -f compose.yml up -d
docker compose -p springblade-sentinel --env-file .env -f compose.yml ps
```

Sentinel Dashboard不依赖本地目录持久化规则。需要规则持久化时使用Nacos数据源。

## 9. 应用部署

### 9.1 Gateway独立目录

应用不再共用一个聚合Compose。每个应用位于`/opt/springblade/app/<service>/`并使用独立`.env`和`springblade-<service>` project。Gateway示例目录如下；六服务三机分配和其他环境模板见三服务器文档：

```text
/opt/springblade/app/gateway/
├── compose.yml
└── .env
```

`/opt/springblade/app/gateway/.env`：

```dotenv
REGISTRY=ghcr.io
IMAGE_NAMESPACE=<小写GitHub用户名或组织名>
IMAGE_TAG=<12位Git SHA>

TZ=Asia/Shanghai
SPRING_PROFILES_ACTIVE=prod
JAVA_TOOL_OPTIONS=-Xms128m -Xmx256m -Xss512k -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:MaxDirectMemorySize=128m -XX:MaxMetaspaceSize=160m -XX:ReservedCodeCacheSize=96m -XX:+ExitOnOutOfMemoryError -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai

BLADE_NACOS_ADDR=<SHANGHAI_PUBLIC_IP>:8848
BLADE_NACOS_NAMESPACE=prod
BLADE_NACOS_USERNAME=<Nacos账号>
BLADE_NACOS_PASSWORD=<Nacos密码>
BLADE_SENTINEL_ADDR=<SHANGHAI_PUBLIC_IP>:8858

BLADE_REDIS_HOST=<SHANGHAI_PUBLIC_IP>
BLADE_REDIS_PORT=6379
BLADE_REDIS_PASSWORD=<Redis密码>
BLADE_REDIS_DATABASE=0

BLADE_TOKEN_SIGN_KEY=<实际签名密钥>
BLADE_TOKEN_CRYPTO_KEY=<实际加密密钥>

GATEWAY_BIND_ADDRESS=127.0.0.1
GATEWAY_PORT=8080
GATEWAY_MEMORY_LIMIT=640m
GATEWAY_MEMORY_RESERVATION=384m
GATEWAY_CPUS=1.0
```

三机模式下Gateway只加入上海本机的`springblade-ingress`，通过上海公网地址访问Nacos、Redis和Sentinel；东京、硅谷应用不加入上海的Docker网络，使用各自目录的环境模板和Nacos宿主机注册地址。

### 9.2 GHCR登录

```bash
echo '<GHCR_READ_TOKEN>' | docker login ghcr.io \
  -u '<github-user>' --password-stdin
```

凭据只授予镜像读取权限，不写入Compose或`.env`。

### 9.3 配置检查

```bash
cd /opt/springblade/app/gateway
docker compose -p springblade-gateway --env-file .env -f compose.yml config --quiet
```

配置检查不会拉取镜像或启动容器。确认解析结果后，手动部署命令为：

```bash
docker compose -p springblade-gateway --env-file .env -f compose.yml pull
docker compose -p springblade-gateway --env-file .env -f compose.yml up -d
docker compose -p springblade-gateway --env-file .env -f compose.yml ps
```

上述命令只操作Gateway project，不得停止或重建中间件及其他应用project，也不由 GitHub Actions 远程调用。

## 10. Nginx入口

`/opt/springblade/ingress/.env`：

```dotenv
HTTP_BIND_ADDRESS=0.0.0.0
HTTP_PORT=80
HTTPS_PORT=443
```

Nginx只加入`springblade-ingress`，上游为`blade-gateway:80`。

```bash
cd /opt/springblade/ingress
docker compose -p springblade-ingress --env-file .env -f compose.yml config --quiet
docker compose -p springblade-ingress --env-file .env -f compose.yml up -d
```

生产环境配置TLS证书后只对公网开放80/443。

## 11. GitHub Actions 镜像发布

GitHub Actions 只负责构建和推送镜像，不负责 SSH 登录、上传服务器文件或自动切换应用。工作流使用 JDK 21 执行：

```bash
mvn clean package -DskipTests -Ddocker.skip=true
```

工作流支持两种触发方式：

- 手工触发 `workflow_dispatch`。
- 推送名称匹配 `v*` 的 Git tag。

成功后，GHCR 中会生成 10 个使用同一提交 SHA 前 12 位作为标签的服务镜像。Actions 不需要以下配置：

- `production` Environment
- `DEPLOY_HOST`
- `DEPLOY_USER`
- `DEPLOY_PORT`
- `DEPLOY_SSH_KEY`
- `DEPLOY_KNOWN_HOSTS`

Actions 仅需要仓库 `GITHUB_TOKEN` 的 `packages: write` 权限。服务器端按需部署时，先登录 GHCR，再手动执行：

```bash
echo '<GHCR_READ_TOKEN>' | docker login ghcr.io \
  -u '<github-user>' --password-stdin

cd /opt/springblade/app/gateway
# 修改.env中的IMAGE_TAG后执行
docker compose -p springblade-gateway --env-file .env -f compose.yml pull
docker compose -p springblade-gateway --env-file .env -f compose.yml up -d
```

私有 GHCR 包使用只读 `read:packages` 凭据；凭据只保存在服务器或运维终端，不写入仓库、Compose 文件或 `.env`。
## 12. 启动顺序

首次部署严格按以下顺序执行：

```text
1. 创建springblade-backend和springblade-ingress
2. 启动MySQL并等待健康
3. 启动Redis并等待健康
4. 启动Nacos并确认连接MySQL成功
5. 启动Sentinel
6. 初始化Nacos配置
7. 按依赖顺序逐个启动SpringBlade应用；六服务三机顺序见三服务器文档
8. 检查服务注册和依赖连接
9. 启动Nginx
```

独立Compose project之间不能使用`depends_on`。`middleware/deploy.sh`必须负责启动顺序和健康等待。

## 13. 备份与迁移

### 13.1 MySQL逻辑备份

```bash
cd /opt/springblade/middleware/mysql

docker compose -p springblade-mysql --env-file .env -f compose.yml \
  exec -T mysql sh -c \
  'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --databases blade nacos_config' \
  > "./backup/mysql-$(date +%Y%m%d-%H%M%S).sql"
```

备份文件应复制到独立存储，并验证恢复流程。

### 13.2 本地目录迁移

直接迁移`data`目录前必须停止对应服务：

```bash
docker compose -p springblade-mysql --env-file .env -f compose.yml down
docker compose -p springblade-redis --env-file .env -f compose.yml down
docker compose -p springblade-nacos --env-file .env -f compose.yml down
```

迁移目录：

```text
/opt/springblade/middleware/mysql/data/
/opt/springblade/middleware/redis/data/
/opt/springblade/middleware/nacos/data/
/opt/springblade/middleware/nacos/logs/
```

目标服务器必须使用兼容镜像版本，并恢复原UID/GID和文件权限。MySQL优先使用逻辑备份，不以直接复制数据目录作为默认迁移方式。

## 14. 发布后验证

### 14.1 中间件

```bash
docker compose -p springblade-mysql -f /opt/springblade/middleware/mysql/compose.yml ps
docker compose -p springblade-redis -f /opt/springblade/middleware/redis/compose.yml ps
docker compose -p springblade-nacos -f /opt/springblade/middleware/nacos/compose.yml ps
docker compose -p springblade-sentinel -f /opt/springblade/middleware/sentinel/compose.yml ps
```

确认：

- MySQL存在`blade`和`nacos_config`。
- Redis带密码执行`PING`返回`PONG`。
- Nacos可以正常读写配置并注册服务。
- 中间件重启后`data`目录内容保持。

### 14.2 网络

```bash
docker network inspect springblade-backend
docker network inspect springblade-ingress
```

确认上海入口网络存在，Gateway和Nginx只连接入口网络；跨主机应用通过Nacos注册的宿主机地址通信。

### 14.3 应用

在Nacos `prod` namespace确认服务按实际部署范围注册。当前Gateway检查：

```text
blade-gateway
```

后续应用目录完成并部署后，再逐项确认：

```text
blade-auth
blade-admin
blade-develop
blade-report
blade-resource
blade-desk
blade-log
blade-system
blade-ai
```

检查入口：

```bash
curl --fail http://127.0.0.1/healthz
curl --silent --output /dev/null --write-out '%{http_code}\n' http://127.0.0.1:8080/
```

## 15. 回滚

单个应用回滚，以Gateway为例：

```bash
cd /opt/springblade/app/gateway
# 将.env中的IMAGE_TAG改为上一稳定SHA
docker compose -p springblade-gateway --env-file .env -f compose.yml pull
docker compose -p springblade-gateway --env-file .env -f compose.yml up -d
```

单个应用回滚不得重建其他应用或中间件，也不自动回滚MySQL升级脚本、Nacos配置、外部AI服务和前端。包含数据库或配置变化的版本必须按对应需求文档执行兼容回滚。

## 16. 运维限制

- 不得删除`middleware/*/data`和`middleware/*/logs`。
- 不得对中间件执行未确认范围的清理命令。
- 不得在服务运行时直接复制MySQL数据目录。
- 不得将`.env`、备份、私钥或生产配置提交到Git。
- Nacos容器必须抑制官方启动脚本的xtrace，并在重建后使用实际密钥值执行日志反查。
- 不得向公网开放MySQL、Redis、Nacos和Sentinel端口。
- 不得使用`latest`作为生产应用镜像标签。

## 17. 完成检查

- [ ] 每个中间件使用独立目录和Compose project。
- [ ] 持久化数据全部绑定到对应服务的`data`或`logs`目录。
- [ ] 所有`.env`权限为`600`，备份目录权限为`700`。
- [ ] 已创建`springblade-backend`和`springblade-ingress`。
- [ ] 固定网络别名和通信地址配置正确。
- [ ] Nginx不能访问后端网络中的中间件。
- [ ] Nacos Console使用宿主机`18080`，Gateway使用`8080`。
- [ ] 腾讯云安全组未开放中间件端口。
- [ ] `blade`全量SQL只在确认空库时执行。
- [ ] Nacos `prod` namespace和公共配置已创建。
- [ ] 10个应用镜像使用同一Git SHA标签。
- [ ] 每个已提供应用使用独立目录、`.env`和`springblade-<service>` project。
- [ ] 手动发布单个应用不改变其他应用、中间件容器和本地数据目录。
- [ ] MySQL备份和手动应用回滚均已演练。
