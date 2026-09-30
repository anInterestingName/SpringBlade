SET NAMES utf8mb4;

DROP TEMPORARY TABLE IF EXISTS tmp_ai_config_menu_seed;
DROP TEMPORARY TABLE IF EXISTS tmp_ai_config_scope_seed;
DROP TEMPORARY TABLE IF EXISTS tmp_ai_config_grant_seed;

CREATE TEMPORARY TABLE tmp_ai_config_menu_seed (
  id bigint NOT NULL PRIMARY KEY,
  actual_id bigint NULL,
  parent_id bigint NOT NULL,
  code varchar(64) NOT NULL,
  name varchar(100) NOT NULL,
  alias varchar(32) NOT NULL,
  path varchar(255) NOT NULL,
  source varchar(100) NOT NULL,
  sort int NOT NULL,
  category int NOT NULL,
  action_type int NOT NULL
);

INSERT INTO tmp_ai_config_menu_seed
  (id, parent_id, code, name, alias, path, source, sort, category, action_type)
VALUES
  (220260909100000001, 0, 'asset', '资产管理', 'menu', '/asset',
   'iconfont iconicon_savememo', 6, 1, 0),
  (220260930200000001, 220260909100000001, 'ai_provider', '供应商配置', 'menu',
   '/asset/ai-provider', 'iconfont iconicon_savememo', 2, 1, 0),
  (220260930200000002, 220260930200000001, 'ai_provider_view', '查看', 'view',
   '/asset/ai-provider/view', 'file-text', 1, 2, 2),
  (220260930200000003, 220260909100000001, 'ai_model', '模型配置', 'menu',
   '/asset/ai-model', 'iconfont iconicon_doc', 3, 1, 0),
  (220260930200000004, 220260930200000003, 'ai_model_view', '查看', 'view',
   '/asset/ai-model/view', 'file-text', 1, 2, 2);

UPDATE tmp_ai_config_menu_seed seed
LEFT JOIN blade_menu menu ON menu.code = seed.code AND menu.is_deleted = 0
SET seed.actual_id = COALESCE(menu.id, seed.id);

SET @ai_asset_menu_id = (SELECT actual_id FROM tmp_ai_config_menu_seed WHERE code = 'asset');
SET @ai_provider_menu_id = (SELECT actual_id FROM tmp_ai_config_menu_seed WHERE code = 'ai_provider');
SET @ai_model_menu_id = (SELECT actual_id FROM tmp_ai_config_menu_seed WHERE code = 'ai_model');

UPDATE tmp_ai_config_menu_seed
SET parent_id = CASE code
  WHEN 'asset' THEN 0
  WHEN 'ai_provider' THEN @ai_asset_menu_id
  WHEN 'ai_model' THEN @ai_asset_menu_id
  WHEN 'ai_provider_view' THEN @ai_provider_menu_id
  WHEN 'ai_model_view' THEN @ai_model_menu_id
END;

CREATE TEMPORARY TABLE tmp_ai_config_scope_seed (
  id bigint NOT NULL PRIMARY KEY,
  actual_id bigint NULL,
  menu_id bigint NOT NULL,
  resource_code varchar(100) NOT NULL,
  scope_name varchar(100) NOT NULL,
  scope_path varchar(255) NOT NULL
);

INSERT INTO tmp_ai_config_scope_seed (id, menu_id, resource_code, scope_name, scope_path)
VALUES
  (220260930100000001, @ai_provider_menu_id, 'ai:model:provider:view',
   '供应商配置查看', '/blade-ai/ai/provider/**'),
  (220260930100000005, @ai_model_menu_id, 'ai:model:config:view',
   '模型配置查看', '/blade-ai/ai/model/**');

UPDATE tmp_ai_config_scope_seed seed
LEFT JOIN blade_scope_api scope_api
  ON scope_api.resource_code = seed.resource_code AND scope_api.is_deleted = 0
SET seed.actual_id = COALESCE(scope_api.id, seed.id);

SET @ai_administrator_role_id = (
  SELECT CASE WHEN COUNT(*) = 1 THEN MIN(id) ELSE NULL END
  FROM blade_role
  WHERE tenant_id = '000000' AND role_alias = 'administrator' AND is_deleted = 0
);
SET @ai_active_top_menu_count = (
  SELECT COUNT(*) FROM blade_top_menu
  WHERE status = 1 AND is_deleted = 0 AND (tenant_id = '000000' OR tenant_id IS NULL)
);
SET @ai_target_top_menu_id = (
  SELECT CASE WHEN COUNT(*) = 1 THEN MIN(id) ELSE NULL END
  FROM blade_top_menu
  WHERE status = 1 AND is_deleted = 0 AND (tenant_id = '000000' OR tenant_id IS NULL)
);

