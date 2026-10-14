# 视觉回归安全网：实施计划

## Pre-Start Gate

- [x] PRD、design、本计划和两份 JSONL 通过路径校验（2026-10-14，JSONL 各 11 条）。
- [x] 最新规划摘要已展示，用户在 2026-10-14 续做消息中明确批准本子任务实施。
- [x] 主会话运行 task.py start，仅启动本子任务；不启动父任务或令牌草案。（2026-10-14 核验：task.py current 指向本子任务且 status=in_progress；父任务与令牌草案仍为 planning）
- [x] 实现代理提示以 Active task: .trellis/tasks/10-14-ui-regression-safety-net 开头，按 JSONL 获取上下文；遵守文件所有权，不回滚他人修改。（2026-10-14 第二轮派发满足；未回滚任何既有改动）

## Ordered Checklist

### 1. 最小兼容性验证

- [x] 记录当前工作区、源码、JDK、Gradle、AGP/Compose 与已安装宿主运行环境。
- [x] 查官方发布版本，固定 Roborazzi/Robolectric 依赖，保留既有主工具链。
- [x] 用一个普通真实组件跑通 record/verify，再用真实扮演气泡检查 Native Graphics/玻璃边界。
- [x] 核实测试筛选与基准输出目录，确保原有 JVM 测试集合不被改变。
- [x] 在固定宿主连续验证；将具体版本、SDK、参数、成功/失败结果写入 research/tooling.md（2026-10-14 本机 Windows + JDK 21 实测通过）。
- [x] 若现有组合确实无法满足目标，提供失败证据并更新方案，不扩大为全局工具升级（组合可用，未触发）。

### 2. 合成场景与首批基准

- [x] 依据 screenshot-matrix.md 实现 P01/P02/P03 及 H/B/C 用例族，真实组件不复制、不使用真实资料（2026-10-14，76 张 Windows 候选基准，见 app/src/test/snapshots/manifest.md）。
- [x] 优先复用测试 Fake；AssistantListScreen 如必须拆可测性入口，仅做保持行为的提取并验证原有交互（复用 createTestAiServices + FakeSettingsStore，构造真实 SettingsViewModel，未做状态提取）。
- [x] 固定日期、图片、字体、动画时刻、色板与尺寸；截图前确认内容和根节点。
- [x] 记录初始候选图，列明当前已有视觉缺陷，等待人工确认后纳入批准基准。
- [x] 标记宿主无法覆盖的项及原因，准备 D01 设备回归，不偷偷降低矩阵。

### 3. 验证机制与 CI

- [x] 严格零阈值验证：同样输入连续两次通过。（2026-10-14 首轮：verify 两次，第二次 --rerun-tasks 全量真实通过；见 manifest.md 第 2 节）
- [x] 在临时副本注入 1px 差异，真实 verify 非零；缺失一张基准也非零；恢复后通过且基准哈希不变。（2026-10-14 第二轮登记用例实测：H01 注入/恢复、H02 缺失/恢复、守卫失败；78 张 PNG 前后 0 差异；见 tooling.md「负向门禁/验证机制」）
- [x] 用例清单与结果计数相符，零测试、缺场景不能被当成成功。（ScreenshotBaselineGuardTest 冻结分族计数；缺基准实测失败；--tests 过滤为空时 Gradle 直接失败）
- [x] 新增 screenshots CI job，保留原 quality job，失败也上传合成数据产生的差异报告。（android-quality.yml：ubuntu-24.04，verify + if: always() 诊断产物；YAML 解析通过；远端首发失败于 setup-android 外部漂移，修复后 run 38573095548 通过）
- [x] 记录基准生成/人工接受/验证命令，确认 CI 不自动 record。（research/ci-screenshots.md：record 仅限手动 workflow_dispatch，不提交、不使用 verifyAndRecord；人工确认与 CI 宿主复现已完成——run 38573095548 零阈值通过）
- [x] 记录远端 CI run：run 38572287401（`c5835620`）两 job 于 setup-android 外部漂移失败，修复后 run 38573095548（`073dba6c`）screenshots 与 quality 全部通过；详见 verification.md 与 research/ci-screenshots.md。

### 4. 设备与性能证据

