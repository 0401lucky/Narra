# v2.4.1 CI 失败诊断补齐（2026-11-20）

本次只补实际失败后的证据保存，不宣称修复 `packageDebug` 或 JVM 测试根因。
文件所有权限定为两份 workflow 和本报告；应用源码、测试及 Gradle 配置由主会话和其它检查代理负责。

## 已确认的失败与诊断缺口

- [Android Quality 44734382203](https://github.com/0401lucky/Narra/actions/runs/44734382203)：
  独立 `assembleDebug` 的 `:app:packageDebug` 失败，日志只给出
  `PackageAndroidArtifact$IncrementalSplitterRunnable` 包装层，并提示追加 `--stacktrace`。
  当前证据不足以归因内存、并行、签名或缓存。
- [Dev Release 44734464478](https://github.com/0401lucky/Narra/actions/runs/44734464478)：
  主会话已核对原签名 APK 构建成功；随后完整 JVM 为 2956 项、12 项失败。
  失败日志仅列测试类、方法及源码行，Gradle 堆栈只说明测试门禁失败，没有上传 JVM XML/HTML。
  其中 `CompanionEntryViewModelTest` 7 项、`CompanionPendingRecoveryTest` 5 项；
  11 项为 `AssertionError`，`changedSource_rejectsVisiblePendingSaveWithoutRequest`
  为 `IllegalArgumentException`（`CompanionPendingRecoveryTest.kt:133`）。
- 两轮日志均明确指出根工程的 `build/reports/problems/problems-report.html`。
  原始日志保存在 `.tmp/narra-release-241/ci-44734382203/failed-jobs.txt` 和
  `.tmp/narra-release-241/ci-44734464478/failed-jobs.txt`；本改动不把这些临时日志纳入提交。
- 既有截图 job 已正确上传 `screenshot-diagnostics`。主会话核对 221 图为
  207 changed / 14 unchanged；本改动不调整判定、截图用例或基准。

## 实际改动

### `.github/workflows/android-quality.yml`

现有独立 `assembleDebug`、`testDebugUnitTest`、`lintDebug` 与截图 verify 调用
只追加 `--stacktrace`；保留各自任务范围、顺序及截图筛选/verify 参数。

quality job 新增 `Upload quality diagnostics`，条件为 `if: failure()`。
artifact 名为 `quality-diagnostics-${{ github.run_id }}-${{ github.run_attempt }}`，
保存 7 天，`if-no-files-found: ignore`，路径白名单为：

```text
app/build/test-results/testDebugUnitTest/**
app/build/reports/tests/testDebugUnitTest/**
build/reports/problems/**
```

### `.github/workflows/dev-release.yml`

build job 新增 `Upload build diagnostics`，条件同为 `if: failure()`。
artifact 名为 `dev-build-diagnostics-${{ github.run_id }}-${{ github.run_attempt }}`，
保存 7 天，`if-no-files-found: ignore`，路径白名单为：

```text
app/build/test-results/testDebugUnitTest/**
app/build/reports/tests/testDebugUnitTest/**
app/build/outputs/roborazzi/**
app/build/test-results/roborazzi/**
app/build/reports/roborazzi/**
build/reports/problems/**
```

诊断名称按 run/attempt 区分，避免失败 job 重跑时碰撞，也与成功冻结的 APK/
`release-info.json` artifact 分开。仅上传当前合成 runner 的上述报告路径，不捕获
环境、签名文件、私钥或配置；原 `always()` 临时签名清理步骤继续保留。

## 范围核对与验证交接

- 已读取本任务 JSONL、PRD/design/implement、相关质量/发版/网络/日志规范及真实失败日志。
- 已对两份 workflow 的编辑前后文本做规范换行后的定向比对：全部差异仅为四处
  `--stacktrace` 和两段失败诊断上传步骤；其余内容一致。
- 原签名、main 手动触发/版本校验、独立门禁、成功 artifact 字段/路径、R2/七文档/
  Pages 顺序保留；未引入 `continue-on-error`、测试删减或门禁旁路，未执行 record。
- 本代理未运行 Gradle、Git、formatter、设备或任何外部发布；未新增测试文件。
  actionlint、PowerShell 语法及现有发版启动检查由主会话统一执行，本报告不标记其通过。

两份 workflow 的所有权已交还主会话。下一次 CI 由主会话在源码回归修复及 UI 候选
人工确认后统一触发；新增诊断仍需实际失败 run 验证。`packageDebug` 根因继续待完整
堆栈，JVM 竞态修复与视觉接纳另行保留实际证据。
