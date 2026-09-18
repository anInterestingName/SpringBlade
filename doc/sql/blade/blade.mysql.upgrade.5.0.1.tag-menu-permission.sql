-- REQ-2026-002 / Saber REQ-2026-010 标签管理菜单与权限配置
-- 适用范围：blade_menu、blade_scope_api 已创建，标签管理接口准备联调的 MySQL 环境。
-- 默认授权：不写入 blade_role_menu、blade_role_scope 或 blade_top_menu_setting。
-- 执行方式：可重复执行；同编码的逻辑删除记录会恢复，已有有效记录会复用。

START TRANSACTION;

SET @tag_menu_seed_id = 220260910100000001;
SET @system_menu_id = (
  SELECT id
  FROM blade_menu
  WHERE code = 'system'
    AND category = 1
    AND is_deleted = 0
  ORDER BY id
  LIMIT 1
);

-- 在“系统管理”下创建标签管理页面。若同编码页面已存在，则复用其 ID。
INSERT INTO blade_menu
  (id, parent_id, code, name, alias, path, source, sort, category, `action`, is_open, remark, is_deleted)
SELECT
  @tag_menu_seed_id,
  @system_menu_id,
  'tag_manage',
  '标签管理',
  'menu',
  '/system/tag',
  'iconfont iconicon_addresslist',
  9,
  1,
  0,
  1,
  '标签分类与层级标签管理',
  0
FROM DUAL
WHERE @system_menu_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM blade_menu WHERE code = 'tag_manage' AND category = 1
  )
  AND NOT EXISTS (
    SELECT 1 FROM blade_menu WHERE id = @tag_menu_seed_id
  );

SET @tag_menu_id = (
  SELECT id
  FROM blade_menu
  WHERE code = 'tag_manage'
    AND category = 1
  ORDER BY is_deleted, id
  LIMIT 1
);

UPDATE blade_menu
SET parent_id = @system_menu_id,
    name = '标签管理',
    alias = 'menu',
    path = '/system/tag',
    source = 'iconfont iconicon_addresslist',
    sort = 9,
    category = 1,
    `action` = 0,
    is_open = 1,
    remark = '标签分类与层级标签管理',
    is_deleted = 0
WHERE id = @tag_menu_id
  AND @system_menu_id IS NOT NULL;

-- 十个前端按钮权限。action：1 工具栏，2 行操作。
DROP TEMPORARY TABLE IF EXISTS tmp_tag_menu_seed;
CREATE TEMPORARY TABLE tmp_tag_menu_seed (
  id bigint NOT NULL,
  code varchar(100) NOT NULL,
  name varchar(255) NOT NULL,
  alias varchar(255) NOT NULL,
  path varchar(255) NOT NULL,
  source varchar(255) NOT NULL,
  sort int NOT NULL,
  action_type int NOT NULL,
  PRIMARY KEY (code),
  UNIQUE KEY uk_tmp_tag_menu_seed_id (id)
);

INSERT INTO tmp_tag_menu_seed
  (id, code, name, alias, path, source, sort, action_type)
VALUES
  (220260910100000002, 'tag_category_view', '分类查看', 'view',
   '/system/tag/category/view', 'file-text', 1, 2),
  (220260910100000003, 'tag_category_add', '分类新增', 'add',
   '/system/tag/category/add', 'plus', 2, 1),
  (220260910100000004, 'tag_category_edit', '分类编辑', 'edit',
   '/system/tag/category/edit', 'form', 3, 2),
  (220260910100000005, 'tag_category_status', '分类状态', 'status',
   '/system/tag/category/status', 'switch-button', 4, 2),
  (220260910100000006, 'tag_category_delete', '分类删除', 'delete',
   '/system/tag/category/delete', 'delete', 5, 2),
  (220260910100000007, 'tag_view', '标签查看', 'view',
   '/system/tag/view', 'file-text', 6, 2),
  (220260910100000008, 'tag_add', '标签新增', 'add',
   '/system/tag/add', 'plus', 7, 1),
  (220260910100000009, 'tag_edit', '标签编辑', 'edit',
   '/system/tag/edit', 'form', 8, 2),
  (220260910100000010, 'tag_status', '标签状态', 'status',
   '/system/tag/status', 'switch-button', 9, 2),
  (220260910100000011, 'tag_delete', '标签删除', 'delete',
   '/system/tag/delete', 'delete', 10, 2);

