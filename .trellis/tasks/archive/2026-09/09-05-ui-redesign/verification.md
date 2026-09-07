# UI 重设计最终验证

## 环境与边界

- 执行时间：2026-09-06 至 2026-09-07。
- 雷电：Android 14，`emulator-5554`，1440×2560，640 dpi，约 360×640dp。
- 开始时已安装 `com.narra.app.dev` 2.2.10-dev / 20210，正常打开消息主壳。
- 保留既有应用数据；不清除数据、不修改产品导航、不恢复普通 Chat 或独立 Translator 的日常入口。
- UI 验证使用 2.2.10-dev 包；2026-09-07 用户将本轮工作指定到 2.2.11-dev，并授权构建、GitHub 和内置更新发布。
- 系统悬浮权限初始为 `SYSTEM_ALERT_WINDOW: default`。不擅自扩大权限；优先验证受 Activity 托管的实际窗口/控件、权限缺失分支及截图，跨应用悬浮和屏幕捕获端到端结果单独记录。
- 截图临时目录：`%TEMP%/narra-ui-redesign-final-20260906`。原始截图用于检查，不纳入源码提交。

## 最终覆盖矩阵

| 页面组 | 关键入口和交互 | 本轮验证状态 |
| --- | --- | --- |
| 主壳与个人资料 | 消息/通讯录/发现/我、悬浮导航、面具切换、资料库、会话与角色入口 | 终审通过；最终包查看身份、面具展开、资料入口，原有计数保留 |
| Roleplay | 在线/场景、侧栏、场景管理/编辑、角色定制、输入与菜单 | 对应 UI 用例通过；删除和选择角色的旧定位修正后复验通过 |
| Roleplay 子页 | 日记/详情/阅读、视频/语音、信箱、钱包 | 保留前轮检查，日记/视频/系统栏等现有用例本轮通过 |
| 设置与提供商 | 主页、显示/主题、更新、提供商/模型/能力、保存防重 | 现有 UI 用例通过，最终包查看主页和实际切换深色模式 |
| 角色与数据 | 角色列表/基本资料/提示词/扩展/图工作台、面具、世界书、预设、记忆、资料迁移 | 对应用例通过，实际打开角色/世界书/记忆/预设/资料库；未执行真实导入或删除 |
| 语音与工具 | 识别/API/TTS、脚本、合规、诊断 | 对应 UI 用例通过，保留各自配置与回调 |
| Moments | 全局/会话时间线、发布配图、点赞、回复、删除/批量清空、封面、空态与刷新 | 7 项 UI 用例通过；实际打开空态、编辑器和系统照片选择器并取消 |
| PhoneCheck | 分类、历史内容、搜索详情/失败重试、局部刷新确认、信箱入口、空态 | 6 项 UI 用例通过；实际会话选择与空态入口验证 |
| 欢迎页/上下文日志 | 配置状态/入口、日志开关/容量/列表/详情/复制/导出/删除 | 10 项用例通过，最终截图确认状态换行与文字对比度 |
| 系统悬浮翻译 | 浮球、选区操作、加载/原文/译文/错误、复制/关闭、主题与窗口约束 | 8 项原生窗口用例通过；独立设置页 2 项通过，系统端到端边界见下文 |
| 共享弹窗与说明 | SettingsHelpButton、长内容滚动、固定关闭/确认、大字体、外部点击与返回 | 4 项共享弹窗及现有说明入口用例通过；截图确认长文和固定操作区 |

## 已处理的审计发现

- 欢迎页仍由 Settings 在配置不完整时导航进入，已纳入大字体/窄屏回归。
- 上下文日志由 Settings 与 Roleplay 侧栏访问，已补齐列表、详情和旧快照阅读。
- `ScreenTranslationSettingsScreen` 当前只有定义，没有注册或调用方；不通过重设计恢复产品入口。服务/controller 必须独立验证。
- PhoneCheck 已移除系统深色判断/固定蓝色，改为可滚动分类栏，补搜索重试和空态。
- Moments 已统一主题与触控区；快速发布使用独立成功标识，失败保留草稿。
- 共享 `NarraAlertDialog` 已统一滚动与固定操作区，并验证外部点击/返回不会确认删除。
- 悬浮翻译帮助切换已重置阅读偏移；语言保存、翻译和授权结果共享取消边界。

