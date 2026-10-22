# P10 执行计划

先决条件：父摘要已批准，P05 及其独立窗口能力已验收；当前 `planning`。

1. 加载 JSONL → PRD → design → implement，核对工作区、旧默认所有消费者及已接纳 PNG 哈希；派 `trellis-implement`，本批独占 SettingsScreen / SettingsComponents。
2. 使用固定 SettingsUiState + 回调计数渲染真实首页，补 E10 16 个登记 / 方法和迁移前 actual；三个 Sheet 使用 P05 已验证窗口捕获，不能拍底页。
3. 添加最小 opt-in 参数并在两个组重载中透传，默认严格兼容；首页显式迁移到现有令牌，补大字体 / 语义 / 颜色配对和长列表行为；按声明更新源守卫。
4. 定向复用 SettingsScreenTest、SettingsAppearanceScreenTest、SettingsComponentsTest，并补 320×640 / 200% 整页 / Sheet 宿主测试。检查 P02/P05 原调用及必要共享消费者。
5. 派 `trellis-check` 检查接口兼容、真实回调 / 保存边界与视觉。用户审阅 16 个 after / diff 后才接纳本批 PNG / manifest。
6. 全包已接纳截图 verify、所需单测、Lint / dev 构建及 AndroidTest 编译；保留 CI / D01 / 性能未取得结果。更新版本记录 / 必要 spec，经具体提交审阅后独立归档 / journal。

建议截图类 `SettingsHomeScreenshotE10Test`，每张独立 `@Test`。完整命令、失败与回退见 [父计划](../10-21-ui-token-accessibility-expansion/implement.md)。P11 在此 opt-in 与视觉验收稳定后开始，不能同时修改 SettingsComponents。
