-- REQ-2026-001 / REQ-2026-009 提示词菜单与权限初始化
-- 适用范围：提示词业务表和 blade_scope_api 基础数据已经部署的环境。
-- 默认授权：仅授权 000000 租户的 administrator 角色，不授权普通租户管理员。
-- 数据权限：REQ-2026-005 通过独立升级脚本初始化提示词 OWN/ALL DataScope，本脚本只维护菜单与 API Scope。

START TRANSACTION;

-- 固定资源 ID。若环境中已存在同 code 的菜单，将复用已有菜单 ID。
SET @asset_menu_seed_id = 220260909100000001;
SET @prompt_menu_seed_id = 220260909100000002;

INSERT INTO blade_menu
  (id, parent_id, code, name, alias, path, source, sort, category, `action`, is_open, remark, is_deleted)
SELECT
  @asset_menu_seed_id,
  0,
  'asset',
  '资产管理',
  'menu',
  '/asset',
  'iconfont iconicon_savememo',
  6,
  1,
  0,
  1,
  '可复用业务资产管理',
  0
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM blade_menu WHERE code = 'asset' AND category = 1 AND is_deleted = 0
)
AND NOT EXISTS (
  SELECT 1 FROM blade_menu WHERE id = @asset_menu_seed_id
);

SET @asset_menu_id = (
  SELECT id
  FROM blade_menu
  WHERE code = 'asset' AND category = 1
  ORDER BY is_deleted, id
  LIMIT 1
);

UPDATE blade_menu
SET parent_id = 0,
    name = '资产管理',
    alias = 'menu',
    path = '/asset',
    sort = 6,
    category = 1,
    `action` = 0,
    is_open = 1,
    is_deleted = 0
WHERE id = @asset_menu_id;

INSERT INTO blade_menu
  (id, parent_id, code, name, alias, path, source, sort, category, `action`, is_open, remark, is_deleted)
SELECT
  @prompt_menu_seed_id,
  @asset_menu_id,
  'prompt',
  '提示词管理',
  'menu',
  '/asset/prompt',
  'iconfont iconicon_doc',
  1,
  1,
  0,
  1,
  '提示词草稿、预览与版本发布',
  0
FROM DUAL
WHERE @asset_menu_id IS NOT NULL
AND NOT EXISTS (
  SELECT 1 FROM blade_menu WHERE code = 'prompt' AND category = 1 AND is_deleted = 0
)
AND NOT EXISTS (
  SELECT 1 FROM blade_menu WHERE id = @prompt_menu_seed_id
);

SET @prompt_menu_id = (
  SELECT id
  FROM blade_menu
  WHERE code = 'prompt' AND category = 1
  ORDER BY is_deleted, id
  LIMIT 1
);

UPDATE blade_scope_data
SET menu_id = @prompt_menu_id
WHERE resource_code IN (
  'ai:prompt:data:page:own', 'ai:prompt:data:page:all',
  'ai:prompt:data:resource:own', 'ai:prompt:data:resource:all'
)
  AND @prompt_menu_id IS NOT NULL;

-- 统一已有提示词菜单的父级和页面路径。
UPDATE blade_menu
SET parent_id = @asset_menu_id,
    name = '提示词管理',
    alias = 'menu',
    path = '/asset/prompt',
    sort = 1,
    category = 1,
    `action` = 0,
    is_open = 1,
    is_deleted = 0
WHERE id = @prompt_menu_id
  AND @asset_menu_id IS NOT NULL;

-- 九个前端按钮权限。action：1 工具栏，2 操作栏，3 工具操作栏。
INSERT INTO blade_menu
  (id, parent_id, code, name, alias, path, source, sort, category, `action`, is_open, remark, is_deleted)
SELECT seed.id,
       @prompt_menu_id,
       seed.code,
       seed.name,
       seed.alias,
       seed.path,
       seed.source,
       seed.sort,
       2,
       seed.action_type,
       1,
       '提示词管理按钮权限',
       0
