# 应用部署目录

每个 SpringBlade 应用使用独立目录、独立 `.env` 和独立 Compose project，禁止重新合并为一个全量应用栈。

当前六个服务按三台服务器分配：

| 服务器 | 服务目录 | Compose project |
| --- | --- | --- |
| 上海 | `gateway/` | `springblade-gateway` |
| 东京 | `auth/`、`system/` | `springblade-auth`、`springblade-system` |
| 硅谷 | `ai/`、`admin/`、`log/` | `springblade-ai`、`springblade-admin`、`springblade-log` |

每个服务目录都包含独立的 `compose.yml` 和 `.env.example`：

```text
app/
├── README.md
├── gateway/
│   ├── compose.yml
│   └── .env.example
├── auth/
│   ├── compose.yml
│   └── .env.example
├── system/
│   ├── compose.yml
│   └── .env.example
├── ai/
│   ├── compose.yml
│   └── .env.example
├── admin/
│   ├── compose.yml
│   └── .env.example
└── log/
    ├── compose.yml
    └── .env.example
```

三机模式下 Docker bridge 只在各自服务器本地生效，服务间通信通过上海节点的 Nacos 注册信息和宿主机映射端口完成。除 Gateway 与上海 Nginx 共用 `springblade-ingress` 外，应用 Compose 不依赖跨服务器 external network。

真实 `.env` 不得提交到 Git。部署前在目标应用目录复制 `.env.example`，填写实际镜像标签、上海节点地址、服务注册地址、Nacos、Redis、MySQL、AI 和密钥参数，再执行 `docker compose config --quiet`。
