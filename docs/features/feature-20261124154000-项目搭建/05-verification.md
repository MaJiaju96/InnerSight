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

## GitHub 基线回归与接口覆盖补充（2026-11-25）

- GitHub 默认分支已为 `master`；`master`、`test`、`feat-20261124154000-项目搭建` 均指向 `d5cc5e2`。本地在功能分支回归，未登记正式候选、合入或发布。
- 基线 `d5cc5e2`：`mvn -B -f backend/pom.xml verify` 输出 `Tests run: 9, Failures: 0, Errors: 0, Skipped: 1`、`BUILD SUCCESS`。`npm run typecheck`、`npm run test`（4 项通过）、`npm run build`（`✓ 42 modules transformed.`）和 `npm run test:visual`（`Browser smoke PASS`，固定 API）均通过。
- 功能分支工作树：新增 3 项 MockMvc 契约测试，覆盖 AC-02／AC-10 的 401／429 不发 Cookie、退出调用会话撤销并清除 Cookie、生产配置下登录 Cookie 属性及响应不回传会话令牌。再次 `verify` 输出 `Tests run: 12, Failures: 0, Errors: 0, Skipped: 1`、`BUILD SUCCESS`。MockMvc 不证明实际数据库的限流、会话撤销或依赖故障恢复。
- 本轮未启动任何本地数据库，未写远程开发库或测试库。MariaDB 集成用例未设置专用测试连接，故跳过。AC-08 管理员并发保留、AC-09 多类并发编辑、AC-10 断库恢复、AC-11 性能基线及真实双会话验收仍需补齐。

## AC-08／AC-09 用例补充（2026-11-25）

- AC-08：新增唯一管理员自停用／撤除 `ADMIN` 的拒绝检查，以及两个独立会话同步交叉停用后的状态、会话失效、幸存管理员重新登录和单条审计验证。
- AC-09：新增两个管理员针对同一旧版本提交角色绑定、用户资料、角色权限的冲突检查；验证首笔结果保留，含非法角色的绑定请求不改变状态。
- `mvn -B -f backend/pom.xml verify`：`Tests run: 12, Failures: 0, Errors: 0, Skipped: 1`、`BUILD SUCCESS`。新增实库断言已编译，归属的 `MariaDbAcceptanceTest` 未执行。远程 `innersight_test` 只读预检以 `sslMode=verify-full` 连接时返回 `SQLState=08001`，尚未检查版本和空库；未迁移或写入远程库，未启动本地数据库。

## AC-11 性能测量准备（2026-11-25）

- 集成测试在同一 `REPEATABLE READ` 事务中补齐 1,000 用户、20 角色，检查筛选、分页上限、同时间按 ID 倒序且跨页无重复和遗漏。通过 MariaDB 会话 `Com_select` 比较 20／100 条列表的查询次数，并输出两组耗时。性能样本事务回滚；整套测试仍会迁移、初始化及写入其他验收数据，仅可对专用空测试库使用。
- 远程测试入口只读复查仍为 `SQLState=08001`，未改测试账号或远程数据，也未启动本地库。`mvn -B -f backend/pom.xml verify` 输出 `Tests run: 12, Failures: 0, Errors: 0, Skipped: 1`、`BUILD SUCCESS`。新增用例已编译，尚无实测查询次数和耗时，不能据此通过 AC-11。

## AC-10 故障响应回归（2026-11-25）

- 新增两项 MockMvc 契约用例：列表读取模拟数据源故障时返回 `503 UNAVAILABLE`，不伪装成空列表，依赖恢复后的读取返回真实形状的数据；事务创建失败的写入不返回成功。两条失败响应都断言不回传底层连接详情或设置 Cookie。
- `mvn -B -f backend/pom.xml verify` 输出 `Tests run: 14, Failures: 0, Errors: 0, Skipped: 1`、`BUILD SUCCESS`。本轮没有操作远程库，也没有启动本地库；真实断库恢复、并发和性能仍待专用测试库复验。

## AC-10 浏览器读取失败与恢复（2026-11-25）

- 在固定 API 的 Edge 冒烟中验证：用户列表首次读取收到 503 时显示错误和「重试」，不显示空态或旧记录；重试返回数据；无匹配筛选显示独立空态，清除筛选恢复列表。未使用真实账号或数据库。
- `npm run typecheck`、`npm run test`（`Tests 4 passed (4)`）、`npm run build`（`✓ 42 modules transformed.`）通过；`npm run test:visual` 输出 `Browser smoke PASS`，包含 `503/retry and empty-result distinction`。不替代真实断库或正式验收。

## 远程测试库实跑（2026-11-25）

