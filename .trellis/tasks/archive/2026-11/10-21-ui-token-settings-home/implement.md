# P10 执行计划

先决条件已满足：父摘要与本批启动已获用户批准；P05及其独立窗口能力已于2026-11-05验收并归档。本批已启动，当前`in_progress`。

1. 加载 JSONL → PRD → design → implement，核对工作区、旧默认所有消费者及已接纳 PNG 哈希；派 `trellis-implement`，本批独占 SettingsScreen / SettingsComponents。
2. 使用固定 SettingsUiState + 回调计数渲染真实首页，补 E10 16 个登记 / 方法和迁移前 actual；三个 Sheet 使用 P05 已验证窗口捕获，不能拍底页。
3. 添加最小 opt-in 参数并在两个组重载中透传，默认严格兼容；首页显式迁移到现有令牌，补大字体 / 语义 / 颜色配对和长列表行为；按声明更新源守卫。
4. 定向复用 SettingsScreenTest、SettingsAppearanceScreenTest、SettingsComponentsTest，并补 320×640 / 200% 整页 / Sheet 宿主测试。检查 P02/P05 原调用及必要共享消费者。
5. 派 `trellis-check` 检查接口兼容、真实回调 / 保存边界与视觉。用户审阅 16 个 after / diff 后才接纳本批 PNG / manifest。
6. 全包已接纳截图 verify、所需单测、Lint / dev 构建及 AndroidTest 编译；保留 CI / D01 / 性能未取得结果。更新版本记录 / 必要 spec，经具体提交审阅后独立归档 / journal。

建议截图类 `SettingsHomeScreenshotE10Test`，每张独立 `@Test`。完整命令、失败与回退见 [父计划](../10-21-ui-token-accessibility-expansion/implement.md)。P11 在此 opt-in 与视觉验收稳定后开始，不能同时修改 SettingsComponents。

## 当前执行记录（2026-11-05）

- [x] 核对P05的工作提交、归档与日志；111/111截图、1719/1719完整JVM通过，当前已有110张PNG。
- [x] 按用户已批准的顺序启动现有P10，产品/测试源码在启动时无未提交改动。
- [x] 冻结110张PNG，注册16个E10用例并取得真实迁移前证据：16张actual/语义、6个独立Sheet窗口；17项检查仅为16新图及计数守卫缺图失败，产品修改前旧PNG哈希不变。
- [x] 首页及三类Sheet迁移与共享组件最小opt-in已实施；113项相关JVM、Lint/dev构建/AndroidTest编译通过，消除本轮3条新增参数规范警告。
- [x] 修正显示Sheet捕获前置，从18c1613f隔离工作树重取16 before并重取after；显示浅深分别达到264/264与670/670，最后完整Switch可见。两阶段各14张非显示图与initial字节不变，来源哈希已记录。
- [x] 独立代码/捕获/质量复核通过；16组after/diff齐全，最后参数修正后16图字节未变，110旧图比较及哈希保持。
- [x] 用户接纳16组效果，按精确SHA新增16张E10，共126张已接纳PNG。
- [x] 接纳后127/127真实截图、1755/1755完整JVM通过，均0失败/错误/跳过；126张PNG和产品源码SHA未变，独立检查可提交。
- [x] 用户确认具体清单，完成`b7aad3e6`、`76adadc9`两笔工作提交、`cbe1c195`归档及journal记录；父任务3/4完成，P11仍待实施。
