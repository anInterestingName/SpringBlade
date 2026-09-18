# Docker Compose 分层部署与 GitHub Actions 发布测试文档

## 1. 文档信息

| 项目 | 内容 |
| --- | --- |
| 测试编号 | TEST-REQ-2026-006 |
| 关联需求 | [REQ-2026-006](../requirements/REQ-2026-006-compose-deployment.md) |
| 关联详细设计 | [DESIGN-REQ-2026-006](../design/DESIGN-REQ-2026-006-compose-deployment.md) |
| 关联数据库设计 | 不涉及 |
| 文档版本 | 0.3 |
| 文档状态 | 执行中 |
| 测试负责人 | Codex |
| 测试日期 | 2026-09-17完成中间件实机部署验证；应用与CI/CD待执行 |

## 2. 测试范围与依据

- 测试目标：验证中间件独立Compose、本地目录持久化、双网络通信、镜像发布、应用部署和回滚。
- 范围内：Java编译、Dockerfile、六套Compose、目录权限、MySQL初始化、Nacos配置、网络别名、GHCR、SSH部署、备份迁移和回滚。
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
| 网络 | `springblade-backend`、`springblade-ingress` |
| 镜像仓库 | 本次不涉及 |
| 目标服务 | MySQL、Redis、Nacos、Sentinel；应用与入口不部署 |
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

### TC-003 应用与入口Compose配置

- 优先级：P0
- 关联需求/验收标准：REQ-001、REQ-004 / AC-002、AC-008
- 测试层级：部署静态检查
- 目标模块与入口：`springblade-app`、`springblade-ingress`。
- 前置条件：应用和入口Compose已适配双网络。
- 步骤：执行`docker compose config --quiet`，检查网络、端口、镜像标签和服务范围。
- 预期结果：全部应用加入`springblade-backend`；Gateway额外加入`springblade-ingress`；Nginx只加入入口网络；Nacos Console使用宿主机18080，Gateway使用8080。
- 证据：Compose输出和解析后的配置。
- 清理：删除测试用`.env`。
- 实际结果：待执行。
- 结果：未执行

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
- 预期结果：后端网络通信正常；Nginx只能访问Gateway，不能解析或访问中间件；服务重建后别名不变，不依赖固定IP。
- 证据：`docker network inspect`、连接检查和容器日志。
- 清理：无。
- 实际结果：已验证四个中间件只加入`springblade-backend`且固定别名正确；应用与Nginx未按本次部署范围启动，因此应用访问和入口网络隔离步骤未执行。
- 结果：未执行

### TC-007 Nacos生产配置与服务注册

- 优先级：P0
- 关联需求/验收标准：REQ-003 / AC-005、AC-006
- 测试层级：集成
- 前置条件：Nacos、MySQL、Redis和应用测试环境可用。
- 步骤：创建`prod` namespace，导入`blade.yaml`和`blade-prod.yaml`，启动应用并检查注册列表和依赖连接。
- 预期结果：10个应用按实际范围注册；地址使用固定网络别名；无旧固定IP；配置和日志不泄露敏感值。
- 证据：Nacos注册列表、应用日志和连接结果。
- 清理：停止测试应用，保留归属明确的测试中间件。
- 实际结果：已创建`prod` namespace并导入`blade.yaml`、`blade-prod.yaml`；应用未部署，服务注册和应用依赖连接未执行。
- 结果：未执行

### TC-008 GitHub Actions发布与应用隔离

- 优先级：P0
- 关联需求/验收标准：REQ-001、REQ-004 / AC-001、AC-007、AC-008
- 测试层级：CI/CD和部署集成
- 前置条件：测试GHCR、SSH和production Environment已准备。
- 步骤：记录四个中间件容器ID和数据目录摘要；触发工作流；检查10个SHA镜像和应用更新；复查中间件。
- 预期结果：全部镜像使用同一12位SHA；只更新`springblade-app`；中间件容器、目录、数据库和Nacos配置不变。
- 证据：Actions日志、镜像清单、容器ID和目录摘要。
- 清理：按镜像保留策略处理测试镜像，不删除未知版本。
- 实际结果：待执行。
- 结果：未执行

### TC-009 Git SHA回滚

