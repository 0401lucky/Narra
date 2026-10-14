# 截图 CI 门禁：job 结构、命令与分工（2026-10-14）

本文件记录 `.github/workflows/android-quality.yml` 的截图门禁接入现状。
结论区分「本机（Windows）已验证」与「远端 CI 未执行」。

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

当前仓库中的 78 张 PNG 是 Windows 本机候选（见 app/src/test/snapshots/manifest.md），
尚未经 CI 宿主复现，不得当作 CI 宿主“已确认”基准。

## 6. YAML 校验（本机，2026-10-14）

- 解析器：Python 3.11 + PyYAML 6.0.3，`yaml.safe_load` 通过（注：PyYAML 将顶层 `on:` 解析为布尔 True，属 YAML 1.1 行为，不是文件错误）。
- 结构断言通过：quality 步骤列表与改动前一致；screenshots `runs-on=ubuntu-24.04`；
  dispatch 输入 default=verify / options=[verify, record] / type=choice；
  verify 命令与规定完全一致；record 步骤 if 同时要求 workflow_dispatch 与 record；
  诊断上传路径集合正确且 `if: always()`；候选基准路径 `app/src/test/snapshots/**`；
  所有 `run:` 步骤不含 verifyAndRecord。
- 该校验只是本地语法/结构解析，不替代 GitHub Actions 语义校验与远端运行。

## 7. 远端状态

- **远端 CI 尚未触发**：本地未 push，无任何 run 链接。不得写“CI 已通过”。
- 首次远端触发后需记录：run URL、两个 job 结果、screenshots job 首次 verify 在 ubuntu-24.04 + JDK 17 下的行为（含 Robolectric android-all 下载与缓存）。
- 若首次 verify 失败，先按“宿主差异”排查（渲染/字体差异），失败产物用 artifact 审阅，不得直接 record 覆盖。

## 8. 与 quality job 的交互（本机实测）

`quality` job 的裸 `./gradlew testDebugUnitTest` 也会跑到截图测试（无 roborazzi 属性）。
2026-10-14 本机探针（None 模式，H02 类）：退出码 0；基准 mtime 与 SHA-256 不变；
无 actual/compare 产物；results-summary.json 未重写。结论：None 模式不写基准、不比较，
quality job 行为保持原样；计数守卫在 None 模式仍强制基准存在。

## 9. 待办

- [ ] 触发远端 CI（push 后）并记录 run URL 与结果。
- [ ] CI 宿主首次 record 候选 → 人工确认 → 更新基准（Windows 候选仅限本机）。
