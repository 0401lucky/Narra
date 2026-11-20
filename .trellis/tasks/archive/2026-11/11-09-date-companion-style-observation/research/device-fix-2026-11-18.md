# D3 观测设备定向修复（2026-11-18）

Active task: `.trellis/tasks/11-09-date-companion-style-observation`

版本：v2.4.1；worker：`d3-device-cli`。

## 状态与范围

**测试修复已实现，源码释放给 main-v241；新编译与设备结果待验证。**

本次仅处理 `CompanionObservationPanelTest.narrowLargeFont_longObservationScrollsToEndAndKeepsCloseTouchTarget`。主会话最新交接的 `d3-device-06` 已解决 17 个旧增量 ABI 及模板 EditableText，18 项全部通过；D3 设备按方法去重仍为 **36/37**，本记录不把待验证的一项计为通过。

已读取 AGENTS.md、check.jsonl 六项、prd/design/implement 及定向交接；没有重做全范围 D3 审查或读取全部历史。修改文件仅：

- `app/src/androidTest/java/com/example/myapplication/ui/screen/roleplay/CompanionObservationPanelTest.kt`。
- 本记录。

`NarraAlertDialog`、主题、12 个已有 UI 漂移文件、基准/inventory、版本与更新 JSON 均未修改。没有运行 Gradle、formatter、设备、付费文本模型或 TTS；没有提交、推送或发布。

## 根因证据与实际值的边界

旧测试用外层 `CompositionLocalProvider(LocalDensity = Density(..., 2f))`，同时只在外层声明 320×600dp。它没有核对 Dialog 的 Android Resources、布局节点或 TextLayoutResult，所以 200% 字体只是未经验证的测试声明；首轮实际 `range.maxValue() = 0`，48dp 已通过。

本机当前解析的 Compose UI 为 **1.10.4**（app 使用 BOM 2026.02.01）。只读 javap 检查了缓存 `ui.aar/classes.jar`，确认：

1. `DialogWrapper` 用宿主 `View.getContext()` 创建独立 `ContextThemeWrapper`，并用该 Context 创建 `DialogLayout`。
2. 独立 `AndroidComposeView` 构造时调用 `AndroidDensity.Density(context)`。
3. `ProvideCommonCompositionLocals` 用 `Owner.getDensity()` 重新提供 `LocalDensity`。
4. `SemanticsNode.layoutInfo.density` 是可读取的真实布局信息；已存在的 P06 测试也使用 `ViewRootForTest` 和 `GetTextLayoutResult` 核对跨窗口字体。

因此外层 fake density 不会改变独立 Dialog 所使用的 Android Resources。`ChatAppTheme` 与观测面板没有产品级 LocalDensity 字体覆盖；没有生产字体偏好丢失的证据，不需要共享产品修改。

**旧失败日志没有输出 Dialog 的具体 fontScale。**“旧 Dialog 很可能仍为平台 100%，原观测文字在正常字号下放得下”是由实际字节码与现有夹具得出的解释，不伪装成旧设备 fontScale 的实测。新夹具会记录并严格断言真实值；设备是否达到 2.0 仍由主会话复验确认。

## 已实现的修复

`CompanionObservationPanelTest.kt:365` 的 `withNarrowPlatformWindow`：

- 在 Activity 上安装专用 ComposeView；其 ContextThemeWrapper 通过 `applyOverrideConfiguration` 提供实际 Android Resources：fontScale=2、screenWidthDp=320、screenHeightDp=600。
- 实际 Activity 窗口约束为 density 换算后的 320×600dp。保留现有主题和观测文本，不再靠 narrow 外层 fake LocalDensity 传递字体。
- finally 先 dispose Composition（连带释放 Dialog），再移除专用 View 并恢复原窗口宽高。核对窗口参数恢复、基础 Activity Resources 的 fontScale/宽高未变，记录恢复证据。没有写系统 font_scale、wm size 或应用设置。

