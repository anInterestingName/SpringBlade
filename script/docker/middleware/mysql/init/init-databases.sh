#!/usr/bin/env bash

set -euo pipefail

escape_sql_literal() {
	printf '%s' "$1" | sed "s/'/''/g"
}

blade_password=$(escape_sql_literal "${BLADE_MYSQL_PASSWORD}")
nacos_password=$(escape_sql_literal "${NACOS_MYSQL_PASSWORD}")

mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
CREATE DATABASE IF NOT EXISTS blade CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE IF NOT EXISTS nacos_config CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'blade'@'%' IDENTIFIED BY '${blade_password}';
ALTER USER 'blade'@'%' IDENTIFIED BY '${blade_password}';
GRANT ALL PRIVILEGES ON blade.* TO 'blade'@'%';
CREATE USER IF NOT EXISTS 'nacos'@'%' IDENTIFIED BY '${nacos_password}';
ALTER USER 'nacos'@'%' IDENTIFIED BY '${nacos_password}';
GRANT ALL PRIVILEGES ON nacos_config.* TO 'nacos'@'%';
FLUSH PRIVILEGES;
SQL

mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}" nacos_config \
	< /opt/springblade/bootstrap/nacos.mysql-schema.sql