- [ ] 检查当前可用设备，记录设备类型；模拟器只做 UI/fixture smoke。（2026-10-14 已记录：adb 无物理设备，仅 AVD Codex_Pixel_7_Large / Pixel_7 / Pixel_9_Pro；模拟器 smoke 未执行；2026-10-14 用户决定 D01 移交用户后续真机自测）
- [ ] 执行玻璃、背景保留、窄屏大字体与相关既有交互测试。（未执行：无真机且模拟器不在本轮范围；宿主侧合成基准不能替代设备；移交用户后续真机自测）
- [ ] 核验现有性能归档的提交、工具、Profile 与环境；不匹配则只作历史参考。（2026-10-14 已完成核验：d/e 为 commit d089e0bd 的合成 fixture、decidable=false，与 HEAD 276825ba 不可比，仅历史/框架参考；见 research/performance-baseline.md）
- [ ] 具备固定真机和温控条件时运行现有完整场景并独立归档，更新 performance-baseline.md。（未执行：无固定物理设备与温控条件；移交用户后续真机自测补录）
- [ ] 缺设备、Profile 或可比环境时保留 A6 未完成；不得宣称全验收或无性能回归。（A6 保持未完成；未给出任何性能结论；归档时移交用户后续真机自测）

### 5. 质量与交付

- [x] 运行相关测试与既有三项质量门禁，必要时编译 AndroidTest。（2026-10-14 主会话：assembleDebug / testDebugUnitTest 1613 项 0 失败 0 跳过 / lintDebug / :app:compileDebugAndroidTestKotlin 全部通过；截图 verify --rerun-tasks 真实执行通过且 78/78 基准哈希不变）
- [x] trellis-check 代理审查代码、真实用例、负向门禁及兼容边界；相关失败修复后复验。（2026-10-14 全范围检查完成：守卫增加 @Test 数量校验、更正覆盖归因、新增质量规范截图回归（Roborazzi）小节；随后调色板 fixture 修复轮重录 19 张并复验通过）
- [x] 主会话更新验证记录、版本记录和必要规范，审阅实际 diff。（verification.md、版本功能添加/优化.md、quality-guidelines.md 与 research/* 已更新；产品代码唯一改动为 deriveImmersiveGlassPalette 可见性 private 改 internal）
- [x] 仅安全网完成范围可登记完成；未通过的人工、设备、CI、性能项保留状态。（A5 视觉确认与远端 CI 首次运行已于 2026-10-14 完成；A6 真机性能与 D01 设备效果仍保持未完成/未执行，未写成通过）
- [x] 把可审阅基准交给令牌试点，后续任务独立完成视觉决策和实施批准。（基准已于 2026-10-14 人工确认，位于 app/src/test/snapshots/，可直接作为令牌试点对照；试点任务保持 planning，待用户启动）

## Validation Commands

以下截图命令已于 2026-10-14 在 Roborazzi 1.76.0 下核对任务名并实测（Windows 本机，见 research/tooling.md）；CI 宿主已于同日复跑通过（run 38573095548）。PowerShell 中 `-P` 参数必须整体加引号；插件任务默认依赖该 variant 全部单测，必须带 `--tests` 过滤。已核对任务名：recordRoborazziDebug / verifyRoborazziDebug / compareRoborazziDebug / verifyAndRecordRoborazziDebug / clearRoborazziDebug / finalizeTestRoborazziDebug。

~~~powershell
python ./.trellis/scripts/task.py validate .trellis/tasks/10-14-ui-regression-safety-net
.\gradlew.bat :app:recordRoborazziDebug --tests "com.example.myapplication.ui.screenshot.*" --console=plain
.\gradlew.bat :app:verifyRoborazziDebug --tests "com.example.myapplication.ui.screenshot.*" --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.myapplication.ui.screenshot.*" "-Proborazzi.test.verify=true" --console=plain
.\gradlew.bat :app:compileDebugAndroidTestKotlin --console=plain
.\gradlew.bat assembleDebug --console=plain
.\gradlew.bat testDebugUnitTest --console=plain
.\gradlew.bat lintDebug --console=plain
~~~

record 只生成候选，初始人工确认后常规流程仅运行 verify；不能在每次 verify 前 record。本机与固定 CI 宿主分别记录环境，避免误用基准。单测按项目规范分组执行并记录超时，构建耗时不冒充测试执行结果。

具备真实设备条件时复用现有入口，不修改全局设备配置：
~~~powershell
.\gradlew.bat :app:verifyNarraBaselineProfile --console=plain
$uiBaselineRunId = "ui-before-" + (Get-Date -Format "yyyyMMdd-HHmmss")
.\gradlew.bat :baselineprofile:runAndArchiveNarraBenchmarks "-PnarraBenchmarkRunId=$uiBaselineRunId" --console=plain
~~~

一次完整运行使用同一个 runId；前后比较各自独立归档。执行前核实目标为 baseline 测试包与固定物理设备，准备过程不接触用户 dev/release 数据。