`CompanionObservationPanelTest.kt:259` 的设备用例：

- 先检查宿主与 Dialog 是独立的 View owner；平台配置、根布局、观测节点及实际 TextLayoutResult 的 fontScale 全部必须为 **2.0**。
- 核对平台 320dp 配置、宿主实际宽度，以及 Dialog 实际宽度；保留原有 Dialog ≤320dp 断言。
- 原来两段 `repeat(4)` 长值逐字保持；仍强制 `maxValue > 0`、滚动到底。
- 按 TextLayoutResult 的末字段首末行与真实滚动视口的像素边界，验证整个最后字段可见。没有以 `assertExists` 代替末项可见。
- 核对入口、关闭按钮 48dp；关闭按钮在 Dialog 内。点击关闭后宿主 Back 未触发；重开后第一次系统 Back 只关 Dialog，第二次才触发宿主 Back。
- 只有字体、滚动、末项和按钮边界都通过后，才写 `observation-320dp-font200-last-field.png` 候选及实测 metadata。PNG 仍不等于完整用例通过，关闭/Back/finally 后续动作也必须通过。

原有普通 render 提取为相同的 `FixtureContent`；其余六个测试方法正文逐字未变。解析、历史 label、canonical raw 和来源 revision 合同没有改变。

## 已做的静态核对

- 7 个测试方法名称/数量保留；其余 6 个方法正文逐字一致。
- 长观测 raw 构造逐字一致；没有增大文本绕过字体问题。
- UTF-8/LF、无重复 import、无尾随空白、无替换占位符。
- 保存的开工前源码从 PowerShell 输出还原 LF 后，SHA-256 与磁盘原始字节一致（没有以规范化相同冒充不同 raw SHA）。
- 对 `rg --files app/src baselineprofile/src app/build.gradle gradle.properties gradle/libs.versions.toml docs/updates/dev.json` 的 **1370 个范围外可见文件**，按排序后的路径 + raw SHA 聚合；修复前后均为：
  `054b3a87f971eadd5e41e98117173fd1ca83a4429e99c1561eff4113e97c7ff7`。
  此计数是本次 rg 可见范围，不冒充此前全量 1425 源/281 保护项验收。
- 本测试在 Git 中是既有未跟踪文件，普通 git diff 不显示其本轮增量；实际增量已对照读取时原始 SHA 和源码检查。

TypeCheck / Lint / AndroidTest：**本 worker 未执行，待唯一执行者 main-v241**。之前通过的工程检查不能代替新测试源码编译和设备交互。

## 原始字节 SHA-256

- 测试修改前：`0b9f44a8e3712467b1f3a27978a537457bb91c1d1ef1cb733826b2d2c5b3d2d9`。
- 测试释放时：`402f8652b84be4ce3c21945fb9752769d2e6c481c55cf0fa551c11ed4a2c79a3`。
- 未修改的 NarraAlertDialog：`5739bf4fb8ad023de5393ecef9e1de05315b9173018aff14f0f335582855a592`。

本记录自身 SHA 由最终释放回复提供，避免自引用哈希。

## 交给主会话的最小复验

只需重新编译/构建 AndroidTest APK，当前 Debug 产品 APK 未变：

```powershell
.\gradlew.bat :app:compileDebugAndroidTestKotlin :app:assembleDebugAndroidTest --console=plain
```

实际 output-metadata.json 的测试包为 `com.narra.app.dev.test`、文件为 `app-debug-androidTest.apk`、runner 为 `androidx.test.runner.AndroidJUnitRunner`；以下命令由主会话在既有隔离 `emulator-5580 / Narra_Memory_V24` 执行：

```powershell
$adbD3 = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
& $adbD3 -s emulator-5580 install -r -t 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
& $adbD3 -s emulator-5580 shell am instrument -w -r -e class 'com.example.myapplication.ui.screen.roleplay.CompanionObservationPanelTest#narrowLargeFont_longObservationScrollsToEndAndKeepsCloseTouchTarget' 'com.narra.app.dev.test/androidx.test.runner.AndroidJUnitRunner'
```