INSERT INTO blade_menu
  (id, parent_id, code, name, alias, path, source, sort, category, `action`, is_open, remark, is_deleted)
SELECT seed.id,
       @tag_menu_id,
       seed.code,
       seed.name,
       seed.alias,
       seed.path,
       seed.source,
       seed.sort,
       2,
       seed.action_type,
       1,
       '标签管理按钮权限',
       0
FROM tmp_tag_menu_seed seed
WHERE @tag_menu_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1
    FROM blade_menu existing
    WHERE existing.code = seed.code
      AND existing.category = 2
  )
  AND NOT EXISTS (
    SELECT 1 FROM blade_menu existing_id WHERE existing_id.id = seed.id
  );

DROP TEMPORARY TABLE IF EXISTS tmp_tag_menu_target;
CREATE TEMPORARY TABLE tmp_tag_menu_target (
  code varchar(100) NOT NULL,
  menu_id bigint NOT NULL,
  PRIMARY KEY (code)
);

INSERT INTO tmp_tag_menu_target (code, menu_id)
SELECT selected.code, selected.menu_id
FROM (
  SELECT seed.code,
         (
           SELECT existing.id
           FROM blade_menu existing
           WHERE existing.code = seed.code
             AND existing.category = 2
           ORDER BY existing.is_deleted, existing.id
           LIMIT 1
         ) AS menu_id
  FROM tmp_tag_menu_seed seed
) selected
WHERE selected.menu_id IS NOT NULL;

UPDATE blade_menu existing
JOIN tmp_tag_menu_target target ON target.menu_id = existing.id
JOIN tmp_tag_menu_seed seed ON seed.code = target.code
SET existing.parent_id = @tag_menu_id,
    existing.name = seed.name,
    existing.alias = seed.alias,
    existing.path = seed.path,
    existing.source = seed.source,
    existing.sort = seed.sort,
    existing.category = 2,
    existing.`action` = seed.action_type,
    existing.is_open = 1,
    existing.remark = '标签管理按钮权限',
    existing.is_deleted = 0
WHERE @tag_menu_id IS NOT NULL;

-- 补齐或恢复十个 API Scope，并统一绑定到标签管理页面菜单。
DROP TEMPORARY TABLE IF EXISTS tmp_tag_scope_seed;
CREATE TEMPORARY TABLE tmp_tag_scope_seed (
  id bigint NOT NULL,
  resource_code varchar(100) NOT NULL,
  scope_name varchar(255) NOT NULL,
  scope_path varchar(255) NOT NULL,
  remark varchar(255) NOT NULL,
  PRIMARY KEY (resource_code),
  UNIQUE KEY uk_tmp_tag_scope_seed_id (id)
);

INSERT INTO tmp_tag_scope_seed
  (id, resource_code, scope_name, scope_path, remark)
VALUES
  (220260910000000001, 'system:tag-category:view', '标签分类查看',
   '/blade-system/tag-category', '标签分类列表与详情查询'),
  (220260910000000002, 'system:tag-category:create', '标签分类新增',
   '/blade-system/tag-category/create', '创建标签分类'),
  (220260910000000003, 'system:tag-category:edit', '标签分类编辑',
   '/blade-system/tag-category/update', '编辑标签分类'),
  (220260910000000004, 'system:tag-category:status', '标签分类状态',
   '/blade-system/tag-category/status', '启用或停用标签分类'),
  (220260910000000005, 'system:tag-category:delete', '标签分类删除',
   '/blade-system/tag-category/remove', '删除空标签分类'),
  (220260910000000006, 'system:tag:view', '标签查看',
   '/blade-system/tag', '标签列表、详情、树与有效选项查询'),
  (220260910000000007, 'system:tag:create', '标签新增',
   '/blade-system/tag/create', '创建标签'),
  (220260910000000008, 'system:tag:edit', '标签编辑',
   '/blade-system/tag/update', '编辑标签和调整层级'),
  (220260910000000009, 'system:tag:status', '标签状态',
   '/blade-system/tag/status', '启用或停用标签'),
  (220260910000000010, 'system:tag:delete', '标签删除',
   '/blade-system/tag/remove', '删除叶子标签');

INSERT INTO blade_scope_api
  (id, menu_id, resource_code, scope_name, scope_path, scope_type, remark, status, is_deleted)
SELECT seed.id,
       @tag_menu_id,
       seed.resource_code,
       seed.scope_name,
       seed.scope_path,
       2,
       seed.remark,
       1,
       0