- 优先级：P0
- 关联需求/验收标准：REQ-004 / AC-009
- 测试层级：部署集成
- 前置条件：存在当前SHA和上一稳定SHA。
- 步骤：部署新SHA，再执行`sh deploy.sh <上一稳定SHA>`，检查应用镜像和中间件状态。
- 预期结果：全部应用恢复上一SHA，中间件容器和本地数据目录无变化。
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
- [ ] Gateway同时加入后端和入口网络。
- [ ] Nginx只加入入口网络，无法访问中间件。
- [x] Nacos Console映射到18080；Gateway未部署，宿主机8080未占用。
- [x] 中间件端口仅绑定宿主机回环地址，没有通过容器端口向公网开放。
- [ ] 应用镜像使用非root UID和不可变SHA标签。
- [x] 新建`blade`保持空库，未自动执行业务全量SQL。
- [x] 当前中间件启动与验证日志不泄露已配置的密码、Token和身份密钥。

## 6. 验收覆盖矩阵

| 验收标准 | 测试用例 | 结果 | 说明 |
| --- | --- | --- | --- |
| AC-001、AC-002 | TC-002、TC-003、TC-005、TC-006、TC-008 | 部分通过 | 中间件独立Compose、持久化和后端网络通过；应用与入口待执行 |
| AC-003、AC-004 | TC-004 | 未执行 | 镜像运行时验证 |
| AC-005、AC-006 | TC-001、TC-006、TC-007、TC-010 | 部分通过 | 编译通过，真实连接和安全检查待执行 |
| AC-007、AC-008 | TC-008 | 未执行 | 构建和部署 |
| AC-009 | TC-009 | 未执行 | 回滚 |
| AC-010、AC-011 | TC-005、TC-010 | 部分通过 | 中间件首次部署通过；备份恢复和迁移待执行 |

## 7. 缺陷与汇总

| 缺陷编号 | 关联用例 | 严重级别 | 描述 | 状态 |
| --- | --- | --- | --- | --- |
| DEF-001 | TC-005 | 严重 | Nacos Schema源文件直接位于MySQL entrypoint目录会被重复执行，首次启动出现`No database selected`并重启一次 | 已关闭：改为脚本与Schema源文件分别挂载 |
| DEF-002 | TC-005 | 严重 | Nacos连接MySQL 8.4时缺少`allowPublicKeyRetrieval=true`，无法完成`caching_sha2_password`认证 | 已关闭：补充JDBC参数 |
| DEF-003 | TC-005 | 一般 | Nacos 3不再提供旧版`/nacos/v1/console/health/readiness`端点，健康检查持续404 | 已关闭：改为校验8848和8080双端口 |
| DEF-004 | TC-010 | 严重 | Nacos官方启动脚本启用xtrace，会把鉴权环境变量展开到容器日志 | 已关闭：使用`BASH_XTRACEFD`将xtrace定向到`/dev/null`并验证当前日志无密钥 |

| 指标 | 数量 |
| --- | ---: |
| 用例总数 | 10 |
| 通过 | 3 |
| 失败 | 0 |
| 阻塞 | 0 |
| 不适用 | 0 |
| 未执行 | 7 |

- 测试结论：四个中间件的独立Compose、首次初始化、绑定目录持久化、固定别名、回环端口和密钥日志检查通过；需求整体仍处于开发中。
- 未完成项：应用与入口Compose、应用镜像、GHCR、Actions部署、Nginx网络隔离、MySQL备份恢复、目录迁移和应用回滚。
- 遗留风险：单机主机级单点、腾讯云安全组云侧规则和MySQL物理迁移兼容性尚未验证。
- 数据清理结果：Redis验证键`codex:deploy:20260917`已删除；中间件目录和容器作为本次部署目标保留。

## 8. 变更记录

| 日期 | 版本 | 变更内容 | 修改人 |
| --- | --- | --- | --- |
| 2026-09-17 | 0.1 | 建立分层部署、镜像发布、回滚和安全验证用例 | Codex |
| 2026-09-17 | 0.2 | 按独立中间件目录、本地持久化、双网络和固定别名重建测试范围 | Codex |
| 2026-09-17 | 0.3 | 归档腾讯云中间件实机部署、重建、持久化、安全验证及缺陷修复结果 | Codex |
