# v2.3 P11 提供商配置基准接纳（2026-11-05）

> **当前状态：用户已接纳32张E11及其中5张稳定帧修正，共158张PNG；159/159真实截图、1826/1826完整JVM通过。**

- 用户回复“接纳这32组效果（推荐）”。仅从已审阅after按精确SHA-256新增32张E11；原126张PNG不变，未record或降低比较阈值。
- 接纳后识别出模型页签瞬态水波纹。仅E11截图改为真实Tab语义点击，并在ac22bb27隔离副本补before、以两轮32张after字节一致证明稳定。用户随后回复“接纳这5张修正（推荐）”，只替换5张，其他153张不变；[修正对照](../../../../.trellis/tasks/10-21-ui-token-provider-settings/review-capture-stability/index.html)与两次接纳记录均保留，产品动画和行为触控未改。
- 覆盖提供商列表、Config / Models、固定动态浅深、320×640dp / 200%字体和六类真实弹层浅深。见[32组前后对照](../../../../.trellis/tasks/10-21-ui-token-provider-settings/review/index.html)、[接纳记录](../../../../.trellis/tasks/10-21-ui-token-provider-settings/research/visual-acceptance.json)及[158张接纳哈希](../../../../.trellis/tasks/10-21-ui-token-provider-settings/research/accepted-png-hashes-20261105.txt)。
- 独立代码/证据检查已通过；真实截图159/159（15 suites、63秒）与完整JVM1826/1826（245 suites、129秒）均退出0、无失败/错误/跳过，158张PNG哈希未变。必要Lint仍为55条旧warnings/3 hints，无新增；dev及AndroidTest编译通过。真机读屏、系统返回/窗外触控、IME/系统栏、实际材质、物理性能及新视觉远端CI仍待补。

以下P10及更早章节保留历史状态，当前接纳情况以上节为准。

# v2.3 P10 设置首页基准接纳（2026-11-05）

> **当前状态：用户已接纳16张E10候选，共126张PNG；接纳后127/127真实截图、1755/1755完整JVM通过。**

- 用户回复“接纳这16组效果（推荐）”，16张PNG均匹配已审阅after与接纳记录的精确SHA-256；原110张不变。见[接纳哈希](../../../../.trellis/tasks/archive/2026-11/10-21-ui-token-settings-home/research/accepted-png-hashes-20261105.txt)。
- 最后参数规范修正没有改变16张图；接纳后真实截图127/127（48秒）及完整JVM1755/1755（91秒）全部通过，均0失败/错误/跳过，126张PNG哈希不变。Lint/dev/AndroidTest实际编译通过，无新增警告。
- [16组前后对照](../../../../.trellis/tasks/archive/2026-11/10-21-ui-token-settings-home/review/index.html)已标为接纳；设备/物理性能/新视觉远端CI仍待补。

以下为候选阶段及更早批次的历史记录。

# v2.3 P10 设置首页候选（2026-11-05）

> **当前状态：16组真实前后候选已齐全且捕获前置已校正；新增视觉尚未接纳，snapshots仍为110张已接纳PNG。**

- 覆盖首页三主题浅深、固定动态浅深、200%窄屏首页，以及主题色调/颜色模式/显示三个真实Sheet的浅深窗口。
- 产品修改前的17项检查严格仅为16个新图与计数守卫缺基准失败，输出16张actual、16份语义及6份独立窗口记录；参数见[before清单](../../../../.trellis/tasks/archive/2026-11/10-21-ui-token-settings-home/research/before/manifest.json)。
- 旧110张PNG已冻结且取得before时哈希不变。完整迁移后截图矩阵预期127项，最终运行与候选接纳结果另行记录；P05的111/1719结果不代替P10验收。
- 本批先生成同参数after及前后对照，再独立检查与人工接纳；不record、删除用例或放宽缺图/计数守卫。
- 显示Sheet首版仅滚入末项文字的问题已修正：before从18c1613f隔离副本重取，前后均滚至真实max并检查完整Switch。最终127项截图为110旧图通过、16新图与计数守卫缺图失败；相关JVM113项通过，见[16组前后对照](../../../../.trellis/tasks/archive/2026-11/10-21-ui-token-settings-home/review/index.html)与[验证记录](../../../../.trellis/tasks/archive/2026-11/10-21-ui-token-settings-home/verification.md)。

以下P05及更早章节为已完成批次的历史记录。

# v2.3 P05 会话设置侧栏基准接纳（2026-11-05）

> **当前状态：用户已确认接纳18张E05候选；共110张PNG，Windows宿主111/111真实截图与1719/1719完整JVM通过。**

- 用户在18组前后对照及接纳问题后回复“接纳侧栏，完成收尾后做设置首页”。本次仅接纳该批已审阅actual，不重新record。
- 18张新增PNG逐项匹配`research/after/manifest.json`中的SHA-256；原92张PNG与冻结清单一致。接纳后完整哈希清单见[accepted-png-hashes-20261105.txt](../../../../.trellis/tasks/archive/2026-11/10-21-ui-token-session-sidebar/research/accepted-png-hashes-20261105.txt)。
- 最终截图111/111通过，1分5秒；完整JVM1719/1719通过，1分46秒，两轮均0失败/错误/跳过且真实执行。110张PNG哈希前后未变，详情见[本批验证记录](../../../../.trellis/tasks/archive/2026-11/10-21-ui-token-session-sidebar/verification.md)。
- 本机接纳不代表新视觉远端CI、真机读屏/IME/系统栏/实际材质或物理性能已通过。

