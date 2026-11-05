# 默认 UI 令牌与个性化契约

## 1. 适用范围

v2.3 试点迁移消息手机主壳、角色列表和线上扮演，P04 扩面覆盖线下场景 / 建议 / 空态及会话阅读，P05 扩面覆盖会话设置侧栏及其实际弹层。修改默认形状 / 间距、皮肤语义色、玻璃配色缓存或相关无障碍时阅读本文件。代码覆盖不等于视觉接纳或全 App 完成，实际扫描范围以测试 `UiTokenSourceInventory` 为准，验收状态以对应任务记录为准。

## 2. 所有者与签名

- `ui/theme/UiTokens.kt`：`NarraUi.space4/8/12/16/24/32`，`radiusSmall/Medium/Large`，`NarraMinimumTouchTarget = 48.dp`。
- 同文件 `NarraShapes.small/medium/large/pill/material`；`ChatAppTheme` 安装 `material`，小 / 中 / 大为 8 / 16 / 24dp，全圆使用相对尺寸 50%。
- `ui/component/roleplay/RoleplayNoBackgroundSkin.kt`：`resolveRoleplayNoBackgroundSkinSpec(settings: RoleplayNoBackgroundSkinSettings, colorScheme: ColorScheme)`；Composable 包装必须将实际 scheme 纳入 `remember`。
- `ui/theme/RoleplaySkinPalette.kt`：`roleplayBrandSkinPalette(preset)`、`roleplayTextContrast(foreground, surface, backing)`、`readableRoleplayText(preferred, surface, backing)`。
- `ImmersiveGlass.kt`：`immersivePaletteCacheKey(backgroundUri, imageBitmap, colorScheme, highContrast)`；返回的键使用图片 identity / generation / size，不保存图片对象。
- `RoleplayScreenContent.kt` / `RoleplayDialoguePanel.kt`：按实际场景 / 对话可用高度约束辅助信息、建议和输入展开区域；消费 `RoleplaySuggestionSection(..., maxHeight: Dp)` 与 `RoleplayInputBar(..., expandedPanelMaxHeight: Dp)`，不改消息 identity / key / 跟随决策。
- `RoleplayReadingMode.kt`：`RoleplayReadingMode(messages, scenarioTitle, backgroundUri, lineHeightScale, highContrast, onDismiss)` 保持原入口；局部 `resolveReadingModeColors(backdropState)` 与实际阅读层配对，`roleplay_reading_header` 独立于 `roleplay_reading_list`。
- `RoleplaySettingsScreen.kt` → `RoleplaySettingsContent.kt` → `RoleplaySettingsSidebarPanel.kt`：普通路由中的自适应右侧面板，保留 94% / 66%、最大 480dp 及系统 Insets；`ImmersiveTabRow.kt` 由本侧栏的模式 / 沉浸 / 行高选项消费。内部 Sheet / Dialog 属于独立窗口，不在主页面 `StageTag` 内。

## 3. 优先级与输入契约

- 默认组件实际消费共享令牌，不能只新增定义却继续使用旧字面量。图标大小、边框、偏移、系统 Insets 和用户动态几何不按布局间距处理。
- `NarraUi` 的历史别名保留旧数值，试点显式改用新令牌。修改共享 Material shapes 时需检查隐式使用它的按钮、输入框、卡片、Sheet 和 Dialog。
- Narra 默认颜色使用实际 Material 角色，随应用显式主题、深浅色与动态 scheme 改变；不能用 `isSystemInDarkTheme()` 覆盖应用选择。
- 其他 12 套明确选用的皮肤保留品牌颜色及装饰，统一解析阅读语义角色；品牌例外不豁免可读文字对比要求。
- 保存的圆角、宽度、横纵 padding、尾角、玻璃开关原值优先。设置没有标识“是否手调”，不能因值等于旧默认常量而取整或改写存储。
- 真实图片加载状态和有效消息玻璃继续分开，谓词及保存回读见 [角色扮演主题与背景气泡](roleplay-theme-settings.md)。
- 透明文字按 `foreground over surface over backing` 合成后计算对比；实际绘制的遮罩 / 玻璃叠层应计入承载面。检验未绘制的颜色字段不能代替检验实际配对。
- 玻璃缓存必须随完整 ColorScheme、图片身份 / 像素版本、尺寸、URI 和 highContrast 变化；LRU 键不得强引用 Bitmap / ImageBitmap，避免多张大图被缓存额外保活。
- 线下场景的立绘 / 记忆提议与建议区域有界且可滚动；200% 字体组合展开时至少一整行真实正文可读，关键操作能到达，不能只用消息 viewport 为正数判定可用。立绘的说话状态随角色信息自然排版，不能叠在同一区域。
- 会话阅读的关闭操作放在正文 LazyColumn 外；长标题 / 空态 / 长文滚动均不能让关闭消失。阅读保留 lineHeight、高对比、安全区和 P05 保存成功后进入的原门槛，不给它新增皮肤持久化。
- 阅读 PANEL / DIALOGUE 嵌套时要计入每层内容加深与 sheen；文字配色必须与实际绘制层一致。阅读调用点目前显式 `contentDarkenAlpha = 0f` 防止重复加深，并通过本页最小 overlay 调整保证对比；不要为单页问题改公共玻璃默认或只改计算用颜色。
- 侧栏默认卡片 / 选项使用应用 Material 配对，皮肤预览内部继续使用用户完整主题对象的几何与配色。活动页标题按内容换行，不能在窄屏大字体下省略页面名；场景长副标题保留原有省略。全局草稿由导航层 `SettingsViewModel.saveSettings` 成功回调才返回或进入阅读；场景预设 / 面具 / 视觉保留即时回调，不能合并成新的保存事务。当前侧栏没有 Settings message 展示接线，不得编造保存错误面板；系统返回也不等同于顶部返回的保存入口。

