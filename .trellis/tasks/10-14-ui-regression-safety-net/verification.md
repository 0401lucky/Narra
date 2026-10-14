# v2.3 视觉回归安全网：验证记录

日期：2026-10-14（续做会话）。源码基线：main / `276825ba`；本任务已提交 `c5835620`（安全网与 78 张基线）与 `073dba6c`（CI setup 修复）并 push，远端 run [38573095548](https://github.com/0401lucky/Narra/actions/runs/38573095548) 全部通过。
本记录只登记实际执行过的命令与结果；规划、脚本或 YAML 的存在不构成验收通过。

## 验收对照（对应 prd.md 的 A1–A7）

| 项 | 状态 | 依据 |
| --- | --- | --- |
| A1 / S1 覆盖矩阵 | 已通过 | `research/screenshot-matrix.md`：15 个候选界面、首批与后续分批，首批含 P01–P03 与气泡族 |
| A2 / S2–S3 首批确定性用例连续两次通过 | 已通过（Windows 宿主） | 76 个登记用例 + 2 冒烟；verify 两次真实通过（第二次 `--rerun-tasks`，35 tasks 全量执行）；无网络、无真实数据、无随机时间 |
| A3 / S4 1px 差异与缺基准真实失败 | 已通过（Windows 宿主） | 冒烟轮与全量轮各执行一次：1px 注入 exit 1 + compare/actual 产物；缺失基准 exit 1（verify 与计数守卫均失败）；恢复后通过且基准哈希不变 |
| A4 / S5 CI 门禁 | 已通过（远端 run 38573095548） | `screenshots` job（ubuntu-24.04 + JDK 17）零阈值 verify 全部通过（5m18s）；`quality` job assembleDebug / testDebugUnitTest / lintDebug 全部 BUILD SUCCESSFUL。首次 run 38572287401 因 setup-android 默认安装已下架 `tools` 包在 setup 步骤失败（外部漂移），已由 `073dba6c` 修复（两 job 显式 `packages: platform-tools`，步骤内容不变） |
| A5 / S2、S7 初始基准人工确认 | 已完成视觉确认（2026-10-14 用户确认）；CI 宿主已复验一致；代表设备效果移交用户后续真机自测 | 78 张 PNG 位于 `app/src/test/snapshots/`，`manifest.md` 已登记人工确认；run 38573095548 在 ubuntu-24.04 下零阈值复验一致；D01 设备效果移交用户后续真机自测（2026-10-14 决定） |
| A6 / S6–S7 性能证据 | 未完成（保持；移交用户后续真机自测） | 无真机；现有归档全部为合成 fixture（见 `research/performance-baseline.md`）；未运行 S0–S3/F1–F3/N1；不给任何性能结论 |
| A7 质量门禁与范围 | 已通过 | 全量 1613 项 JVM 测试 0 失败/0 跳过、`lintDebug`、`assembleDebug` 通过；产品外观、入口与发布配置无业务变化 |

## 固定环境（唯一运行清单见 `app/src/test/snapshots/manifest.md`）

- 工具：Roborazzi 1.76.0、Robolectric 4.17、`@GraphicsMode(NATIVE)`、`robolectric.pixelCopyRenderMode=hardware`。
- SDK：用例 `@Config(sdk=[35])`，运行时镜像 `android-all-instrumented:15-robolectric-13954326-i7`；应用 compileSdk 36 / minSdk 26。
- 舞台：360×800dp → 720×1600px（density 2）；H04 窄屏 320×640dp → 640×1280px、fontScale 2。
- locale `zh-rCN`、时区 `Asia/Shanghai`、固定 epoch `1791950400000`（2026-10-14 12:00 +08:00）、本地合成图片；代码中无 `System.currentTimeMillis` / `Random`。
- 比较：`SimpleImageComparator(maxDistance = 0f)` + `ThresholdValidator(0f)`（零容忍）。

## 用例与基准

- 分族冻结数量：H01=18、H02=3、H03=3、H04=6、B01=26、B02=4、B03=10、B04=4、C01=2，合计 76；加 2 张冒烟白名单共 **78 张 PNG**（`app/src/test/snapshots/`，约 4 MB）。
- 三方一致由 `ScreenshotBaselineGuardTest` 强制：登记表 ↔ 测试类 `@Test` 数量 ↔ PNG 目录；缺基准、多基准、删除测试方法、零测试均失败（负向探针实测 exit 1）。
- 每个用例文件名带矩阵 ID（如 `H01_p01-messages-matcha-light.png`），映射表见 `manifest.md` 第 4 节。

## 实际执行的命令与结果（Windows 11 + Temurin 21）

| 步骤 | 命令 | 退出码 / 结果 |
| --- | --- | --- |
| record（一次） | `:app:recordRoborazziDebug --tests "com.example.myapplication.ui.screenshot.*"` | 0；79 tests / 0 failed；生成全部 PNG |
| verify ×2 | `:app:verifyRoborazziDebug --tests "..."`、同命令 `--rerun-tasks` | 0；79 tests / 0 failed；35 tasks 真实执行（1m44s / 1m53s） |
| 调色板修复后定向重录（一次，获主会话批准） | 同上 record | 0；19 张按生产派生路径更新（H01 P03 ×6、H02 P03、H03 ×3、H04 P03 ×2、B02 ×4、B04 ×3）；P01/P02、B01/B03、C01、冒烟未变 |
| 修复后 verify ×2 | `:app:verifyRoborazziDebug --tests "..."`、`--rerun-tasks` | 0；79 tests / 0 failed |
| 主会话最终验收 | `:app:verifyRoborazziDebug --tests "..." --rerun-tasks` | 0；35 tasks 真实执行；**78/78 PNG SHA-256 前后零差异**（verify 不写基准） |
| 质量门禁 | `assembleDebug`、`testDebugUnitTest`、`lintDebug` | 全部 0；1613 tests / 0 failures / 0 errors / 0 skipped |

### 负向门禁（真实链路，共三轮）

1. 冒烟轮：注入 1 个不透明像素 → verify exit 1（`is changed`；`results-summary.json` changed=1、diff_percentage=8.68e-7）；删除基准 → exit 1（`original file was not found`，added=1）；恢复后连续两次通过。
2. 全量轮：对 `H01_p01-messages-matcha-light.png` 注入 1px → verify exit 1 并产出 `*_compare.png` / `*_actual.png`；恢复后通过、哈希与备份一致；移除 `H02_p01-messages-empty.png` → verify exit 1 且守卫 exit 1；恢复后通过；全量 78 PNG 哈希前后零差异。
3. 调色板修复轮：对新的 `H01_p03-online-roleplay-matcha-light.png` 注入 1px → exit 1；恢复后哈希与重录清单一致，过滤 verify 与全量 verify 均 0。

### 主题维度真实性（修复后证明）

- P03 `matcha-light` 与 `matcha-dark` 像素差 74,703 px（6.48%）——深浅色维度真实生效。
- 同深浅下三种主题（抹茶/香草/海洋）仍一致：生产 `resolveAppColorScheme` 对 surface 系颜色按深浅统一覆盖，主题只改 primary/secondary，而无背景 P03 外壳不绘制强调色。属**生产行为**，非夹具缺口（已登记，供令牌试点参考）。

## CI 状态与后续

- 新增 `screenshots` job：`ubuntu-24.04` + JDK 17；push/PR 只运行 verify（`testDebugUnitTest --tests "...ui.screenshot.*" -Proborazzi.test.verify=true`），失败仍以 `if: always()` 上传差异报告；`workflow_dispatch` 的 record 模式仅手动、只产出候选基准 artifact、绝不自动提交，且全流程禁用 `verifyAndRecord`。
- run 38572287401（commit `c5835620`）：`quality` 与 `screenshots` 均失败于 “Set up Android SDK”——setup-android 默认 packages 中的旧 `tools` 包已从 SDK 仓库下架（外部漂移，与截图内容无关）；截图 verify 未执行。修复见 `073dba6c`。
- run 38573095548（commit `073dba6c`）通过：`screenshots` 5m18s（`screenshot-diagnostics` artifact 成功上传）；`quality` assembleDebug 5m56s / testDebugUnitTest 1m31s / lintDebug 4m15s。**78 张 Windows 基准在 ubuntu-24.04 下零阈值全部一致**，无需为 CI 宿主另生成候选基准。
- run 38575432534（commit `563af80e`，文档提交）复跑同样通过：`screenshots` 与 `quality` 均 success。
- 若未来宿主偏移（runner 镜像/字体变化）导致 verify 失败：按 `research/ci-screenshots.md` 流程在 CI record 候选 → 人工确认 → 单独提交基准。

## 性能与设备核验

- `adb devices -l` 无物理设备；AVD（Codex_Pixel_7_Large / Pixel_7 / Pixel_9_Pro）仅记录，未启动。
- `baselineprofile/build/outputs/narra-benchmark-runs/`：5 个目录中 a/b/c 为空；d/e 为合成 fixture（commit `d089e0bd` ≠ HEAD、`decidable=false`、仅 `f1ChatScroll`、metrics 为空、perfetto 为 62B 文本）→ 只可作框架自测参考。
- S0–S3 / F1–F3 / N1 与 D01 未执行；**A6 保持未完成**，本任务不给出任何性能回归结论。（2026-10-14 用户决定真机相关项移交用户后续自测；任务归档时保持未完成状态，不写成通过）

## 未覆盖 / 转 D01 设备矩阵

- 真实 Haze 玻璃模糊与背景层级、系统栏/输入法/悬浮窗、真实动态取色、设备字体与动画时序。
- 已知既有缺陷（只登记、未修）：P03 窄屏 200% 字体首条消息被头部遮挡、输入区提示换行；P01 窄屏底栏文字下沿裁切；B02 背景加载失败无占位 UI。

## 剩余风险

- 远端 CI 环境漂移（runner 镜像或 setup-android 动作更新）可能再次引入非截图类失败（本轮为旧 `tools` 包下架）；排查时先看 run 日志与诊断 artifact，不视为截图回归。
- `MessageBubbleScreenshotTest`（冒烟白名单）仍自持手写调色板，不属首批矩阵，未纳入本轮调色板修复。
