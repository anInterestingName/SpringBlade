# Docker Compose 分层部署与 GitHub Actions 发布测试文档

## 1. 文档信息

| 项目 | 内容 |
| --- | --- |
| 测试编号 | TEST-REQ-2026-006 |
| 关联需求 | [REQ-2026-006](../requirements/REQ-2026-006-compose-deployment.md) |
| 关联详细设计 | [DESIGN-REQ-2026-006](../design/DESIGN-REQ-2026-006-compose-deployment.md) |
| 关联数据库设计 | 不涉及 |
| 文档版本 | 0.8 |
| 文档状态 | 执行中 |
| 测试负责人 | Codex |
| 测试日期 | 2026-09-20；完成三台服务器只读核验，三机应用真实部署与回滚仍待执行 |

## 2. 测试范围与依据

- 测试目标：验证中间件独立Compose、六个目标应用按服务独立Compose、三服务器资源限制、跨主机注册地址、入口网络、GHCR镜像发布、服务器手动应用部署和回滚。
- 范围内：Java编译、Dockerfile、中间件与入口Compose、`gateway`/`auth`/`system`/`ai`/`admin`/`log`六套应用Compose、目录权限、MySQL初始化、Nacos配置、网络和端口白名单、GHCR镜像发布、服务器手动拉取、备份迁移和回滚；不包含Actions SSH自动部署。
- 范围外：Kubernetes、多主机高可用、Saber前端和业务接口全量回归。
- 验收依据：REQ-001至REQ-005、AC-001至AC-011及详细设计风险。

## 3. 测试环境与准备

| 项目 | 内容 |
| --- | --- |
| 后端版本/提交 | 当前工作区；本次仅部署中间件配置 |
| JDK / Maven | JDK 21 / 系统Maven |
| Docker | Docker Engine 29.1.3 / Docker Compose 2.40.3 |
| MySQL / Redis | MySQL 8.4.11 / Redis 7 Alpine，使用独立绑定目录 |
| Nacos / Sentinel | Nacos 3.2.2 / Sentinel 1.8.0 |
| 网络 | 单机中间件使用`springblade-backend`；上海入口使用`springblade-ingress`；跨主机使用白名单公网地址 |
| 镜像仓库 | GHCR；历史Actions运行已完成镜像构建推送 |
| 目标服务 | 上海现有 MySQL、Redis、Nacos、Sentinel、Gateway；东京无 Docker/Java；硅谷因 SSH 主机密钥校验未完成核验 |
| 测试数据 | 全新中间件目录；Redis唯一前缀临时键已清理 |

测试目录使用唯一前缀，不复用生产`data`、`logs`和`backup`。清理时只能删除本次测试创建且已确认绝对路径的目录和容器。

## 4. 测试用例

### TC-001 Java公共启动配置编译

- 优先级：P0
- 关联需求/验收标准：REQ-003 / AC-005
- 测试层级：编译/静态
- 目标模块与入口：`blade-common`、父工程Maven构建。
- 前置条件：JDK 21、系统Maven。
- 步骤：执行`mvn clean compile -DskipTests -pl blade-common -am`，再执行父工程跳过测试打包。
- 预期结果：编译成功，环境变量读取和启动参数注入无编译错误。
- 证据：Maven Reactor输出。
- 清理：无。
- 实际结果：目标模块编译成功；父工程25个模块`mvn clean package -DskipTests -Ddocker.skip=true`成功。
- 结果：通过

### TC-002 中间件Compose配置

- 优先级：P0
- 关联需求/验收标准：REQ-001 / AC-001、AC-002
- 测试层级：部署静态检查
- 目标模块与入口：MySQL、Redis、Nacos、Sentinel四套Compose。
- 前置条件：四套`.env.example`和Compose文件已完成。
- 步骤：分别执行`docker compose config --quiet`，检查project、绑定挂载、external network和网络别名。
- 预期结果：四套配置均可解析；数据挂载到各自`data`或`logs`；不存在Docker命名数据卷；别名分别为`springblade-mysql`、`springblade-redis`、`springblade-nacos`、`springblade-sentinel`。
- 证据：Compose输出和解析后的配置。
- 清理：删除测试用`.env`。
- 实际结果：四套Compose均在目标服务器通过`config --quiet`；使用四个独立project、宿主机绑定目录、`springblade-backend` external network和固定别名，不存在命名数据卷。
- 结果：通过

### TC-003 六应用与入口Compose配置

