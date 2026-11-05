# v2.3 扩面三：设置首页

## Goal

让设置首页及三类外观 Sheet 显式采用 v2.3 统一令牌和无障碍契约，同时保留其他共享设置调用方的默认行为。2026-11-05按既有批准方案启动，归属 [扩面父任务](../10-21-ui-token-accessibility-expansion/prd.md)，排在 P05 后、P11 前。

## Background

[设置与提供商研究](../10-21-ui-token-accessibility-expansion/research/settings-provider-entry.md) 核实入口为手机主壳 Profile“设置”→ SettingsNavGraph → SettingsScreen。共享设置符号命中约 45 个 UI 文件（含定义），不能全局改默认尺寸。现有测试主要为直接回调和 160% 局部 Sheet，尚无 P10 整页基准。

## Requirements

- H1：首页的默认外壳、列表组 / 行、顶栏和主题色调 / 颜色模式 / 显示三个 Sheet 显式使用既有形状、六档间距和语义颜色；保持实际可见入口与当前信息层级。
- H2：P10 唯一持有 SettingsComponents 的必要增量参数，优先 shape / contentPadding / 布局参数 opt-in，两个 SettingsGroup 重载均透传；所有旧默认与历史别名不变，不使用全局迁移开关。
- H3：320×640dp / 200% 字体下长 provider / model 摘要、末项与主要操作可达；独立操作至少 48dp、名称 / 状态有效，开关单击一次回调一次。实际三主题 / 动态浅深文字配对至少 4.5:1。
- H4：显式保存、已保存后的会话 / 欢迎动作、保存中禁用、草稿与消息消费按原行为；返回仍是 saveSettings 后立即 pop，不新增失败留页保证。
- H5：新增 E10 16 张页面 / Sheet 候选，实际窗口捕获、源守卫和共享兼容回归；用户接纳后全部基准 verify。

## Acceptance Criteria

- [x] HA1 / H1/H3：首页和三 Sheet 真实布局、选择 / 关闭 / 更新回调、200% / 48dp / 语义及实际主题配对通过。
- [x] HA2 / H2：P10 显式 opt-in 有实际消费者，P02/P05 原调用默认与旧值兼容，源码守卫仅豁免精确旧默认 / 非布局用途，无整目录白名单。
- [x] HA3 / H4：主壳设置入口及提供商 / 默认模型 / 诊断等可见入口回调不串线，保存 / 草稿 / 成功失败边界与现状一致。
- [x] HA4 / H5：16 张 E10 人工接纳，真实窗口内容有证据，所有已接纳图与受影响检查通过；版本记录 / spec 与 CI / 设备 / 性能边界更新。

## Out of scope

- 不因签名有角色 / 世界书 / 记忆回调而恢复首页不存在的行，不迁移被首页链接到的全部设置页。
- 不改 NarraButton / NarraAlertDialog 全局默认、凭据 / 存储 / Provider 协议、保存返回策略；P11 实现归独立批次。
- 发版、`dev.json`、v2.4–v2.6 或其他历史专项。

## Planning status

2026-10-21 四批最终摘要已由用户回复“可以”批准。本批保持 `planning`，等待 P05 验收后按序实施；本批为 P11 提供兼容的共享 opt-in 接口，不将它变成全设置默认。

2026-11-05用户明确要求P05收尾后做设置首页；[P05已验收归档](../archive/2026-11/10-21-ui-token-session-sidebar/verification.md)，本批执行`task.py start`进入`in_progress`。保留上述需求、设计与16张候选矩阵，不重复创建任务或规划确认；本批新增视觉和具体提交清单仍分别审阅。

2026-11-05用户接纳16组效果；接纳后127/127真实截图与1755/1755完整JVM通过，126张PNG哈希不变。Lint、dev构建、AndroidTest实际编译通过，无新增警告；独立检查无代码遗留。HA1–HA4按宿主与人工视觉证据验收，设备/物理性能/远端新视觉CI继续单列未测；当前等待具体提交确认与归档。
