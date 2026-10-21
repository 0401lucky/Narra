# 默认 UI 令牌与个性化契约

## 1. 适用范围

v2.3 试点迁移消息手机主壳、角色列表和线上扮演。修改默认形状 / 间距、皮肤语义色、玻璃配色缓存或相关无障碍时阅读本文件。试点不是全 App 完成记录，实际扫描范围以测试 `UiTokenSourceInventory` 为准。

## 2. 所有者与签名

- `ui/theme/UiTokens.kt`：`NarraUi.space4/8/12/16/24/32`，`radiusSmall/Medium/Large`，`NarraMinimumTouchTarget = 48.dp`。
- 同文件 `NarraShapes.small/medium/large/pill/material`；`ChatAppTheme` 安装 `material`，小 / 中 / 大为 8 / 16 / 24dp，全圆使用相对尺寸 50%。
- `ui/component/roleplay/RoleplayNoBackgroundSkin.kt`：`resolveRoleplayNoBackgroundSkinSpec(settings: RoleplayNoBackgroundSkinSettings, colorScheme: ColorScheme)`；Composable 包装必须将实际 scheme 纳入 `remember`。
- `ui/theme/RoleplaySkinPalette.kt`：`roleplayBrandSkinPalette(preset)`、`roleplayTextContrast(foreground, surface, backing)`、`readableRoleplayText(preferred, surface, backing)`。
- `ImmersiveGlass.kt`：`immersivePaletteCacheKey(backgroundUri, imageBitmap, colorScheme, highContrast)`；返回的键使用图片 identity / generation / size，不保存图片对象。

## 3. 优先级与输入契约

- 默认组件实际消费共享令牌，不能只新增定义却继续使用旧字面量。图标大小、边框、偏移、系统 Insets 和用户动态几何不按布局间距处理。
- `NarraUi` 的历史别名保留旧数值，试点显式改用新令牌。修改共享 Material shapes 时需检查隐式使用它的按钮、输入框、卡片、Sheet 和 Dialog。
- Narra 默认颜色使用实际 Material 角色，随应用显式主题、深浅色与动态 scheme 改变；不能用 `isSystemInDarkTheme()` 覆盖应用选择。
- 其他 12 套明确选用的皮肤保留品牌颜色及装饰，统一解析阅读语义角色；品牌例外不豁免可读文字对比要求。
- 保存的圆角、宽度、横纵 padding、尾角、玻璃开关原值优先。设置没有标识“是否手调”，不能因值等于旧默认常量而取整或改写存储。
- 真实图片加载状态和有效消息玻璃继续分开，谓词及保存回读见 [角色扮演主题与背景气泡](roleplay-theme-settings.md)。
- 透明文字按 `foreground over surface over backing` 合成后计算对比；实际绘制的遮罩 / 玻璃叠层应计入承载面。检验未绘制的颜色字段不能代替检验实际配对。
- 玻璃缓存必须随完整 ColorScheme、图片身份 / 像素版本、尺寸、URI 和 highContrast 变化；LRU 键不得强引用 Bitmap / ImageBitmap，避免多张大图被缓存额外保活。

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
| 新旧截图存在预期差异 | 产出 actual / compare 供人工审阅；旧基准保留，未经确认不自动 record |

## 5. 正常、基线与异常样例

- 正常：P03 默认 Narra 使用 `primaryContainer/onPrimaryContainer` 用户气泡，应用换深色后重新取色；已有 `bubbleRadiusDp=18` 继续为 18dp。
- 基线：用户选用微信或 NOIR，品牌主色保留，公共按钮 / 错误文字按实际背景配对，13 个枚举和 JSON 不变。
- 异常：把“可读强调文字色”同时当按钮底色，再固定黑字；或者在浅色提及面板上使用 NOIR 的白字。
- 异常：以 ImageBitmap 自身作全局 LRU 键，虽使图片替换失效正确，却导致缓存额外持有多张大图。

## 6. 必需的验证点

- `RoleplaySemanticColorsTest`：三主题 / 固定动态 scheme、品牌皮肤成对对比、透明合成，以及现有 / 边界用户几何不被取整。
- 主题 / 图片缓存测试：同组件主题切换、同 URI 图像替换和 generation 变化；不得只断言数据类字段存在。
- Compose 行为：实际页面的标签 / 角色 / 选中状态、48dp 点击范围、回调，以及窄屏大字体下头部、消息和输入区互不遮挡；展开态也要覆盖。
- `UiTokenSourceGuardTest`：新硬编码失败、合法图标 / Insets / 用户值通过、准确品牌例外与陈旧 / 移位清单失败。该守卫只覆盖登记语法，不追踪全部 Kotlin 别名，人工 diff 审查仍须检查规避。
- 截图沿用 H01–H04 / B01–B04 / C01 和原有 PNG 计数守卫；人工确认前保持原文件哈希。设备编译不等于真机运行，宿主交互不能替代 D01 与物理性能。

## 7. 错误与正确处理

错误：把圆角等于 18dp 的旧设置统一写成 16dp，并给主题目录增加整体白名单；每次验证前 record。

正确：默认卡片消费 `NarraShapes.medium`，用户数值在单一设置解析入口优先；品牌与历史几何例外精确登记，迁移后先 verify 并审阅差异，再人工接纳新基准。
