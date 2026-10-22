# P04 独立审查报告

审查日期：2026-10-22。归属 v2.3；构建仍为 2.2.12-dev。本报告对应当前 P04 实施候选，不表示视觉已接纳、任务已完成或版本已发布。

## Findings (fixed)

未发现需要自修的实质问题；本次审查未修改产品代码、测试、fixture、登记表或基准。

已复核全部 6 个生产变更文件、7 个新增测试 / fixture 文件，以及 `ScreenshotCases.kt`、`UiTokenSourceInventory.kt` 和相关生产消费者：

- `RoleplayScreen.kt` 仅迁移线下快捷入口强调色，实际 `ColorScheme` 在 remember 键中；ONLINE_PHONE 分支和既有回调保持原样。
- `RoleplayScreenContent.kt` 的顶栏 / 恢复入口、提议、立绘区域与 `RoleplayDialoguePanel.kt` 的建议 / 展开操作使用实际可用高度。组合测试直接渲染生产场景，在 320×640dp / 200% 字体下检查整行真实正文、输入位置及独立触控区；没有以几 dp 正数高度替代可读性。
- 对话仍以会话 identity、`uiItemId`、`roleplayLazyContentType` 和原自动跟随状态工作。建议生成 / 使用 / 刷新 / 收起 / 取消、发送 / 停止、提议操作和快捷操作通过真实组件回调覆盖。
- `RoleplayDialoguePanelParts.kt` 迁移的是线下建议 / 空态声明；共享颜色解析未被重写。错误文字的背景确实绘制，建议正文使用对应 panel 内容色；测试取生产 TextLayout 颜色及实际绘制面，包含三主题浅深、固定动态 scheme 和代表皮肤。
- `RoleplayPortraitLayer.kt` 修复与实际说话中状态重叠红测对应。资源请求尺寸、图片来源、Crop 展示意图、说话高亮和既有动画规则保留；信息及状态改为自然纵排并在场景辅助区滚动。
- `RoleplayReadingMode.kt` 的关闭操作位于 LazyColumn 外，阅读正文保留稳定 key、混排、长文滚动、lineHeight 和高对比输入。阅读文字解析与实际 PANEL / DIALOGUE / CARD / THOUGHT / PILL 绘制层配对；panel 最小 alpha 及 panel / dialogue 不额外加深仅作用于阅读调用点，公共玻璃算法未改写。
- 输入、消息气泡、公共 ImmersiveGlass、Longform 及持久化没有本批变更；未改变 13 套皮肤、用户几何 / 尾角 / 玻璃开关或线下玻璃谓词。既有共享 P03、皮肤、几何、材质与滚动测试被纳入本批定向检查。
- `RoleplayNavGraph.kt` 的阅读入口仍在 P05 `saveSettings` 成功回调内；本批未增加旁路或改变保存门槛。
- E04 登记 14 项与 14 个独立截图测试对应；渲染真实 scene / reading。阅读背景 fixture 限定本地 `fixture://e04-reading-backdrop`，等待同源图片 / palette 后还检查生产阅读页露出的实际背景像素；只在安装过合成 Loader 时 reset。原截图舞台和旧图参数未改。
- 源码库存扩至实际迁移声明，移除已迁移建议 / 空态的旧布局例外；透明端点、氛围装饰、玻璃复算端点逐表达式 / 次数登记，无整文件或目录白名单扩张。

## Findings (not fixed)

无尚待修复的代码问题。以下是现有验收边界，不应改写为通过：

- E04 新 14 张尚未人工接纳，缺图和数量守卫继续真实失败；本报告不授权接纳、record 或提交。
- 无连接设备，AndroidTest 只有编译结果；真实读屏、IME、系统栏、安全区、真实材质及物理性能待设备补证。新视觉远端 CI 尚未复跑。

## Verification

生产及测试源码在审查中未改变，因此复用本批已有真实 Gradle 结果；未为审查重复完整测试。已独立读取 XML、构建 / Lint 日志并执行轻量证据检查。

| 项目 | 实际结果 / 证据 |
| --- | --- |
| Lint | PASS；`android-compile-lint-build.log` 的 `lintDebug` exit 0，0 errors / 55 warnings / 3 hints。 |
| TypeCheck / 构建 | PASS；同一日志的 `:app:compileDebugAndroidTestKotlin lintDebug assembleDebug --console=plain` 全部完成，exit 0，3m46s。 |
| 定向 JVM | PASS；独立汇总 `final-targeted-results/TEST-*.xml` 为 63 tests / 0 failures / 0 errors / 0 skipped，与 `final-targeted-summary.json` 一致。 |
| 全截图 verify | 93 项，78 通过、15 个预期失败；独立逐项分类为 E04 14 个 original-not-found 与 1 个缺少 14 张基准的守卫失败，0 个额外失败。证据为 `after/all-screenshot-results/`，命令 exit 1。不能报告成全截图通过。 |
| 已接纳旧基准 | 独立重算 `app/src/test/snapshots/**/*.png` SHA-256，与 `baseline-sha256-before.json` 全量相等，78/78 未变，无新增 PNG。 |
| 迁移前后证据 | 独立重算 `before/manifest.json`、`after/manifest.json` 所列 PNG 哈希，各 14/14 一致；before 未改写。 |
| 定向 diff | 对 6 个生产文件、inventory 和 ScreenshotCases 运行 `git diff --check -- <精确路径>`，exit 0；只有 Git LF/CRLF 提示。 |

本次轻量校验命令通过 Python 标准库只读汇总上述 XML 和 SHA-256，exit 0。没有启动 Gradle、模拟器、网络服务、基准 record、git 暂存 / 提交或任务状态变更。

## 主会话后续

由主会话更新必要的 `ui-token-contract.md` 范围与 P04 调用点契约、版本 / 验证文档，并继续独立展示视觉候选供人工确认。不能把本次代码审查通过写成 OA5 的视觉接纳已完成；后续只有接纳后的真实 verify 才能关闭缺图门禁。
