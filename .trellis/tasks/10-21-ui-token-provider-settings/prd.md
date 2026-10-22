# v2.3 扩面四：提供商配置

## Goal

让提供商列表、详情 Config / Models 页签与实际可达弹层扩入 v2.3 令牌和无障碍契约，确保长表单、长模型名与大字体操作可用。归属 [扩面父任务](../10-21-ui-token-accessibility-expansion/prd.md)，复用 P10 已验收的兼容接口。

## Background

[P11 研究](../10-21-ui-token-accessibility-expansion/research/settings-provider-entry.md) 核实入口来自 P10 及 P05 的提供商回调。已有设备测试主要为 Config / 模型卡直接渲染，尚无整页或实际弹层截图。独立 SettingsModelScreen 与 ModelPickerSheet 的共享消费者不等于本批整页迁移对象。

## Requirements

- P1：列表、详情 chrome、Config / Models、模板 / 抓取选择 / 手工添加 / 能力覆盖 / 清空确认 / 协议说明实际消费既有令牌和语义色；健康状态保留文字，不仅依靠颜色。
- P2：320×640dp / 200% 下长名 / 地址 / 模型、列表首末项、表单保存、底栏与浮动操作可达；主要动作至少 48dp，tab selected / role、输入标签 / 错误 / loading 语义有效。
- P3：Provider ID / 类型 / mode / URL / key、模型选择和 override、pending fetch 归属、按 ID 的 tab / 显隐状态、模板等待新草稿出现再导航均保留。显式保存失败保稿 / callback=0；返回当前 save 后立即 pop 的行为不变。
- P4：选择 / 编辑 / 移除互不误触，取消只 dismiss，恢复自动 / 清空仅显式确认；单提供商不可删、手工空 ID 不提交。测试和截图使用固定状态 / Fake，不运行真实健康检测网络，也不展示真实凭据。
- P5：新增 E11 32 张整页 / 真实弹层候选，精确源码守卫和生产回调 / 兼容测试；人工接纳后 verify 全部已接纳基准。

## Acceptance Criteria

- [ ] PA1 / P1/P2：列表 / 双页签 / 关键弹层实际布局、200%、语义 / 48dp、主题 / 动态浅深及实际承载面对比通过，源码守卫无新增违规。
- [ ] PA2 / P3：P10/P05 入口、模板后仅一次正确 ID 导航、provider 缺失提示、字段 / tab 状态隔离、成功 / 失败草稿与保存回调兼容。
- [ ] PA3 / P4：模型编辑 / 移除 / 选择隔离、能力取消 / 恢复自动、抓取选择 / 搜索 / 确认、手工校验与清空 / 删除边界通过；无真实网络或用户数据参与。
- [ ] PA4 / P5：32 张 E11 真实窗口 / 页面候选人工确认，所有已接纳图与受影响检查通过；P02/P05/P10 共享默认无意外变化。
- [ ] PA5：版本记录 / 必要 spec、验证与设备 / CI / 性能缺口准确，本批可独立提交 / 回退。

## Out of scope

- 独立全局模型设置页、ReasoningBudgetSheet、普通 Chat / Translator、语音或其他设置页整页迁移。
- 不改 Provider 协议 / 路由 ID 编码 / 领域状态机 / 凭据 / schema，不改变健康检测触发条件或保存返回策略。
- 不改发布版本、`dev.json`，不发包，不进入 v2.4–v2.6 或历史专项。

## Planning status

2026-10-21 四批最终摘要已由用户回复“可以”批准。本批保持 `planning`，等待 P10 的共享兼容与视觉验收完成后开始；列表、表单、模型与弹层按序实施，整批独立验收。
