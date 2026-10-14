# 首批截图基准运行清单（Windows 候选）

> **状态：已人工确认（2026-10-14 用户确认图片可接受）；CI 宿主复跑待做。**
> 76 张首批基准由 Windows 11 宿主上的 Roborazzi record 生成一次（2026-10-14 因 fixture 调色板来源
> 修复，对 19 张受影响的 P03/背景基准定向重录一次，见第 7 节），并在本机 verify 连续两次真实通过
> （第二次 --rerun-tasks 全量执行；重录后全量 verify 亦通过）。CI（ubuntu-24.04 + JDK 17）与真机尚未复跑，
> 基准内容已获用户视觉确认，可提交；CI（ubuntu-24.04 + JDK 17）首次 verify 仍是指定宿主的唯一依据，首次复跑即验证与 Windows 候选的一致性。

## 1. 环境清单

| 项目 | 值 |
| --- | --- |
| 生成宿主 | Windows 11（机器名 LUCKY）+ PowerShell；本机无 adb 设备/模拟器 |
| JDK / Gradle | Temurin 21.0.9 / Gradle wrapper 9.3.1（AGP 9.1.0，Kotlin 2.2.10） |
| 截图工具 | Roborazzi 1.76.0（插件 io.github.takahirom.roborazzi） |
| 运行时 | Robolectric 4.17，@GraphicsMode(NATIVE)，robolectric.pixelCopyRenderMode=hardware |
| 系统镜像 | org.robolectric:android-all-instrumented:15-robolectric-13954326-i7（API 35，缓存在本机 ~/.m2） |
| 用例 SDK / 应用 SDK | 用例 @Config(sdk=[35])；compileSdk 36 / targetSdk 36 / minSdk 26 |
| 舞台（常规） | 360×800dp → 720×1600px，density 2（xhdpi） |
| 舞台（H04 窄屏） | 320×640dp → 640×1280px，density 2，fontScale 2 |
| locale / 时区 | zh-rCN / Asia/Shanghai |
| 固定时刻 | epoch 1791950400000 = 2026-10-14 12:00 +08:00（代码内无 System.currentTimeMillis / Random） |
| 字体来源 | Robolectric android-all-instrumented 内置字体（非宿主系统字体）；中文渲染已在 Windows 人工确认 |
| 图片比较 | SimpleImageComparator(maxDistance=0f) + ThresholdValidator(0f)（零容忍） |
| 截图接口 | Roborazzi fetchImage（Robolectric 下 Compose captureToImage 会超时，已知坑） |
| 基准目录 | app/src/test/snapshots（由 app/build.gradle 注入系统属性 narra.screenshot.snapshotDir） |
| 计数守卫 | ScreenshotBaselineGuardTest（纯 JVM；分族数量/ID 唯一/测试类存在/PNG 双向一致） |
| PNG 总量 | 78 = 76 张登记用例 + 2 张上一轮冒烟白名单（MessageBubbleScreenshotTest） |

## 2. 运行命令与结果（2026-10-14，Windows 本机）

| 命令 | 退出码 | 结果 |
| --- | --- | --- |
| `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.myapplication.ui.screenshot.ScreenshotBaselineGuardTest" --console=plain`（record 前负向证明） | 1 | 1 test / 1 failed；失败信息逐条列出 76 张缺失基准 |
| `.\gradlew.bat :app:recordRoborazziDebug --tests "com.example.myapplication.ui.screenshot.*" --console=plain` | 0 | 79 tests / 0 failed；生成全部 78 张 PNG |
| `.\gradlew.bat :app:verifyRoborazziDebug --tests "com.example.myapplication.ui.screenshot.*" --console=plain` | 0 | 79 tests / 0 failed（真实执行，非 UP-TO-DATE） |
| `.\gradlew.bat :app:verifyRoborazziDebug --tests "com.example.myapplication.ui.screenshot.*" --rerun-tasks --console=plain` | 0 | 79 tests / 0 failed；35 tasks 全部真实执行（1m44s） |

