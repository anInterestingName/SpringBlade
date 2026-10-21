-- OAuth2/OIDC持久化验证升级，目标MySQL 8，执行前备份blade_client。
-- 不兼容旧认证：首次升级停用旧客户端并清除旧密钥，管理员重新配置授权类型、scope、audience和新密钥。
-- 保留客户端主键、标识及现有用户，禁止将旧Token或内存授权导入新协议状态。
-- 脚本可重复执行；DDL非事务操作，发布期间暂停客户端写入。
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS upgrade_oauth2_clients;
DELIMITER $$
CREATE PROCEDURE upgrade_oauth2_clients()
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='blade_client' AND column_name='authorized_grant_types') THEN
    IF EXISTS (SELECT client_id FROM blade_client WHERE is_deleted=0 GROUP BY client_id HAVING COUNT(*) > 1) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Active client IDs must be unique before migration';
    END IF;
    UPDATE blade_client SET resource_ids=COALESCE(resource_ids, '');
    ALTER TABLE blade_client
      ADD COLUMN client_name varchar(100) NOT NULL DEFAULT '' AFTER client_id,
      MODIFY COLUMN client_id varchar(48) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
      MODIFY COLUMN client_secret varchar(256) DEFAULT NULL COMMENT 'BCrypt密钥摘要',
      CHANGE COLUMN resource_ids audiences varchar(1024) NOT NULL COMMENT '目标资源服务',
      CHANGE COLUMN scope scopes varchar(1024) NOT NULL COMMENT '授权范围',
      CHANGE COLUMN authorized_grant_types grant_types varchar(256) NOT NULL COMMENT 'OAuth2授权类型',
      CHANGE COLUMN web_server_redirect_uri redirect_uris varchar(2048) DEFAULT NULL COMMENT '精确回调地址',
      CHANGE COLUMN access_token_validity access_token_ttl int NOT NULL DEFAULT 300 COMMENT '访问令牌有效秒数',
      CHANGE COLUMN refresh_token_validity refresh_token_ttl int NOT NULL DEFAULT 1800 COMMENT '刷新令牌有效秒数',
      ADD COLUMN client_authentication_methods varchar(100) NOT NULL DEFAULT 'client_secret_basic' AFTER client_secret,
      ADD COLUMN require_proof_key tinyint(1) NOT NULL DEFAULT 1 AFTER refresh_token_ttl,
      ADD COLUMN require_authorization_consent tinyint(1) NOT NULL DEFAULT 1 AFTER require_proof_key,
      ADD COLUMN reuse_refresh_tokens tinyint(1) NOT NULL DEFAULT 0 AFTER require_authorization_consent,
      DROP COLUMN authorities, DROP COLUMN additional_information, DROP COLUMN autoapprove,
      ADD COLUMN active_client_id varchar(48) CHARACTER SET ascii COLLATE ascii_bin GENERATED ALWAYS AS (CASE WHEN is_deleted=0 THEN client_id ELSE NULL END) STORED,
      ADD UNIQUE KEY uk_client_active_id (active_client_id);
    UPDATE blade_client SET client_name=client_id, client_secret=NULL, status=0;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='blade_user' AND column_name='password' AND character_maximum_length < 256) THEN
    ALTER TABLE `blade_user` MODIFY COLUMN `password` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '密码';
  END IF;
END$$
DELIMITER ;
CALL upgrade_oauth2_clients();
DROP PROCEDURE upgrade_oauth2_clients;