## 执行结果

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| `:app:assembleDebug :app:assembleDebugAndroidTest :app:compileDebugUnitTestKotlin :app:lintDebug` | 通过 | `integrated-build-lint-3.log` |
| 最终 UI 修正后的 `assembleDebug assembleDebugAndroidTest bundleDebugClassesToRuntimeJar lintDebug` | 通过，退出码 0 | `device-fixes-build-lint.log` |
| `:app:testDebugUnitTest --console=plain` | 1526 项通过，0 失败/错误/跳过；41 秒 | `unit-tests.log`、`unit-test-summary.json` |
| 全 UI 与原生悬浮设备用例 | 首轮 97 项，93 通过、4 失败 | `device-tests.log`、`instrumentation-classes.txt` |
| 失败修正及截图反馈复验 | 23 项全部通过，39.786 秒；原 4 项失败清零 | `device-retest.log`、`instrumentation-retest-classes.txt` |
| Android Lint | 0 errors、55 warnings、3 hints | `app/build/reports/lint-results-debug.html` |
| 安装 | App 和测试 APK 均 `Success`，`adb install -r` 保留数据 | 原安装时间仍为 2026-06-12 |

日志与截图均在上面的临时目录。JVM 首次命令在测试前准备阶段触及 60 秒上限，
没有将其报告为通过；先单独完成运行时 JAR 准备后，再在上限内取得上述完整通过结果。
`device-test-summary.json` 按测试类与方法合并两轮结果：97 个唯一用例的最新结果均为通过，剩余失败为 0。

## 截图检查

- `device-views-final/` 共 40 张设备测试截图：Moments、PhoneCheck、三主题深浅色、
  1.6 倍字体、短窗口、悬浮状态/说明、确认弹窗、欢迎页、上下文日志和视频。
- 已逐组查看原图和 `moments-theme-review.png`、`phone-theme-review.png`、
  `overlay-theme-review.png` 对照图；修复后重新查看欢迎页、日志详情与悬浮帮助。
- 实际 App 截图包括 `device-profile-final.png`、`device-settings-final.png`、
  `device-settings-dark-final.png`、`device-moments-dark-final.png`、
  `device-characters-dark-final.png`、`device-worldbooks-dark-final.png`、
  `device-memory-dark-final.png`、`device-presets-dark-final.png` 和 `device-transfer-dark-final.png`。
- 代表性长名称、分类、正文、确认/关闭按钮没有发生重叠；滚动截图中离开视口的内容
  属于正常裁剪。测试修复了帮助继承旧滚动位置、欢迎页单字换行和浅色日志标签偏淡。

## 验证边界

- 原生悬浮测试使用真实 Activity 的 `TYPE_APPLICATION_PANEL`，没有授予新的系统悬浮权限。
  跨应用悬浮、MediaProjection/OCR、真实 AI 网络翻译及 OEM 后台限制未做端到端实测。
- 短窗口与 320/360dp、1.6 倍字体由测试提供；不等于已在真实横屏或每款手机上验证。
  当前雷电使用硬件键盘，已检查输入与空间约束，真实软键盘遮挡仍需对应设备环境复验。
- 系统照片选择器已打开/取消；未发布测试动态，未删除用户会话或真实资料。
- 视觉检查临时切换的深色模式已恢复并保存为原来的“深海幽蓝 / 跟随系统”。
- 用户已于 2026-09-07 确认发布 2.2.11-dev；本文件保留 UI 回归包的历史证据，正式发包信息以 `docs/发布候选记录.md` 为准。

## 安装包

- 路径：`app/build/outputs/apk/debug/Narra-v2.2.10-dev-20210-dev.apk`
- 包名：`com.narra.app.dev`；版本：`2.2.10-dev` / `20210`。
- SHA-256：`2D1D706EE342BAA34CBE1DA57F9BAC3688580CAA0C8E4F4D6F8ACBB3F6ECC245`。

实现、验证记录及规范已在 `7b7903a1` 提交。用户于 2026-09-07 明确决定不再补做真实跨应用悬浮/截屏和软键盘检查，要求直接归档并发布 2.2.11-dev；这些未实测条件仅作为已接受的验证边界保留。
