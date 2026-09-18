-- REQ-2026-005 提示词类型、发布方式与数据权限增强升级脚本
-- 执行前备份 blade_ai_prompt、blade_ai_prompt_version、blade_scope_data、blade_role_scope。
-- 本脚本只适用于已经部署 REQ-2026-001 提示词基础表且尚未执行本次结构升级的 MySQL 8 环境。

DROP PROCEDURE IF EXISTS upgrade_prompt_scope_and_publishing;
DELIMITER //
CREATE PROCEDURE upgrade_prompt_scope_and_publishing()
BEGIN
  DECLARE prompt_table_count int DEFAULT 0;
  DECLARE new_column_count int DEFAULT 0;
  DECLARE null_owner_count bigint DEFAULT 0;

  SELECT COUNT(*) INTO prompt_table_count
  FROM information_schema.tables
  WHERE table_schema = DATABASE()
    AND table_name IN ('blade_ai_prompt', 'blade_ai_prompt_version');

  IF prompt_table_count <> 2 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'REQ-2026-005 upgrade aborted: prompt tables are missing';
  END IF;

  SELECT COUNT(*) INTO new_column_count
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND ((table_name = 'blade_ai_prompt' AND column_name IN ('prompt_type', 'publish_mode'))
      OR (table_name = 'blade_ai_prompt_version' AND column_name = 'prompt_type'));

  IF new_column_count <> 0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'REQ-2026-005 upgrade aborted: new columns already exist or schema is partially upgraded';
  END IF;

  SELECT COUNT(*) INTO null_owner_count
  FROM blade_ai_prompt
  WHERE create_user IS NULL;

  IF null_owner_count <> 0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'REQ-2026-005 upgrade aborted: create_user ownership mapping is incomplete';
  END IF;

  ALTER TABLE blade_ai_prompt
    ADD COLUMN prompt_type varchar(32) NOT NULL DEFAULT 'GENERAL' COMMENT '提示词业务类型' AFTER prompt_name,
    ADD COLUMN publish_mode tinyint NOT NULL DEFAULT 1 COMMENT '发布方式:1手工发布,2自动发布' AFTER prompt_type,
    MODIFY COLUMN create_user bigint NOT NULL COMMENT '创建人/数据所有者',
    ADD KEY idx_blade_ai_prompt_tenant_creator (tenant_id, create_user, is_deleted, update_time),
    ADD KEY idx_blade_ai_prompt_tenant_type (tenant_id, prompt_type, is_deleted, update_time);

  ALTER TABLE blade_ai_prompt_version
    ADD COLUMN prompt_type varchar(32) NOT NULL DEFAULT 'GENERAL' COMMENT '提示词类型快照' AFTER prompt_name,
    MODIFY COLUMN source_type int NOT NULL DEFAULT 1 COMMENT '来源类型:1手工发布,2回滚发布,3自动发布';
END//
DELIMITER ;

CALL upgrade_prompt_scope_and_publishing();
DROP PROCEDURE IF EXISTS upgrade_prompt_scope_and_publishing;

START TRANSACTION;

SET @prompt_menu_id = (
  SELECT id
  FROM blade_menu
  WHERE code = 'prompt' AND category = 1 AND is_deleted = 0
  ORDER BY id
  LIMIT 1
);

INSERT INTO blade_scope_data
  (id, menu_id, resource_code, scope_name, scope_field, scope_class, scope_column, scope_type,
   scope_value, remark, status, is_deleted)
SELECT seed.id, @prompt_menu_id, seed.resource_code, seed.scope_name, '*', seed.scope_class,
       seed.scope_column, seed.scope_type, NULL, seed.remark, 1, 0
