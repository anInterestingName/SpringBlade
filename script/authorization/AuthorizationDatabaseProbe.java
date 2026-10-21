/*
 * Copyright 2026 SpringBlade contributors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * https://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.Properties;
import java.time.Instant;
import java.util.regex.Pattern;

/**
 * 本地认证回归的只读数据库探针。仅输出客户端非敏感配置，不输出凭据或连接串。
 *
 * @author SpringBlade
 */
public class AuthorizationDatabaseProbe {
    public static void main(String[] args) {
        try {
            Properties admin = load(args[0]);
            Properties authorization = load(args[1]);
            Properties secrets = load(args[2]);
            if (args.length > 3 && "sync-test-client".equals(args[3])) {
                syncTestClient(admin, secrets, Path.of(args[4]));
            } else if (args.length > 3 && "prepare-refresh-state".equals(args[3])) {
                prepareRefreshState(admin, authorization, secrets, Path.of(args[4]));
            }
            inspect(admin.getProperty("spring.datasource.url"), admin.getProperty("spring.datasource.username"),
                resolve(admin.getProperty("spring.datasource.password"), secrets), true);
            inspect(authorization.getProperty("AUTHORIZATION_DATASOURCE_URL"), authorization.getProperty("AUTHORIZATION_DATASOURCE_USERNAME"),
                resolve(authorization.getProperty("AUTHORIZATION_DATASOURCE_PASSWORD"), secrets), false);
        } catch (Exception exception) {
            System.out.println("数据库探针失败：" + exception.getClass().getSimpleName());
            System.exit(1);
        }
    }

