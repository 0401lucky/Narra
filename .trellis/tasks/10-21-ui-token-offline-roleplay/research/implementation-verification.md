# P04 实施交接与验证证据

归属 v2.3；本批实施批准来自用户在四批规划摘要后回复“可以”。本文件只记录实现代理实际执行的结果，不代替独立 check、人工视觉接纳、设备或远端 CI。

## 实施范围

- `RoleplayScreen.kt`：线下快捷项随实际 ColorScheme 使用语义角色，remember 包含 scheme；未改回调与业务状态。
- `RoleplayScreenContent.kt`：线下顶栏 / 恢复 handle / 记忆提议消费令牌；标题、返回、设置有独立 48dp 命中；次要立绘与提议共享基于页面高度的有界滚动区。
- `RoleplayDialoguePanel.kt`：对话区默认令牌、明确测试标签、建议及快捷展开高度按实际页面约束分配；保留原 identity、uiItemId、contentType、发送和跟随策略。
- `RoleplayDialoguePanelParts.kt`：建议实际承载面成对配色，错误背景确实绘制；生成 / 使用动作命名、按钮角色、最小命中；有界可滚动建议、可换行标签 / 操作和可读空态。
- `RoleplayPortraitLayer.kt`：仅因实际红测证实 200% 字体的说话中标签覆盖角色信息而纳入。文字 / 状态自然纵排并可在场景辅助区滚动；原立绘资源 / 强调 / 动画规则保留。
- `RoleplayReadingMode.kt`：固定关闭入口与滚动正文分离，独立阅读几何显式消费令牌；基于实际玻璃层解析文字 / 强调色。浅色正文加深叠加失败后，仅在阅读 panel / dialogue 调用点取消额外加深，保留玻璃 wrapper、sheen、背景、安全区、行高与高对比输入。

没有改共享 InputBar、Bubble、ImmersiveGlass、Longform、导航保存门槛、持久化 / 皮肤枚举 / schema / Provider / 状态机。用户保存气泡几何、尾角、线上玻璃开关原值和线下玻璃谓词保留；13 皮肤解析与边界几何已有测试纳入最终定向回归。

## 测试与库存

新增 `OfflineRoleplayFixture`、`OfflineRoleplayRenderTest`、`OfflineRoleplayScreenshotE04Test`，每项 E04 登记对应独立 `@Test`，共 14 项。真实页面使用生产 `RoleplaySceneContent` / `RoleplayReadingMode`；截图宿主 / 零阈值比较 / 原78项参数和基准没有改变。

新增四个行为测试类：`OfflineRoleplayAccessibilityTest`（9）、`OfflineRoleplayColorsTest`（5）、`RoleplayReadingModeAccessibilityTest`（4）、`RoleplayReadingModeColorsTest`（3）。真实 TextLayout 与实际绘制像素检查三主题浅深、固定动态浅深、NOIR / LIQUID_GLASS、场景 mood scrim / 玻璃、建议错误面、阅读混排面；组合布局要求至少一整行 200% 正文可读，不以正数高度薄带代替可用性。

`UiTokenSourceInventory` 新增精确场景 / 对话 / 立绘 / 阅读声明范围；已迁移建议与空态的 legacyLayout 清单删除。氛围装饰色、透明端点和实际玻璃合成黑白端点精确登记用途与次数，未扩大白名单。`RoleplayScreenCallbacks` 是同前缀既有文件，显式登记以保持新增同前缀文件检测有效。

阅读背景 fixture 用测试限定 `fixture://e04-reading-backdrop` 的 Coil Interceptor 返回固定本地合成图，不发网络请求。等待同源加载 / palette 就绪后，额外采样生产阅读页露出的蓝色色带；不能只以旁路 probe 成功代替页面实际绘制。声明 `DelicateCoilApi` opt-in，仅本测试安装过 synthetic Loader 才在 `@After` reset；后续同一全截图 invocation 的旧78项全部通过，未污染旧图。

## 迁移前后证据

