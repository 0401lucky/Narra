# 线上背景玻璃气泡技术设计

## 配置与保存

在 model/RoleplayNoBackgroundSkinSettings.kt 的现有配置对象末尾增加 onlineBackgroundGlassEnabled: Boolean = true。该对象已经全局保存并穿过设置草稿、协调器、Repository 和导航层；使用带线上语义的字段名，保留现有类名与存储键，避免为一个开关另建重复配置链路。

AppSettingsStore 中继续使用 roleplay_no_background_skin_json。decodeRoleplayNoBackgroundSkin 显式读取 onlineBackgroundGlassEnabled：缺失或 JSON null 时为 true，合法 false 必须保留。非法类型按该字段默认 true 处理，不应因此丢掉其他有效的主题字段；原有整段损坏 JSON 的安全回退和脱敏日志保持不变。

保存沿用 gson.toJson(skin.normalized())；normalized、copy、草稿变更检测和保存参数自然携带新布尔值。通过测试确认这一链路，不为了新字段添加另一套 setter 或扩展 saveDisplaySettings 的参数表。

数据流为 RoleplaySettingSwitchRow → onUpdateRoleplayNoBackgroundSkin → SettingsPreferenceDraftSupport → SettingsPersistenceCoordinator / AiSettingsEditor → AppSettingsStore → settingsFlow。RoleplayNavGraph 现有返回回调负责保存成功后退出；保存失败保持原有反馈和草稿。

不增加 Room 字段或版本。旧安装缺失新字段时开启；旧应用读取新增 JSON 字段时忽略未知字段。

## 设置入口

- 在 RoleplayNoBackgroundThemeSection 的气泡设置附近复用 RoleplaySettingSwitchRow。
- 标题：“线上背景玻璃气泡”。说明：“作用于所有线上聊天；关闭后保留背景图，气泡使用所选主题。”
- 控件使用 test tag roleplay_online_background_glass_switch，触控区域沿用现有设置控件规范。
- 即使当前未配置背景，也可调整全局选择；文案说明适用场景，不把加载状态当成保存条件。
- 切换预设和各滑条均通过 skin.copy 保留开关。现有“重置气泡样式”恢复原有样式字段时显式保留 onlineBackgroundGlassEnabled，不将独立材质选择一起重置。

## 统一视觉判定

在既有 RoleplayNoBackgroundSkin.kt 内放置一个无副作用的判定，供消息调色、几何样式、宽度和外壳共用：

useGlassBubble = hasBackgroundImage && (bubbleMode != ONLINE_PHONE || onlineBackgroundGlassEnabled)

| 模式 | 背景已加载 | 开关 | 气泡路径 |
| --- | --- | --- | --- |
| 线上 | 否 | 任意 | 现有主题气泡 |
| 线上 | 是 | 开 | 现有玻璃气泡 |
| 线上 | 是 | 关 | 所选主题气泡 |
| 线下 | 任意 | 任意 | 完全沿用原有背景判定 |

场景背景 URI、imageBitmap、palette 和 hasImage 继续表示真实图片状态。不得复制并清空背景状态来伪造“无背景”，也不改变图片解码、缓存、系统栏或页面遮罩。

### 消息配色与页面配色分离

RoleplayOnlinePhoneContent 保留基于真实 backdropState 的现有页面 colors，供顶部栏、时间标识、菜单、输入区等使用。另行派生 messageColors，复用线上颜色解析规则，但以 useGlassBubble 选择主题 / 玻璃配色，并只传给 RoleplayMessageItem。

文字、引用面板、情绪提示及错误配色必须跟随各自气泡配色，避免浅色主题气泡里仍出现背景玻璃使用的浅色文字。线上心声等继续复用消息配色，但不重做专用卡片的布局或交互。

### 形状、宽度与外壳

- rememberRoleplayDialogueBubbleStyle 使用同一判定，关闭时恢复 skinSpec 的宽度、圆角、padding 和尾巴。
- RoleplayMessageBubbles 的 resolveDialogueMaxWidthFraction 同步使用有效玻璃状态，避免 hasImage 把已恢复的主题宽度再次覆盖。
- UserDialogueBubbleContent 与 CharacterDialogueBubbleContent 接收明确的材质判定；关闭时选 Surface，开启时仍选 ImmersiveReadingGlassSurface。现有非线上调用的默认行为保持兼容。
- 与材质有关的文字强调色也使用有效状态，不能残留另一个仅由 hasImage 控制的视觉分支。
- 新值进入相应 remember 的依赖项，切换后无需重启或重设背景才能刷新。

## 范围与兼容

主要文件为 RoleplayNoBackgroundSkinSettings.kt、AppSettingsStore.kt、RoleplaySettingsSections.kt、RoleplayOnlinePhoneContent.kt、RoleplayNoBackgroundSkin.kt、RoleplayMessageBubbles.kt、RoleplayDialogueBubbles.kt。设置协调器与导航层优先复用，仅在实际发现字段丢失时做定向修正。

所选液态玻璃主题自身可以有透明色；关闭本开关的含义是停止“背景图强制换玻璃”，不是把所有主题转换为纯不透明色。转账、礼物等专用卡片、背景图和线下页面不做外观改版。

## 风险与回退

最易遗漏的是“颜色已变，但外壳和宽度仍走有背景分支”以及旧 JSON 缺失字段的默认值。保存、组合状态和可视化验证必须覆盖这些边界。回退时局部撤回本任务的配置 / UI / 测试修改，不清空主题 JSON 或用户背景。