FROM (
  SELECT 220260916500000001 AS id, 'ai:prompt:data:page:own' AS resource_code,
         '提示词分页本人可见' AS scope_name,
         'org.springblade.ai.prompt.mapper.PromptMapper.selectScopePage' AS scope_class,
         'create_user' AS scope_column, 2 AS scope_type, '提示词分页按创建人过滤' AS remark
  UNION ALL
  SELECT 220260916500000002, 'ai:prompt:data:page:all', '提示词分页全部可见',
         'org.springblade.ai.prompt.mapper.PromptMapper.selectScopePage', '-', 1,
         '提示词分页查看当前租户全部数据'
  UNION ALL
  SELECT 220260916500000003, 'ai:prompt:data:resource:own', '提示词资源本人可见',
         'org.springblade.ai.prompt.mapper.PromptMapper.selectScopePrompt', 'create_user', 2,
         '提示词详情、版本和写操作按创建人过滤'
  UNION ALL
  SELECT 220260916500000004, 'ai:prompt:data:resource:all', '提示词资源全部可见',
         'org.springblade.ai.prompt.mapper.PromptMapper.selectScopePrompt', '-', 1,
         '提示词详情、版本和写操作查看当前租户全部数据'
) seed
WHERE NOT EXISTS (
  SELECT 1
  FROM blade_scope_data existing
  WHERE existing.resource_code = seed.resource_code
)
AND NOT EXISTS (
  SELECT 1 FROM blade_scope_data existing_id WHERE existing_id.id = seed.id
);

UPDATE blade_scope_data
SET menu_id = @prompt_menu_id,
    scope_field = '*',
    scope_class = CASE resource_code
      WHEN 'ai:prompt:data:page:own' THEN 'org.springblade.ai.prompt.mapper.PromptMapper.selectScopePage'
      WHEN 'ai:prompt:data:page:all' THEN 'org.springblade.ai.prompt.mapper.PromptMapper.selectScopePage'
      WHEN 'ai:prompt:data:resource:own' THEN 'org.springblade.ai.prompt.mapper.PromptMapper.selectScopePrompt'
      WHEN 'ai:prompt:data:resource:all' THEN 'org.springblade.ai.prompt.mapper.PromptMapper.selectScopePrompt'
    END,
    scope_column = CASE
      WHEN resource_code LIKE '%:own' THEN 'create_user'
      ELSE '-'
    END,
    scope_type = CASE
      WHEN resource_code LIKE '%:own' THEN 2
      ELSE 1
    END,
    status = 1,
    is_deleted = 0
WHERE resource_code IN (
  'ai:prompt:data:page:own', 'ai:prompt:data:page:all',
  'ai:prompt:data:resource:own', 'ai:prompt:data:resource:all'
);

-- 标准角色同一 Mapper 只允许一种提示词范围：user=OWN，admin/administrator=ALL。
-- 先清理错误范围，避免角色管理中全选后同时绑定 OWN/ALL，导致框架读取首条规则产生不确定结果。
DELETE role_scope
FROM blade_role_scope role_scope
JOIN blade_role role ON role.id = role_scope.role_id
JOIN blade_scope_data scope ON scope.id = role_scope.scope_id
WHERE role_scope.scope_category = 1
  AND role.is_deleted = 0
  AND scope.scope_class IN (
    'org.springblade.ai.prompt.mapper.PromptMapper.selectScopePage',
    'org.springblade.ai.prompt.mapper.PromptMapper.selectScopePrompt'
  )
  AND (
    (role.role_alias = 'user' AND scope.scope_type <> 2)
    OR (role.role_alias IN ('admin', 'administrator') AND scope.scope_type <> 1)
  );