## 3. 用例登记与覆盖

单一登记表：app/src/test/java/com/example/myapplication/ui/screenshot/ScreenshotCases.kt（测试与守卫都从这里取 ID 与文件路径）。
分族数量（冻结值）：H01=18、H02=3、H03=3、H04=6、B01=26、B02=4、B03=10、B04=4、C01=2，合计 76。

## 4. 用例 ID → 测试方法 → 基准文件

| 用例 ID | 测试类.方法() | 基准文件（相对本目录） |
| --- | --- | --- |
| B01_imessage-bubbles-dark | SkinScreenshotB01Test.b01_imessage_bubbles_dark() | SkinScreenshotB01Test/B01_imessage-bubbles-dark.png |
| B01_imessage-bubbles-light | SkinScreenshotB01Test.b01_imessage_bubbles_light() | SkinScreenshotB01Test/B01_imessage-bubbles-light.png |
| B01_kakao-bubbles-dark | SkinScreenshotB01Test.b01_kakao_bubbles_dark() | SkinScreenshotB01Test/B01_kakao-bubbles-dark.png |
| B01_kakao-bubbles-light | SkinScreenshotB01Test.b01_kakao_bubbles_light() | SkinScreenshotB01Test/B01_kakao-bubbles-light.png |
| B01_liquidglass-bubbles-dark | SkinScreenshotB01Test.b01_liquidglass_bubbles_dark() | SkinScreenshotB01Test/B01_liquidglass-bubbles-dark.png |
| B01_liquidglass-bubbles-light | SkinScreenshotB01Test.b01_liquidglass_bubbles_light() | SkinScreenshotB01Test/B01_liquidglass-bubbles-light.png |
| B01_narra-bubbles-dark | SkinScreenshotB01Test.b01_narra_bubbles_dark() | SkinScreenshotB01Test/B01_narra-bubbles-dark.png |
| B01_narra-bubbles-light | SkinScreenshotB01Test.b01_narra_bubbles_light() | SkinScreenshotB01Test/B01_narra-bubbles-light.png |
| B01_noir-bubbles-dark | SkinScreenshotB01Test.b01_noir_bubbles_dark() | SkinScreenshotB01Test/B01_noir-bubbles-dark.png |
| B01_noir-bubbles-light | SkinScreenshotB01Test.b01_noir_bubbles_light() | SkinScreenshotB01Test/B01_noir-bubbles-light.png |
| B01_pixel-bubbles-dark | SkinScreenshotB01Test.b01_pixel_bubbles_dark() | SkinScreenshotB01Test/B01_pixel-bubbles-dark.png |
| B01_pixel-bubbles-light | SkinScreenshotB01Test.b01_pixel_bubbles_light() | SkinScreenshotB01Test/B01_pixel-bubbles-light.png |
| B01_polkadot-bubbles-dark | SkinScreenshotB01Test.b01_polkadot_bubbles_dark() | SkinScreenshotB01Test/B01_polkadot-bubbles-dark.png |
| B01_polkadot-bubbles-light | SkinScreenshotB01Test.b01_polkadot_bubbles_light() | SkinScreenshotB01Test/B01_polkadot-bubbles-light.png |
| B01_qq-bubbles-dark | SkinScreenshotB01Test.b01_qq_bubbles_dark() | SkinScreenshotB01Test/B01_qq-bubbles-dark.png |
| B01_qq-bubbles-light | SkinScreenshotB01Test.b01_qq_bubbles_light() | SkinScreenshotB01Test/B01_qq-bubbles-light.png |
| B01_retro-bubbles-dark | SkinScreenshotB01Test.b01_retro_bubbles_dark() | SkinScreenshotB01Test/B01_retro-bubbles-dark.png |
| B01_retro-bubbles-light | SkinScreenshotB01Test.b01_retro_bubbles_light() | SkinScreenshotB01Test/B01_retro-bubbles-light.png |
| B01_rose-bubbles-dark | SkinScreenshotB01Test.b01_rose_bubbles_dark() | SkinScreenshotB01Test/B01_rose-bubbles-dark.png |
| B01_rose-bubbles-light | SkinScreenshotB01Test.b01_rose_bubbles_light() | SkinScreenshotB01Test/B01_rose-bubbles-light.png |
| B01_telegram-bubbles-dark | SkinScreenshotB01Test.b01_telegram_bubbles_dark() | SkinScreenshotB01Test/B01_telegram-bubbles-dark.png |
| B01_telegram-bubbles-light | SkinScreenshotB01Test.b01_telegram_bubbles_light() | SkinScreenshotB01Test/B01_telegram-bubbles-light.png |
| B01_wechat-bubbles-dark | SkinScreenshotB01Test.b01_wechat_bubbles_dark() | SkinScreenshotB01Test/B01_wechat-bubbles-dark.png |
| B01_wechat-bubbles-light | SkinScreenshotB01Test.b01_wechat_bubbles_light() | SkinScreenshotB01Test/B01_wechat-bubbles-light.png |
| B01_y2k-bubbles-dark | SkinScreenshotB01Test.b01_y2k_bubbles_dark() | SkinScreenshotB01Test/B01_y2k-bubbles-dark.png |
| B01_y2k-bubbles-light | SkinScreenshotB01Test.b01_y2k_bubbles_light() | SkinScreenshotB01Test/B01_y2k-bubbles-light.png |
| B02_p03-background-glass-off | BubbleScreenshotB02Test.b02_p03_background_glass_off() | BubbleScreenshotB02Test/B02_p03-background-glass-off.png |
| B02_p03-background-glass-on | BubbleScreenshotB02Test.b02_p03_background_glass_on() | BubbleScreenshotB02Test/B02_p03-background-glass-on.png |
| B02_p03-background-load-failed | BubbleScreenshotB02Test.b02_p03_background_load_failed() | BubbleScreenshotB02Test/B02_p03-background-load-failed.png |
| B02_p03-no-background | BubbleScreenshotB02Test.b02_p03_no_background() | BubbleScreenshotB02Test/B02_p03-no-background.png |
| B03_padding-horizontal-max | BubbleScreenshotB03Test.b03_padding_horizontal_max() | BubbleScreenshotB03Test/B03_padding-horizontal-max.png |
| B03_padding-horizontal-min | BubbleScreenshotB03Test.b03_padding_horizontal_min() | BubbleScreenshotB03Test/B03_padding-horizontal-min.png |
| B03_padding-vertical-max | BubbleScreenshotB03Test.b03_padding_vertical_max() | BubbleScreenshotB03Test/B03_padding-vertical-max.png |
| B03_padding-vertical-min | BubbleScreenshotB03Test.b03_padding_vertical_min() | BubbleScreenshotB03Test/B03_padding-vertical-min.png |
| B03_radius-max | BubbleScreenshotB03Test.b03_radius_max() | BubbleScreenshotB03Test/B03_radius-max.png |
| B03_radius-min | BubbleScreenshotB03Test.b03_radius_min() | BubbleScreenshotB03Test/B03_radius-min.png |
| B03_tail-off | BubbleScreenshotB03Test.b03_tail_off() | BubbleScreenshotB03Test/B03_tail-off.png |
| B03_tail-on | BubbleScreenshotB03Test.b03_tail_on() | BubbleScreenshotB03Test/B03_tail-on.png |
| B03_width-max | BubbleScreenshotB03Test.b03_width_max() | BubbleScreenshotB03Test/B03_width-max.png |
| B03_width-min | BubbleScreenshotB03Test.b03_width_min() | BubbleScreenshotB03Test/B03_width-min.png |
| B04_offline-background | BubbleScreenshotB04Test.b04_offline_background() | BubbleScreenshotB04Test/B04_offline-background.png |
| B04_offline-no-background | BubbleScreenshotB04Test.b04_offline_no_background() | BubbleScreenshotB04Test/B04_offline-no-background.png |
| B04_online-glass-off | BubbleScreenshotB04Test.b04_online_glass_off() | BubbleScreenshotB04Test/B04_online-glass-off.png |
| B04_online-glass-on | BubbleScreenshotB04Test.b04_online_glass_on() | BubbleScreenshotB04Test/B04_online-glass-on.png |
| C01_dynamic-dark | ThemeScreenshotC01Test.c01_dynamic_dark() | ThemeScreenshotC01Test/C01_dynamic-dark.png |
| C01_dynamic-light | ThemeScreenshotC01Test.c01_dynamic_light() | ThemeScreenshotC01Test/C01_dynamic-light.png |
| H01_p01-messages-matcha-dark | PageScreenshotH01Test.h01_p01_messages_matcha_dark() | PageScreenshotH01Test/H01_p01-messages-matcha-dark.png |
| H01_p01-messages-matcha-light | PageScreenshotH01Test.h01_p01_messages_matcha_light() | PageScreenshotH01Test/H01_p01-messages-matcha-light.png |
| H01_p01-messages-ocean-dark | PageScreenshotH01Test.h01_p01_messages_ocean_dark() | PageScreenshotH01Test/H01_p01-messages-ocean-dark.png |
| H01_p01-messages-ocean-light | PageScreenshotH01Test.h01_p01_messages_ocean_light() | PageScreenshotH01Test/H01_p01-messages-ocean-light.png |
| H01_p01-messages-vanilla-dark | PageScreenshotH01Test.h01_p01_messages_vanilla_dark() | PageScreenshotH01Test/H01_p01-messages-vanilla-dark.png |
| H01_p01-messages-vanilla-light | PageScreenshotH01Test.h01_p01_messages_vanilla_light() | PageScreenshotH01Test/H01_p01-messages-vanilla-light.png |
| H01_p02-assistants-matcha-dark | PageScreenshotH01Test.h01_p02_assistants_matcha_dark() | PageScreenshotH01Test/H01_p02-assistants-matcha-dark.png |
| H01_p02-assistants-matcha-light | PageScreenshotH01Test.h01_p02_assistants_matcha_light() | PageScreenshotH01Test/H01_p02-assistants-matcha-light.png |
| H01_p02-assistants-ocean-dark | PageScreenshotH01Test.h01_p02_assistants_ocean_dark() | PageScreenshotH01Test/H01_p02-assistants-ocean-dark.png |
| H01_p02-assistants-ocean-light | PageScreenshotH01Test.h01_p02_assistants_ocean_light() | PageScreenshotH01Test/H01_p02-assistants-ocean-light.png |
| H01_p02-assistants-vanilla-dark | PageScreenshotH01Test.h01_p02_assistants_vanilla_dark() | PageScreenshotH01Test/H01_p02-assistants-vanilla-dark.png |
| H01_p02-assistants-vanilla-light | PageScreenshotH01Test.h01_p02_assistants_vanilla_light() | PageScreenshotH01Test/H01_p02-assistants-vanilla-light.png |
| H01_p03-online-roleplay-matcha-dark | PageScreenshotH01Test.h01_p03_online_play_matcha_dark() | PageScreenshotH01Test/H01_p03-online-roleplay-matcha-dark.png |
| H01_p03-online-roleplay-matcha-light | PageScreenshotH01Test.h01_p03_online_play_matcha_light() | PageScreenshotH01Test/H01_p03-online-roleplay-matcha-light.png |
| H01_p03-online-roleplay-ocean-dark | PageScreenshotH01Test.h01_p03_online_play_ocean_dark() | PageScreenshotH01Test/H01_p03-online-roleplay-ocean-dark.png |
| H01_p03-online-roleplay-ocean-light | PageScreenshotH01Test.h01_p03_online_play_ocean_light() | PageScreenshotH01Test/H01_p03-online-roleplay-ocean-light.png |
| H01_p03-online-roleplay-vanilla-dark | PageScreenshotH01Test.h01_p03_online_play_vanilla_dark() | PageScreenshotH01Test/H01_p03-online-roleplay-vanilla-dark.png |
| H01_p03-online-roleplay-vanilla-light | PageScreenshotH01Test.h01_p03_online_play_vanilla_light() | PageScreenshotH01Test/H01_p03-online-roleplay-vanilla-light.png |
| H02_p01-messages-empty | PageScreenshotH02Test.h02_p01_messages_empty_state() | PageScreenshotH02Test/H02_p01-messages-empty.png |
| H02_p02-assistants-no-search-result | PageScreenshotH02Test.h02_p02_assistants_no_search_result() | PageScreenshotH02Test/H02_p02-assistants-no-search-result.png |
| H02_p03-online-empty-session | PageScreenshotH02Test.h02_p03_online_empty_session() | PageScreenshotH02Test/H02_p03-online-empty-session.png |
| H03_p03-online-error-message | PageScreenshotH03Test.h03_p03_online_error_message() | PageScreenshotH03Test/H03_p03-online-error-message.png |
| H03_p03-online-long-reply | PageScreenshotH03Test.h03_p03_online_long_reply() | PageScreenshotH03Test/H03_p03-online-long-reply.png |
| H03_p03-online-streaming-frame | PageScreenshotH03Test.h03_p03_online_streaming_frame() | PageScreenshotH03Test/H03_p03-online-streaming-frame.png |
| H04_p01-messages-narrow-dark | PageScreenshotH04Test.h04_p01_messages_narrow_dark() | PageScreenshotH04Test/H04_p01-messages-narrow-dark.png |
| H04_p01-messages-narrow-light | PageScreenshotH04Test.h04_p01_messages_narrow_light() | PageScreenshotH04Test/H04_p01-messages-narrow-light.png |
| H04_p02-assistants-narrow-dark | PageScreenshotH04Test.h04_p02_assistants_narrow_dark() | PageScreenshotH04Test/H04_p02-assistants-narrow-dark.png |
| H04_p02-assistants-narrow-light | PageScreenshotH04Test.h04_p02_assistants_narrow_light() | PageScreenshotH04Test/H04_p02-assistants-narrow-light.png |
| H04_p03-online-narrow-dark | PageScreenshotH04Test.h04_p03_online_narrow_dark() | PageScreenshotH04Test/H04_p03-online-narrow-dark.png |
| H04_p03-online-narrow-light | PageScreenshotH04Test.h04_p03_online_narrow_light() | PageScreenshotH04Test/H04_p03-online-narrow-light.png |

