# 内观 · InnerSight

面向 Java 工程师的 AI 模拟面试与知识自省系统。中文名「内观」取意于道家的内观理念：内视自身，自省自身。英文名为 InnerSight。通过自建题库、可控的追问和有依据的复盘，把面试练习连接到工程实践。

## 从这里开始

| 文档 | 用途 |
|---|---|
| [项目书](docs/01-project-proposal.md) | 整体目标、阶段范围、技术方向与二期规划 |
| [技术方案书](docs/02-technical-design.md) | 主数据库版本、开发与测试隔离、接入方式和验收前提 |
| [项目开发约束](AGENTS.md) | PRD/UI 作为开发依据、分支流程、逐份 PRD 的代码自审与发布条件 |
| [项目总进度](docs/PROJECT_PROGRESS.md) | 各阶段实际交付及下一步 |
| [PRD 进度](docs/PRD_PROGRESS.md) | 每个需求的文档、UI、开发分支、代码自审、测试与发布记录 |

## 当期需求：项目搭建

`feature-20261124154000-项目搭建` 属于 S0。通用后台的目标技术栈为 Vue + TypeScript、Spring Boot 和 MariaDB Server 10.11.19，覆盖基础登录、权限、用户与角色管理。第一条验收主线是管理员为第二个账号授权，该账号能使用获准的操作；收回授权后，原会话立即失去相应权限。

1. 读[需求分析书](docs/features/feature-20261124154000-项目搭建/01-requirements.md)，确认使用者、授权规则与质量要求。
2. 读[项目搭建 PRD](docs/features/feature-20261124154000-项目搭建/02-prd.md)，确认页面行为、异常场景与验收用例。
3. 读[本期 UI 文字设计](docs/features/feature-20261124154000-项目搭建/03-ui-design.md)，评审页面和交互。Pixso 可按相同编号对照[浅色稿](https://pixso.cn/app/design/NYBXHNEYrfdASgLhilojEg?page-id=6%3A198&item-id=6%3A199)和[深色稿](https://pixso.cn/app/design/NYBXHNEYrfdASgLhilojEg?page-id=6%3A2344&item-id=6%3A2345)；[登录与首页交互样例](docs/features/feature-20261124154000-项目搭建/04-motion-study.html)可用浏览器本地打开，试按右上角主题胶囊。浅色和深色整套稿已指定为开发依据，正式视觉验收另行记录。本阶段不用配置模型、创建题库或准备训练数据。

启动前后端、初始化管理员及验证用例见 [本地启动](docs/local-development.md)。开发、测试和发布状态见 [PRD 进度](docs/PRD_PROGRESS.md)。
