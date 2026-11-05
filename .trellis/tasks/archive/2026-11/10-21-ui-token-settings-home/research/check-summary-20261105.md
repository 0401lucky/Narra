# P10 设置首页最终独立检查（2026-11-05）

结论：**P10独立检查与接纳后质量门禁全部完成，可进入本批提交审阅。** 未发现额外产品或测试问题。用户已明确接纳16张 E10；最终截图 **127/127**、完整 JVM **1755/1755** 通过，均真实执行且无失败 / 错误 / 跳过。Lint、dev构建及 Kotlin / AndroidTest 编译通过，126张已接纳 PNG与最终生产源码哈希保持不变。

接纳前为110张已接纳 PNG；主会话按用户确认精确写入16张 E10，现共126张。本检查独占 Gradle，先完成最终127项截图并立即归档结果，再完成无selector的完整 JVM；全部 Gradle 已结束。本检查没有复制基准、record、暂存、提交、推送或归档。

## Findings (fixed)

本检查没有追加产品或测试修改。以下本批修正由实现代理完成，并经本检查对真实代码、测试结果和证据复核：

| 文件 | 问题与已完成修正 |
| --- | --- |
| `SettingsScreen.kt` | Slider原内部触控高度为44dp，外层 `heightIn` 未扩大其进度语义区。局部设置 `SliderDefaults.Thumb` 最小48dp，保留原值和回调；真实顶部边缘点击仅触发一次更新的检查通过。 |
| `SettingsComponents.kt`、`SettingsScreen.kt` | 首轮 Lint 新增3条 `ModifierParameter` 警告。标题专用 Modifier 改为 `titleIsHeading: Boolean = false`，私有行的 modifier 调整到第一可选参数位置；最终3条新增警告全部消除，没有增加 Lint 例外。 |
| `SettingsHomeScreenshotE10Test.kt` | 初版显示 Sheet 只滚入末项文字，最后开关未完整入镜。改为滚到真实 max 并断言整个末项 Toggleable 触控范围在窗口内；在原始提交隔离副本及当前实现用同样策略重取 before / after。最终显示浅深原图与窗口记录均已复核。 |
| `SettingsHomeEntryTest.kt`、`SettingsHomePersistenceTest.kt` | 修正测试对旧 Profile 入口 Role.Button 的错误假设，并避开固定底栏；保存成功 fixture 使用已规范化的 URL。保留主壳产品与 VM / URL 规范化行为，不为测试改写原业务契约。 |

源码守卫实际为 **13项**，7个新 `SettingsHome*Test` 合计19项，最终相关总数113项；14条 JSONL 上下文与源码守卫测试数量不是同一口径。

## Findings (not fixed)

无本批代码阻断。实际设备与外部环境仍保留以下验证边界：

- 真机 TalkBack、系统返回 / 窗外触摸、IME / 系统栏、实际材质、D01、物理性能及新视觉远端 CI 未验证。AndroidTest 编译与宿主行为不能替代设备运行。

## 代码与契约审查

