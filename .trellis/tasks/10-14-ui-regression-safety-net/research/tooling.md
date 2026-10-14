# 截图工具接入依据与实测结果（2026-10-14）

本文件区分「已实测通过（Windows 本机）」与「待 CI 宿主验证」。命令与结果均来自实际执行；原始 Gradle 日志保存在 `%TEMP%\narra-screenshot-smoke-evidence\01..10-*.log`。

## 本地事实

- 仓库 D:\code\AndroidStudioprojects\MyApplication（分支 main，HEAD 276825b），Windows 11 + PowerShell。
- 本机 JDK：Temurin 21.0.9（JAVA_HOME 已设，`org.gradle.java.installations.auto-detect=false`），Gradle wrapper 9.3.1。
- AGP 9.1.0（Groovy DSL），Kotlin/Compose Compiler 2.2.10，Compose BOM 2026.02.01，compileSdk 36 / targetSdk 36 / minSdk 26，configuration cache 开启。
- CI 对照宿主：`.github/workflows/android-quality.yml`（ubuntu-latest + JDK 17，现有 assembleDebug / testDebugUnitTest / lintDebug 三项）。
- 本机无 adb 设备、无模拟器；WSL 无 JDK/Android SDK；Docker 未启动。结论：本机只验证「链路可跑」，基准按宿主分开，不混用 PNG。

## 官方资料（2026-10-14 检索）