- 优先级：P0
- 关联需求/验收标准：REQ-001、REQ-004 / AC-002、AC-008
- 测试层级：部署静态检查
- 目标模块与入口：`app/gateway`、`app/auth`、`app/system`、`app/ai`、`app/admin`、`app/log`、`springblade-ingress`。
- 前置条件：六套应用Compose、六个`.env.example`和入口Compose已完成；未创建真实`.env`。
- 步骤：分别使用各自`.env.example`执行`docker compose config --quiet`，检查独立project、端口、镜像标签、JVM、容器资源限制、Gateway入口网络和远端注册地址变量。
- 预期结果：六套应用均能解析；Gateway仅加入上海入口网络；东京和硅谷应用不依赖上海Docker external network；服务端口分别为8100、8106、8107、7002、8103；不存在聚合应用Compose。
- 证据：Compose输出和解析后的配置。
- 清理：删除测试用`.env`。
- 实际结果：使用六个`.env.example`分别执行六套应用的`docker compose config --quiet`，并执行Ingress配置解析，全部通过。Gateway解析为仅加入上海入口网络、640 MiB内存上限、384 MiB预留和宿主机回环端口；Auth、System、AI、Admin、Log分别解析为记录中的端口和资源上限。未拉取镜像或启动容器。
- 结果：通过

### TC-004 微服务镜像纯净度

- 优先级：P0
- 关联需求/验收标准：REQ-002 / AC-003、AC-004
- 测试层级：镜像
- 目标模块与入口：10个应用Dockerfile和构建镜像。
- 前置条件：Maven打包完成，Docker可用。
- 步骤：构建镜像，检查文件、启动参数、profile和进程UID。
- 预期结果：镜像只包含运行时和单个应用JAR；不存在`.env`、中间件或固化`test` profile；Java进程使用非root UID。
- 证据：镜像inspect、容器进程和文件清单。
- 清理：删除测试镜像。
- 实际结果：Dockerfile静态扫描已完成，实际镜像构建和inspect未执行。
- 结果：未执行

### TC-005 本地目录初始化与持久化

- 优先级：P0
- 关联需求/验收标准：REQ-001、REQ-005 / AC-001、AC-010
- 测试层级：MySQL/Redis/Nacos集成
- 前置条件：全新且归属明确的测试目录，目录UID/GID已按镜像设置。
- 步骤：按顺序启动MySQL、Redis、Nacos、Sentinel；检查数据库、账号、目录内容和健康状态；分别重建容器。
- 预期结果：`blade`和`nacos_config`创建成功；Nacos Schema完成；业务全量SQL未自动执行；容器重建后本地数据保持；目录无权限错误。
- 证据：容器状态、日志、目录清单和数据库检查结果。
- 清理：停止测试project，仅删除本用例创建且已确认归属的测试目录。
- 实际结果：全新目录按MySQL、Redis、Nacos、Sentinel顺序启动成功；创建空`blade`和含13张表的`nacos_config`，业务全量SQL未执行。MySQL、Redis、Nacos均完成容器重建验证，Redis临时键在重建后保持并已删除，Nacos管理员、`prod` namespace和配置在重建后保持。
- 结果：通过

### TC-006 网络隔离与中间件通信

- 优先级：P0
- 关联需求/验收标准：REQ-001、REQ-003 / AC-002、AC-005
- 测试层级：网络/集成
- 前置条件：两个external network和全部测试容器已启动。
- 步骤：检查网络成员；从Nacos访问MySQL；从应用访问四个中间件；从Nginx尝试解析中间件别名；重建中间件容器后重复检查。
- 预期结果：上海本机入口网络通信正常；Nginx只能访问Gateway，不能解析或访问中间件；东京和硅谷应用通过上海白名单公网端口访问中间件；Nacos注册实例使用宿主机地址，不使用固定容器IP。
- 证据：`docker network inspect`、连接检查和容器日志。
- 清理：无。
- 实际结果：上海现有 Gateway 仍连接 `springblade-backend` 和 `springblade-ingress`，并使用 `springblade-nacos`、`springblade-redis`、`springblade-sentinel` 单机别名。东京只读探测上海 `3306/6379/8848/9848/8858` 全部不可达；上海 Docker 端口映射均为 `127.0.0.1`。三机跨主机前置条件不满足，未启动应用。
- 结果：失败

### TC-007 Nacos生产配置与服务注册

- 优先级：P0
- 关联需求/验收标准：REQ-003 / AC-005、AC-006
- 测试层级：集成
- 前置条件：Nacos、MySQL、Redis和应用测试环境可用。
- 步骤：创建`prod` namespace，导入`blade.yaml`和`blade-prod.yaml`，启动应用并检查注册列表和依赖连接。
- 预期结果：六个目标应用按实际范围注册；跨主机实例地址为宿主机公网地址和映射端口；无容器`172.x`注册地址；配置和日志不泄露敏感值。
- 证据：Nacos注册列表、应用日志和连接结果。
- 清理：停止测试应用，保留归属明确的测试中间件。
- 实际结果：已创建`prod` namespace并导入`blade.yaml`、`blade-prod.yaml`；应用未部署，服务注册和应用依赖连接未执行。
- 结果：未执行

