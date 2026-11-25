# 内观（InnerSight）技术方案书

v0.1 · 2026-11-25 · 数据库选型已确认，后端已适配；正式 PRD 验收另记。

## 主数据库

InnerSight 的主数据库固定为 **MariaDB Server 10.11.19**。S0 先保存账号、角色、授权、会话和管理操作记录；题库、训练记录和评价随各自需求进入主库。开发与验收均使用这个服务端版本。版本号不用于 JDBC 驱动或 Flyway 的依赖版本。

| 项目 | 约定 |
|---|---|
| 环境隔离 | 开发库 `innersight_dev` 和测试库 `innersight_test` 各用独立账号，仅授予本库所需权限。首次初始化从专用空库开始，库字符集采用 `utf8mb4`。 |
| Java 连接 | 使用 MariaDB Connector/J（`org.mariadb.jdbc:mariadb-java-client`）和 `jdbc:mariadb://`。远程连接配置可信证书，使用 `sslMode=verify-full` 验证服务端证书及主机名。 |
| 结构迁移 | DBA 准备库与账号，应用用 Flyway 管理版本化表结构。MariaDB 支持由 `flyway-mysql` 模块提供。建库脚本中的排序规则、账号和授权语法须在目标版本验证。 |

## S0 如何确认用对了库

在首次迁移前，用测试账号连接目标库并保存 `SELECT VERSION(), DATABASE();` 的原始结果。确认服务端为 MariaDB、版本号为 `10.11.19`（可带发行包后缀）、当前库名为 `innersight_test`。再查 `SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE();`，结果为 `0` 才执行迁移及 [PRD 验收用例](features/feature-20261124154000-项目搭建/02-prd.md#acceptance)。版本、库名或空库检查不符时停止，不写入数据。记录候选提交、迁移结果及实际执行的用例；跳过实库集成的构建不能作为验收证据。

驱动、默认连接地址、建库授权脚本与集成测试已切换为 MariaDB；此前的联通尝试和自测结果保留在[历史记录](features/feature-20261124154000-项目搭建/05-verification.md)。

项目阶段见[项目书](01-project-proposal.md)，配置入口见[本地启动说明](local-development.md)。技术依据：[MariaDB 10.11.19 发布说明](https://mariadb.com/docs/release-notes/community-server/10.11/10.11.19)、[Connector/J 的 TLS 配置](https://mariadb.com/docs/connectors/mariadb-connector-j/using-tls-ssl-with-mariadb-java-connector)、[Flyway 的 MariaDB 支持](https://documentation.red-gate.com/flyway/reference/database-driver-reference/mariadb)。
