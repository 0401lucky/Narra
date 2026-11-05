# P10 设置首页实施记录

本批沿用已批准的 v2.3 计划，基点为 `18c1613f3f935dda790cbb6d01789bdd316c2982`。完成设置首页和三个真实外观 Sheet 的显式令牌迁移、行为与共享兼容验证、16 组候选前后证据。交接时仍保持 110 张已接纳 PNG；本代理没有新增基准 PNG、record、暂存、提交或推送，视觉接纳记录由主会话维护。版本配置与下载源保持原样。

## 修改文件与实际行为

- `app/src/main/java/com/example/myapplication/ui/screen/settings/SettingsScreen.kt`：首页外边距、组/行间距与形状显式使用 `NarraUi` / `NarraShapes`。本地 `SettingsHomeGroup` / `SettingsHomeRow` 负责选择新参数，组标题使用实际 `onSurfaceVariant`，避免扩大共享 `SettingsSectionHeader` 的公共接口。长标题和 provider/model 摘要可完整换行；仅迁移实际存在的 11 行。
- 同文件的主题色调、颜色模式、显示 Sheet：保留真实 `ModalBottomSheet`，显式使用 large 形状和完全展开状态。选择项以 `primaryContainer/onPrimaryContainer` 配对；未选项和正文使用实际 Material 表面角色。主题预览圆点保留已有品牌颜色，未用它们承载文字。200% 字体开关分行排列，整行仍为单一 `Role.Switch`，子 Switch 无独立回调。字号滑块增加可访问名称，实际 thumb 最小 48dp。
- `SettingsComponents.kt`：仅增必要可选参数，旧默认值、Palette、消息消费及其他组件保持原行为，详见下表。
- `ui/screenshot/SettingsHomeRenderTest.kt`、`SettingsHomeScreenshotE10Test.kt`、`ScreenshotCases.kt`：固定真实 Screen、长摘要与回调宿主；登记 16 个独立 `@Test`；严格查找独立 Dialog root，检查实际文字的 density/fontScale、窗口尺寸和末项操作。不修改 P05 捕获实现。
- `ui/screen/settings/SettingsHomeAccessibilityTest.kt`、`SettingsHomeActionsTest.kt`、`SettingsHomeAppearanceTest.kt`、`SettingsHomeColorsTest.kt`、`SettingsHomePersistenceTest.kt`、`SettingsHomeEntryTest.kt`、`SettingsHomeSharedCompatibilityTest.kt`：19 项宿主行为测试，覆盖 320×640dp/200%、真实触控、三 Sheet、8 套实际配色、保存与主壳入口。
- `ui/theme/UiTokenSourceInventory.kt`、`UiTokenSourceGuard.kt`、`UiTokenSourceGuardTest.kt`：登记本批声明和精确旧默认/装饰色例外；允许显式登记同名重载数量，两个 `SettingsGroup` 都受扫描。默认数量仍严格为 1，只有该声明登记为 2。负向验证任一重载新增硬编码、缺少/新增重载和陈旧映射均失败。
- 本目录 `freeze_e10_evidence.py`、`collect_test_results.py`、`capture-recovery-provenance.json` 及对应阶段产物：冻结缺图 actual、字体/窗口/语义、JUnit、命令日志和哈希。审阅页由主会话维护。

## 共享接口与兼容证据

| API 增量 | 保留的旧默认 | P10 消费与运行证据 |
| --- | --- | --- |
| `SettingsTopBar(titleMaxLines: Int = 1, titleIsHeading: Boolean = false)` | `horizontalPadding = SettingsScreenPadding = 20.dp`；标题一行省略；原副标题间距 | 首页显式 16dp、完整标题与 heading。局部 heading 语义选项不作为迁移开关。测试长标题默认仍省略，opt-in 后完整；返回与保存独立命中。P02 原截图不变。 |
| 两个 `SettingsGroup(..., shape: Shape, contentPadding: PaddingValues, content)` | `RoundedCornerShape(SettingsGroupRadius = 8.dp)`；垂直 8dp；有标题重载仍调用原 SectionHeader | 有标题重载明确透传 shape/padding 给无标题重载；首页本地标题组合使用 medium + 8dp。测试用 24dp 形状和 padding 检查两个重载实际像素与布局，旧调用保持 8dp 圆角和边距。 |
| `SettingsGroupDivider(contentPadding: PaddingValues)` | start 56dp / end 18dp | 首页显式横向 16dp。 |
| `SettingsListRow(shape, contentPadding, horizontalSpacing, textSpacing, titleMaxLines, supportingTextMaxLines)` | 旧圆角 8dp；padding 16×12dp；横向 14dp、文间 5dp；标题/摘要各两行；原 56dp 最小行高 | 首页显式 medium、16×12dp、12dp/4dp、完整行数。旧行两行省略与显式展开的真实布局、点击均受测。 |
| `UiTokenSourceGuard.Scope.expectedDeclarationCounts` | 未登记仍必须唯一；无目录级豁免 | `SettingsComponents.kt#SettingsGroup = 2`；新增负向用例检查两个重载的内容，非字段镜像测试。 |