FROM (
  SELECT 220260909100000003 AS id, 'prompt_view' AS code, '查看' AS name,
         'view' AS alias, '/asset/prompt/view' AS path, 'file-text' AS source,
         1 AS sort, 2 AS action_type
  UNION ALL
  SELECT 220260909100000004, 'prompt_add', '新增', 'add',
         '/asset/prompt/add', 'plus', 2, 1
  UNION ALL
  SELECT 220260909100000005, 'prompt_edit', '编辑', 'edit',
         '/asset/prompt/edit', 'form', 3, 2
  UNION ALL
  SELECT 220260909100000006, 'prompt_copy', '复制', 'copy',
         '/asset/prompt/copy', 'copy', 4, 3
  UNION ALL
  SELECT 220260909100000007, 'prompt_delete', '删除', 'delete',
         '/asset/prompt/delete', 'delete', 5, 2
  UNION ALL
  SELECT 220260909100000008, 'prompt_preview', '预览', 'preview',
         '/asset/prompt/preview', 'eye', 6, 3
  UNION ALL
  SELECT 220260909100000009, 'prompt_publish', '发布', 'publish',
         '/asset/prompt/publish', 'upload', 7, 3
  UNION ALL
  SELECT 220260909100000010, 'prompt_disable', '停用', 'disable',
         '/asset/prompt/disable', 'circle-close', 8, 3
  UNION ALL
  SELECT 220260909100000011, 'prompt_rollback', '回滚', 'rollback',
         '/asset/prompt/rollback', 'refresh-left', 9, 3
) seed
WHERE @prompt_menu_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1
    FROM blade_menu existing
    WHERE existing.code = seed.code
      AND existing.category = 2
      AND existing.is_deleted = 0
  )
  AND NOT EXISTS (
    SELECT 1 FROM blade_menu existing_id WHERE existing_id.id = seed.id
  );

UPDATE blade_menu
SET parent_id = @prompt_menu_id,
    category = 2,
    is_open = 1,
    is_deleted = 0
WHERE code IN (
  'prompt_view', 'prompt_add', 'prompt_edit', 'prompt_copy', 'prompt_delete',
  'prompt_preview', 'prompt_publish', 'prompt_disable', 'prompt_rollback'
)
  AND category = 2
  AND @prompt_menu_id IS NOT NULL;

-- 若 API Scope 尚未初始化则补齐；已存在时复用并在后续统一绑定菜单。
INSERT INTO blade_scope_api
  (id, menu_id, resource_code, scope_name, scope_path, scope_type, remark, status, is_deleted)
SELECT seed.id,
       @prompt_menu_id,
       seed.resource_code,
       seed.scope_name,
       seed.scope_path,
       2,
       seed.remark,
       1,
       0
FROM (
  SELECT 220260909000000001 AS id, 'ai:prompt:view' AS resource_code,
         '提示词查看' AS scope_name, '/blade-ai/prompt' AS scope_path,
         '提示词列表、详情和版本查询' AS remark
  UNION ALL
  SELECT 220260909000000002, 'ai:prompt:create', '提示词新增',
         '/blade-ai/prompt/create', '创建提示词草稿'
  UNION ALL
  SELECT 220260909000000003, 'ai:prompt:edit', '提示词编辑',
         '/blade-ai/prompt/update', '编辑提示词草稿'
  UNION ALL
  SELECT 220260909000000004, 'ai:prompt:copy', '提示词复制',
         '/blade-ai/prompt/copy', '复制提示词草稿'
  UNION ALL
  SELECT 220260909000000005, 'ai:prompt:delete', '提示词删除',
         '/blade-ai/prompt/remove', '删除从未发布的草稿'
  UNION ALL
  SELECT 220260909000000006, 'ai:prompt:preview', '提示词预览',
         '/blade-ai/prompt/preview', '无状态模板预览'
  UNION ALL
  SELECT 220260909000000007, 'ai:prompt:publish', '提示词发布',
         '/blade-ai/prompt/publish', '发布不可变版本'
  UNION ALL
  SELECT 220260909000000008, 'ai:prompt:disable', '提示词停用',
         '/blade-ai/prompt/disable', '停用当前发布提示词'
  UNION ALL
  SELECT 220260909000000009, 'ai:prompt:rollback', '提示词回滚',
         '/blade-ai/prompt/rollback', '基于历史版本生成新版本'
) seed
WHERE @prompt_menu_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1
    FROM blade_scope_api existing
    WHERE existing.resource_code = seed.resource_code
      AND existing.is_deleted = 0
  )
  AND NOT EXISTS (
    SELECT 1 FROM blade_scope_api existing_id WHERE existing_id.id = seed.id
  );

UPDATE blade_scope_api
SET menu_id = @prompt_menu_id,
    status = 1,
    is_deleted = 0
