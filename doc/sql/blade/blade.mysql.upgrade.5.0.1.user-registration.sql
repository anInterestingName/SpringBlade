-- 用户自助注册升级脚本
-- 执行前必须先确认下列检查结果没有需要人工处理的重复或异常数据。

-- 同一租户内按注册规则归一化后重复的账号。
SELECT
    tenant_id,
    LOWER(TRIM(account)) AS normalized_account,
    COUNT(*) AS duplicate_count
FROM blade_user
WHERE tenant_id IS NOT NULL
  AND account IS NOT NULL
GROUP BY tenant_id, LOWER(TRIM(account))
HAVING COUNT(*) > 1;

-- 历史空租户、空账号和空白账号。禁止在迁移中自动猜测归属。
SELECT id, tenant_id, account, is_deleted
FROM blade_user
WHERE tenant_id IS NULL
   OR TRIM(tenant_id) = ''
   OR account IS NULL
   OR TRIM(account) = '';

-- 扩展昵称字段以容纳最长32位账号作为默认昵称。
ALTER TABLE blade_user
    MODIFY COLUMN name varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '昵称';

-- 确认以上查询无阻断结果，且目标表不存在同名索引后再执行。
ALTER TABLE blade_user
    ADD UNIQUE KEY uk_blade_user_tenant_account (tenant_id, account);

-- 升级完成后核对：
-- SHOW CREATE TABLE blade_user;
-- SHOW INDEX FROM blade_user WHERE Key_name = 'uk_blade_user_tenant_account';