CREATE TEMPORARY TABLE tmp_ai_config_grant_seed (
  id bigint NOT NULL PRIMARY KEY,
  grant_type int NOT NULL,
  target_id bigint NOT NULL
);

INSERT INTO tmp_ai_config_grant_seed (id, grant_type, target_id)
SELECT 220260930300000000 + ROW_NUMBER() OVER (ORDER BY id), 1, actual_id
FROM tmp_ai_config_menu_seed;
INSERT INTO tmp_ai_config_grant_seed (id, grant_type, target_id)
SELECT 220260930400000000 + ROW_NUMBER() OVER (ORDER BY id), 2, actual_id
FROM tmp_ai_config_scope_seed;
INSERT INTO tmp_ai_config_grant_seed (id, grant_type, target_id)
VALUES
  (220260930500000001, 3, @ai_asset_menu_id),
  (220260930500000002, 3, @ai_provider_menu_id),
  (220260930500000003, 3, @ai_model_menu_id);

SET @ai_menu_conflicts = (
  SELECT COUNT(*) FROM tmp_ai_config_menu_seed seed
  WHERE (SELECT COUNT(*) FROM blade_menu WHERE code = seed.code AND is_deleted = 0) > 1
    OR EXISTS (
      SELECT 1 FROM blade_menu WHERE id = seed.actual_id
        AND (code <> seed.code OR code IS NULL OR category <> seed.category
          OR category IS NULL OR is_deleted <> 0 OR is_deleted IS NULL)
    )
    OR (NOT EXISTS (SELECT 1 FROM blade_menu WHERE code = seed.code AND is_deleted = 0)
      AND EXISTS (SELECT 1 FROM blade_menu WHERE code = seed.code))
    OR EXISTS (
      SELECT 1 FROM blade_menu WHERE path = seed.path AND category = 1
        AND is_deleted = 0 AND id <> seed.actual_id
    )
    OR EXISTS (
      SELECT 1 FROM blade_menu WHERE code = 'asset' AND is_deleted = 0
        AND (parent_id <> 0 OR parent_id IS NULL OR path <> '/asset' OR path IS NULL)
    )
);
SET @ai_scope_conflicts = (
  SELECT COUNT(*) FROM tmp_ai_config_scope_seed seed
  WHERE (SELECT COUNT(*) FROM blade_scope_api
    WHERE resource_code = seed.resource_code AND is_deleted = 0) > 1
    OR EXISTS (
      SELECT 1 FROM blade_scope_api WHERE id = seed.actual_id
        AND (resource_code <> seed.resource_code OR resource_code IS NULL
          OR is_deleted <> 0 OR is_deleted IS NULL OR status <> 1 OR status IS NULL
          OR scope_type <> 2 OR scope_type IS NULL
          OR scope_path <> seed.scope_path OR scope_path IS NULL
          OR (menu_id IS NOT NULL AND menu_id <> seed.menu_id))
    )
    OR (NOT EXISTS (SELECT 1 FROM blade_scope_api
      WHERE resource_code = seed.resource_code AND is_deleted = 0)
      AND EXISTS (SELECT 1 FROM blade_scope_api WHERE resource_code = seed.resource_code))
);
SET @ai_grant_conflicts = (
  SELECT COUNT(*) FROM tmp_ai_config_grant_seed seed
  WHERE (seed.grant_type = 1 AND EXISTS (SELECT 1 FROM blade_role_menu WHERE id = seed.id)
    AND NOT EXISTS (SELECT 1 FROM blade_role_menu
      WHERE role_id = @ai_administrator_role_id AND menu_id = seed.target_id))
    OR (seed.grant_type = 2 AND EXISTS (SELECT 1 FROM blade_role_scope WHERE id = seed.id)
      AND NOT EXISTS (SELECT 1 FROM blade_role_scope WHERE scope_category = 2
        AND role_id = @ai_administrator_role_id AND scope_id = seed.target_id))
    OR (seed.grant_type = 3 AND @ai_target_top_menu_id IS NOT NULL
      AND EXISTS (SELECT 1 FROM blade_top_menu_setting WHERE id = seed.id)
      AND NOT EXISTS (SELECT 1 FROM blade_top_menu_setting
        WHERE top_menu_id = @ai_target_top_menu_id AND menu_id = seed.target_id))
);
SET @ai_config_ready = @ai_administrator_role_id IS NOT NULL
  AND @ai_menu_conflicts = 0 AND @ai_scope_conflicts = 0 AND @ai_grant_conflicts = 0;

SELECT @ai_config_ready AS ready, @ai_administrator_role_id AS administrator_role_id,
  @ai_menu_conflicts AS menu_conflicts, @ai_scope_conflicts AS scope_conflicts,
  @ai_grant_conflicts AS grant_conflicts, @ai_active_top_menu_count AS active_top_menu_count,
  @ai_target_top_menu_id AS target_top_menu_id;