修改前搜索了 45 个 UI 消费文件。没有调整历史别名、NarraButton / NarraAlertDialog、共享玻璃默认、Provider 协议或保存策略。P11 只依赖上述稳定 opt-in，未提前迁移提供商链。

## 保存和入口边界

- 真实手机主壳 Profile 的“设置”回调单次触发，并检查点击区位于固定底栏上方。该旧入口没有 `Role.Button`，本批未扩改 P01；首页迁移行自身明确为 Button。
- 首页模型、提供商、语音合成/识别、更新、条款、诊断、脚本回调逐项独立验证，没有恢复签名中未显示的世界书/记忆/角色入口。
- 草稿触发显式保存；保存中禁用；已保存配置按现有分支进入会话/欢迎页；消息显示后消费一次。
- Fake Store + 真实 SettingsViewModel 的延迟写入测试：保存前不执行成功回调、保存中不能重复提交；成功后持久化并回调一次；失败留草稿、展示原消息、成功回调为零。API provider 显式注入 `TestOpenAiCompatibleApi`，未访问真实 API 或用户数据。
- `SettingsNavGraph` 没有修改：返回仍 `saveSettings {}` 后立即 `popBackStack()`。这里只核对原接线和 Screen 返回回调，不宣称返回失败会留页，也不把宿主点击当作设备系统返回实测。

## 检查结果

| 命令/范围 | 实际结果 | 证据 |
| --- | --- | --- |
| 单图 verify probe | 1 项，仅缺原图失败；确实产生 actual；此时产品未改 | `before-probe-20261105.log` |
| 初始 before 16 图 + 计数守卫 | 17 项/17 个预期缺图失败；后续发现显示图只滚到文字，保留为历史 | `before-initial/` |
| 受控 before 重捕，隔离 `18c1613f`，E10 + 计数守卫 | 17 项/17 个缺图失败，0 errors/skipped；3分10秒，35 个 Gradle task 真实执行 | `before/`、`capture-recovery-provenance.json` |
| 最终相关 JVM：P10、P05、P02 无障碍、语义色和设置状态/保存回归 | **113/113**，20 suites，0失败/错误/跳过，1分12秒；最终参数版本重新编译并真实运行，7 tasks executed / 27 UP-TO-DATE | `targeted-final/summary.json`、`targeted-verified-20261105.log` |
| 其中源码守卫 | **13/13**；与 JSONL 上下文条数无关 | `targeted-final/TEST-com.example.myapplication.ui.theme.UiTokenSourceGuardTest.xml` |
| 最终全截图 verify，`com.example.myapplication.ui.screenshot.*` | **127 项**，14 suites，110 个旧图通过；16 个 E10 缺图 + 1 计数守卫预期失败，0错误/跳过，46秒；16 张候选与审阅副本字节一致 | `screenshot-final/summary.json`、`after/screenshot-verified-20261105.log` |
| `:app:lintDebug :app:assembleDebug :app:compileDebugAndroidTestKotlin --console=plain` | **退出 0**，5分19秒；Lint 0 errors / 55 warnings / 3 hints，无新增警告。实际执行 Lint 分析、dev 打包和 AndroidTest Kotlin 编译；13 tasks executed / 47 UP-TO-DATE；主 Kotlin 复用最终定向测试编译产物 | `quality-final/summary.json`、`quality-verified-20261105.log` |
| 110 张已接纳 PNG | 两轮捕获后 SHA-256 全部不变；最终旧图零像素比较通过 | `accepted-png-hashes-before-p10.txt` |
| 定向 `git diff --check` | 通过；只有仓库既有 LF/CRLF 转换提示 | 本批受控产品和测试文件 |

