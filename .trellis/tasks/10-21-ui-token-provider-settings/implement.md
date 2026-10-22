# P11 执行计划

先决条件：父摘要已批准，P10 的 opt-in 接口、窗口能力与独立验收完成；当前 `planning`。

1. JSONL → PRD → design → implement 加载；核对共享 API、消费者、工作区与基准哈希。派 `trellis-implement`，只拥有 Provider 七个局部文件及测试。
2. 建真实列表 / 详情 / 弹层 fixture，显式隔离自动 health check 网络、用户数据和密钥；注册 E11 32 个用例，先验证真实窗口 root 与迁移前 actual。
3. 顺序迁移列表 / 模板、Config / chrome、Models / 弹层，消费既有令牌和 P10 参数；验证 320×640 / 200%、48dp、语义 / 实际颜色配对，精确扩源码守卫。
4. 复用 ProviderSettingsUiTest 的协议 / 模型卡 / 能力行为，并补整页和真实窗口；真实 VM 集成复用 SettingsViewModelTest、Draft/ProviderDraft/Load/Health/Persistence 对应 Fake / 回归模式，不重写领域实现。
5. 检查 P10 与 P05 入口、等待新 provider ID 后单次导航、保存 / 草稿失败、字段 / tab / pending fetch 归属和模型动作隔离；不改变立即 pop 返回现状。
6. 派 `trellis-check` 检查 / 定向修复；展示 32 个 after / diff 与共享旧图变化，人工确认后才接纳 PNG / manifest。
7. 全包已接纳截图 verify、所需单测、Lint / dev 构建与 AndroidTest 编译。主会话更新版本记录 / spec，审阅具体提交后独立归档 / journal，再由父任务跨批收口。

建议截图类 `ProviderSettingsScreenshotE11Test`，每图独立 `@Test`。不在图中录真实 key / key 明文，不以更改自动检测触发条件换确定性。其他验证命令、失败 / 回退与设备 / CI / 性能边界见 [父计划](../10-21-ui-token-accessibility-expansion/implement.md)。