START TRANSACTION;

INSERT INTO blade_menu
  (id, parent_id, code, name, alias, path, source, sort, category, `action`, is_open, remark, is_deleted)
SELECT seed.actual_id, seed.parent_id, seed.code, seed.name, seed.alias, seed.path,
  seed.source, seed.sort, seed.category, seed.action_type, 1, '模型配置首期只读菜单与查看权限', 0
FROM tmp_ai_config_menu_seed seed
WHERE @ai_config_ready = 1
  AND NOT EXISTS (SELECT 1 FROM blade_menu WHERE id = seed.actual_id);

UPDATE blade_menu menu
JOIN tmp_ai_config_menu_seed seed ON seed.actual_id = menu.id
SET menu.parent_id = seed.parent_id, menu.name = seed.name, menu.alias = seed.alias,
  menu.path = seed.path, menu.`action` = seed.action_type, menu.is_open = 1
WHERE @ai_config_ready = 1 AND seed.code <> 'asset';

INSERT INTO blade_scope_api
  (id, menu_id, resource_code, scope_name, scope_path, scope_type, remark, status, is_deleted)
SELECT seed.actual_id, seed.menu_id, seed.resource_code, seed.scope_name,
  seed.scope_path, 2, '模型配置首期只读管理接口', 1, 0
FROM tmp_ai_config_scope_seed seed
WHERE @ai_config_ready = 1
  AND NOT EXISTS (SELECT 1 FROM blade_scope_api WHERE id = seed.actual_id);

UPDATE blade_scope_api scope_api
JOIN tmp_ai_config_scope_seed seed ON seed.actual_id = scope_api.id
SET scope_api.menu_id = seed.menu_id
WHERE @ai_config_ready = 1;

INSERT INTO blade_role_menu (id, menu_id, role_id)
SELECT seed.id, seed.target_id, @ai_administrator_role_id
FROM tmp_ai_config_grant_seed seed
WHERE @ai_config_ready = 1 AND seed.grant_type = 1
  AND NOT EXISTS (SELECT 1 FROM blade_role_menu
    WHERE role_id = @ai_administrator_role_id AND menu_id = seed.target_id);

INSERT INTO blade_role_scope (id, scope_category, scope_id, role_id)
SELECT seed.id, 2, seed.target_id, @ai_administrator_role_id
FROM tmp_ai_config_grant_seed seed
WHERE @ai_config_ready = 1 AND seed.grant_type = 2
  AND NOT EXISTS (SELECT 1 FROM blade_role_scope WHERE scope_category = 2
    AND role_id = @ai_administrator_role_id AND scope_id = seed.target_id);

INSERT INTO blade_top_menu_setting (id, top_menu_id, menu_id)
SELECT seed.id, @ai_target_top_menu_id, seed.target_id
FROM tmp_ai_config_grant_seed seed
WHERE @ai_config_ready = 1 AND seed.grant_type = 3 AND @ai_target_top_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM blade_top_menu_setting
    WHERE top_menu_id = @ai_target_top_menu_id AND menu_id = seed.target_id);

COMMIT;

SELECT menu.id, menu.parent_id, menu.code, menu.path, menu.category,
  COUNT(role_menu.id) AS administrator_grants
FROM tmp_ai_config_menu_seed seed
JOIN blade_menu menu ON menu.id = seed.actual_id
LEFT JOIN blade_role_menu role_menu
  ON role_menu.menu_id = menu.id AND role_menu.role_id = @ai_administrator_role_id
GROUP BY menu.id, menu.parent_id, menu.code, menu.path, menu.category
ORDER BY menu.id;

SELECT scope_api.id, scope_api.resource_code, scope_api.menu_id, menu.category,
  COUNT(role_scope.id) AS administrator_grants
FROM tmp_ai_config_scope_seed seed
JOIN blade_scope_api scope_api ON scope_api.id = seed.actual_id
LEFT JOIN blade_menu menu ON menu.id = scope_api.menu_id
LEFT JOIN blade_role_scope role_scope ON role_scope.scope_id = scope_api.id
  AND role_scope.scope_category = 2 AND role_scope.role_id = @ai_administrator_role_id
GROUP BY scope_api.id, scope_api.resource_code, scope_api.menu_id, menu.category
ORDER BY scope_api.id;

SELECT @ai_config_ready AS ready, @ai_active_top_menu_count > 1 AS manual_top_menu_required,
  @ai_target_top_menu_id AS target_top_menu_id;

DROP TEMPORARY TABLE tmp_ai_config_grant_seed;
DROP TEMPORARY TABLE tmp_ai_config_scope_seed;
DROP TEMPORARY TABLE tmp_ai_config_menu_seed;
