# 内观（InnerSight）PRD 进度

更新：2026-11-24。每项独立 PRD 一条记录；选定阶段及全局状态见[项目总进度](PROJECT_PROGRESS.md)，阶段流转与证据要求见[项目开发约束](../AGENTS.md)。

| 需求编号 | 阶段 | 需求分析 / PRD | UI | 实现状态 | 对应开发分支 |
|---|---|---|---|---|---|
| `feature-20261124154000-项目搭建` | S0 | [需求分析](features/feature-20261124154000-项目搭建/01-requirements.md) / [PRD](features/feature-20261124154000-项目搭建/02-prd.md)：v0.1，已确认 | [UI 文字设计](features/feature-20261124154000-项目搭建/03-ui-design.md)、[浅色总览](https://pixso.cn/app/design/NYBXHNEYrfdASgLhilojEg?page-id=6%3A198&item-id=6%3A199) / [深色总览](https://pixso.cn/app/design/NYBXHNEYrfdASgLhilojEg?page-id=6%3A2344&item-id=6%3A2345)：v0.2，折光方向已确认，整套稿待终审 | 未开发 | `feat-20261124154000-项目搭建`（计划，未创建） |

## 项目搭建 · 交付记录

| 环节 | 实际证据 / 下一步 |
|---|---|
| 需求评审 | 2026-11-24，项目发起人确认本期需求分析和 PRD。 |
| UI 评审 | 2026-11-24，项目发起人确认「折光」试稿方向。浅、深各 21 张（00–20），对应页面、角色、异常与 320px 窄屏；原稿与试稿保留在「页面 1」。可从[浅色登录](https://pixso.cn/app/design/NYBXHNEYrfdASgLhilojEg?page-id=6%3A198&item-id=6%3A242)、[深色首页](https://pixso.cn/app/design/NYBXHNEYrfdASgLhilojEg?page-id=6%3A2344&item-id=6%3A2505)、[深色授权差异](https://pixso.cn/app/design/NYBXHNEYrfdASgLhilojEg?page-id=6%3A2344&item-id=6%3A3194)、[窄屏只读列表](https://pixso.cn/app/design/NYBXHNEYrfdASgLhilojEg?page-id=6%3A198&item-id=6%3A1741)进入。两套 Pixso 为可编辑静态稿；[登录与首页 HTML 样例](features/feature-20261124154000-项目搭建/04-motion-study.html)供实际体验主题与动效。整套稿待终审核对，业务开发未启动。 |
| 设计走查 | Pixso：两套各 21 张，19 个产品场景逐一核对文字与权限动作；抽查浅色登录、深色首页、授权抽屉和窄屏。离线样例：Edge 通过 24 项原有交互回归和 56 项主题检查；1280px 与 320px 下无横向溢出，登录前后可切色，刷新保留手选，禁用存储时仍可切。浏览器记录 `JS exceptions=0; HTTP(S) requests=0`。此处只记录设计与样例，业务验收仍待实现。 |
| 开发与自测 | 未开始；尚无仓库、基线提交或应用构建命令。 |
| 代码自审 | 未执行，无代码差异。实现后登记审查范围、问题、修复、验证输出与复验结果。 |
| `test` 验收 | 未执行。届时登记候选提交、用例结果、测试环境、缺陷与确认人。 |
| `master` 发布 | 未发布。届时登记标签、提交及发布日。 |