WHERE resource_code IN (
  'ai:prompt:view',
  'ai:prompt:create',
  'ai:prompt:edit',
  'ai:prompt:copy',
  'ai:prompt:delete',
  'ai:prompt:preview',
  'ai:prompt:publish',
  'ai:prompt:disable',
  'ai:prompt:rollback'
)
AND @prompt_menu_id IS NOT NULL;

-- 仅给平台超级管理员授权，普通租户角色仍需在角色管理页面按需授权。
SET @administrator_role_id = (
  SELECT id
  FROM blade_role
  WHERE tenant_id = '000000'
    AND role_alias = 'administrator'
    AND is_deleted = 0
  ORDER BY id
  LIMIT 1
);

INSERT INTO blade_role_menu (id, menu_id, role_id)
SELECT grant_seed.id, grant_seed.menu_id, @administrator_role_id
FROM (
  SELECT 220260909200000001 AS id, @asset_menu_id AS menu_id
  UNION ALL
  SELECT 220260909200000002, @prompt_menu_id
  UNION ALL
  SELECT 220260909200000003, (
    SELECT id FROM blade_menu WHERE code = 'prompt_view' AND category = 2 AND is_deleted = 0 LIMIT 1
  )
  UNION ALL
  SELECT 220260909200000004, (
    SELECT id FROM blade_menu WHERE code = 'prompt_add' AND category = 2 AND is_deleted = 0 LIMIT 1
  )
  UNION ALL
  SELECT 220260909200000005, (
    SELECT id FROM blade_menu WHERE code = 'prompt_edit' AND category = 2 AND is_deleted = 0 LIMIT 1
  )
  UNION ALL
  SELECT 220260909200000006, (
    SELECT id FROM blade_menu WHERE code = 'prompt_copy' AND category = 2 AND is_deleted = 0 LIMIT 1
  )
  UNION ALL
  SELECT 220260909200000007, (
    SELECT id FROM blade_menu WHERE code = 'prompt_delete' AND category = 2 AND is_deleted = 0 LIMIT 1
  )
  UNION ALL
  SELECT 220260909200000008, (
    SELECT id FROM blade_menu WHERE code = 'prompt_preview' AND category = 2 AND is_deleted = 0 LIMIT 1
  )
  UNION ALL
  SELECT 220260909200000009, (
    SELECT id FROM blade_menu WHERE code = 'prompt_publish' AND category = 2 AND is_deleted = 0 LIMIT 1
  )
  UNION ALL
  SELECT 220260909200000010, (
    SELECT id FROM blade_menu WHERE code = 'prompt_disable' AND category = 2 AND is_deleted = 0 LIMIT 1
  )
  UNION ALL
  SELECT 220260909200000011, (
    SELECT id FROM blade_menu WHERE code = 'prompt_rollback' AND category = 2 AND is_deleted = 0 LIMIT 1
  )
) grant_seed
WHERE @administrator_role_id IS NOT NULL
  AND grant_seed.menu_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1
    FROM blade_role_menu existing
    WHERE existing.role_id = @administrator_role_id
      AND existing.menu_id = grant_seed.menu_id
  )
  AND NOT EXISTS (
    SELECT 1 FROM blade_role_menu existing_id WHERE existing_id.id = grant_seed.id
  );

-- scope_category=2 表示接口权限。运行时 Scope 不授予前端管理员。
INSERT INTO blade_role_scope (id, scope_category, scope_id, role_id)
SELECT grant_seed.id, 2, grant_seed.scope_id, @administrator_role_id
FROM (
  SELECT CASE resource_code
           WHEN 'ai:prompt:view' THEN 220260909300000001
           WHEN 'ai:prompt:create' THEN 220260909300000002
           WHEN 'ai:prompt:edit' THEN 220260909300000003
           WHEN 'ai:prompt:copy' THEN 220260909300000004
           WHEN 'ai:prompt:delete' THEN 220260909300000005
           WHEN 'ai:prompt:preview' THEN 220260909300000006
           WHEN 'ai:prompt:publish' THEN 220260909300000007
           WHEN 'ai:prompt:disable' THEN 220260909300000008
           WHEN 'ai:prompt:rollback' THEN 220260909300000009
         END AS id,
         MIN(id) AS scope_id
  FROM blade_scope_api
  WHERE resource_code IN (
    'ai:prompt:view', 'ai:prompt:create', 'ai:prompt:edit', 'ai:prompt:copy',
    'ai:prompt:delete', 'ai:prompt:preview', 'ai:prompt:publish',
    'ai:prompt:disable', 'ai:prompt:rollback'
  )
    AND is_deleted = 0
  GROUP BY resource_code
) grant_seed
WHERE @administrator_role_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1
    FROM blade_role_scope existing
    WHERE existing.scope_category = 2
      AND existing.role_id = @administrator_role_id
      AND existing.scope_id = grant_seed.scope_id
  )
  AND NOT EXISTS (
    SELECT 1 FROM blade_role_scope existing_id WHERE existing_id.id = grant_seed.id
  );