- 两个 `SettingsGroup` 重载均透传 shape / contentPadding。共享20dp页面 padding、8dp组圆角 / 内边距、56/18dp分隔线、16/12dp行内边距、14/5dp间距、行标题 / 摘要两行、顶栏一行等旧默认保留；首页通过局部组合显式消费现有令牌，未扩大共享 SectionHeader 或全局组件默认。
- 最终 `SettingsTopBar` 的 `titleIsHeading=false` 直接使用原空 Modifier，不增加空语义节点；首页显式 true，保留完整标题与 heading。该参数只控制标题语义，符合组件级显式配置要求。私有行参数重排未改布局或回调。
- 已沿实际主壳 Profile → `RoleplayNavGraph` → `SettingsNavGraph` 核对首页入口与各目的地绑定；真实主壳测试及首页逐项回调 / 相邻触控均通过。签名里存在但首页没有的角色、世界书、记忆等行没有被恢复。
- 显式保存仍经真实 `SettingsViewModel.saveSettings` 的成功回调后导航；失败保留草稿、复位保存中并沿原消息通道反馈。返回仍调用保存后立即 pop，不扩写为失败留页。VM、NavGraph、存储与 Provider 协议无本批修改。
- 320×640dp / 200%宿主验证长摘要、标题、末项可达、名称 / 角色 / 选中状态、48dp实际触控及一次点击一次回调。整行 Switch 持有唯一更新回调，子 Switch 仅绘制。
- 八种 scheme 的颜色检查读取真实 `TextLayoutResult` 前景及 `fetchImage` 像素承载面；选中面为 `primaryContainer/onPrimaryContainer`，对比至少4.5:1。固定主题色块只作品牌预览。
- `expectedDeclarationCounts` 缺省仍为1，inventory仅为 `SettingsGroup` 明确登记2；任一重载的新增硬编码、数量漂移和陈旧映射均有负向检查。没有整目录白名单或降低截图比较阈值。
- 已核对 `.trellis/spec/backend/ui-token-contract.md` 的最终 `titleIsHeading` 签名、共享兼容 / 滑块 / 滚尾约束，及 `quality-guidelines.md` 的隔离重捕契约；版本记录仍归属v2.3，构建版本保持2.2.12-dev。

## Verification

实施阶段的定向、Lint / 构建 / 编译命令由实现代理串行执行，本检查独立读取最终日志 / JUnit / Lint XML。用户接纳并完成精确基准写入后，**最终截图和完整 JVM 由本检查实际执行**，逐轮保存证据并核对126张 PNG哈希。生产源码未再变化，已通过的 Lint / 构建 / 编译未重复执行。

| 检查 | 最终结果与实际执行 | 证据 |
| --- | --- | --- |
| 接纳后最终截图 verify | **127/127**、14 suites，0失败 / 错误 / 跳过，退出0，48秒。实际比较；2 tasks executed / 33 UP-TO-DATE。 | [摘要](accepted-screenshots-20261105/summary.json)、[日志](accepted-screenshots-20261105/accepted-screenshots-20261105.log) |
| 接纳后完整 JVM | **1755/1755**、236 suites，0失败 / 错误 / 跳过，退出0，1分31秒。无selector，测试实际执行；1 task executed / 33 UP-TO-DATE。普通 JVM不比较截图，与上一轮分别记录。 | [摘要](accepted-full-jvm-20261105/summary.json)、[日志](accepted-full-jvm-20261105/accepted-full-jvm-20261105.log) |
| 接纳前相关 JVM | **113/113**、20 suites，0失败 / 错误 / 跳过，退出0，1分12秒。主 Kotlin、单测 Kotlin和测试实际执行；7 tasks executed / 27 UP-TO-DATE。 | [摘要](targeted-final/summary.json)、[日志](targeted-verified-20261105.log) |
| 接纳前截图候选验证 | **127项**、14 suites，110旧图通过，17项仅缺新基准，0错误 / 跳过，退出1，46秒。保留该阶段真实比较及缺图门禁证据。 | [摘要](screenshot-final/summary.json)、[日志](after/screenshot-verified-20261105.log) |
| Lint | **通过**，0 errors / 55 warnings / 3 hints；真实执行主源码、单测、AndroidTest Lint分析。逐项比较初版与最终报告，恰好移除3条新增 ModifierParameter，无新增项。 | [质量摘要](quality-final/summary.json)、[Lint XML](quality-final/lint-results-debug.xml) |
| TypeCheck / 编译 | **通过**。最终定向轮实际执行 `compileDebugKotlin` / `compileDebugUnitTestKotlin`；质量轮复用它们，实际执行 `compileDebugAndroidTestKotlin`。 | [质量最终日志](quality-final/quality-verified-20261105.log) |
| dev构建 | **通过**，实际执行 `packageDebug` / `assembleDebug`。质量组合命令退出0，5分19秒，13 tasks executed / 47 UP-TO-DATE。APK尺寸 / SHA-256与质量元数据一致，未上传。 | [质量摘要](quality-final/summary.json)、[APK元数据](quality-final/output-metadata.json) |
| 源码与基准冻结 | 当前两生产文件及最终 after manifest 与 `postCaptureLintParameterFix` 一致；原110张不变，新增16张与审阅后图相同。两轮最终验证前后126张PNG逐项SHA-256均不变。 | [来源与阶段哈希](capture-recovery-provenance.json)、[接纳后126张冻结清单](accepted-png-hashes-20261105.txt)、[原110张清单](accepted-png-hashes-before-p10.txt) |
| 静态检查 | 本批跟踪代码的定向 `git diff --check` 退出0；9个新增测试 / 宿主文件无尾随空白。只存在仓库既有LF/CRLF转换提示。 | [分阶段记录](check-review-20261105.md) |