最终 JVM 完整命令如下。完整 JVM 仍按主计划在人工接纳后执行，本轮 113 项不称为全量 JVM。

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
```

首轮 34 项中 3 项失败均已处理：滑块实际命中只有 44dp，外层 `heightIn` 不会扩大 M3 1.4 内部进度语义区，因此局部把 thumb 最小高度设为 48dp，复验包含顶边真实点击；Profile 测试去掉不属于现有入口的角色假设，并确保避开底栏；保存测试使用规范化的已存 URL 尾斜杠，避免把 API Editor 的既有规范化差异当作外观任务的保存结果。没有改动 VM 或 API 规范化。

首轮质量命令成功但产生 3 条新增 ModifierParameter 警告，已通过收窄标题语义参数和调整私有行参数顺序消除；最终 Lint 回到原有 55 warnings / 3 hints。`quality-initial/` 保留首轮 58 warnings 的原报告，`quality-final/` 为修正后的真实结果。dev 构建验证产物仍为 `Narra-v2.2.12-dev-20212-dev.apk`（未改发版配置），SHA-256 为 `434a8fc46e3b3d4fa8f35126ec74cba9b185595c3153c83145dd1f290b8701d3`，未上传或发布。

## 前后截图与捕获修正

- 最终 `before/manifest.json` 与 `after/manifest.json` 各 16 项：三主题浅深 6、固定动态浅深 2、首页窄屏 200% 2、三个真实 Sheet 浅深 6。每项含 actual 文件、SHA-256、像素尺寸、fontScale、stageDp、target、window。
- 常规为 360×800dp/100%，窄屏为 320×640dp/200%，density 固定 2；6 个 Sheet 的 target 均为 `independent-dialog-root`，捕获真实生产窗口。没有窗口时行为负向测试失败，不回退底页。
- 初版显示截图只把末项文字滚入视口，after 开关落在窗口外。初版留在并列 `before-initial/`、`after-initial/`，不作为当前接纳候选。
- 修正为真实 ScrollAxisRange 到 max，并检查最后整个 Switch 的触控范围在窗口内。before 浅深均为 **264/264px**，after 浅深均为 **670/670px**。另有行为检查证明六个开关标签/实际开关单次更新、相邻命中不重叠。
- 为保持前后同策略，before 在 `D:/p10-capture/before-20261105` 的 detached worktree 重捕。只叠入三个 E10 测试文件及捕获工具，未导入新增共享 API 行为测试；本机配置仅复制 `sdk.dir`，未复制凭据。基点、覆盖文件、来源/目标哈希、复制文件哈希均记录在 `capture-recovery-provenance.json`。
- 受控捕获期间主工作区两个产品文件哈希不变。两阶段各 14 张非显示 PNG 与相应 initial 逐字节一致，只有显示浅深两张改变滚动视口。已接纳 110 张 PNG 未变。
- 随后的 Lint 参数修正把标题专用 Modifier 收窄为局部 `titleIsHeading`，并整理私有行的参数顺序。原 `after` 保留为 `after-pre-lint`；`capture-recovery-provenance.json` 追加 `postCaptureLintParameterFix`，保留原始捕获 source/manifest 哈希并记录最终源哈希。最终 16 张 PNG 与原审阅副本全部逐字节一致，没有静默改写历史来源。
- 复核隔离捕获产物与三个覆盖文件哈希后，使用经过绝对路径校验的 `git -c core.longpaths=true worktree remove --force` 清理该 worktree；目录与 git registry 条目均已不存在，其他工作树未改动。

## 待主会话完成与未验证项

E10 视觉接纳记录、接纳后的完整截图 verify / 全 JVM、具体提交和任务归档由主会话继续。本代理未复制 E10 接纳 PNG。设备测试仅编译；未运行设备 TalkBack、系统返回/窗外触控、IME/系统栏、真实玻璃材质、D01、物理性能或远端 CI，不能由宿主截图与行为结果替代。
