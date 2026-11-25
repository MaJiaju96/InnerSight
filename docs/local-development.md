# InnerSight 本地启动

## 环境

- Node.js 22.12–24，使用 npm 与 `frontend/package-lock.json`。本次构建使用 Node.js 24.19.0、npm 11.17.0。
- Java 17、Maven 3.6.3 或更高版本，Spring Boot 3.5.16。执行 `mvn --version` 确认 Maven 实际选用的 JDK。
- 数据库目标为 MariaDB Server 10.11.19，使用独立的 `innersight_dev`、`innersight_test`、`innersight_master` 库及账号，默认字符集为 `utf8mb4`。不需要 Docker、AI 服务或外部模型。

后端使用 MariaDB Connector/J 3.5.8、Flyway 11.7.2（均由 Spring Boot BOM 管理）。数据库约定见[技术方案书](02-technical-design.md)。

## 代码和数据库文件放在哪里

- 后端 `backend/src/main/java/cn/innersight` 按 `auth`、`user`、`role`、`health` 分模块。Controller 接收参数、调用 Service 并返回 DTO；事务和授权在 Service；JDBC 查询与写入在 Repository。公共配置、Web 组件、错误处理放在 `common/`。
- 前端 `frontend/src` 以 `App.vue` 为应用外壳；`router/` 管理路由，`views/` 放页面，`components/` 放共用组件，`api/` 统一请求与接口类型，`composables/` 放主题逻辑，`styles/` 放样式。
- DBA 负责准备专用库和账号。仓库中的[建库与授权脚本](../backend/sql/00-create-databases.sql)按 MariaDB 10.11.19 编写，首次分段执行；已有专用库和账号时先核对，不重复建账号。表结构由 `backend/src/main/resources/db/migration` 下的 Flyway 迁移维护；后续变化新增版本化文件，不回改已执行的迁移。
- 远程连接使用支持 TLS 的 MariaDB 入口和与服务端证书匹配的主机名。为 MariaDB Connector/J 配置可信证书和 `sslMode=verify-full`。经批准的专用开发／回归账号可使用 `REQUIRE NONE` 并由客户端指定 `sslMode=disable`；该例外不适用于生产，不修改服务器全局设置。

## 启动开发环境（数据库适配完成后）

在项目根目录执行 `mvn -B -f backend/pom.xml verify`。在后端 PowerShell 窗口设置以下变量，值使用自己的配置：

| 环境变量 | 用途 |
|---|---|
| `INNERSIGHT_DB_URL` | 目标 JDBC URL 示例：`jdbc:mariadb://db.example.test:3306/innersight_dev?sslMode=verify-full`。将示例主机名替换为实际证书匹配的域名 |
| `INNERSIGHT_DB_USER` | 数据库账号 |
| `INNERSIGHT_DB_PASSWORD` | 数据库密码 |
| `INNERSIGHT_ADMIN_LOGIN`、`INNERSIGHT_ADMIN_NAME`、`INNERSIGHT_ADMIN_PASSWORD` | 单次初始化使用。密码 15–128 字符。不要把这些值写入 Git、命令参数或会被共享的日志。 |
| `INNERSIGHT_ORIGIN` | 浏览器同源地址；默认 `http://localhost:5173`，必须与实际访问地址一致 |
| `INNERSIGHT_SECURE_COOKIE` | 默认 `true`；仅本地 HTTP 开发设置 `false`。生产使用 HTTPS，保持 `true`。 |
| `INNERSIGHT_PORT` | 后端端口，默认 `8080` |

首次初始化执行 `java -jar backend/target/innersight-0.1.0-SNAPSHOT.jar --spring.profiles.active=initialize`。这一步启动 Flyway 并在无 Web 监听的进程里创建管理员；已经初始化的库不会重置已有账号。完成后从当前进程环境移除 `INNERSIGHT_ADMIN_*` 变量。服务端进程用 `java -jar backend/target/innersight-0.1.0-SNAPSHOT.jar` 启动，不带初始化 profile。

另开前端窗口，在 `frontend` 目录执行 `npm ci`、`npm run dev`，用 `http://localhost:5173` 访问。首次登录必须设置新密码并重新登录。Vite 同源代理 `/api`，后端端口不同时用 `INNERSIGHT_API_TARGET` 设置代理目标。部署时用同一 HTTPS 域名提供 `frontend/dist` 和 `/api`，非 API 页面路径回退到 `index.html`。代理后端前请设置真实浏览器 Origin；限流默认用直接连接来源，代理层如需区分客户端须在可信边界另行配置，应用不直接信任客户端传入的转发地址。

- `GET /api/health/live` 检查服务可达性；`GET /api/health/ready` 检查数据库，运行中断库返回 `503` 与 `database: down`。若首次迁移无法连接库，应用启动失败，不进入服务状态。
- 前端路由为 `/login`、`/password`、`/`、`/users`、`/roles`。管理权限由后端每次判定；本地存储只记录主题，不保存凭据。

## 验证

- 前端在 `frontend` 下执行 `npm run typecheck`、`npm run test`、`npm run build`。运行 Vite 后，将 `INNERSIGHT_BROWSER` 设为本机 Edge 或 Chromium 可执行文件绝对路径，再运行 `npm run test:visual`。这一步使用固定后端响应，只验证浏览器布局和交互。
- 后端在项目根运行 `mvn -B -f backend/pom.xml verify`。设置测试连接后会启用 `MariaDbAcceptanceTest`；不设置时跳过。用测试账号按[技术方案书](02-technical-design.md)核对服务端版本、库名和空库状态，再设置 `INNERSIGHT_TEST_DB_URL`、`INNERSIGHT_TEST_DB_USER`、`INNERSIGHT_TEST_DB_PASSWORD` 并运行同一命令。空库用例还必须显式指定 `INNERSIGHT_TEST_SCOPE=master`，目标只允许 `innersight_master`，会迁移并写测试数据，不清库。部分回归使用 `-Dtest=MariaDbAcceptanceTest#sessionBoundariesOnExistingTestSchema` 和 `INNERSIGHT_TEST_EXISTING_SCHEMA=true`，目标只允许 `innersight_test`，测试变更回滚。跳过实库用例的构建不算实库验证。
- 全量验收使用 `master` 候选提交、专用 MariaDB Server 10.11.19 全量回归库和两个浏览器会话，按 [PRD 的 AC-01–AC-11](features/feature-20261124154000-项目搭建/02-prd.md#acceptance) 记录真实结果。
