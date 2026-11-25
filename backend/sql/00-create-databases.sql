-- InnerSight / MariaDB Server 10.11.19 / DBA 手工执行，请分段执行且遇错即停。
-- 核对下方版本；两个库必须专用且为空，不要对现有业务库执行迁移。
SELECT VERSION();

CREATE DATABASE IF NOT EXISTS `innersight_dev`
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `innersight_test`
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

SELECT s.schema_name, COUNT(t.table_name) AS table_count
FROM information_schema.schemata s
LEFT JOIN information_schema.tables t ON t.table_schema = s.schema_name
WHERE s.schema_name IN ('innersight_dev', 'innersight_test')
GROUP BY s.schema_name;

-- 确认两个库的 table_count 均为 0 再继续。通过安全渠道生成和传递不同密码。
-- 将占位符替换成各自的凭据，再取消下面账号和授权段的注释；不要保存替换后的脚本。
-- 远程账号要求 TLS。将 <client-host> 替换为数据库侧看到的客户端主机或地址。
-- 已有专用账号时，不要重建或重置密码，先核对 SHOW GRANTS。
-- CREATE USER 'innersight_s0_dev'@'<client-host>' IDENTIFIED BY '<unique-dev-secret>' REQUIRE SSL;
-- CREATE USER 'innersight_s0_test'@'<client-host>' IDENTIFIED BY '<unique-test-secret>' REQUIRE SSL;

-- 转义库名中的下划线，限制权限只覆盖对应库。
-- GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
--   ON `innersight\_dev`.* TO 'innersight_s0_dev'@'<client-host>';
-- GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
--   ON `innersight\_test`.* TO 'innersight_s0_test'@'<client-host>';
-- SHOW GRANTS FOR 'innersight_s0_dev'@'<client-host>';
-- SHOW GRANTS FOR 'innersight_s0_test'@'<client-host>';

-- 业务表由应用自动运行 Flyway 创建，不应在此手动建表。
