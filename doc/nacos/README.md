* SpringBlade的注册中心
* 具体文档详见：https://nacos.io/zh-cn/docs/quick-start.html
* SpringBlade配置详见：https://www.kancloud.cn/smallchill/blade/913215

授权服务沿用公共 `BladeApplication` 启动链路，按 profile 使用 `blade.yaml`、`blade-{profile}.yaml` 和 `blade-authorization-server-{profile}.yaml`，统一发布到对应 Nacos namespace 的 `DEFAULT_GROUP`。