## 5. 白名单基准（上一轮冒烟，字节未变）

| 文件 | 说明 |
| --- | --- |
| MessageBubbleScreenshotTest/chat-user-message-matcha-light.png | 冒烟：聊天用户气泡（matcha/light）；SHA-256 F150D723...2A2E0B12 |
| MessageBubbleScreenshotTest/roleplay-character-bubble-glass.png | 冒烟：扮演玻璃气泡；SHA-256 13F833F8...8A3ED01E |

两张冒烟基准在 record 前后 SHA-256 完全一致；白名单之外的未登记 PNG 会让守卫失败。

## 6. 未覆盖项 / 转 D01 设备矩阵

- 真实玻璃/模糊材质（Haze）与背景-遮罩层级：Robolectric 合成结果可能与设备渲染不同，B02/B04 的玻璃基准只锁“结构不回归”，材质真实性以 D01 真机图为准。
- 系统栏/导航栏、输入法（IME）、悬浮窗、通知：宿主图不含真实系统组件。
- 真实动态取色（Android 12+ 壁纸取色）与厂商 ROM 差异：C01 只覆盖 resolveAppColorScheme 的固定输入。
- 真实设备字体（含厂商替换字体）与不同 dpi 的取整差异：宿主使用 Robolectric 内置字体。
- 交互与动效（点击、长按、滚动惯性与真实触摸反馈）：截图只覆盖静态帧。
- CI 宿主一致性：ubuntu-24.04 + JDK 17 需复跑 record/verify 比对像素后再人工确认。