-- 只有一个启用的平台顶部菜单时自动挂载；多个顶部菜单时由管理员选择目标菜单。
SET @active_top_menu_count = (
  SELECT COUNT(*)
  FROM blade_top_menu
  WHERE status = 1
    AND is_deleted = 0
    AND (tenant_id = '000000' OR tenant_id IS NULL)
);

SET @target_top_menu_id = (
  SELECT CASE WHEN @active_top_menu_count = 1 THEN MIN(id) ELSE NULL END
  FROM blade_top_menu
  WHERE status = 1
    AND is_deleted = 0
    AND (tenant_id = '000000' OR tenant_id IS NULL)
);

INSERT INTO blade_top_menu_setting (id, top_menu_id, menu_id)
SELECT mapping.id, @target_top_menu_id, mapping.menu_id
FROM (
  SELECT 220260909400000001 AS id, @asset_menu_id AS menu_id
  UNION ALL
  SELECT 220260909400000002, @prompt_menu_id
) mapping
WHERE @target_top_menu_id IS NOT NULL
  AND mapping.menu_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1
    FROM blade_top_menu_setting existing
    WHERE existing.top_menu_id = @target_top_menu_id
      AND existing.menu_id = mapping.menu_id
  )
  AND NOT EXISTS (
    SELECT 1 FROM blade_top_menu_setting existing_id WHERE existing_id.id = mapping.id
  );

COMMIT;

-- 执行结果核验：菜单应为 11 条（2 个菜单 + 9 个按钮）。
SELECT id, parent_id, code, name, path, category, `action`, is_deleted
FROM blade_menu
WHERE code IN (
  'asset', 'prompt', 'prompt_view', 'prompt_add', 'prompt_edit', 'prompt_copy',
  'prompt_delete', 'prompt_preview', 'prompt_publish', 'prompt_disable', 'prompt_rollback'
)
ORDER BY category, parent_id, sort;

-- 超级管理员菜单授权应为 11 条。
SELECT r.role_alias, menu.code, menu.name
FROM blade_role_menu role_menu
JOIN blade_role r ON r.id = role_menu.role_id
JOIN blade_menu menu ON menu.id = role_menu.menu_id
WHERE r.id = @administrator_role_id
  AND menu.code IN (
    'asset', 'prompt', 'prompt_view', 'prompt_add', 'prompt_edit', 'prompt_copy',
    'prompt_delete', 'prompt_preview', 'prompt_publish', 'prompt_disable', 'prompt_rollback'
  )
ORDER BY menu.category, menu.sort;

-- 九个管理 Scope 应绑定提示词菜单并授权 administrator；runtime 不在本结果中。
SELECT scope.id,
       scope.resource_code,
       scope.menu_id,
       CASE WHEN role_scope.id IS NULL THEN 0 ELSE 1 END AS administrator_granted
FROM blade_scope_api scope
LEFT JOIN blade_role_scope role_scope
  ON role_scope.scope_category = 2
 AND role_scope.scope_id = scope.id
 AND role_scope.role_id = @administrator_role_id
WHERE scope.resource_code IN (
  'ai:prompt:view', 'ai:prompt:create', 'ai:prompt:edit', 'ai:prompt:copy',
  'ai:prompt:delete', 'ai:prompt:preview', 'ai:prompt:publish',
  'ai:prompt:disable', 'ai:prompt:rollback'
)
ORDER BY scope.id;

-- active_top_menu_count=0 表示未启用顶部菜单；=1 已自动挂载；>1 需手工选择顶部菜单。
SELECT @active_top_menu_count AS active_top_menu_count,
       @target_top_menu_id AS auto_configured_top_menu_id;