## 4. 验证与错误矩阵

| 条件 | 要求 |
| --- | --- |
| 相同皮肤切换应用主题 / 动态 scheme | 重新解析实际颜色，不复用旧主题结果 |
| 同 URI / 尺寸的图片替换或像素修改 | 图片 identity / generation 使缓存键失效 |
| 用户设置仍等于历史默认数值 | 原值保留，不推断为“可以迁移” |
| 深色品牌皮肤放入 Material 浅色容器 | 内容色与该容器成对取值，不能直接沿用品牌白字 |
| 透明面上文字低对比 | 调整内容或实际文字承载面；至少 4.5:1，不仅修改计算用字段 |
| 新增试点颜色 / 默认圆角 / 布局字面量 | 源码守卫失败；例外必须精确到文件、声明、表达式与次数 |
| 字体 200%、窄屏 | 使用内容驱动测量、换行或滚动，关键操作仍可达；不能固定高度裁切 |
| 立绘 / 提议 / 建议 / 快捷面板同时展开 | 从真实页面测量正文行、辅助区和输入区位置；正文不能只剩几 dp，次要内容仍可滚动到达 |
| 阅读长文滚到底 | 固定关闭仍可见可点，触控至少48dp；不得放进随正文消失的列表项 |
| 无图 / 浅色阅读面叠加两层内容加深 | 验证实际合成面，不以单层解析通过代替；局部修正后复验浅深、固定动态色及真实背景组合 |
| 新旧截图存在预期差异 | 产出 actual / compare 供人工审阅；旧基准保留，未经确认不自动 record |
| 侧栏实际 Sheet / Dialog 未打开 | 窗口捕获必须失败，不回退截取底层页面；打开后断言标题、末项 / 操作、真实窗口 root 和字体 / 尺寸 |
| 生产帮助文案不足以滚动 | 如实保留该窗口证据；长文滚尾与固定关闭用合成长文宿主另测，不能伪造生产文案或宣称已经滚动 |

## 5. 正常、基线与异常样例

- 正常：P03 默认 Narra 使用 `primaryContainer/onPrimaryContainer` 用户气泡，应用换深色后重新取色；已有 `bubbleRadiusDp=18` 继续为 18dp。
- 基线：用户选用微信或 NOIR，品牌主色保留，公共按钮 / 错误文字按实际背景配对，13 个枚举和 JSON 不变。
- 异常：把“可读强调文字色”同时当按钮底色，再固定黑字；或者在浅色提及面板上使用 NOIR 的白字。
- 异常：以 ImageBitmap 自身作全局 LRU 键，虽使图片替换失效正确，却导致缓存额外持有多张大图。
- 正常：线下建议区有界滚动，展开后正文至少一行完整可读；阅读标题与关闭独立于长正文，返回依旧经过原保存回调。
- 异常：两层玻璃分别增加正文加深，计算只检查单层；或只断言消息容器高度大于零，截图正文实际只剩薄带。

## 6. 必需的验证点

- `RoleplaySemanticColorsTest`：三主题 / 固定动态 scheme、品牌皮肤成对对比、透明合成，以及现有 / 边界用户几何不被取整。
- 主题 / 图片缓存测试：同组件主题切换、同 URI 图像替换和 generation 变化；不得只断言数据类字段存在。
- Compose 行为：实际页面的标签 / 角色 / 选中状态、48dp 点击范围、回调，以及窄屏大字体下头部、消息和输入区互不遮挡；展开态也要覆盖。
- `OfflineRoleplayAccessibilityTest` / `OfflineRoleplayColorsTest`：真实线下两种模式、辅助组合、首行正文、滚动、独立动作与实际建议 / 错误承载面；保留共享P03、皮肤 / 用户几何与玻璃谓词回归。
- `RoleplayReadingModeAccessibilityTest` / `RoleplayReadingModeColorsTest`：长文到底后的固定关闭、空 / 混排、实际引号span前景与承载面、主题切换 / 动态色、行高与高对比；采样避开文字 / 边框，不能拿未被使用的 Text 默认色代替实际 span 颜色。
- `UiTokenSourceGuardTest`：新硬编码失败、合法图标 / Insets / 用户值通过、准确品牌例外与陈旧 / 移位清单失败。该守卫只覆盖登记语法，不追踪全部 Kotlin 别名，人工 diff 审查仍须检查规避。
- 截图沿用 H01–H04 / B01–B04 / C01，P04 新增 E04 14项真实整页 / 阅读候选，保留原有 PNG 计数守卫；人工确认前保持原文件哈希。背景用例必须等待生产页面实际绘出图片 / palette，测试Loader限定合成URI并在安装后恢复；旁路probe成功不能代替页面已绘出。设备编译不等于真机运行，宿主交互不能替代 D01 与物理性能。
- P05 的 `SessionSidebarScreenshotE05Test` 登记 18 项，其中 5 项由 `SessionSidebarRenderTest.windowRoot(title)` 捕获独立窗口；`SessionSidebarWindowGuardTest` 断言未打开窗口时失败。`before/after` 参数与哈希、92 张原基准冻结清单和人工接纳状态分别记录在本批任务中；候选缺原图时 verify 的失败不能当作产品行为失败，也不能豁免缺图守卫。

## 7. 错误与正确处理

错误：把圆角等于 18dp 的旧设置统一写成 16dp，并给主题目录增加整体白名单；每次验证前 record。

正确：默认卡片消费 `NarraShapes.medium`，用户数值在单一设置解析入口优先；品牌与历史几何例外精确登记，迁移后先 verify 并审阅差异，再人工接纳新基准。
