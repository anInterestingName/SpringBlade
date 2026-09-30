-- REQ-2026-008 大模型供应商与模型配置管理升级脚本
-- 单次执行脚本。执行前必须确认 blade_ai_provider_config 与 blade_ai_model_config 均不存在。

START TRANSACTION;

-- 1. 供应商接入配置表
CREATE TABLE IF NOT EXISTS blade_ai_provider_config (
  id bigint NOT NULL COMMENT '主键',
  provider_code varchar(64) NOT NULL COMMENT '所有人范围内的供应商管理编码',
  provider_name varchar(100) NOT NULL COMMENT '供应商显示名称快照',
  preset_dict_id bigint NULL DEFAULT NULL COMMENT '供应商预设字典项ID',
  base_url varchar(1024) NOT NULL COMMENT '接入根地址',
  api_key_value text NULL COMMENT '用户提供的接入凭据',
  owner_user_id bigint NOT NULL COMMENT '配置所有人',
  lock_version bigint NOT NULL DEFAULT 0 COMMENT '并发控制版本',
  status int NOT NULL DEFAULT 0 COMMENT '0停用 1启用',
  tenant_id varchar(12) NOT NULL DEFAULT '000000' COMMENT '租户ID',
  create_user bigint NULL DEFAULT NULL COMMENT '实际创建人',
  create_dept bigint NULL DEFAULT NULL COMMENT '创建部门',
  create_time datetime NULL DEFAULT NULL COMMENT '创建时间',
  update_user bigint NULL DEFAULT NULL COMMENT '最近修改人',
  update_time datetime NULL DEFAULT NULL COMMENT '最近修改时间',
  is_deleted int NOT NULL DEFAULT 0 COMMENT '是否已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_blade_ai_provider_owner_code (tenant_id, owner_user_id, provider_code),
  KEY idx_blade_ai_provider_owner_list (tenant_id, owner_user_id, is_deleted, update_time, id),
  KEY idx_blade_ai_provider_tenant_list (tenant_id, is_deleted, update_time, id),
  KEY idx_blade_ai_provider_global_list (is_deleted, update_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户大模型供应商接入配置';

-- 2. 模型配置表
CREATE TABLE IF NOT EXISTS blade_ai_model_config (
  id bigint NOT NULL COMMENT '主键',
  provider_id bigint NOT NULL COMMENT '所属供应商配置ID',
  model_code varchar(64) NOT NULL COMMENT '所有人范围内的模型管理编码',
  model_name varchar(100) NOT NULL COMMENT '模型显示名称',
  upstream_model_id varchar(128) NOT NULL COMMENT '上游模型标识',
  api_protocol varchar(32) NOT NULL COMMENT '模型API协议稳定标识',
  capability_type varchar(16) NOT NULL COMMENT '模型主要能力类型',
  context_window int NULL DEFAULT NULL COMMENT '上下文窗口',
  input_types text NOT NULL COMMENT '单模型输入模态JSON数组',
  reasoning_efforts text NULL COMMENT '单模型推理档位JSON对象',
  owner_user_id bigint NOT NULL COMMENT '配置所有人',
  lock_version bigint NOT NULL DEFAULT 0 COMMENT '并发控制版本',
  status int NOT NULL DEFAULT 0 COMMENT '0停用 1启用',
  tenant_id varchar(12) NOT NULL DEFAULT '000000' COMMENT '租户ID',
  create_user bigint NULL DEFAULT NULL COMMENT '实际创建人',
  create_dept bigint NULL DEFAULT NULL COMMENT '创建部门',
  create_time datetime NULL DEFAULT NULL COMMENT '创建时间',
  update_user bigint NULL DEFAULT NULL COMMENT '最近修改人',
  update_time datetime NULL DEFAULT NULL COMMENT '最近修改时间',
  is_deleted int NOT NULL DEFAULT 0 COMMENT '是否已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_blade_ai_model_owner_code (tenant_id, owner_user_id, model_code),
  KEY idx_blade_ai_model_provider (tenant_id, provider_id, is_deleted, id),
  KEY idx_blade_ai_model_owner_list (tenant_id, owner_user_id, is_deleted, update_time, id),
  KEY idx_blade_ai_model_tenant_list (tenant_id, is_deleted, update_time, id),
  KEY idx_blade_ai_model_global_list (is_deleted, update_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户大模型配置';

-- 3. 字典数据 (ai_model_api_protocol, ai_model_capability, ai_provider_preset)
-- 协议父项
INSERT INTO blade_dict (id, parent_id, code, dict_key, dict_value, sort, remark, is_deleted)
SELECT 220260930000000001, 0, 'ai_model_api_protocol', -1, '大模型API协议', 1, '大模型API协议类型', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM blade_dict WHERE code = 'ai_model_api_protocol' AND parent_id = 0 AND is_deleted = 0);

-- 协议子项
INSERT INTO blade_dict (id, parent_id, code, dict_key, dict_value, sort, remark, is_deleted)
SELECT seed.id, 220260930000000001, 'ai_model_api_protocol', seed.dict_key, seed.dict_value, seed.sort, seed.remark, 0
FROM (
  SELECT 220260930000000002 AS id, 1 AS dict_key, 'openai-completions' AS dict_value, 1 AS sort, 'OpenAI Chat Completions 兼容协议' AS remark
  UNION ALL
  SELECT 220260930000000003, 2, 'openai-responses', 2, 'OpenAI Responses 协议'
  UNION ALL
  SELECT 220260930000000004, 3, 'anthropic-messages', 3, 'Anthropic Messages 协议'
) seed
WHERE NOT EXISTS (
  SELECT 1 FROM blade_dict existing WHERE existing.code = 'ai_model_api_protocol' AND existing.dict_value = seed.dict_value AND existing.is_deleted = 0
);

-- 能力父项
INSERT INTO blade_dict (id, parent_id, code, dict_key, dict_value, sort, remark, is_deleted)
SELECT 220260930000000010, 0, 'ai_model_capability', -1, '大模型能力类型', 2, '大模型主要能力类型与输入模态', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM blade_dict WHERE code = 'ai_model_capability' AND parent_id = 0 AND is_deleted = 0);

-- 能力子项
INSERT INTO blade_dict (id, parent_id, code, dict_key, dict_value, sort, remark, is_deleted)
SELECT seed.id, 220260930000000010, 'ai_model_capability', seed.dict_key, seed.dict_value, seed.sort, seed.remark, 0
FROM (
  SELECT 220260930000000011 AS id, 1 AS dict_key, 'text' AS dict_value, 1 AS sort, '文本能力' AS remark
  UNION ALL
  SELECT 220260930000000012, 2, 'image', 2, '图像能力'
  UNION ALL
  SELECT 220260930000000013, 3, 'video', 3, '视频能力'
) seed
WHERE NOT EXISTS (
  SELECT 1 FROM blade_dict existing WHERE existing.code = 'ai_model_capability' AND existing.dict_value = seed.dict_value AND existing.is_deleted = 0
);

-- 供应商预设父项
INSERT INTO blade_dict (id, parent_id, code, dict_key, dict_value, sort, remark, is_deleted)
SELECT 220260930000000020, 0, 'ai_provider_preset', -1, '大模型供应商预设', 3, '大模型供应商预设清单', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM blade_dict WHERE code = 'ai_provider_preset' AND parent_id = 0 AND is_deleted = 0);

-- 供应商预设子项
INSERT INTO blade_dict (id, parent_id, code, dict_key, dict_value, sort, remark, is_deleted)
SELECT seed.id, 220260930000000020, 'ai_provider_preset', seed.dict_key, seed.dict_value, seed.sort, seed.remark, 0
FROM (
  SELECT 220260930000000021 AS id, 1 AS dict_key, 'OpenAI' AS dict_value, 1 AS sort, 'OpenAI 官方' AS remark
  UNION ALL
  SELECT 220260930000000022, 2, 'Anthropic', 2, 'Anthropic Claude'
  UNION ALL
  SELECT 220260930000000023, 3, 'Google Gemini', 3, 'Google Gemini'
  UNION ALL
  SELECT 220260930000000024, 4, 'Microsoft Azure OpenAI', 4, 'Azure OpenAI 服务'
  UNION ALL
  SELECT 220260930000000025, 5, '阿里云百炼', 5, '阿里云百炼 / 通义千问'
  UNION ALL
  SELECT 220260930000000026, 6, 'DeepSeek', 6, '深度求索 DeepSeek'
  UNION ALL
  SELECT 220260930000000027, 7, '百度千帆', 7, '百度智能云千帆大模型平台'
  UNION ALL
  SELECT 220260930000000028, 8, '腾讯混元', 8, '腾讯混元大模型'
  UNION ALL
  SELECT 220260930000000029, 9, '智谱 GLM', 9, '智谱 BigModel'
  UNION ALL
  SELECT 220260930000000030, 10, 'Moonshot', 10, '月之暗面 Kimi'
  UNION ALL
  SELECT 220260930000000031, 11, '火山方舟', 11, '字节跳动火山方舟'
  UNION ALL
  SELECT 220260930000000032, 12, 'xAI', 12, 'xAI Grok'
  UNION ALL
  SELECT 220260930000000033, 13, 'Mistral', 13, 'Mistral AI'
) seed
WHERE NOT EXISTS (
  SELECT 1 FROM blade_dict existing WHERE existing.code = 'ai_provider_preset' AND existing.dict_value = seed.dict_value AND existing.is_deleted = 0
);

-- 4. 权限与接口范围资源
INSERT INTO blade_scope_api
  (id, menu_id, resource_code, scope_name, scope_path, scope_type, remark, status, is_deleted)
SELECT seed.id, NULL, seed.resource_code, seed.scope_name, seed.scope_path,
       2, seed.remark, 1, 0
FROM (
  SELECT 220260930100000001 AS id, 'ai:model:provider:view' AS resource_code,
         '供应商配置查看' AS scope_name, '/blade-ai/ai/provider/**' AS scope_path, '供应商配置列表与详情' AS remark
  UNION ALL
  SELECT 220260930100000002, 'ai:model:provider:create',
         '供应商配置新增', '/blade-ai/ai/provider/create', '创建供应商配置'
  UNION ALL
  SELECT 220260930100000003, 'ai:model:provider:edit',
         '供应商配置编辑', '/blade-ai/ai/provider/update', '更新供应商配置与启停状态'
  UNION ALL
  SELECT 220260930100000004, 'ai:model:provider:delete',
         '供应商配置删除', '/blade-ai/ai/provider/remove', '删除供应商配置'
  UNION ALL
  SELECT 220260930100000005, 'ai:model:config:view',
         '模型配置查看', '/blade-ai/ai/model/**', '模型配置列表与详情'
  UNION ALL
  SELECT 220260930100000006, 'ai:model:config:create',
         '模型配置新增', '/blade-ai/ai/model/create', '创建模型配置'
  UNION ALL
  SELECT 220260930100000007, 'ai:model:config:edit',
         '模型配置编辑', '/blade-ai/ai/model/update', '更新模型配置与启停状态'
  UNION ALL
  SELECT 220260930100000008, 'ai:model:config:delete',
         '模型配置删除', '/blade-ai/ai/model/remove', '删除模型配置'
  UNION ALL
  SELECT 220260930100000009, 'ai:model:runtime:read',
         '模型运行时读取', '/feign/client/ai-model-config/v1/runtime', '任务服务内部读取运行配置与凭据'
) seed
WHERE NOT EXISTS (
  SELECT 1 FROM blade_scope_api existing
  WHERE existing.resource_code = seed.resource_code AND existing.is_deleted = 0
)
AND NOT EXISTS (SELECT 1 FROM blade_scope_api existing_id WHERE existing_id.id = seed.id);

COMMIT;