INSERT INTO blade_role_scope (id, scope_category, scope_id, role_id)
SELECT desired.id, 1, desired.scope_id, desired.role_id
FROM (
  SELECT 220260916600000000 + ROW_NUMBER() OVER (ORDER BY assignment.role_id, assignment.scope_id) AS id,
         assignment.scope_id,
         assignment.role_id
  FROM (
    SELECT role.id AS role_id, scope.id AS scope_id
    FROM blade_role role
    JOIN blade_scope_data scope
      ON scope.resource_code IN ('ai:prompt:data:page:own', 'ai:prompt:data:resource:own')
     AND scope.is_deleted = 0
    WHERE role.role_alias = 'user' AND role.is_deleted = 0
    UNION ALL
    SELECT role.id, scope.id
    FROM blade_role role
    JOIN blade_scope_data scope
      ON scope.resource_code IN ('ai:prompt:data:page:all', 'ai:prompt:data:resource:all')
     AND scope.is_deleted = 0
    WHERE role.role_alias IN ('admin', 'administrator') AND role.is_deleted = 0
  ) assignment
) desired
WHERE NOT EXISTS (
  SELECT 1
  FROM blade_role_scope existing
  WHERE existing.scope_category = 1
    AND existing.scope_id = desired.scope_id
    AND existing.role_id = desired.role_id
)
AND NOT EXISTS (
  SELECT 1 FROM blade_role_scope existing_id WHERE existing_id.id = desired.id
)
AND NOT EXISTS (
  SELECT 1
  FROM blade_role_scope existing
  JOIN blade_scope_data existing_scope ON existing_scope.id = existing.scope_id
  JOIN blade_scope_data desired_scope ON desired_scope.id = desired.scope_id
  WHERE existing.scope_category = 1
    AND existing.role_id = desired.role_id
    AND existing_scope.scope_class = desired_scope.scope_class
    AND existing.scope_id <> desired.scope_id
);

COMMIT;

SELECT column_name, column_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND ((table_name = 'blade_ai_prompt' AND column_name IN ('prompt_type', 'publish_mode', 'create_user'))
    OR (table_name = 'blade_ai_prompt_version' AND column_name = 'prompt_type'))
ORDER BY table_name, ordinal_position;

SELECT index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS index_columns
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'blade_ai_prompt'
  AND index_name IN ('idx_blade_ai_prompt_tenant_creator', 'idx_blade_ai_prompt_tenant_type')
GROUP BY index_name
ORDER BY index_name;

SELECT resource_code, scope_class, scope_column, scope_type, status, is_deleted
FROM blade_scope_data
WHERE resource_code IN (
  'ai:prompt:data:page:own', 'ai:prompt:data:page:all',
  'ai:prompt:data:resource:own', 'ai:prompt:data:resource:all'
)
ORDER BY resource_code;

SELECT role.tenant_id, role.role_alias, scope.scope_class, scope.scope_type, COUNT(*) AS binding_count
FROM blade_role_scope role_scope
JOIN blade_role role ON role.id = role_scope.role_id
JOIN blade_scope_data scope ON scope.id = role_scope.scope_id
WHERE role_scope.scope_category = 1
  AND scope.scope_class IN (
    'org.springblade.ai.prompt.mapper.PromptMapper.selectScopePage',
    'org.springblade.ai.prompt.mapper.PromptMapper.selectScopePrompt'
  )
GROUP BY role.tenant_id, role.role_alias, scope.scope_class, scope.scope_type
ORDER BY role.tenant_id, role.role_alias, scope.scope_class;

SELECT role_scope.role_id, scope.scope_class, COUNT(DISTINCT scope.scope_type) AS scope_type_count
FROM blade_role_scope role_scope
JOIN blade_scope_data scope ON scope.id = role_scope.scope_id
WHERE role_scope.scope_category = 1
  AND scope.scope_class IN (
    'org.springblade.ai.prompt.mapper.PromptMapper.selectScopePage',
    'org.springblade.ai.prompt.mapper.PromptMapper.selectScopePrompt'
  )
GROUP BY role_scope.role_id, scope.scope_class
HAVING COUNT(DISTINCT scope.scope_type) > 1;

SELECT COUNT(*) AS invalid_prompt_type_count
FROM blade_ai_prompt
WHERE prompt_type NOT IN ('GENERAL', 'SYSTEM', 'TEXT', 'IMAGE');

SELECT COUNT(*) AS invalid_publish_mode_count
FROM blade_ai_prompt
WHERE publish_mode NOT IN (1, 2);
