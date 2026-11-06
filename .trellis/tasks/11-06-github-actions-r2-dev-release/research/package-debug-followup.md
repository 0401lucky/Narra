# packageDebug 失败后的构建顺序与签名预检调整

## 已知证据与限制

- [Dev Release 42181383716](https://github.com/0401lucky/Narra/actions/runs/42181383716)，源码 `acb51b748dc481ba32b6f7daccfc10089347c305`：Linux 原证书校验及两种 PowerShell 启动检查通过，随后合并请求 `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` 在 `:app:packageDebug` 失败。
- 最终日志只有 `PackageAndroidArtifact$IncrementalSplitterRunnable` 包装层失败，没有底层异常或堆栈。任务时间重叠不是内存或并行冲突的证明，当前根因仍未确定。
- 同源码 [Android Quality 42181377835](https://github.com/0401lucky/Narra/actions/runs/42181377835) 已使用独立 assemble → JVM → lint 调用完成全部门禁及截图；此前 [42178000109](https://github.com/0401lucky/Narra/actions/runs/42178000109) 也成功。主会话提供上述真实 CI 状态。
- 只读 `git diff 95460b58 acb51b74` 确认 `app`、`baselineprofile`、Gradle 配置 / wrapper 和普通 Android Quality workflow 无差异。

## 请求任务敏感逻辑检查

- `app/build.gradle:182` 只识别 `generateDebugComposeCompilerReportArchive` / `generateBaselineComposeCompilerReportArchive`；普通三个任务都不会触发正式报告分支或其强制重编译设置（`:300`）。
- `app/build.gradle:512` 只在请求 `verifyNarraBaselineProfile` 时禁用相关 profile task 的缓存；本次没有该请求。
- APK 命名由 `androidComponents.onVariants` 的 buildType 决定（`:479`），注解处理列表 workaround 按 debug Java 编译任务绑定；没有发现按这三个普通任务是否组合请求切换 APK 变体、签名或输出配置的仓库代码路径。
- `gradle.properties` 虽启用了配置缓存与并行，但现有日志不能证明它们导致了本次失败；没有改这些选项或重置构建状态。

## 本次调整

1. `.github/workflows/dev-release.yml` 改为四个独立步骤 / Gradle 调用：

   ```bash
   ./gradlew :app:assembleDebug --console=plain --stacktrace
   ./gradlew :app:testDebugUnitTest --console=plain --stacktrace
   ./gradlew :app:lintDebug --console=plain --stacktrace
   ./gradlew :app:testDebugUnitTest --tests "com.example.myapplication.ui.screenshot.*" -Proborazzi.test.verify=true --console=plain --stacktrace
   ```

   签名检查、发布脚本检查及全部 Android 门禁仍保留，artifact 冻结仍在四步之后；没有 `continue-on-error`、截图 record 或 `--debug`。此调整复用已成功的调用顺序并补充诊断，不能提前宣称已修复打包根因。

2. 主会话已在本机 JDK 21 用原 keystore 执行 `keytool -certreq`，确认标准密码可以解锁并签名；CI 使用 JDK 17，结果需在 runner 再确认。检查代理没有读取私钥。

3. `Test-DevKeystore` 在原证书匹配之后执行相同轻量私钥探针。`-storepass:env` / `-keypass:env` 使用现有密码环境变量；没有 CSR 输出文件、keystore 修改或 CA 请求，stdout 捕获后丢弃，失败只显示阶段与退出码。`finally` 恢复密码环境。

4. `VerifyKeystore` 成功日志明确证书与私钥签名均可用。新增假 keytool 的三项回归，覆盖成功、私钥解锁失败和错误证书；验证 CSR / 诊断不泄漏、密码恢复，以及错误证书不会执行签名探针。

## 验证与交接

- 实际运行 `pwsh -NoLogo -NoProfile -NonInteractive -Command ". './.trellis/tasks/11-06-github-actions-r2-dev-release/research/github-style-startup-wrapper.ps1'"`。
- `-File` 与 GitHub wrapper 各通过 22 项模块测试 + 11 项真实入口测试，合计同一组 33 个用例执行两次，共 **66 次通过**，退出 0；[完整日志](package-stage-startup.txt)。没有调用实际 keytool 或读取真实 keystore。
- actionlint 1.7.12 通过；定向静态检查确认四次 Gradle 调用的顺序、`--stacktrace`、仅截图 verify、artifact 冻结顺序和无门禁豁免。
- 改动的 PowerShell 文件解析通过。源码 / Kotlin、Gradle properties 和当前更新清单未变，本地 JVM / Lint 结果沿用既有证据，没有执行本地 Gradle、APK 构建或外部写入。
- 代码改动仅为 workflow、`tools/dev-release.psm1`、`tools/dev-release.ps1` 和模块测试；同步发布规范与本记录。主会话负责提交及下一次真实发版，届时需观察 JDK 17 私钥探针和独立 `assembleDebug` 的实际结果。
