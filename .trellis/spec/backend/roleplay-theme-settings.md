# 角色扮演主题与背景气泡契约

## 1. 适用范围

修改角色扮演主题配置、线上背景气泡、气泡宽度 / 外壳或相关设置保存链路时阅读本文件。该契约从 2.2.12-dev 引入，目标是允许背景图与主题气泡独立选择，保留线下行为。

## 2. 签名

- model/RoleplayNoBackgroundSkinSettings.kt：onlineBackgroundGlassEnabled: Boolean = true。
- ui/component/roleplay/RoleplayNoBackgroundSkin.kt：shouldUseRoleplayGlassBubble(hasBackgroundImage: Boolean, bubbleMode: RoleplayMessageBubbleMode, onlineBackgroundGlassEnabled: Boolean): Boolean。
- AppSettingsStore.decodeRoleplayNoBackgroundSkin 显式解码主题 JSON；保存继续经过现有 saveDisplaySettings 的完整主题对象参数。

## 3. 配置与显示契约

DataStore 键为 roleplay_no_background_skin_json，其中 onlineBackgroundGlassEnabled 是布尔字段。偏好全局保存，但仅在线上模式影响材质选择。草稿、保存协调器、Repository 和 settingsFlow 都传递同一个完整主题配置对象。

有效玻璃状态为 hasBackgroundImage && (bubbleMode != ONLINE_PHONE || onlineBackgroundGlassEnabled)。此值同时用于消息配色、几何样式、最大宽度、文字强调色和实际渲染外壳。

backdropState.hasImage 始终表示真实背景是否加载成功。线上消息使用独立 messageColors；顶部栏、时间标识、输入区、背景资源和系统栏仍按真实背景状态渲染。不能通过清空 imageBitmap 来切换气泡材质。

切换预设或重置旧气泡样式必须保留独立玻璃开关。沿用设置页返回时保存成功再退出的流程，不另建直接写入 Store 的 UI 路径。

## 4. 校验与错误矩阵

| 条件 | 结果 |
| --- | --- |
| 旧 JSON 无新字段或字段为 null | 开关为 true，保留其他合法主题值 |
| 新字段是合法布尔 false / true | 原样保留 |
| 新字段为字符串、数字、对象或数组 | 仅该字段回退 true，不丢弃其他合法主题值 |
| 整段主题 JSON 损坏 | 沿用原有默认主题回退及只记录长度的失败日志 |
| 设置保存失败 / 取消 | 沿用既有错误与取消边界，不报告成功或清除草稿 |
| 无背景图或图片未加载成功 | 主题气泡路径，与开关值无关 |
| 线下模式 | 忽略此开关，保持原有背景判定 |

## 5. 正常、基线与异常样例

- 正常：线上有背景，关闭后保留背景图，双方气泡使用主题颜色、形状和宽度；重启后仍关闭。
- 基线：升级时旧主题 JSON 没有该字段，仍显示已有玻璃气泡效果。
- 异常：新字段误为字符串 false 时回退 true，其他有效主题颜色 / 尺寸配置仍可读。
- 主题自身含透明色时继续保留，例如液态玻璃主题不会被强制转成纯不透明色。

## 6. 必要测试断言

- RoleplayNoBackgroundSkinSettingsTest：默认值及 normalized 保留 false。
- AppSettingsStoreTest：旧字段缺失、合法布尔、null / 非法类型及 false 保存回读，其他主题字段不丢失。
- SettingsPersistenceCoordinatorTest / SettingsViewModelTest：草稿、保存成功及失败后的状态不丢失。
- RoleplayGlassBubblePolicyTest：模式、背景与开关的全部组合。
- RoleplaySettingsContentTest：全局开关、预设切换、样式重置。
- RoleplayOnlineBackgroundBubbleTest：真正线上页面的背景保留、材质、配色、宽度及页面控件保持；不能仅测试构造出来的布尔对象。

## 7. 常见错误与正确处理

错误：只提高颜色 alpha，宽度解析和 DialogueBubbleContent 仍按 hasImage 选择玻璃外壳。

正确：在一处得到有效玻璃状态，并贯通配色、宽度、几何样式和 Surface / ImmersiveReadingGlassSurface 选择，同时保留真实 backdropState。