### TC-008 GitHub Actions镜像发布与手动应用部署

- 优先级：P0
- 关联需求/验收标准：REQ-001、REQ-004 / AC-001、AC-007、AC-008
- 测试层级：CI/CD和手动部署集成
- 前置条件：GHCR访问权限、服务器应用目录和中间件环境已准备；不需要production Environment或Actions SSH变量。
- 步骤：触发工作流并检查10个同一SHA镜像；在服务器登录GHCR，更新目标应用`.env`的`IMAGE_TAG`，执行该应用Compose的`pull`与`up -d`；复查应用和中间件状态。
- 预期结果：全部镜像使用同一12位SHA；手动部署只更新目标`springblade-<service>` project；中间件和其他应用不变。
- 证据：Actions日志、镜像清单、容器ID和目录摘要。
- 清理：按镜像保留策略处理测试镜像，不删除未知版本。
- 实际结果：历史Actions运行#1中镜像构建推送步骤通过；原`deploy_app`因`DEPLOY_PORT`为空失败。按用户确认已移除自动部署job和全部SSH配置；新的镜像发布工作流及服务器手动拉取尚未重新执行。
- 结果：失败

### TC-009 Git SHA回滚

- 优先级：P0
- 关联需求/验收标准：REQ-004 / AC-009
- 测试层级：部署集成
- 前置条件：存在当前SHA和上一稳定SHA。
- 步骤：为目标应用部署新SHA，再将其`IMAGE_TAG`改回上一稳定SHA并重新执行该应用Compose，检查其他应用和中间件状态。
- 预期结果：仅目标应用恢复上一SHA，其他应用、中间件容器和本地数据目录无变化。
- 证据：镜像清单、部署日志和容器状态。
- 清理：根据测试计划保留稳定版本。
- 实际结果：待执行。
- 结果：未执行

### TC-010 安全、备份与迁移

- 优先级：P0
- 关联需求/验收标准：REQ-003、REQ-005 / AC-006、AC-010、AC-011
- 测试层级：安全/运维
- 前置条件：中间件和应用测试环境可用。
- 步骤：检查端口绑定、安全组、`.env`权限、目录权限和敏感信息；执行MySQL逻辑备份与恢复；停止服务后迁移测试目录并验证启动。
- 预期结果：中间件只绑定回环地址；`.env`为600、备份目录为700；无真实凭据进入Git和日志；备份可恢复；迁移后权限、数据和服务正常。
- 证据：端口、权限、Git扫描、备份恢复和迁移记录。
- 清理：只删除本用例创建的恢复库和迁移副本。
- 实际结果：已验证中间件端口仅绑定`127.0.0.1`，四个`.env`权限为600，MySQL/Redis备份目录权限为700，当前和持久化Nacos日志已完成密钥反查与脱敏。腾讯云安全组云侧规则、MySQL备份恢复和目录迁移未执行。
- 结果：未执行

## 5. 专项检查

- [x] 每个中间件使用独立目录和Compose project。
- [x] 持久化数据使用宿主机绑定目录，不使用Docker命名数据卷。
- [x] 数据目录UID/GID与容器实际运行用户一致，未使用`chmod 777`。
- [x] 中间件只加入`springblade-backend`。
- [ ] Gateway仅加入上海入口网络，六个目标应用使用各自独立目录、`.env`和`springblade-<service>` project，不存在聚合应用Compose。
- [ ] Nginx只加入入口网络，无法访问中间件。
- [x] Nacos Console映射到18080；Gateway未部署，宿主机8080未占用。
- [x] 单机中间件配置默认绑定宿主机回环地址；三机公网白名单和实际绑定地址未执行。
- [ ] 应用镜像使用非root UID和不可变SHA标签。
- [x] GitHub Actions不包含SSH自动部署或production Environment变量。
- [x] 新建`blade`保持空库，未自动执行业务全量SQL。
- [x] 当前中间件启动与验证日志不泄露已配置的密码、Token和身份密钥。
- [x] 已完成上海和东京只读远程核验；东京未安装 Java 和 Docker，硅谷因 SSH 主机密钥校验失败未读取服务器状态。

## 6. 验收覆盖矩阵

