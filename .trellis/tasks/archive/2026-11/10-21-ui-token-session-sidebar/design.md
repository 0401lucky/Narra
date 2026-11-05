# P05 技术设计

共享契约见 [父设计](../../../10-21-ui-token-accessibility-expansion/design.md)，源码证据与完整消费者见 [侧栏研究](../../../10-21-ui-token-accessibility-expansion/research/sidebar-entry.md)。

## 文件所有权与数据流

P05 唯一持有 `RoleplaySettingsScreen.kt`、`RoleplaySettingsContent.kt`、`RoleplaySettingsSidebarPanel.kt`、`RoleplaySettingsSections.kt`，以及只有这三组模式 / 沉浸 / 行高调用的 `ImmersiveTabRow.kt`。复用已有 Material 配色适配，不新建主题来源。导航 / 存储代码默认不改。

全局设置继续用 SettingsViewModel draft → saveSettings → stored flow，成功 callback 才退出或进入阅读；失败留稿 / callback=0 用真实 VM + Fake 验证。场景预设 / 面具 / 视觉继续走原 RoleplayViewModel 回调，不并入新全局事务。不存在的保存错误视图不纳入截图。

面板保持屏宽适配与 IME / insets，内部 LazyColumn 和三个等宽选项行在大字体下按内容测量；顶部返回详情先 MAIN，MAIN 成功保存才退出。当前系统返回没有专用 BackHandler，本批不重定义。

SettingsHelpButton、ModelPickerSheet、ImmersiveGlass 默认仅调用；如实测必须改共享布局，先指定本批单一文件所有者，按研究中的全部消费者回归，不能和 P10/P11 并行编辑。共享设置旧 20dp 默认不改。

## E05 最小截图矩阵：18 张

| 场景 / 捕获目标 | 数量 |
| --- | --- |
| MAIN 三主题×浅深，360×800 / 100%，长标题 | 6 |
| MAIN 固定动态浅深 | 2 |
| MAIN 320×640 / 200% 浅深，展开高级区并滚到底 | 2 |
| THEME 320×640 / 200% 浅深，保存原值几何、尾角 true、玻璃 false | 2 |
| 长预设 Sheet / 面具 Sheet，各一浅一深，200% 末项 / 管理操作 | 2 |
| 说明 Dialog 深色、模型 Sheet 浅色、重置确认深色，200% | 3 |
| GROUP 320×640 / 200%，长成员名 / 状态 | 1 |

五张 Sheet / Dialog 必须捕获真实窗口 root，并断言标题、末项 / 操作和该窗口尺寸 / 字体。StageTag 主页面路径保持；窗口 root 不存在或未打开时失败。成员添加 / 移除及重启用真实窗口行为测试，八页与 13 皮肤逐项操作用行为覆盖，不扩大图像笛卡尔积。

2026-10-22迁移前实测修正：P05当前帮助入口的生产文案较短；E05说明窗口显示实际“线上背景玻璃气泡”说明，200%时正文滚动范围仍为0。保留该真实入口与窗口图，不伪造生产长规则；长规则滚尾 / 固定关闭能力另用现有帮助组件的合成长文宿主行为测试覆盖。研究中的“长规则说明”是待验证目标的过度表述，不能作为已有生产状态或已发生滚动的证据。

## 行为与回退

实际 P03/P04 设置入口和 route scenario 对齐；所有八页逐页进入 / 到底 / 返回，THEME 最后控件与独立帮助可达。验证选项 selected、Switch 标签 / 状态、展开状态、相邻触控 / 单次回调，重启 / 重置未成功不退出、取消只 dismiss。

复用现有 SettingsViewModelTest 的完整 skin 成功 / 失败 Fake，SettingsContent/Screen 和 NarraAlertDialog 的操作方法；新增的是生产 P05 真面板和窗口证据。控件默认几何、窗口捕获、保存回归为三个串行回退点，完整 18 图接纳后才收口本批；设备级 TalkBack / IME 等单列。