最终质量日志为 `quality-final/quality-verified-20261105.log`；根目录的 `quality-final-20261105.log` 是4分58秒、58 warnings的首轮历史记录，不用于最终通过结论。

已执行的最终命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.example.myapplication.ui.screen.settings.SettingsHome*Test' `
  --tests 'com.example.myapplication.ui.screen.settings.SettingsBenchmarkReadyTest' `
  --tests 'com.example.myapplication.ui.theme.UiTokenSourceGuardTest' `
  --tests 'com.example.myapplication.ui.screen.roleplay.SessionSidebar*Test' `
  --tests 'com.example.myapplication.ui.component.roleplay.PilotAccessibilityTest' `
  --tests 'com.example.myapplication.ui.component.roleplay.RoleplaySemanticColorsTest' `
  --tests 'com.example.myapplication.viewmodel.SettingsViewModelTest' `
  --tests 'com.example.myapplication.viewmodel.SettingsPersistenceCoordinatorTest' `
  --tests 'com.example.myapplication.viewmodel.SettingsUiMutationSupportTest' `
  --tests 'com.example.myapplication.viewmodel.SettingsDraftStateSupportTest' `
  --console=plain

.\gradlew.bat :app:verifyRoborazziDebug `
  --tests 'com.example.myapplication.ui.screenshot.*' --console=plain

.\gradlew.bat :app:lintDebug :app:assembleDebug :app:compileDebugAndroidTestKotlin --console=plain

# 用户接纳后：先执行并归档上方截图 verify，再执行不带 selector 的完整 JVM。
.\gradlew.bat :app:testDebugUnitTest --console=plain
```

## 最终候选与捕获来源

- before基点为 `18c1613f3f935dda790cbb6d01789bdd316c2982`。隔离 detached worktree只叠3个E10捕获测试文件及捕获工具，原两生产文件Git blob与提交相符；3个覆盖文件和42个复制证据哈希已独立校验。临时目录和Git worktree登记现均已清理。
- 两阶段各16张 PNG、6个真实独立窗口；常规360×800dp / 100%，窄屏320×640dp / 200%，density=2。两阶段各14张非显示图与 initial逐字节一致，只有显示浅深2张因滚动修正改变；before / after共16组均有变化。
- 显示浅深的真实滚动均为before **264/264**、after **670/670**。四份窗口root为640×1280px；before末项父行y=1104..1216、子Switch y=1128..1192，after父行y=1035..1216、子Switch y=1136..1200。已目视检查4张原图，标签和最后开关完整可见，父行触控范围满足48dp且在窗口内。
- 原capture source / manifest哈希保留；Lint参数修正追加独立 `postCaptureLintParameterFix`，当前两生产文件哈希与其最终记录一致。最终16张 after与 `after-pre-lint` 及 `review/after` 全部逐字节一致，没有用新来源静默覆盖旧证据。
- 用户原话“接纳这16组效果（推荐）”及16张精确SHA已记录于[visual-acceptance.json](visual-acceptance.json)。已核对新增16张基准与最终after、[审阅数据](../review/review-data.json)的 after_sha256全部一致；审阅数据和接纳记录现均为 `accepted=true`。[审阅页](../review/index.html)与接纳后的126张冻结清单一致。