| 验收标准 | 测试用例 | 结果 | 说明 |
| --- | --- | --- | --- |
| AC-001、AC-002 | TC-002、TC-003、TC-005、TC-006、TC-008 | 失败 | 静态 Compose 通过，但上海中间件仍绑定回环地址，东京无法访问，现有 Gateway 仍为单机网络配置 |
| AC-003、AC-004 | TC-004 | 未执行 | 镜像运行时验证 |
| AC-005、AC-006 | TC-001、TC-006、TC-007、TC-010 | 部分通过 | 编译通过，真实连接和安全检查待执行 |
| AC-007、AC-008 | TC-008 | 部分通过 | 历史运行已验证镜像构建推送；新工作流和服务器手动拉取待执行 |
| AC-009 | TC-009 | 未执行 | 回滚 |
| AC-010、AC-011 | TC-005、TC-010 | 部分通过 | 中间件首次部署通过；备份恢复和迁移待执行 |

## 7. 缺陷与汇总

| 缺陷编号 | 关联用例 | 严重级别 | 描述 | 状态 |
| --- | --- | --- | --- | --- |
| DEF-001 | TC-005 | 严重 | Nacos Schema源文件直接位于MySQL entrypoint目录会被重复执行，首次启动出现`No database selected`并重启一次 | 已关闭：改为脚本与Schema源文件分别挂载 |
| DEF-002 | TC-005 | 严重 | Nacos连接MySQL 8.4时缺少`allowPublicKeyRetrieval=true`，无法完成`caching_sha2_password`认证 | 已关闭：补充JDBC参数 |
| DEF-003 | TC-005 | 一般 | Nacos 3不再提供旧版`/nacos/v1/console/health/readiness`端点，健康检查持续404 | 已关闭：改为校验8848和8080双端口 |
| DEF-004 | TC-010 | 严重 | Nacos官方启动脚本启用xtrace，会把鉴权环境变量展开到容器日志 | 已关闭：使用`BASH_XTRACEFD`将xtrace定向到`/dev/null`并验证当前日志无密钥 |
| DEF-005 | TC-008 | 严重 | 原自动部署job因`production` Environment缺少`DEPLOY_PORT`导致SSH以空端口失败 | 已关闭：按用户确认移除自动部署job；手动部署不依赖该配置 |
| DEF-006 | TC-006 | 严重 | 上海中间件 Docker 端口映射仍绑定`127.0.0.1`，东京对`3306/6379/8848/9848/8858`全部不可达；现有 Gateway 仍使用单机网络别名 | 开放：须先调整上海绑定地址和安全组，再重新核验 |
| DEF-007 | TC-006 | 一般 | 硅谷服务器 SSH 主机密钥校验失败，无法完成 Docker、资源和端口核验 | 开放：用户确认主机密钥后重新核验；不绕过校验 |

| 指标 | 数量 |
| --- | ---: |
| 用例总数 | 10 |
| 通过 | 4 |
| 失败 | 2 |
| 阻塞 | 0 |
| 不适用 | 0 |
| 未执行 | 4 |

- 测试结论：四个中间件的独立Compose、首次初始化、绑定目录持久化、固定别名、回环端口和密钥日志检查通过；六套应用与Ingress的Compose静态解析通过，但远程核验确认三机跨主机前置条件未满足，不能进入应用部署。
- 未完成项：上海中间件跨主机开放、腾讯云安全组核验、东京 Docker/Java 准备、硅谷主机核验、服务器应用部署、Nacos跨主机注册、Nginx网络隔离、应用镜像运行时检查、MySQL备份恢复、目录迁移和应用回滚。
- 遗留风险：上海主机级单点、东京资源余量、硅谷 SSH 主机密钥状态、腾讯云安全组规则和MySQL物理迁移兼容性尚未验证。
- 数据清理结果：Redis验证键`codex:deploy:20260917`已删除；中间件目录和容器作为本次部署目标保留。

## 8. 变更记录

| 日期 | 版本 | 变更内容 | 修改人 |
| --- | --- | --- | --- |
| 2026-09-17 | 0.1 | 建立分层部署、镜像发布、回滚和安全验证用例 | Codex |
| 2026-09-17 | 0.2 | 按独立中间件目录、本地持久化、双网络和固定别名重建测试范围 | Codex |
| 2026-09-17 | 0.3 | 归档腾讯云中间件实机部署、重建、持久化、安全验证及缺陷修复结果 | Codex |
| 2026-09-18 | 0.4 | 记录首次Actions执行失败并增加部署变量预检验证 | Codex |
| 2026-09-18 | 0.5 | 按用户确认取消Actions自动发布，改为验证镜像发布和服务器手动拉取 | Codex |
| 2026-09-18 | 0.6 | 应用改为按服务独立Compose，增加Gateway目录、资源限制和逐应用发布检查 | Codex |
| 2026-09-20 | 0.7 | 增加六服务三机Compose静态检查、JVM资源边界和跨主机注册检查 | Codex |
| 2026-09-20 | 0.8 | 完成上海/东京远程只读核验，记录跨主机端口不可达、东京运行时缺失和硅谷主机密钥阻断 | Codex |
