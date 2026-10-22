# P11 技术设计

沿用 [父设计](../10-21-ui-token-accessibility-expansion/design.md)；完整入口、字段 / 保存 / ID 边界与 Fake 证据见 [提供商研究](../10-21-ui-token-accessibility-expansion/research/settings-provider-entry.md)。

## 所有权与真实状态

本批拥有 settings 包下 `ProviderSettingsScreen.kt`、`ProviderDetailScreen.kt`、`ProviderDetailChrome.kt`、`ProviderConfigTab.kt`、`ProviderModelTab.kt`、`ProviderModelDialogs.kt`、`ProviderTemplateDialog.kt` 及相关测试。消费 P10 的 opt-in 参数，不并行修改 SettingsComponents；若还缺必要公共参数，由同一共享所有者集中处理并复验全部消费者。

保持列表 pending 新 provider 等待、provider.id 键控 tab / 密钥显隐、pending fetch 的归属及现有模型排序 / override。Config 的协议 / mode 显示已有窄屏纵向分支，优先复用。表单 / 列表的 120dp 底部留白先测量与浮动控件关系，不把功能避让空间按任意 spacing 删除。

状态圆点属于状态呈现，优先使用现有 Material 语义角色，保留检查 / 正常 / 失败文字与禁用 / loading 语义；不把 9dp 装饰点改成 48dp 视觉块。独立输入的标签与错误关联到真正文本输入语义，tab / 开关只触发一次原回调。

测试可使用纯 UiState + 计数回调驱动真实页面，集成时用真实 SettingsViewModel + Fake。`createTestAiServices` 默认会建真实 API factory，列表又会自动 health check，因此必须显式注入 Test API 或固定健康 map + 本地 callbacks，证明无网络。AndroidTest 不引用 src/test Fake；本源集保留合成 builder。

API Key 只用合成占位并保持密码变换，录图不切明文；不改真实凭据或安全存储。保存失败的 VM 草稿 / message / callback 证据与返回立即 pop 的现状分别测试，不能混成“失败留在该 destination”。

## E11 最小截图矩阵：32 张

| 场景 | 数量 |
| --- | --- |
| 列表三主题×浅深，固定长名称 / 已选模型 / 健康最终态 / 停用 | 6 |
| Config 与 Models 两页签各 MATCHA 浅深，常规尺寸 | 4 |
| Config 固定动态浅深 | 2 |
| 列表、Config 保存区、Models 末项 / 浮动操作，320×640 / 200% 各浅深 | 6 |
| 模板、抓取选择、手工错误、能力覆盖、清空确认、协议说明真实窗口，200% 各浅深 | 12 |
| 列表搜索无结果、Models 空态，代表浅 / 深各一场景 | 2 |

十二张弹层图复用 P05 / P10 窗口捕获，实际打开生产窗口并断言关键内容、操作和窗口尺寸。CHECKING 无限动画不放常规图，需要行为验证时固定时钟；不得为截图删除生产动画。provider 不存在由行为覆盖；若本批改变其 notice 外观，先显式登记新增图并更新数量，不能默默遗漏。

## 行为与回退

按研究的行为矩阵覆盖：搜索 / 模板取消及新 ID 后导航；Config 字段 / 显隐 / 协议 / 路径 / 最后一个不可删 / 保存禁重与失败保稿；tab 状态；模型选择 / 编辑 / 移除隔离；抓取归属 / 搜索 / 多选 / 确认；能力取消 / 恢复自动；手工 ID 校验；清空仅确认。末项、底栏、浮动操作在大字体下不遮挡，受测文字实际配对至少 4.5:1。

共享 ModelPickerSheet（物理文件在 chat 目录）默认不改，必要改动才按 P05 / 独立模型页 / Chat overlays 消费表回归，不启用旧入口。每个局部 UI 阶段可回退，本批 32 图与兼容检查完成后才独立验收，不全局换默认或 record 消除差异。