最小诊断范围是这个 **1 方法**。因为共同 FixtureContent 被提取，独立收口建议直接把 class selector 改为 `com.example.myapplication.ui.screen.roleplay.CompanionObservationPanelTest`，执行本类 **7 方法一次**；可直接选择本类运行，避免先单项再整类重复。不需要重跑 17 ABI、模板或完整 37 方法。

设备成功标准：实际 JUnit 1/1（或本类7/7），以及 cache 中各项实测 fontScale=2、scroll max>0/current=max、最后字段首末行位于 viewport、窗口/基础字体恢复，关闭/Back 动作全部走完。主会话确认后才能把该项并入 D3 方法去重结果。

## 设备产物与失败续接

测试包缓存 `cache/companion-d3-ui-review/`：

- `observation-platform-window.txt`：独立窗口/布局/实际文本 fontScale、宽高/density、viewport 和滚动范围；先写后断言，失败时也能定位环境。
- `observation-platform-restored.txt`：基础字体前后值和原/恢复窗口尺寸；finally 在失败路径同样执行。
- `observation-320dp-font200-last-field.png` 与同名 `.txt`：严格通过字体/末项可见后才生成的候选和坐标证明；不接纳为截图基准。

如失败，请交回精确 JUnit 堆栈和上述 metadata，并重新释放这个测试文件的修复窗口。若资源=2但文本/节点≠2，先查 View Context/独立窗口装配；若所有字体=2但 max=0，查记录的 Dialog/文本/viewport 实际高度；若末项或关闭/Back失败，保留后续动作与断言，定向修复。当前没有产品修改依据，扩大共享源码范围须先协调主会话。

## Channel 与编辑工具限制

显式 `trellis channel send v241-companion-resume --as d3-device-cli --to main-v241 --stdin` 被沙箱拒绝：`EPERM`，无法打开用户目录下的 channel `.lock` 文件。没有扩大权限或另建 channel。源码释放声明通过本轮最终回复供 runtime 路由，交接正文保存在本记录。

apply_patch 对目标源文件写入失败；确认文件非只读、原始 SHA 未变且同一限定目标可写后，采用带原始 SHA/当前字节守卫的精确片段替换。没有脚本重写其它文件。

**本轮定向自修已完成，源码已释放；等待主会话编译与设备证据，未宣称 D3 或整个 v2.4.1 完成。**

## 主会话补充方向的只读核对

主会话关于配置 Context/独立窗口的建议与已释放修复同向。当前夹具通过 ContextThemeWrapper.applyOverrideConfiguration 给 ComposeView 提供真实 Android Resources；DialogWrapper 所取 View.context 是该 Context。Dialog 内观测正文的 `text.layoutInfo.density.fontScale` 与实际 `layout.layoutInput.density.fontScale` 已分别断言为2.0，覆盖内容布局节点及实际字体，不只依赖外层 locals 或文件名。

追加只读 javap 核对当前依赖：Compose UI Test JUnit4 1.10.4 的 AndroidComposeTestRule/ComposeTestRule 均提供 runOnUiThread、runOnIdle、waitForIdle；Compose UI 1.10.4 的 AbstractComposeView 提供 disposeComposition；Activity 1.12.4 的 ComponentActivity.setContentView(View) 先调用 initializeViewTreeOwners，再挂载 View，支持本夹具的生命周期/返回拥有者接线。

本次保持已释放测试源码 SHA `402f8652b84be4ce3c21945fb9752769d2e6c481c55cf0fa551c11ed4a2c79a3` 不变，仅补充 API 证据。未运行 Gradle/formatter/设备；最小复验仍为前述单方法，实际节点2.0及成功结果待主会话执行。