    private static Properties load(String file) throws Exception {
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(Path.of(file), StandardCharsets.UTF_8)) { properties.load(reader); }
        return properties;
    }

    private static String resolve(String value, Properties secrets) {
        if (value != null && value.startsWith("${") && value.endsWith("}")) {
            String key = value.substring(2, value.length() - 1);
            return secrets.getProperty(key, System.getenv(key));
        }
        return value;
    }

    /** 在本地测试库执行已准备的刷新归属表 DDL；不执行升级脚本中其他客户端迁移。 */
    private static void prepareRefreshState(Properties admin, Properties authorization, Properties secrets, Path migration) throws Exception {
        String url = admin.getProperty("spring.datasource.url");
        if (url == null || !url.matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost)(:[0-9]+)?/.*")) {
            throw new IllegalArgumentException("仅允许本地测试地址");
        }
        String sql = Files.readString(migration, StandardCharsets.UTF_8);
        var matcher = Pattern.compile("(?s)CREATE TABLE IF NOT EXISTS `blade_oauth2_refresh_token`\\s*\\(.*?\\) ENGINE=InnoDB[^;]*;").matcher(sql);
        if (!matcher.find()) { throw new IllegalArgumentException("升级脚本缺少目标表 DDL"); }
        String ddl = matcher.group();
        try (Connection connection = DriverManager.getConnection(url, admin.getProperty("spring.datasource.username"),
            resolve(admin.getProperty("spring.datasource.password"), secrets)); var statement = connection.createStatement()) {
            statement.execute(ddl);
            int inserted = statement.executeUpdate("""
                INSERT IGNORE INTO blade_oauth2_refresh_token (id, token_hash, authorization_id, expires_at, tenant_id)
                SELECT id, refresh_token_hash, authorization_id, expires_at, tenant_id FROM blade_oauth2_authorization
                WHERE refresh_token_hash IS NOT NULL AND status=1 AND is_deleted=0 AND expires_at>CURRENT_TIMESTAMP
                """);
            System.out.println("LOCAL_REFRESH_STATE_TABLE_READY=1;BACKFILLED=" + inserted);
            // 原测试账号只被授予既有两张状态表的权限，新表需要同样的最小访问能力。
            try (Connection application = DriverManager.getConnection(authorization.getProperty("AUTHORIZATION_DATASOURCE_URL"),
                authorization.getProperty("AUTHORIZATION_DATASOURCE_USERNAME"),
                resolve(authorization.getProperty("AUTHORIZATION_DATASOURCE_PASSWORD"), secrets));
                var applicationStatement = application.createStatement()) {
                String account;
                try (var current = applicationStatement.executeQuery("SELECT CURRENT_USER()")) { current.next(); account = current.getString(1); }
                String[] principal = account.split("@", 2);
                String schema = application.getCatalog();
                if (principal.length != 2 || !principal[0].matches("[A-Za-z0-9_.-]+")
                    || !principal[1].matches("[%A-Za-z0-9_.:-]+") || !schema.matches("[A-Za-z0-9_]+")) {
                    throw new IllegalArgumentException("测试数据库身份格式不符");
                }
                statement.execute("GRANT SELECT, INSERT ON `" + schema + "`.`blade_oauth2_refresh_token` TO '"
                    + principal[0] + "'@'" + principal[1] + "'");
                try (var count = applicationStatement.executeQuery("SELECT COUNT(*) FROM blade_oauth2_refresh_token")) {
                    count.next(); System.out.println("APPLICATION_REFRESH_STATE_READ_READY=1");
                }
            }
        }
    }

    /** 仅同步本地 gateway-bff 的已确认 scope/audience，保存不含密钥的回滚依据。 */
    private static void syncTestClient(Properties admin, Properties secrets, Path evidence) throws Exception {
        String url = admin.getProperty("spring.datasource.url");
        if (url == null || !url.matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost)(:[0-9]+)?/.*")) {
            throw new IllegalArgumentException("仅允许本地测试配置");
        }
        try (Connection connection = DriverManager.getConnection(url, admin.getProperty("spring.datasource.username"),
            resolve(admin.getProperty("spring.datasource.password"), secrets))) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, scopes, audiences FROM blade_client WHERE client_id=? AND status=1 AND is_deleted=0 FOR UPDATE")) {
                statement.setString(1, "gateway-bff");
                try (var rows = statement.executeQuery()) {
                    if (!rows.next()) { throw new IllegalStateException("测试 BFF 客户端不存在"); }
                    long id = rows.getLong(1);
                    String oldScopes = rows.getString(2);
                    String oldAudiences = rows.getString(3);
                    if (rows.next()) { throw new IllegalStateException("测试 BFF 客户端不唯一"); }
                    Properties before = new Properties();
                    before.setProperty("client.id", String.valueOf(id));
                    before.setProperty("client.id-name", "gateway-bff");
                    before.setProperty("before.scopes", oldScopes);
                    before.setProperty("before.audiences", oldAudiences);
                    before.setProperty("after.scopes", "openid,profile,demo.read,demo.write,service.read");
                    before.setProperty("after.audiences", "blade-demo,blade-system,blade-desk,blade-ai,blade-log,blade-resource,blade-develop,blade-report");
                    before.setProperty("recorded-at-utc", Instant.now().toString());
                    if (Files.exists(evidence)) { throw new IllegalStateException("禁止覆盖既有配置回滚依据"); }
                    try (var writer = Files.newBufferedWriter(evidence, StandardCharsets.UTF_8)) { before.store(writer, "Local test client scope/audience migration; no credentials"); }
                    try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE blade_client SET scopes=?, audiences=? WHERE id=? AND client_id=? AND scopes=? AND audiences=? AND status=1 AND is_deleted=0")) {
                        update.setString(1, before.getProperty("after.scopes"));
                        update.setString(2, before.getProperty("after.audiences"));
                        update.setLong(3, id);
                        update.setString(4, "gateway-bff");
                        update.setString(5, oldScopes);
                        update.setString(6, oldAudiences);
                        if (update.executeUpdate() != 1) { throw new IllegalStateException("测试配置并发变化"); }
                    }
                    connection.commit();
                    System.out.println("LOCAL_TEST_CLIENT_SCOPE_AUDIENCE_UPDATED=1;ROLLBACK_EVIDENCE=" + evidence.getFileName());
                }
            } catch (Exception exception) { connection.rollback(); throw exception; }
        }
    }

    private static void inspect(String url, String user, String password, boolean clients) throws Exception {
        if (url == null || !url.matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost)(:[0-9]+)?/.*")) {
            throw new IllegalArgumentException("探针仅允许本地测试地址");
        }
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            connection.setReadOnly(true);
            System.out.println((clients ? "SYSTEM" : "AUTHORIZATION") + "_SCHEMA=" + connection.getCatalog());
            if (clients) {
                try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT client_id, scopes, audiences, status FROM blade_client WHERE client_id IN (?, ?) AND is_deleted=0")) {
                    statement.setString(1, "gateway-bff");
                    statement.setString(2, "demo-service");
                    try (var rows = statement.executeQuery()) {
                        while (rows.next()) {
                            System.out.println("CLIENT=" + rows.getString(1) + ";SCOPES=" + rows.getString(2)
                                + ";AUDIENCES=" + rows.getString(3) + ";STATUS=" + rows.getInt(4));
                        }
                    }
                }
            } else {
                try (var rows = connection.getMetaData().getTables(connection.getCatalog(), null, "blade_oauth2_%", new String[]{"TABLE"})) {
                    while (rows.next()) { System.out.println("AUTH_TABLE=" + rows.getString("TABLE_NAME")); }
                }
            }
        }
    }
}
