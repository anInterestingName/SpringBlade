# Docker Compose 部署目录

目标部署结构：

- `middleware/mysql/`：MySQL独立Compose、配置、初始化脚本、本地`data`和`backup`。
- `middleware/redis/`：Redis独立Compose、配置、本地`data`和`backup`。
- `middleware/nacos/`：Nacos独立Compose、配置、本地`data`和`logs`。
- `middleware/sentinel/`：Sentinel独立Compose及本地目录。
- `app/<service>/`：每个 SpringBlade 应用使用独立目录、独立环境文件和独立 Compose project，只引用远程镜像；六服务三机分配见[`三服务器部署文档`](../../doc/guide/three-server-compose-deployment.md)。
- `ingress/`：Nginx入口，只代理`blade-gateway`。

单机基础设施通过`springblade-backend`和`springblade-ingress` external network通信，不使用固定容器IP。三机应用模式下 Docker bridge 只在本机生效，远端应用通过上海节点的公网白名单地址访问中间件，并通过 Nacos 注册宿主机地址。真实`.env`和持久化目录不得提交到Git。

完整目录、参数、部署、备份和迁移步骤见[`doc/guide/docker-compose-github-actions-deployment.md`](../../doc/guide/docker-compose-github-actions-deployment.md)。
