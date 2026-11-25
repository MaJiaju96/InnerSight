# 项目搭建 · 功能分支自测记录

- 日期：2026-11-24；文档基线：`d51a76f`；分支：`feat-20261124154000-项目搭建`。
- 范围：前端与后端初版工作树，尚无 `test` 候选提交。此记录不是完整代码自审、PRD 验收或发布证明。

| 检查点 | 场景与证据 | 结论 |
|---|---|---|
| 前端静态验证 | `npm ci`；`npm run typecheck`；`npm run test`：`Tests 4 passed (4)`；`npm run build`：`✓ 38 modules transformed.` | 通过。 |
| 浏览器 | Vite 与 Edge，无外部测试库。`npm run test:visual`：`Browser smoke PASS: system/hand-picked themes, input persistence, refresh, readonly navigation, modal Escape/focus, 320px/1280px no overflow, 0 JS exceptions (fixture API).` | 交互冒烟通过。固定响应不能证明真实授权或整套画板验收通过。 |
| 后端构建 | `mvn -B -f backend/pom.xml verify`：`Tests run: 6, Failures: 0, Errors: 0, Skipped: 1`、`BUILD SUCCESS`。 | 五项规则单测通过；真实 MySQL 集成用例跳过。 |
| 数据服务配置 | 对用户提供的 MySQL 主库地址执行 TCP 与 JDBC 只读预检；认证成功，未见 InnerSight 专用开发库或测试库。未在原业务库执行迁移或写入。 | 待专用库及权限准备完毕后验证 AC-01–AC-11；不依赖本地 Docker。 |
| 并发、授权与故障 | `MySqlAcceptanceTest` 包含并发初始化、强制改密、权限并集、撤权、版本冲突和停用会话断开的测试；未执行。 | 无真实事务或会话验收结论。需继续逐项执行业务、权限及故障用例。 |
| 性能与视觉验收 | 业务列表采用服务端分页与批量读取角色，尚未实测 1,000 用户 / 20 角色查询基线；未对全部 Pixso 画板逐一验收。 | 待正式验证，不合入 `test`。 |

## 目录重整后复测（2026-11-24）

- 后端 `mvn -B -f backend/pom.xml verify`：`Tests run: 9, Failures: 0, Errors: 0, Skipped: 1`、`BUILD SUCCESS`。新增 3 项 MockMvc 契约测试，覆盖健康检查、会话响应形状、管理路径和跨站写入拒绝。
- 前端 `npm run typecheck`、`npm run test`（`Tests 4 passed (4)`）、`npm run build`（`✓ 41 modules transformed.`）均通过。`npm run test:visual` 输出 `Browser smoke PASS`，固定 API 响应、320px／1280px 无横向溢出、无 JS 异常。
- 项目发起人报告已手工建库。两个专用账号在指定 MySQL 入口 `sslMode=REQUIRED` 均返回 `SQLState=08001`（TLS 不可用）；未用真实凭据尝试明文，未执行 Flyway。真实 MySQL 集成用例仍跳过。未使用本地 Docker。

## MariaDB 适配复测（2026-11-25）

- 功能分支工作树，基线 `d51a76f`；未合入 `test`，无候选提交。后端使用 MariaDB Connector/J 3.5.8 与 Flyway 11.7.2。临时实例为 Windows 原生 MariaDB（未使用 Docker），只绑定回环，使用短期测试 CA 与 `sslMode=verify-full`；不修改远程环境。
- DBA 脚本在 MariaDB 10.11.19 运行成功。开发、测试库初始表数均为 `0`；各账号的 `GRANT` 只指向对应库。测试账号运行 Flyway、应用和测试。结束时测试库有 9 张表，开发库仍为空；临时实例已关闭。
- `mvn -B -f backend/pom.xml verify`：`SELECT VERSION(), DATABASE(): 10.11.19-MariaDB | innersight_test`；`Successfully applied 1 migration to schema `innersight_test`, now at version v1`；`Tests run: 9, Failures: 0, Errors: 0, Skipped: 0`；`BUILD SUCCESS`。
- `MariaDbAcceptanceTest` 覆盖 AC-01 的空库迁移及并发初始化，和 AC-03、AC-04 至 AC-07、AC-09 的部分服务层用例：强制改密、初始无角色、并集与撤权、过期写拒绝、停用后原会话失效。将集成测试 URL 改为开发库的负向测试在连接前被拒绝。未完成 PRD 全量用例、双浏览器和 1,000 用户性能验证。
- 远程 MariaDB 此前查询显示 `have_ssl=DISABLED`；尚未证明远程 `verify-full` 可用。远程 Flyway、正式 `test` 验收和发布均未执行。

## 远程开发库预览与登录 UI 复测（2026-11-25）

- 经项目发起人确认，仅开发账号取消强制数据库 TLS；开发库直连属于预览例外，不改变正式部署和测试验收要求。远程 `innersight_dev` 从空库执行 Flyway V1 并初始化应用管理员。经 Vite 代理检查就绪：`{"database":"up","service":"up"}`；真实临时密码登录及退出验证成功，未修改测试库。
- 登录与改密字段统一外框和错误状态。Edge 在 1280px、320px 的浅色／深色下检查登录字段和按钮对齐、焦点与密码显隐。退出成功提示水平居中，在主题切换栏底边下方 20px，使用半透明毛玻璃并于 3 秒后消失；浏览器冒烟通过，前端类型检查、4 项单测与构建均通过。