CREATE TABLE IF NOT EXISTS `blade_oauth2_authorization` (
  `id` bigint NOT NULL COMMENT '雪花主键',
  `authorization_id` varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '协议授权编号',
  `registered_client_id` bigint NOT NULL COMMENT '客户端主键',
  `principal_name` varchar(200) NOT NULL COMMENT '稳定用户主体或客户端标识',
  `grant_type` varchar(100) NOT NULL COMMENT '授权类型',
  `authorized_scopes` varchar(1024) NOT NULL COMMENT '已授权范围',
  `payload` mediumtext NOT NULL COMMENT '授权上下文和令牌状态，AES-GCM信封密文，仅授权服务器读取',
  `state_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT 'state的SHA256',
  `authorization_code_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '授权码的SHA256',
  `access_token_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '访问令牌的SHA256',
  `refresh_token_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '刷新令牌的SHA256',
  `id_token_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '身份令牌的SHA256',
  `expires_at` datetime(6) NOT NULL COMMENT '最晚状态到期时间',
  `tenant_id` varchar(12) DEFAULT NULL COMMENT '用户租户，客户端凭据授权为空',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `create_dept` bigint DEFAULT NULL COMMENT '创建部门',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` bigint DEFAULT NULL COMMENT '修改人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `status` int NOT NULL DEFAULT 1 COMMENT '状态',
  `is_deleted` int NOT NULL DEFAULT 0 COMMENT '是否已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_authorization_id` (`authorization_id`),
  KEY `idx_authorization_state` (`state_hash`),
  KEY `idx_authorization_code` (`authorization_code_hash`),
  KEY `idx_authorization_access` (`access_token_hash`),
  KEY `idx_authorization_refresh` (`refresh_token_hash`),
  KEY `idx_authorization_id_token` (`id_token_hash`),
  KEY `idx_authorization_expiry` (`expires_at`),
  KEY `idx_authorization_owner` (`tenant_id`, `principal_name`, `registered_client_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAuth2授权状态';

CREATE TABLE IF NOT EXISTS `blade_oauth2_authorization_consent` (
  `id` bigint NOT NULL COMMENT '雪花主键',
  `registered_client_id` bigint NOT NULL COMMENT '客户端主键',
  `principal_name` varchar(200) NOT NULL COMMENT '稳定用户主体',
  `authorities` varchar(2048) NOT NULL COMMENT '已确认授权范围',
  `tenant_id` varchar(12) NOT NULL COMMENT '用户租户',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `create_dept` bigint DEFAULT NULL COMMENT '创建部门',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` bigint DEFAULT NULL COMMENT '修改人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `status` int NOT NULL DEFAULT 1 COMMENT '状态',
  `is_deleted` int NOT NULL DEFAULT 0 COMMENT '是否已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_consent_client_principal` (`registered_client_id`, `principal_name`),
  KEY `idx_consent_tenant` (`tenant_id`, `principal_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAuth2用户授权同意';

-- 验证：SHOW CREATE TABLE blade_client; SHOW CREATE TABLE blade_oauth2_authorization;
CREATE TABLE IF NOT EXISTS `blade_oauth2_refresh_token` (
  `id` bigint NOT NULL COMMENT '雪花主键',
  `token_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '刷新令牌SHA256，不保存令牌原文',
  `authorization_id` varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属授权编号与刷新令牌族',
  `expires_at` datetime(6) NOT NULL COMMENT '该刷新令牌原始到期时间',
  `tenant_id` varchar(12) DEFAULT NULL COMMENT '用户租户',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `create_dept` bigint DEFAULT NULL COMMENT '创建部门',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` bigint DEFAULT NULL COMMENT '修改人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `status` int NOT NULL DEFAULT 1 COMMENT '状态',
  `is_deleted` int NOT NULL DEFAULT 0 COMMENT '是否已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refresh_token_hash` (`token_hash`),
  KEY `idx_refresh_authorization` (`authorization_id`),
  KEY `idx_refresh_expiry` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAuth2刷新令牌归属与重用状态';

-- 仅为当前有效刷新摘要建立归属；已经丢失的历史摘要不能回填，需用户重新登录进入完整保护链。
INSERT IGNORE INTO blade_oauth2_refresh_token (id, token_hash, authorization_id, expires_at, tenant_id)
SELECT id, refresh_token_hash, authorization_id, expires_at, tenant_id FROM blade_oauth2_authorization
WHERE refresh_token_hash IS NOT NULL AND status=1 AND is_deleted=0 AND expires_at>CURRENT_TIMESTAMP;

-- 验证：旧应用应全部停用，重新配置前不得参与新认证。
-- 补充：全量接入客户端种子数据（BFF客户端覆盖所有微服务audience）
-- 种子保持停用且不设置共享默认密钥；已有客户端配置不覆盖，发布时由管理员配置外部密钥并启用。
INSERT INTO `blade_client` (`id`, `client_id`, `client_name`, `client_secret`, `client_authentication_methods`, `grant_types`, `redirect_uris`, `scopes`, `audiences`, `access_token_ttl`, `refresh_token_ttl`, `require_proof_key`, `require_authorization_consent`, `reuse_refresh_tokens`, `create_user`, `create_dept`, `create_time`, `update_user`, `update_time`, `status`, `is_deleted`)
SELECT 2108451995948937201, 'gateway-bff', '网关BFF客户端', NULL, 'client_secret_basic', 'authorization_code,refresh_token', 'http://127.0.0.1:2888/login/oauth2/code/blade', 'openid,profile,demo.read,demo.write,service.read', 'blade-demo,blade-system,blade-desk,blade-ai,blade-log,blade-resource,blade-develop,blade-report', 300, 1800, 1, 1, 0, 1123598821738675201, 1123598821738675201, NOW(), 1123598821738675201, NOW(), 0, 0
WHERE NOT EXISTS (SELECT 1 FROM `blade_client` WHERE `client_id` = 'gateway-bff');

-- 清理：仅在维护窗口按expires_at删除过期blade_oauth2_authorization；同意记录只按明确用户/客户端撤销。
-- 回滚：先停止新认证流量，恢复发布前应用版本、配置和blade_client备份。
-- 新授权表保留，不删除验证记录；旧协议不会识别新协议状态，用户重新登录。
