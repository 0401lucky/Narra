# 截图 CI 门禁：job 结构、命令与分工（2026-10-14）

本文件记录 `.github/workflows/android-quality.yml` 的截图门禁接入现状。
结论区分「本机（Windows）已验证」与「远端 CI 已执行」（2026-10-14 首次运行、修复后通过）。

## 1. Job 结构

- `quality`（原有，未改动）：ubuntu-latest + JDK 17；assembleDebug / testDebugUnitTest / lintDebug。
- `screenshots`（新增）：`runs-on: ubuntu-24.04`（固定镜像版本，不用 ubuntu-latest）；
  步骤：checkout → JDK 17（temurin + cache: gradle）→ android-actions/setup-android@v3 →
  chmod +x gradlew → verify 或 record（见下）。
- 不新增密钥、发布权限或外部截图服务；只上传合成 fixture 产物。

## 2. 触发与模式

- push（main/master）与 pull_request：只运行 verify，永不 record。
- workflow_dispatch（手动）：输入 `screenshot_mode`（choice，默认 `verify`）。
  - `verify`：与 push/PR 相同，只比较；
  - `record`：只生成“候选基准” artifact，绝不自动提交仓库，也绝不使用 verifyAndRecord。
- workflow 内注释已写明：初始基准必须在该固定 CI 宿主生成候选后，由人工查看确认，
  再作为独立提交合入；待 CI 宿主首次复跑后才能标记“已确认”。

## 3. 命令

verify（push / PR / 手动 verify 模式）：

~~~bash
./gradlew :app:testDebugUnitTest --tests "com.example.myapplication.ui.screenshot.*" -Proborazzi.test.verify=true --console=plain
~~~

record（仅手动 record 模式；生成候选，不提交）：

~~~bash
./gradlew :app:recordRoborazziDebug --tests "com.example.myapplication.ui.screenshot.*" --console=plain
~~~

与本地记录的验证入口一致（PowerShell 本地需给 -P 参数整体加引号；CI 为 bash 不需要）。

## 4. 产物路径

失败也上传（`if: always()`）的 `screenshot-diagnostics` artifact：

| 路径 | 内容 |
| --- | --- |
| `app/build/outputs/roborazzi/**` | 差异 compare / actual 图（2026-10-14 本机实测生成，见 tooling.md） |
| `app/build/test-results/testDebugUnitTest/**` | JUnit XML（含 Roborazzi 失败断言文本） |
| `app/build/reports/tests/testDebugUnitTest/**` | Gradle 测试 HTML 报告 |
| `app/build/test-results/roborazzi/**` | `results-summary.json`（changed/added/unchanged 计数与 diff_percentage） |
| `app/build/reports/roborazzi/**` | Roborazzi HTML 报告 |

record 模式额外上传 `app/src/test/snapshots/**` 为 `screenshot-candidate-baselines` artifact（仅候选）。

## 5. 基准生成与人工确认流程

1. 手动 dispatch，`screenshot_mode=record`（固定宿主 ubuntu-24.04 + JDK 17）。
2. 下载 `screenshot-candidate-baselines` artifact，人工审阅（含既有缺陷登记）。
3. 确认后作为独立提交把 PNG 放进 `app/src/test/snapshots/` 并更新 manifest.md。
4. 之后 push/PR 只运行 verify；更新基准同样需要预期差异 + 确认人记录。

仓库中的 78 张 PNG 生成于 Windows 本机（见 app/src/test/snapshots/manifest.md）。
2026-10-14 run 38573095548 已在 CI 宿主（ubuntu-24.04 + JDK 17）零阈值 verify 全部通过，
即两宿主渲染逐像素一致，基准对 CI 宿主同样有效；后续更新基准仍需预期差异 + 确认人记录。

## 6. YAML 校验（本机，2026-10-14）

- 解析器：Python 3.11 + PyYAML 6.0.3，`yaml.safe_load` 通过（注：PyYAML 将顶层 `on:` 解析为布尔 True，属 YAML 1.1 行为，不是文件错误）。
- 结构断言通过：quality 步骤列表与改动前一致；screenshots `runs-on=ubuntu-24.04`；
  dispatch 输入 default=verify / options=[verify, record] / type=choice；
  verify 命令与规定完全一致；record 步骤 if 同时要求 workflow_dispatch 与 record；
  诊断上传路径集合正确且 `if: always()`；候选基准路径 `app/src/test/snapshots/**`；
  所有 `run:` 步骤不含 verifyAndRecord。
- 该校验只是本地语法/结构解析，不替代 GitHub Actions 语义校验与远端运行。
- 2026-10-14 修复 commit 073dba6c 后再次解析通过：两个 job 的 setup 步骤均为 `packages: platform-tools`。

## 7. 远端运行记录（2026-10-14）

- 首次 run [38572287401](https://github.com/0401lucky/Narra/actions/runs/38572287401)（commit c5835620）：`quality` 与 `screenshots` 均在 “Set up Android SDK” 失败——android-actions/setup-android@v3 默认安装 `tools platform-tools`，其中旧 `tools` 包已从 SDK 仓库下架（`Failed to find package 'tools'`，外部漂移，与截图内容无关）。截图 verify 未执行，无 artifact。
- 修复 commit 073dba6c：两个 job 显式 `packages: platform-tools`，构建/单测/lint 与截图步骤内容不变。
- 第二次 run [38573095548](https://github.com/0401lucky/Narra/actions/runs/38573095548)（commit 073dba6c）全部通过：
  - `screenshots`：verify 命令 → `finalizeTestRoborazziDebug` → BUILD SUCCESSFUL（5m18s，job 13:35:49–13:41:49 UTC）。78 张 Windows 基准在 ubuntu-24.04 + JDK 17 下零阈值比较全部通过，首次实测确认两宿主渲染一致；`screenshot-diagnostics` artifact（346 KB，191 文件，含 79 个 Roborazzi 结果 JSON）成功上传。
  - `quality`：assembleDebug（5m56s）/ testDebugUnitTest（1m31s）/ lintDebug（4m15s）全部 BUILD SUCCESSFUL。
- 结论：基准已提交并在两个宿主同为有效门禁；无需为 CI 宿主另生成 record 候选。

## 8. 与 quality job 的交互（本机实测）

`quality` job 的裸 `./gradlew testDebugUnitTest` 也会跑到截图测试（无 roborazzi 属性）。
2026-10-14 本机探针（None 模式，H02 类）：退出码 0；基准 mtime 与 SHA-256 不变；
无 actual/compare 产物；results-summary.json 未重写。结论：None 模式不写基准、不比较，
quality job 行为保持原样；计数守卫在 None 模式仍强制基准存在。

## 9. 待办

- [x] 触发远端 CI（push 后）并记录 run URL 与结果（2026-10-14：run 38573095548 通过；首发 38572287401 因 setup-android 外部漂移失败，修复后通过）。
- [x] CI 宿主复验：无需 record 候选——首次 verify 即与 Windows 基准零阈值一致（run 38573095548）；若未来出现宿主偏移，按第 5 节流程重新 record 候选。
