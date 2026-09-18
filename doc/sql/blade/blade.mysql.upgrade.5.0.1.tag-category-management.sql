-- REQ-2026-002 标签分类管理升级脚本
-- 单次执行。执行前必须确认 blade_tag_category 与 blade_tag 均不存在。

CREATE TABLE blade_tag_category (
  id bigint NOT NULL COMMENT '主键',
  category_code varchar(64) NOT NULL COMMENT '分类稳定编码',
  category_name varchar(100) NOT NULL COMMENT '分类名称',
  selection_mode tinyint NOT NULL DEFAULT 1 COMMENT '选择模式:1单选 2多选',
  max_select_count int NOT NULL DEFAULT 1 COMMENT '最大可选数量',
  sort int NOT NULL DEFAULT 0 COMMENT '排序',
  remark varchar(500) NULL DEFAULT NULL COMMENT '说明',
  lock_version bigint NOT NULL DEFAULT 0 COMMENT '并发控制版本',
  status int NOT NULL DEFAULT 1 COMMENT '状态:0停用 1启用',
  tenant_id varchar(12) NOT NULL DEFAULT '000000' COMMENT '租户ID',
  create_user bigint NULL DEFAULT NULL COMMENT '创建人',
  create_dept bigint NULL DEFAULT NULL COMMENT '创建部门',
  create_time datetime NULL DEFAULT NULL COMMENT '创建时间',
  update_user bigint NULL DEFAULT NULL COMMENT '修改人',
  update_time datetime NULL DEFAULT NULL COMMENT '修改时间',
  is_deleted int NOT NULL DEFAULT 0 COMMENT '是否已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_blade_tag_category_tenant_code (tenant_id, category_code),
  KEY idx_blade_tag_category_list (tenant_id, is_deleted, sort, create_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='标签分类';

CREATE TABLE blade_tag (
  id bigint NOT NULL COMMENT '主键',
  category_id bigint NOT NULL COMMENT '所属分类ID',
  parent_id bigint NOT NULL DEFAULT 0 COMMENT '父标签ID',
  ancestors varchar(512) NOT NULL DEFAULT '0' COMMENT '祖先标签ID路径',
  depth tinyint NOT NULL DEFAULT 1 COMMENT '层级深度',
  tag_code varchar(64) NOT NULL COMMENT '标签稳定编码',
  tag_name varchar(100) NOT NULL COMMENT '标签名称',
  sort int NOT NULL DEFAULT 0 COMMENT '排序',
  remark varchar(500) NULL DEFAULT NULL COMMENT '说明',
  lock_version bigint NOT NULL DEFAULT 0 COMMENT '并发控制版本',
  status int NOT NULL DEFAULT 1 COMMENT '状态:0停用 1启用',
  tenant_id varchar(12) NOT NULL DEFAULT '000000' COMMENT '租户ID',
  create_user bigint NULL DEFAULT NULL COMMENT '创建人',
  create_dept bigint NULL DEFAULT NULL COMMENT '创建部门',
  create_time datetime NULL DEFAULT NULL COMMENT '创建时间',
  update_user bigint NULL DEFAULT NULL COMMENT '修改人',
  update_time datetime NULL DEFAULT NULL COMMENT '修改时间',
  is_deleted int NOT NULL DEFAULT 0 COMMENT '是否已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_blade_tag_tenant_category_code (tenant_id, category_id, tag_code),
  KEY idx_blade_tag_tree (tenant_id, category_id, is_deleted, sort, create_time, id),
  KEY idx_blade_tag_parent (tenant_id, category_id, parent_id, is_deleted),
  KEY idx_blade_tag_option (tenant_id, category_id, status, is_deleted, sort, create_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='标签';

INSERT INTO blade_scope_api (id, menu_id, resource_code, scope_name, scope_path, scope_type, remark, status, is_deleted) VALUES
(220260910000000001, NULL, 'system:tag-category:view', '标签分类查看', '/blade-system/tag-category', 2, '标签分类列表与详情查询', 1, 0),
(220260910000000002, NULL, 'system:tag-category:create', '标签分类新增', '/blade-system/tag-category/create', 2, '创建标签分类', 1, 0),
(220260910000000003, NULL, 'system:tag-category:edit', '标签分类编辑', '/blade-system/tag-category/update', 2, '编辑标签分类', 1, 0),
(220260910000000004, NULL, 'system:tag-category:status', '标签分类状态', '/blade-system/tag-category/status', 2, '启用或停用标签分类', 1, 0),
(220260910000000005, NULL, 'system:tag-category:delete', '标签分类删除', '/blade-system/tag-category/remove', 2, '删除空标签分类', 1, 0),
(220260910000000006, NULL, 'system:tag:view', '标签查看', '/blade-system/tag', 2, '标签列表、详情、树与有效选项查询', 1, 0),
(220260910000000007, NULL, 'system:tag:create', '标签新增', '/blade-system/tag/create', 2, '创建标签', 1, 0),
(220260910000000008, NULL, 'system:tag:edit', '标签编辑', '/blade-system/tag/update', 2, '编辑标签和调整层级', 1, 0),
(220260910000000009, NULL, 'system:tag:status', '标签状态', '/blade-system/tag/status', 2, '启用或停用标签', 1, 0),
(220260910000000010, NULL, 'system:tag:delete', '标签删除', '/blade-system/tag/remove', 2, '删除叶子标签', 1, 0);