- `before-probe.log`：单项 verify 退出 1，真实页面尺寸 / 内容守卫通过，因 original PNG 缺失失败，并生成 actual。
- `before-all.log`：首轮 14 项缺图 actual；目视发现阅读背景捕获过早，首次 fallback 证据保留在 `before-initial-fallback/`。
- `before-background-probe.log`：显式等待图片的探针超时，未将旧 actual 误报为背景成功；改为限定 URI 的合成响应。
- `before-background-probe-2.log`：背景真实绘制断言通过，仍仅因基准缺失失败。
- `before-final-and-layout-negative.log`：14 项 original-missing，加 1 项真实大字组合布局红测（更多玩法不在可视范围），退出 1。
- `before/`：最终迁移前 14 个稳定 E04 ID PNG、manifest（SHA-256 / 宿主 / 参数）、verify XML；在任何产品 Kotlin 改动前冻结。首轮 fallback 另存，没有覆盖掉历史证据。
- `portrait-negative.xml`：角色 fallback Rect 与说话中 Rect 真实重叠；修复后同测通过。
- `p04-2-negative.log`：2 项阅读红测，长文滚后关闭不可见、引号正文对比失败；之后均转绿。
- `p04-2-checkpoint.log` / `p04-2-dynamic-probe.log`：实际浅色用户阅读面叠层加深引发对比失败，没有降 4.5 阈值；阅读调用点修正后转绿。
- `after/`：迁移后 14 个 PNG、manifest、verify XML、全截图结果与 summary。未向 `app/src/test/snapshots` 增加任何 PNG，也未 record / verifyAndRecord / 自动接纳。

冻结脚本 `capture_evidence.py` 只复制 verify actual 到 task research，并校验旧78图的原始 SHA-256；`before` 目录不可覆盖。最终 after 冻结再次校验旧78全部未变。主会话也已独立复核最终 before14 参数 / 哈希（`before-main-check.json`）。

## 已执行的最终检查

| 检查 | 实际结果 | 证据 |
| --- | --- | --- |
| P04.1 定向行为 / 共享 P03 / 主题 / 源码守卫 | 42/42 通过，exit 0，53s | `p04-1-checkpoint.log` |
| P04.2 行为 / 配色 / 守卫 / 稳定 key | 21/21 通过，exit 0，39s | `p04-2-checkpoint-fixed.log` |
| 最终定向 JVM 集成 | 63/63 通过，0 skipped，exit 0，33s | `final-targeted.log`、`final-targeted-summary.json`、`final-targeted-results/` |
| 全截图 verify（包含既有及 E04） | 93 项实际执行：旧78通过；E04新14 original-missing、计数守卫缺14图，合计15个预期失败，exit 1，53s | `after-all-screenshots.log`、`after/all-screenshot-results/`、`after/screenshot-summary.json` |
| AndroidTest 编译 / Lint / dev 构建 | 全部通过，exit 0，3m46s；60 tasks（13执行、47 up-to-date），Lint 0 errors / 55 warnings / 3 hints | `android-compile-lint-build.log` |
| 定向 `git diff --check` | exit 0（仅 LF/CRLF 工作区提示） | 实现代理命令记录 |
| SDK adb devices -l | 无连接设备 | `C:/Users/lucky0401/AppData/Local/Android/Sdk/platform-tools/adb.exe devices -l` |

最终 JVM 命令为 `:app:testDebugUnitTest --tests ... --console=plain`，选择 final-targeted-summary 中的12个测试类；没有重跑完整试点1642单测。截图命令为 `:app:verifyRoborazziDebug --tests 'com.example.myapplication.ui.screenshot.*' --console=plain`；对应 `testDebugUnitTest` 确实执行，未以 UP-TO-DATE 冒充截图比较。

Android 编译构建命令：`.\gradlew.bat :app:compileDebugAndroidTestKotlin lintDebug assembleDebug --console=plain`。Gradle 全程单进程串行调度，不同时启动另一 Gradle；继续读取原 session，未因长运行重启。

## 交接与限制

新14张未人工接纳，缺图和计数守卫继续真实失败；整体视觉验收保持未完成。主会话 / check 应审核所有候选及共享旧图结果，确认后才单独接纳基准 / manifest 并重新 verify。

设备测试只有编译，宿主交互不代表真机读屏、IME、安全区、真实玻璃或物理性能。远端新视觉 CI 未执行。构建配置仍是 2.2.12-dev，不能宣称 v2.3 已发布。实现代理未做 git 暂存 / 提交 / 推送、任务状态变更、spec / 版本 / dev.json / CI / 其他批次变更；未回滚任何并发用户文件。
