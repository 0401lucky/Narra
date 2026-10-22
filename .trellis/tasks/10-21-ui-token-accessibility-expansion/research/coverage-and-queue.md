# Research: 扩面截图契约与后续入口队列

- Query：如何为 P04/P05/P10/P11 添加可审阅覆盖，并在 v2.3 范围内安排后续页面。
- Scope：internal；主会话负责公共截图基础设施与后续队列，三个研究代理分别负责优先入口，避免重复探索。
- Date：2026-10-21

## 已有覆盖事实

- `app/src/test/java/com/example/myapplication/ui/screenshot/ScreenshotCases.kt:60` 的 H01 只注册 P01/P02/P03；`:184` 的 B04 线下两项明确是组件级，不是 `RoleplayScreenContent` 整页。
- 登记表 `:199` 汇总 76 个矩阵用例，另有 2 张显式冒烟 PNG；`ScreenshotBaselineGuardTest.kt:70` 检查每类 `@Test` 数与登记一致，`:92` 拒绝未登记 PNG，`:100` 检查缺图。新增页不能用改写 B04 描述或删旧图来冒充覆盖。
- `ScreenshotStageTest.kt:53` 固定 API 35 / native graphics；`:175` 的 `captureCase` 当前只捕获 `StageTag`，`:193` 校验固定像素 / 字体 / 非空内容，`:247` 使用零色差与零变化像素阈值。
- Dialog / Popup 不属于舞台节点子树。P05 侧栏本体是独立路由面板，其内部的 Sheet / AlertDialog 与 P11 模型 / 模板弹层需要捕获真实独立窗口；先证明目标窗口被捕获、窗口缺失时失败，不能仅截下面的页面通过。具体入口见各入口研究。
- `app/build.gradle:177` 默认输出仍为 `src/test/snapshots`；`.github/workflows/android-quality.yml:87` 的 push/PR 仅 verify，手动 record 只产候选。扩面不改变 CI 门禁和默认目录。

## 推荐新增基准过程

1. 每个批次开始冻结当前已接纳 PNG 哈希及登记 ID；只对本批添加稳定用例 ID / 测试方法和真实页面 fixture。现有 78 张不重新 record。
2. 产品迁移前运行本批新增用例的 verify。新页没有旧基准，缺图失败是预期结果；保存真实 actual 作为“迁移前证据”，不把它称为已批准基准。先做一个单用例探针，确认工具确实输出 actual；如不输出，必须先落实显式隔离到 task/build 目录的候选捕获方式并证明生产基准哈希不变，不能向源码目录 record。
3. 迁移后同参数再次 verify，保存 after / diff 与失败类型。新增登记尚未接纳期间，计数 / 缺图守卫继续失败，不能临时关闭门禁；不要将此时的全套测试描述为通过。
4. 每批分别展示前后对照、新增用例及所有共享旧图变化；用户确认后才能从已审阅 actual 接纳对应 PNG、增加 manifest 接纳段落并最终 verify。产品 / 测试代码和基准接纳分开提交。
5. 最终期望截图检查数量从当前 79 开始按已接纳新用例累加，不能沿用固定 79 宣称全量覆盖。三主题×浅深、固定动态浅深、320×640dp / 200% 字体及该页状态采用分维度组合，避免全矩阵笛卡尔积。
6. 独立窗口先在宿主做真实内容 / root / 尺寸证据；若工具不能稳定捕获，阻断该批视觉验收并记录具体原因。设备编译或底层页面 PNG 不能代替弹层视觉验收。

## 后续队列（本轮不创建实现子任务）

以下只承接试点扩面清单。四个优先批次后逐项做入口研究与独立规划，不能据此直接开始全 App 改造。

| 顺序 | 范围与入口证据 | 现有可复用证据 | v2.3 边界 |
| --- | --- | --- | --- |
| 5 | P06 世界书：`ui/navigation/SettingsDataNavRoutes.kt:28` 列表，`:65` 书目，`:124` 编辑 | `WorldBookListScreenGroupingTest`、`WorldBookListStyleTest`、`WorldBookViewModelTest` | 列表 / 详情 / 表单默认令牌、语义和大字体；仅观察已存在状态，不新增 v2.4 状态策略、匹配或导入行为 |
| 6 | P07 记忆：`SettingsDataNavRoutes.kt:183` 的 `MemoryManagementScreen`；角色独立记忆还有 `SettingsAssistantNavRoutes.kt:252` | `MemoryManagementScreenTest`、`MemoryManagementScreenFilterTest`、`AssistantMemoryScreenTest` | 先管理页后评估角色记忆；保持筛选、固定 / 删除确认和数据来源，不改提取 / 摘要逻辑 |
| 7 | P08 朋友圈：`ui/navigation/AppNavHost.kt:114` 与 `:143` 使用同一 `MomentsScreen` | `MomentsScreenTest` 覆盖紧凑评论、大字体、相邻命中、发布失败保稿 | 2dp 评论内边距和 4dp 条目间距作为精确兼容例外，触控扩展不能换成固定 48dp 视觉行高；不改发布状态机 |
| 8 | P09 信箱：`RoleplayNavGraph.kt:1001` → `MailboxScreen`；`MailboxScreen.kt:173` / `:517` 有真实弹窗 | `MailboxViewModelTest`、`MailboxRepositoryTest`；未找到对应信箱设备 UI 测试 | 单列列表 / 长信 / 弹层覆盖，不以仓储测试代替 UI 证据；不改生成 / 定时 / 服务 |
| 9 | P12 角色基础设定、P13 语音合成设置 | 沿试点清单，在新规划中核对实际路由、长表单、保存 / 取消 / 错误测试 | 不跟随 P11 一次性迁移所有设置页或调用外部语音服务 |
| 10 | P14 日记阅读：`RoleplayNavGraph.kt:886`；P15 查看手机：`AppNavHost.kt:81` | `RoleplayDiaryScreenTest`、`PhoneCheckScreenTest` | 不透明阅读面、系统栏生命周期、分类切换为既有契约；实际设备效果继续单列 |

上表文件名在无前缀时相对 `app/src/main/java/com/example/myapplication/`；测试按现有源集查找。没有本轮运行结果，不声明这些测试已重新通过。

## 约束与缺口

- 已归档试点的 1642 / 79 通过结果属于该试点。本轮只做静态研究，不能移作扩面实现通过的证据。
- 新视觉 CI、D01、系统栏 / IME / 实际玻璃和物理性能仍待取得；批准扩面不自动消除这些缺口。
- 规范来源：`.trellis/spec/backend/ui-token-contract.md`、`ui-state-guidelines.md`、`quality-guidelines.md`、`.trellis/spec/guides/code-reuse-thinking-guide.md`。