FROM tmp_tag_scope_seed seed
WHERE @tag_menu_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1
    FROM blade_scope_api existing
    WHERE existing.resource_code = seed.resource_code
  )
  AND NOT EXISTS (
    SELECT 1 FROM blade_scope_api existing_id WHERE existing_id.id = seed.id
  );

DROP TEMPORARY TABLE IF EXISTS tmp_tag_scope_target;
CREATE TEMPORARY TABLE tmp_tag_scope_target (
  resource_code varchar(100) NOT NULL,
  scope_id bigint NOT NULL,
  PRIMARY KEY (resource_code)
);

INSERT INTO tmp_tag_scope_target (resource_code, scope_id)
SELECT selected.resource_code, selected.scope_id
FROM (
  SELECT seed.resource_code,
         (
           SELECT existing.id
           FROM blade_scope_api existing
           WHERE existing.resource_code = seed.resource_code
           ORDER BY existing.is_deleted, existing.id
           LIMIT 1
         ) AS scope_id
  FROM tmp_tag_scope_seed seed
) selected
WHERE selected.scope_id IS NOT NULL;

UPDATE blade_scope_api existing
JOIN tmp_tag_scope_target target ON target.scope_id = existing.id
JOIN tmp_tag_scope_seed seed ON seed.resource_code = target.resource_code
SET existing.menu_id = @tag_menu_id,
    existing.scope_name = seed.scope_name,
    existing.scope_path = seed.scope_path,
    existing.scope_type = 2,
    existing.remark = seed.remark,
    existing.status = 1,
    existing.is_deleted = 0
WHERE @tag_menu_id IS NOT NULL;

DROP TEMPORARY TABLE IF EXISTS tmp_tag_scope_target;
DROP TEMPORARY TABLE IF EXISTS tmp_tag_scope_seed;
DROP TEMPORARY TABLE IF EXISTS tmp_tag_menu_target;
DROP TEMPORARY TABLE IF EXISTS tmp_tag_menu_seed;

COMMIT;

-- 核验 1：system_menu_id 与 tag_menu_id 均应非 NULL。
SELECT @system_menu_id AS system_menu_id,
       @tag_menu_id AS tag_menu_id;

-- 核验 2：应返回 11 条有效记录（1 个页面 + 10 个按钮）。
SELECT id, parent_id, code, name, alias, path, source, sort, category, `action`, is_deleted
FROM blade_menu
WHERE code IN (
  'tag_manage',
  'tag_category_view', 'tag_category_add', 'tag_category_edit',
  'tag_category_status', 'tag_category_delete',
  'tag_view', 'tag_add', 'tag_edit', 'tag_status', 'tag_delete'
)
ORDER BY category, parent_id, sort, id;

-- 核验 3：应返回 10 条 Scope，menu_id 均等于 tag_menu_id。
SELECT id, menu_id, resource_code, scope_name, scope_path, scope_type, status, is_deleted
FROM blade_scope_api
WHERE resource_code IN (
  'system:tag-category:view', 'system:tag-category:create',
  'system:tag-category:edit', 'system:tag-category:status',
  'system:tag-category:delete', 'system:tag:view', 'system:tag:create',
  'system:tag:edit', 'system:tag:status', 'system:tag:delete'
)
ORDER BY id;

-- 核验 4：脚本不自动授权；此查询仅展示环境中已经存在的角色菜单授权。
SELECT role.tenant_id, role.role_alias, menu.code, menu.name
FROM blade_role_menu role_menu
JOIN blade_role role ON role.id = role_menu.role_id
JOIN blade_menu menu ON menu.id = role_menu.menu_id
WHERE menu.code IN (
  'tag_manage',
  'tag_category_view', 'tag_category_add', 'tag_category_edit',
  'tag_category_status', 'tag_category_delete',
  'tag_view', 'tag_add', 'tag_edit', 'tag_status', 'tag_delete'
)
ORDER BY role.tenant_id, role.role_alias, menu.category, menu.sort;

-- 核验 5：如启用顶部菜单，请从以下结果中选择目标 top_menu_id 后通过顶部菜单管理配置。
SELECT id AS top_menu_id, tenant_id, code, name, status, is_deleted
FROM blade_top_menu
WHERE status = 1
  AND is_deleted = 0
ORDER BY tenant_id, sort, id;