## 7. 观测与既有缺陷登记（本轮只记录，不修产品）

- P03/背景调色板覆盖缺口：已修复（2026-10-14 定向重录）。原缺口：fixture 给所有 backdropState 注入手写固定调色板（ScreenshotFixture.FixedGlassPalette），无背景用例的外壳/气泡不随主题与深浅色变化、有背景用例的配色也不是生产派生。现改为在组合内调用生产函数 deriveImmersiveGlassPalette(imageBitmap, MaterialTheme.colorScheme, highContrast = false)（无背景时自动走主题回退，有背景时从固定合成图采样），固定输入保证确定性；产品侧仅把 deriveImmersiveGlassPalette 由 private 改为 internal，行为与调用点不变。
- 重录原因与范围：fixture 未走生产派生，且该批基准尚未人工确认，属候选修正。2026-10-14 在 Windows 本机执行一次 record（仅截图包），19 张变化：H01 P03 六张、H02 P03 一张、H03 三张、H04 P03 两张、B02 四张、B04 三张（B04_offline-no-background 未变：无背景皮肤气泡不消费该调色板）；P01/P02、B01/B03、C01 与两张冒烟白名单未变，重录后全量 verify 两次通过。
- 重录后 P03 差异：H01 P03 六张浅/深两组哈希不同（matcha-light 401F76CC… vs matcha-dark 68746885…，像素差 74,703 px / 6.48%）；同一深浅下三种主题仍像素一致，原因是生产 resolveAppColorScheme 按深浅统一覆盖 surface/surfaceVariant/outline，主题仅改 primary/secondary，而无背景 P03 外壳不绘制这些强调色——属生产行为，不再是夹具缺口。基准定性不变：Windows 本机候选、CI 宿主待复验、未经人工确认。
- B02“已配置背景 URI 但 imageBitmap=null（加载未成功）”与“未配置背景”像素完全一致：组件只在背景 bitmap 可用时绘制，没有占位/失败 UI；若产品后续增加占位需同步更新基准。
- H04 窄屏 320×640dp + fontScale 2：P03 首条消息被放大的头部遮挡、输入区提示文字换行溢出；P01 底栏文字下沿裁切；均属既有布局缺陷。
- B03_tail-off 与 B01_narra-bubbles-light 像素一致：Narra 默认 showBubbleTail=false，属预期重合，不是缺陷。
- C01 基准中 background 色板与页面底色相同导致色块不可见：测试舞台表现问题，不影响主题回归检测。

## 8. 确定性机制

- 合成 fixture：角色/会话/消息/图片全部由 ScreenshotFixture 构造，背景图用 Canvas 绘制（syntheticBackdrop）。
- 固定时间：epoch 1791950400000、Asia/Shanghai；无 Random / System.currentTimeMillis / 网络请求 / 真实用户数据。
- 动画：stopAnimationClock() 后 advanceTimeBy(130ms) 取流式中间帧（520ms 脉冲的 1/4 相位）。
- 截图守卫：目标节点存在 + 关键文本 + 固定像素尺寸 + fetchImage 采样非单色空白；失败即测试失败，无 try/catch 吞断言。
- 计数守卫在缺场景/零测试/多基准/少基准时失败；record 模式只跳过“基准存在性”，结构检查仍然生效。
