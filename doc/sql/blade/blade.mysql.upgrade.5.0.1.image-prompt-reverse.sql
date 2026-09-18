-- REQ-2026-004 图片提示词反推权限初始化
-- 不变更业务表；默认仅授权 000000 租户 administrator。

START TRANSACTION;

SET @prompt_menu_id = (
  SELECT id FROM blade_menu
  WHERE code = 'prompt' AND category = 1 AND is_deleted = 0
  ORDER BY id LIMIT 1
);

INSERT INTO blade_menu
  (id, parent_id, code, name, alias, path, source, sort, category, `action`, is_open, remark, is_deleted)
SELECT seed.id, @prompt_menu_id, seed.code, seed.name, seed.alias, seed.path, seed.source,
       seed.sort, 2, seed.action_type, 1, seed.remark, 0
FROM (
  SELECT 220260917100000001 AS id, 'prompt_reverse' AS code, '图片反推' AS name,
         'prompt_reverse' AS alias, '/asset/prompt/reverse' AS path, 'picture' AS source,
         10 AS sort, 1 AS action_type, '图片提示词反推入口' AS remark
  UNION ALL
  SELECT 220260917100000002, 'prompt_system_manage', '系统策略',
         'prompt_system_manage', '/asset/prompt/system-manage', 'setting',
         11, 3, '系统提示词策略管理权限'
) seed
WHERE @prompt_menu_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM blade_menu existing
    WHERE existing.code = seed.code AND existing.category = 2 AND existing.is_deleted = 0
  )
  AND NOT EXISTS (SELECT 1 FROM blade_menu existing_id WHERE existing_id.id = seed.id);

UPDATE blade_menu
SET parent_id = @prompt_menu_id, category = 2, is_open = 1, is_deleted = 0
WHERE code IN ('prompt_reverse', 'prompt_system_manage')
  AND category = 2 AND @prompt_menu_id IS NOT NULL;

INSERT INTO blade_scope_api
  (id, menu_id, resource_code, scope_name, scope_path, scope_type, remark, status, is_deleted)
SELECT seed.id, @prompt_menu_id, seed.resource_code, seed.scope_name, seed.scope_path,
       2, seed.remark, 1, 0
FROM (
  SELECT 220260917000000001 AS id, 'ai:prompt:reverse' AS resource_code,
         '图片提示词反推' AS scope_name, '/blade-ai/prompt/reverse' AS scope_path,
         '上传单图并返回受控标签和生图提示词' AS remark
  UNION ALL
  SELECT 220260917000000002, 'ai:prompt:system-manage',
         '系统提示词策略管理', '/blade-ai/prompt/**',
         '维护SYSTEM提示词和保留分析策略'
  UNION ALL
  SELECT 220260917000000003, 'system:tag:runtime',
         '标签运行时读取', '/feign/client/tag-runtime/taxonomy',
         '内部服务读取当前租户有效标签体系'
) seed
WHERE NOT EXISTS (
  SELECT 1 FROM blade_scope_api existing
  WHERE existing.resource_code = seed.resource_code AND existing.is_deleted = 0
)
AND NOT EXISTS (SELECT 1 FROM blade_scope_api existing_id WHERE existing_id.id = seed.id);

UPDATE blade_scope_api
SET menu_id = @prompt_menu_id, status = 1, is_deleted = 0
WHERE resource_code IN ('ai:prompt:reverse', 'ai:prompt:system-manage', 'system:tag:runtime');

SET @administrator_role_id = (
  SELECT id FROM blade_role
  WHERE tenant_id = '000000' AND role_alias = 'administrator' AND is_deleted = 0
  ORDER BY id LIMIT 1
);

INSERT INTO blade_role_menu (id, menu_id, role_id)
SELECT seed.id, seed.menu_id, @administrator_role_id
FROM (
  SELECT 220260917200000001 AS id, (
    SELECT id FROM blade_menu WHERE code = 'prompt_reverse' AND category = 2 AND is_deleted = 0 LIMIT 1
  ) AS menu_id
  UNION ALL
  SELECT 220260917200000002, (
    SELECT id FROM blade_menu WHERE code = 'prompt_system_manage' AND category = 2 AND is_deleted = 0 LIMIT 1
  )
) seed
WHERE @administrator_role_id IS NOT NULL AND seed.menu_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM blade_role_menu existing
    WHERE existing.role_id = @administrator_role_id AND existing.menu_id = seed.menu_id
  )
  AND NOT EXISTS (SELECT 1 FROM blade_role_menu existing_id WHERE existing_id.id = seed.id);

INSERT INTO blade_role_scope (id, scope_category, scope_id, role_id)
SELECT seed.id, 2, seed.scope_id, @administrator_role_id
FROM (
  SELECT CASE resource_code
           WHEN 'ai:prompt:reverse' THEN 220260917300000001
           WHEN 'ai:prompt:system-manage' THEN 220260917300000002
           WHEN 'system:tag:runtime' THEN 220260917300000003
         END AS id,
         MIN(id) AS scope_id
  FROM blade_scope_api
  WHERE resource_code IN ('ai:prompt:reverse', 'ai:prompt:system-manage', 'system:tag:runtime')
    AND is_deleted = 0
  GROUP BY resource_code
) seed
WHERE @administrator_role_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM blade_role_scope existing
    WHERE existing.scope_category = 2 AND existing.role_id = @administrator_role_id
      AND existing.scope_id = seed.scope_id
  )
  AND NOT EXISTS (SELECT 1 FROM blade_role_scope existing_id WHERE existing_id.id = seed.id);

COMMIT;

SELECT id, parent_id, code, name, alias, path, category, is_deleted
FROM blade_menu
WHERE code IN ('prompt_reverse', 'prompt_system_manage')
ORDER BY id;

SELECT scope.resource_code, scope.scope_path,
       CASE WHEN role_scope.id IS NULL THEN 0 ELSE 1 END AS administrator_granted
FROM blade_scope_api scope
LEFT JOIN blade_role_scope role_scope
  ON role_scope.scope_category = 2 AND role_scope.scope_id = scope.id
 AND role_scope.role_id = @administrator_role_id
WHERE scope.resource_code IN ('ai:prompt:reverse', 'ai:prompt:system-manage', 'system:tag:runtime')
ORDER BY scope.id;

-- 缺失系统分析策略的租户必须由开通或运维流程创建并发布，脚本不猜测正文和所有者。
SELECT role.tenant_id
FROM blade_role role
WHERE role.role_alias IN ('admin', 'administrator') AND role.is_deleted = 0
GROUP BY role.tenant_id
HAVING NOT EXISTS (
  SELECT 1 FROM blade_ai_prompt prompt
  WHERE prompt.tenant_id = role.tenant_id
    AND prompt.prompt_code = 'image_prompt_reverse'
    AND prompt.prompt_type = 'SYSTEM'
    AND prompt.publish_mode = 1
    AND prompt.status = 1
    AND prompt.current_version_id IS NOT NULL
    AND prompt.is_deleted = 0
);
