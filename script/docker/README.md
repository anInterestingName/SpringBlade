# Docker Compose 部署目录

目标部署结构：

- `middleware/mysql/`：MySQL独立Compose、配置、初始化脚本、本地`data`和`backup`。
- `middleware/redis/`：Redis独立Compose、配置、本地`data`和`backup`。
- `middleware/nacos/`：Nacos独立Compose、配置、本地`data`和`logs`。
- `middleware/sentinel/`：Sentinel独立Compose及本地目录。
- `app/`：SpringBlade微服务，只引用远程镜像。
- `ingress/`：Nginx入口，只代理`blade-gateway`。

全部服务通过`springblade-backend`和`springblade-ingress` external network通信，不使用固定容器IP。真实`.env`和持久化目录不得提交到Git。

完整目录、参数、部署、备份和迁移步骤见[`doc/guide/docker-compose-github-actions-deployment.md`](../../doc/guide/docker-compose-github-actions-deployment.md)。