- [Roborazzi README](https://github.com/takahirom/roborazzi/blob/main/README.md)：Gradle 插件 ID 为 io.github.takahirom.roborazzi。
- [使用说明](https://github.com/takahirom/roborazzi/blob/main/docs/topics/how_to_use.md)：captureRoboImage、Compose/Android Rule、Robolectric Native Graphics 及比较选项。
- [Gradle 选项](https://github.com/takahirom/roborazzi/blob/main/docs/topics/gradle_properties_options.md)：roborazzi.test.record/verify、filePathStrategy、pixelCopyRenderMode 等。
- [GitHub Actions](https://github.com/takahirom/roborazzi/blob/main/docs/topics/github_actions.md)：verify 失败及 always() 产物上传。
- [结果判定源码](https://github.com/takahirom/roborazzi/blob/main/include-build/roborazzi-core/src/commonMain/kotlin/com/github/takahirom/roborazzi/RoborazziOptions.common.kt)：Verify 对 Changed 与 Added（缺基准）均抛错——已由负向门禁实测确认。

## 固定版本与依赖（已接入）

| 组件 | 坐标 / 插件 ID | 版本 |
| --- | --- | --- |
| Roborazzi 插件 | `io.github.takahirom.roborazzi` | 1.76.0 |
| roborazzi | `io.github.takahirom.roborazzi:roborazzi` | 1.76.0 |
| roborazzi-compose | `io.github.takahirom.roborazzi:roborazzi-compose` | 1.76.0 |
| Robolectric | `org.robolectric:robolectric` | 4.17 |
| AndroidX Test | `androidx.test.ext:junit`（libs.androidx.junit） | 1.3.0 |
| Compose 测试 | `platform(androidx.compose:compose-bom:2026.02.01)` + `androidx.compose.ui:ui-test-junit4` | BOM 管理 |
| ui-test-manifest | `androidx.compose.ui:ui-test-manifest` | 保持 debugImplementation，未改 |

版本以 Maven Central / GitHub Releases 已发布版本为准；本轮不升级 AGP/Kotlin/Compose/Gradle。组合实跑通过（见下）。

## 配置变更

- `gradle/libs.versions.toml`：新增 `roborazzi`、`robolectric` 版本、库别名与 plugins 别名。
- `build.gradle`（根）：`alias(libs.plugins.roborazzi) apply false`。
- `app/build.gradle`：
  - 应用 `alias(libs.plugins.roborazzi)`；
  - `testOptions.unitTests.includeAndroidResources = true`；
  - `all { test -> ... }`：`systemProperty "robolectric.pixelCopyRenderMode", "hardware"`，`jvmArgs` 9 条 `--add-opens`（JDK 17+ 通用）；
  - `roborazzi { outputDir.set(file("src/test/snapshots")) }`；
  - testImplementation 新增 roborazzi / roborazzi-compose / robolectric / androidx-junit / platform(compose-bom) / ui-test-junit4；未添加全局 `returnDefaultValues`。
- `gradle.properties`：`roborazzi.record.filePathStrategy=relativePathFromRoborazziContextOutputDirectory`。

## 冒烟用例（真实生产组件）

`app/src/test/java/com/example/myapplication/ui/screenshot/MessageBubbleScreenshotTest.kt`：

- 组件：`MessageBubble`（聊天用户气泡，`ChatAppTheme(MATCHA, light)`）与 `CharacterDialogueBubbleContent`（扮演角色玻璃气泡 + 合成双色背景图）；不依赖 ViewModel/AppGraph/网络/数据库。
- 环境：`@RunWith(AndroidJUnit4::class)`、`@GraphicsMode(NATIVE)`、`@Config(sdk = [35], application = Application::class, qualifiers = "zh-rCN-w360dp-h800dp-xhdpi")`。
- 确定性：固定 epoch 1791950400000（2026-10-14 12:00 Asia/Shanghai）、时区 Asia/Shanghai、fontScale 1、无 Random / System.currentTimeMillis、无网络与用户图片。
- 舞台：360×800dp → 720×1600px（density 2）。
- 截图前守卫：节点 displayed、关键中文子串存在、stage 尺寸 720×1600、`fetchImage` 尺寸一致、像素采样（步长 16，采样点 ≥1000、颜色数 ≥8）非单色空白。
- 严格比较：`SimpleImageComparator(maxDistance = 0f)` + `ThresholdValidator(0f)`（零色差容忍、零变化比例）。
- 基准：`app/src/test/snapshots/MessageBubbleScreenshotTest/{chat-user-message-matcha-light.png, roleplay-character-bubble-glass.png}`。

## 实测命令与结果（Windows 本机，已通过）

| 命令 | 退出码 | 结果 |
| --- | --- | --- |
| `.\gradlew.bat :app:recordRoborazziDebug --tests "com.example.myapplication.ui.screenshot.*" --console=plain` | 0 | 真实执行；`recordRoborazziDebug` + `finalizeTestRoborazziDebug` 运行；重录制字节一致 |
| `.\gradlew.bat :app:verifyRoborazziDebug --tests "com.example.myapplication.ui.screenshot.*" --console=plain` | 0 | 2 tests, 0 failed |
| `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.myapplication.ui.screenshot.*" "-Proborazzi.test.verify=true" --console=plain` | 0 | 官方属性入口；PowerShell 中 `-P` 必须整体加引号 |
| `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.myapplication.ui.screenshot.*" "-Proborazzi.test.record=true" --console=plain` | 0 | 基准字节与上一轮一致 |
| `.\gradlew.bat :app:testDebugUnitTest --tests "...RoleplayNoBackgroundSkinSettingsTest" --tests "...CharacterShakeViewModelTest" --tests "...RoleplayMessageUiMapperTest" --console=plain` | 0 | 39 tests, 0 failed（4 / 1 / 34） |
| 同上截图命令加 `--rerun-tasks` | 0 | 35 tasks 全量真实执行，1m56s |

Roborazzi 1.76.0 实际任务名（`:app:tasks --all` 核对）：`recordRoborazziDebug`、`verifyRoborazziDebug`、`compareRoborazziDebug`、`verifyAndRecordRoborazziDebug`、`clearRoborazziDebug`、`finalizeTestRoborazziDebug`（另有不带 Debug 后缀的聚合任务）。插件任务默认依赖该 variant 全部单测，因此命令必须带 `--tests` 过滤。

## 稳定性与基准哈希

3 次独立 record（`recordRoborazziDebug` ×2 + `-Proborazzi.test.record=true` ×1）产出的 PNG SHA-256 完全一致：

- `chat-user-message-matcha-light.png` = F150D723881874C7F052CE48B9B04FF850A4F1BA180319DDC49B5E2E5A2E0B12
- `roleplay-character-bubble-glass.png` = 13F833F8B2BCD9419262CF393E8481CF3CD2F8C98F86DA16FEDEF18A8A3ED01E

## 负向门禁实测（第一轮：冒烟链路）

| 场景 | 退出码 | 证据 |
| --- | --- | --- |
| 基准中翻转 1 个不透明像素：(360,800) FFF3F6F5 → FF00F6FF，diff=1px | 1 | 断言 `Roborazzi: ...chat-user-message-matcha-light.png is changed.`；compare/actual 图写入 `app/build/outputs/roborazzi/`；`results-summary.json` changed=1，diff_percentage=8.6805557E-7 |
| 移走 1 张基准后 verify | 1 | 断言 `Roborazzi: The original file(...) was not found.`；`results-summary.json` added=1 |
| 恢复基准后 verify | 0 | 真实执行通过；基准 SHA-256 不变 |
| 相同输入再次 verify | 0 | Gradle 显示 UP-TO-DATE（增量语义，非真实执行）；`--rerun-tasks` 强制真实重跑同样通过 |
| verify 前后基准哈希 | - | 全部运行前后均不变；失败产物只写 `app/build/{outputs,test-results,reports}/roborazzi`，不污染 `src/test/snapshots` |

## 负向门禁/验证机制（第二轮：登记用例全链路，Windows 本机 2026-10-14）

在 76 张登记基准（+2 张冒烟白名单，共 78 张 PNG）上重放完整负向门禁。
全过程只运行 verify，未调用任何 record / verifyAndRecord 入口；
基准原文件先全量备份到 `%TEMP%\narra-negative-gate-20261014`（含前后 SHA-256 清单）。

| 场景 | 验证入口（--tests 过滤） | 退出码 | 证据 |
| --- | --- | --- | --- |
| 注入 1px 差异：基准 (720×1600) 坐标 (360,800) 由 (245,246,247,255) 改为 (0,246,255,255)；Pillow 解码复核仅 1 像素不同 | `:app:verifyRoborazziDebug` `PageScreenshotH01Test` | 1 | 18 tests / 1 failed；`Roborazzi: ...H01_p01-messages-matcha-light.png is changed.`；compare/actual 写入 `app/build/outputs/roborazzi/`；`results-summary.json` changed=1、unchanged=17，diff_percentage=8.6805557E-7 |
| 恢复备份后重验 | 同上 | 0 | 真实执行（2 tasks executed，非 UP-TO-DATE）；恢复后 SHA-256 与备份一致（8B0D0A92…） |
| 临时移除 `H02_p01-messages-empty.png` | `:app:verifyRoborazziDebug` `PageScreenshotH02Test` | 1 | 3 tests / 1 failed；`Roborazzi: The original file(...H02_p01-messages-empty.png) was not found.`；actual 图照常产出 |
| 仍缺失，跑计数守卫 | `:app:testDebugUnitTest` `ScreenshotBaselineGuardTest` | 1 | `缺少已登记的基准 PNG：[PageScreenshotH02Test/H02_p01-messages-empty.png]` |
| 恢复备份后重验 | `:app:verifyRoborazziDebug` `PageScreenshotH02Test` | 0 | 真实执行；恢复后 SHA-256 与备份一致（65B623EB…） |
| 完整性审计 | 全量 SHA-256 清单（78 PNG + manifest.md，共 79 文件） | - | 全过程前后 0 差异；失败产物只写 `app/build/**`，未写 `src/test/snapshots` |

补充（quality job 交互，None 模式探针）：裸 `:app:testDebugUnitTest --tests PageScreenshotH02Test`（无 roborazzi 属性）退出码 0；
基准 mtime/哈希不变、无 actual/compare 产物、`results-summary.json` 未重写 → None 模式既不 record 也不 compare。

本机（Windows + JDK 21）结论：
- 零阈值 verify 链路在登记用例上真实有效：单像素变化与缺基准均非零退出，恢复后真实通过；
- verify 不修改任何基准，诊断只落 app/build（含 results-summary.json 与 Roborazzi HTML 报告）；
- 全过程未运行 record / verifyAndRecord。

待 CI 复核（ubuntu-24.04 + JDK 17；远端 run 尚未触发）：
- 同一 verify 入口在 CI 宿主首次运行需先通过（含 Robolectric android-all 下载与缓存）；
- 跨宿主像素一致性未验证：Windows 生成的 78 张候选基准不得直接当作 CI 基准；
- 注入/缺失负向场景是否在 CI 宿主重放由门禁评审决定，当前证据只覆盖本机。

## 负向门禁抽验（第三轮：调色板来源修复后的定向重录基准，Windows 本机 2026-10-14）

背景：ScreenshotFixture 原来给所有 backdropState 注入手写固定调色板，P03/背景用例未走生产派生（覆盖缺口登记于 manifest.md 第 7 节）。本轮把 fixture 改为在组合内调用生产 `deriveImmersiveGlassPalette`（产品侧仅 private→internal，行为与调用点不变），并定向重录 19 张受影响基准；该调色板覆盖缺口已修复。

| 场景 | 验证入口（--tests 过滤） | 退出码 | 证据 |
| --- | --- | --- | --- |
| 重录后零阈值 verify（全量） | `:app:verifyRoborazziDebug` `com.example.myapplication.ui.screenshot.*` | 0 | 79 tests / 0 failed（真实执行，非 UP-TO-DATE） |
| 同上加 `--rerun-tasks` | 同上 | 0 | 79 tests / 0 failed；35 tasks 全部真实执行（1m53s） |
| 注入 1px 差异：新 P03 基准 (720×1600) 坐标 (360,800) 由 (227,231,227,255) 改为 (0,246,255,255)；Pillow 复核仅 1 像素不同 | `:app:verifyRoborazziDebug` `PageScreenshotH01Test` | 1 | 18 tests / 1 failed；`Roborazzi: ...H01_p03-online-roleplay-matcha-light.png is changed.`；compare/actual 写入 `app/build/outputs/roborazzi/` |
| 恢复备份后重验 | 同上 | 0 | 真实执行；恢复后 SHA-256 与重录后清单一致（401F76CC…） |
| 完整性审计 | 全量 SHA-256 清单（78 PNG + manifest.md，共 79 文件） | - | 重录前后清单、抽验恢复后与重录清单均 0 差异；verify 不改基准 |

结论：调色板来源修复后的新基准仍保持零阈值门禁有效（单像素变化非零退出、恢复后真实通过）；基准状态仍为 Windows 候选、待 CI 宿主复验与人工确认。

## 宿主能力结论（Windows 本机）

- Robolectric Native Graphics：可用（Robolectric 4.17 + NATIVE 模式 + SDK 35），未出现 DLL/宿主失败。
- 中文渲染：人工查看生成 PNG，无豆腐块、无缺字（用户气泡与扮演玻璃气泡均确认）。
- 玻璃/扮演路径：玻璃面板、边框与强调色文本在合成背景上正常渲染。
- record / verify：真实可跑；失败非零退出并产出 expected/actual/diff 报告。
- 已知坑：Compose 测试的 `captureToImage()` 在 Robolectric 下等待重绘会 `ComposeTimeoutException`；守卫改用 Roborazzi `fetchImage(...)`（同一原生渲染路径，尺寸一致）。
- 网络：首次运行需下载 Robolectric `android-all-instrumented`（SDK 35，约 100MB+，耗时较长）；Maven Central 偶发 TLS handshake 失败，重试即可。
- `ui-test-manifest` 保持 debugImplementation 即可被 `testDebugUnitTest` 使用（debug variant 合并清单含 ComponentActivity），无需新增 test 依赖。

## 待 CI 宿主验证（不得宣称已通过）

1. ubuntu-24.04 + JDK 17 下复跑 record/verify 两条链路（record 仅限手动 dispatch），确认退出码与渲染行为一致（当前结论只证明 Windows + JDK 21 宿主可跑；quality job 仍为 ubuntu-latest，不受影响）。
2. 基准必须由 CI 宿主生成或经 CI 宿主复现后再人工确认；当前 78 张基准均为本机 Windows 产物，仅用于本机链路验证，不得直接作为 CI 基准（设计约定：宿主分离）。
3. CI 首次运行需允许下载 Robolectric android-all 与 Gradle 依赖，缓存策略待定。
4. 零测试/缺场景防呆（ScreenshotBaselineGuardTest）与完整矩阵（78 张基准）已在实施第 2、3 节完成；其在 CI 宿主的行为仍待本清单第 1、2 项验证。