- 功能分支工作树，HEAD `d5cc5e2`，含尚未提交的测试改动；非正式 `test` 候选。按发起人确认的测试连接例外，认证后执行只读预检：`SELECT VERSION(), DATABASE(): 10.11.19-MariaDB-0+deb12u1 | innersight_test`，迁移前 0 张表。未操作 `innersight_dev`。
- `mvn -B -f backend/pom.xml verify`：`Successfully applied 1 migration to schema ... now at version v1`；`Tests run: 14, Failures: 0, Errors: 0, Skipped: 0`；`BUILD SUCCESS`。AC-08 的双管理员交叉停用、原会话失效和幸存者登录，以及 AC-09 三类旧版本拒绝与非法绑定不落库的断言全部通过。
- AC-11：远程 MariaDB 测试库，从 Java 17 工作站对列表 Repository 测得 `users=1000 roles=20; page20=3 SELECT/235.95 ms; page100=3 SELECT/259.79 ms`。同一 `REPEATABLE READ` 快照的分页顺序、无重复遗漏、筛选及上限断言通过。耗时包含本次远程 JDBC 往返，不是容量承诺。
- 性能样本按事务回滚；V1 表和其他验收数据仍保留。需要新的专用空库才能重跑原有空库用例，不自动清库。真实断库恢复、双浏览器和 `test` 分支正式验收仍待执行。

## 双浏览器与数据库链路恢复自测（2026-11-25）

- 环境：功能分支工作树，HEAD `d5cc5e2`；Windows Edge 两个独立浏览器上下文，隔离 Vite／Spring Boot 进程，远程专用 MariaDB `innersight_test`（沿用已迁移 V1）。`npm run build` 通过，`✓ 42 modules transformed.`。临时脚本从已授权测试库创建随机名和随机密码管理员，仅用于本次自测；测试数据保留。未登记正式候选。
- 双会话：管理员从界面建只读角色与用户并授权；普通用户首次改密、重登、只读访问；原会话刷新后撤权立即生效且清除列表，仍可返回个人首页；停用后原会话回登录。浏览器脚本输出 `PASS two independent browser sessions: role grant, forced password change, readonly, revocation, disabled session`。
- AC-10 链路故障：仅切断隔离应用通往远程库的真实 TCP 连接，不停 MariaDB。存活接口仍为 200，就绪和列表变为 503；编辑提交未报成功，显示「保存结果未知」并禁用再次保存；恢复链路后就绪为 200，重载确认原记录未被覆盖。脚本输出 `PASS database link interruption: live 200, readiness/list/write 503, no false success, recovered without lost or partial write`。
- 验收临时监听 15181／18081／13368 均已关闭；开发预览 5173／8080 仍在运行，就绪接口为 `{"database":"up","service":"up"}`。未触及开发库，没有启动本地数据库。本轮是开发自测，不替代完整代码自审、正式 `test` 分支验收或发布。

## 功能分支预览前复测（2026-11-25）

- 仓库仅登记 S0 项目搭建 PRD；S1 未立项。本轮只改测试和进度记录，无业务 API、schema 或前端生产代码变更；复用已运行的开发环境，`GET /api/health/ready` 返回 `{"database":"up","service":"up"}`。
- `mvn -B -f backend/pom.xml '-Dtest=MariaDbAcceptanceTest#sessionBoundariesOnExistingTestSchema' test`：远程 `10.11.19-MariaDB-0+deb12u1 | innersight_test`，`Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`。AC-02 验证认证失败状态、规范化用户名限流、窗口恢复、空闲／绝对过期和退出作废。采用可控时钟和显式回滚事务；未清库或执行迁移。
- 常规 `mvn -B -f backend/pom.xml verify`：`Tests run: 15, Failures: 0, Errors: 0, Skipped: 2`、`BUILD SUCCESS`，未设置实库参数；前端类型检查、4 项单测、构建和浏览器冒烟通过。空库全套此前版本已实跑，本轮因现有测试库有 9 张表而未复跑；完整 PRD 流程和验收仍以用例证据为准。
- 当前仍待发起人从开发预览验证。未合入 `test`、未发布；正式候选前还需完成全量自审并记录尚未逐项核验的验收步骤。

## 合入 test 前功能分支自审（2026-11-25）

审查范围：开发基线 `d51a76f` 至本批待提交的完整差异，包含 `d5cc5e2` 的工程实现及后续测试。项目发起人已确认功能分支预览无问题并授权合入 `test` 回归。

| 检查点 | 场景与证据 | 结论 |
|---|---|---|
| 需求、UI 与授权 | 核查会话和管理 Controller → Service 授权、Cookie 与同源检查、只读导航、403 清理、表单失败状态。依据 MockMvc、Edge 冒烟和双浏览器实库自测。 | 核心路径未发现阻断；发起人已确认预览，正式 PRD/UI 仍按同一候选回归。 |
| 并发与完整性 | 核查 `write_guard`、唯一约束、版本检查、账号／角色／授权事务；AC-08／AC-09 的并发停用和过期编辑已实跑。 | 已有并发用例通过，未发现阻断。 |
| 稳定与追溯 | 核查 Flyway、审计、会话撤销与故障提示；AC-02 可控时钟、AC-10 隔离真实链路断开和恢复已有证据。 | 已测路径通过；完整空库初始化、重启与密码生命周期需在正式验收单独核对。 |
| 性能 | 核查参数化分页与批量角色摘要；远程测试库 1,000 用户／20 角色下每页 20、100 条均为 3 次 SELECT。 | 已有 AC-11 基线通过，不作容量承诺。 |

结论：同意进入 `test` 回归，不表示已通过正式验收。现有专用测试库已迁移并保留验收数据，不能替代 PRD 所要求的空库，不会自动清库。