以下候选阶段及更早章节保留历史记录，当前接纳状态以上节为准。

# v2.3 P05 会话设置侧栏候选（2026-10-23，当时尚未接纳）

> **当前状态：18张新增候选等待人工确认；snapshots仍只有92张已接纳PNG，没有E05基准。**

- E05登记18项：三主题浅深/固定动态色MAIN、200%窄屏MAIN/THEME/GROUP及5个实际独立窗口。[18组前后对照及差异图](../../../../.trellis/tasks/archive/2026-11/10-21-ui-token-session-sidebar/review/index.html)已整理，17对有变化，模型Sheet像素不变；原始参数、语义和窗口root记录均保留。
- 当前完整verify实际执行111项：92个旧截图用例通过，18个E05因缺原图失败，计数守卫因缺18张新基准失败；0错误/跳过，69秒，退出1。这是候选阶段的预期缺图结果，不能写成全套截图通过。
- 92张旧PNG与本批冻结清单SHA-256完全一致；未record、未修改比较阈值、未删除或豁免用例。接纳后预期110张PNG/111项截图检查，再运行最终verify与完整JVM。
- P05相关119项测试、Lint、dev构建与AndroidTest编译通过；设备与新视觉远端CI仍待补，详情见[本批验证记录](../../../../.trellis/tasks/archive/2026-11/10-21-ui-token-session-sidebar/verification.md)。

以下P04、试点及更早章节保留其当时的接纳与运行记录，不代表P05候选已经接纳。

# v2.3 P04 线下扮演与会话阅读基准接纳（2026-10-22）

> **当前状态：用户已接纳P04的14张新增候选，Windows宿主真实verify 93/93通过；新视觉远端CI待复跑。**

- 用户在14张前后对照与接纳问题后回复“可以，还有任务吗？”，批准接纳本批候选。
- 仅复制已审阅actual新增14张E04基准，旧78张PNG保持原SHA-256；现有90个矩阵用例及2张冒烟图，共92张PNG。没有record、删除用例或放宽零像素阈值。
- 接纳后运行 `.\gradlew.bat :app:verifyRoborazziDebug --tests "com.example.myapplication.ui.screenshot.*" --console=plain`：退出0，32秒，93项实际检查全部通过，0失败 / 错误 / 跳过；92张PNG哈希复验前后一致。
- 本批修复线下大字体组合布局、立绘状态重叠、阅读长文关闭入口和实际承载面低对比；13皮肤、用户几何 / 尾角 / 玻璃开关、原保存 / 发送 / 滚动契约保留。
- 旧78张及本批14张的远端新视觉CI、D01真机 / IME / 系统栏 / 实际材质与物理性能结果仍待取得，本机通过不代表设备或性能通过。

以下为已接纳试点及更早基准的历史记录，原有并发文字保持；当前本机状态以上节为准。

# v2.3 统一试点基准接纳（2026-10-21）

> **当前状态：本轮候选已由用户确认接纳；Windows 宿主真实 verify 通过，CI 新视觉复验待做。**

- 用户在完整前后对照与接纳问题后回复“ok，还有任务吗？”，确认接纳本批候选。
- 本次使用已审阅 actual 候选替换 75 张基准，另 3 张像素一致；保留全部 76 个矩阵用例及 2 张冒烟图，共 78 张 PNG。没有运行 record、删除用例或放宽零像素阈值。
- 接纳后运行 `.\gradlew.bat :app:verifyRoborazziDebug --tests "com.example.myapplication.ui.screenshot.*" --console=plain`：退出 0，79 项检查全部通过，0 失败 / 错误 / 跳过，45 秒；78 张接纳后的 PNG 哈希在复验前后一致。
- 本轮变化为默认形状 / 间距 / Material 语义色、Narra 主题跟随、大字体布局及实际透明合成配色；品牌皮肤和用户保存的几何 / 玻璃开关保持。
- 原基准的 2026-10-14 CI 结果不等同于本轮新视觉已在 CI 通过；D01 真机 / 输入法 / 系统栏 / 实际材质与物理性能继续待补。

以下为 2026-10-14 原始记录，保留既有文字；其中候选 / CI 状态属于历史快照，当前本机接纳状态以上节为准。

# 首批截图基准运行清单

> **状态：已人工确认（2026-10-14 用户确认图片可接受）；CI 宿主已复跑并通过（run 38573095548）。**
> 76 张首批基准由 Windows 11 宿主上的 Roborazzi record 生成一次（2026-10-14 因 fixture 调色板来源
> 修复，对 19 张受影响的 P03/背景基准定向重录一次，见第 7 节），并在本机 verify 连续两次真实通过
> （第二次 --rerun-tasks 全量执行；重录后全量 verify 亦通过）。
> 2026-10-14 远端 CI（ubuntu-24.04 + JDK 17，run 38573095548）零阈值 verify 全部通过：
> 本清单中的 78 张 PNG 在 Windows 与 CI 宿主逐像素一致，基准对两个宿主共同有效；真机（D01 设备效果）待复跑。

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

CI 宿主对应运行（2026-10-14，run 38573095548，ubuntu-24.04 + JDK 17）：`./gradlew :app:testDebugUnitTest --tests "com.example.myapplication.ui.screenshot.*" -Proborazzi.test.verify=true` → BUILD SUCCESSFUL（5m18s），78 张基准零阈值全部一致，无 actual/compare 产物。

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
