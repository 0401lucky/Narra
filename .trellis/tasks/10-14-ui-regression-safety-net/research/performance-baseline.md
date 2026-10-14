# 性能基线登记与归档核验

（2026-10-14 第二轮核验。结论区分「已完成核验」与「未执行」；本任务不给出任何性能回归结论。）

## 结论速览

- 当前源码：HEAD 276825ba（2026-09-08，2.2.12-dev），工作区 dirty（见下）；本任务未改动任何产品性能相关代码。
- 本机本轮无物理设备（adb 为空），未运行 S0–S3 / F1–F3 / N1，也未运行 D01；模拟器不在本轮范围。
- 现有归档 `baselineprofile/build/outputs/narra-benchmark-runs/` 只有合成 fixture（归档自测产物）：
  commit d089e0bd ≠ HEAD，decidable=false，缺失 11 个场景 → 只可作历史/框架自测参考。
- **A6 保持未完成**：无真机、无温控、无可比 Profile 环境；不得宣称安全网全验收或 v2.3 性能无回归。

## 当前源码与工作区状态（2026-10-14）

- HEAD：`276825bafb49308dd202dab1c424ab0c638bebea`（chore(release): 发布 2.2.12-dev 气泡与评论优化，2026-09-08 21:16:59 +08:00）。
- 工作区 dirty（截图安全网改动尚未提交）：
  - 已修改：`app/build.gradle`、`build.gradle`、`gradle.properties`、`gradle/libs.versions.toml`、`.github/workflows/android-quality.yml`、`版本功能添加/优化.md`；
  - 新增未跟踪：`app/src/test/java/com/example/myapplication/ui/screenshot/`、`app/src/test/snapshots/`、本任务 `.trellis` 目录等。
- 性能归档与当前源码不可比：归档生成时 commit 为 d089e0bd（2026-08-07），与 HEAD 相差多个提交。

## 设备检查（2026-10-14）

| 检查 | 结果 |
| --- | --- |
| `adb devices -l` | 无物理设备连接（List of devices attached 为空） |
| `emulator -list-avds` | Codex_Pixel_7_Large、Pixel_7、Pixel_9_Pro（仅记录，供后续 D01 参考；本轮未启动任何模拟器） |

→ 条件不满足：不执行 S0–S3 / F1–F3 / N1 与 D01，不写任何性能结论。

## 归档核验明细（baselineprofile/build/outputs/narra-benchmark-runs/）

目录共 5 个 run；a/b/c 为空目录；d/e 为合成 fixture：

| runId | commit | 归档时间（+08:00） | decidable | 用例 | 判定 |
| --- | --- | --- | --- | --- | --- |
| synthetic-run-20260811 / …b / …c | -（空目录） | - | - | - | 无任何文件 |
| synthetic-run-20260811d | d089e0bd6ffd1ba7eba8a4ff15e9de97fefafb98 | 2026-08-11 09:59:14 | false | 仅 f1ChatScroll | 合成 fixture，仅历史/框架参考 |
| synthetic-run-20260811e | d089e0bd6ffd1ba7eba8a4ff15e9de97fefafb98 | 2026-08-11 09:59:47 | false | 仅 f1ChatScroll | 合成 fixture，仅历史/框架参考 |

合成性质证据（d/e 一致）：

- `trace-summary.json`：`decidable=false`；missingBenchmarkCases 11 条（s0–s3 八条、f2RoleplayScroll、f3RoleplayOfflineStreamReplay、n1SettingsAndReadingNavigation）。
- `raw/benchmark/com.narra.app.baseline-benchmarkData.json`：唯一 benchmark `f1ChatScroll`，`metrics: {}`（无任何测量数值）。
- `raw/perfetto/synthetic.perfetto-trace`（62 字节）内容为文本 `synthetic perfetto fixture for host-side archive verification`。
- environment manifest 仅有 runId / benchmarkName / scenarioId / iterations=5，无设备、系统、刷新率、温控信息。
- `sha256sums.txt` 与 run-manifest files 段 2026-10-14 复核：d/e 各 5 个文件哈希全部匹配（内部完整性 OK，不改变“合成 fixture”判定）。
- 该目录位于 build 输出且未被 git 跟踪（`git ls-files baselineprofile/build/outputs` 为空）；来源为 baselineprofile 的归档自测任务（archiveNarraBenchmarkResults / verifyNarraBenchmarkArchive 路径）。

## 场景登记（全部未执行）

| 场景 | 编译模式 / 次数 | 状态 |
| --- | --- | --- |
| S0 首次、S1 返回、S2 多消息、S3 升级冷启动 | None / Require Baseline，每种 10 次 | 未执行（无固定物理设备） |
| F1 Chat 滚动、F2 RP 滚动 | Require Baseline，各 5 次 | 未执行（无固定物理设备） |
| F3 RP 离线流式回放 | Require Baseline，5 次 | 未执行（无固定物理设备） |
| N1 设置 / 阅读导航 | Require Baseline，5 次 | 未执行（无固定物理设备） |
| Profile 生成、安装与应用规则 | 固定真机及当前源码 | 未执行（无固定物理设备与可比环境） |

归档元数据：androidxBenchmarkVersion=1.5.0-alpha03，fixtureVersion=narra-fixture-v1（仅说明自测框架版本，不构成当前源码的测量证据）。

## 合格记录必须包含

提交与 dirty diff 身份、APK/工具版本、runId、物理设备与 API、刷新率、动画缩放、电量/温控、
fixture 身份、编译模式、迭代次数、Profile 状态、原始 JSON/Perfetto/环境清单路径和哈希。

先验证既有归档与当前源码是否可比；旧版本数据只能保留为历史参考，不能改标签冒充新版本基准。
新运行写独立 runId，不能覆盖旧产物。基准工具版本在前后比较期间冻结。

## A6 为何保持未完成

- 无固定物理设备与温控条件；模拟器数据不能替代（项目规范明确）。
- 现有归档与 HEAD 不一致且为合成 fixture，不能作为 v2.3 对照。
- 性能比较前需先登记指标、噪声范围与回归判定，再做重复测量；当前无任何有效真机测量。
- 因此本任务只交付宿主侧截图门禁证据；A6 与“安全网全验收”保持未完成，也不输出性能回归结论。
