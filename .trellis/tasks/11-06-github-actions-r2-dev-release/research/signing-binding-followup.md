# Gradle dev 签名绑定与证书诊断

## 已取得证据

- 只读查询 [Dev Release 42185037764](https://github.com/0401lucky/Narra/actions/runs/42185037764)，源码 `afde0f10a136f7fd0f4e0b9306fd1dfd98dc9026`。JDK 17 原证书及私钥签名预检、双启动脚本检查、独立 assemble、完整 JVM、Lint 和截图 verify 全部成功；只有 FreezeArtifact 失败。
- 错误只有“APK 签名证书与既有 dev 证书不一致”，没有实际指纹或证书记录数量。API 返回 `artifacts: [] / total_count: 0`，无法再下载该 run 的 APK 区分真实不同证书与解析 / 数量问题；当前不命名根因。
- [筛选的实际日志](signing-cache-evidence.txt) 显示 `08:06:34 > Task :app:validateSigningDebug`、`08:09:40 > Task :app:packageDebug`，都没有 `FROM-CACHE`。共享 setup-java 缓存命中约 945 MB 只证明缓存恢复，不证明 APK 被取回。普通 [Android Quality 42185033145](https://github.com/0401lucky/Narra/actions/runs/42185033145) 也成功且 package task 实际执行。
- 只读验证本机保存的旧公开 `2.2.12-dev` APK：build-tools 36.0.0 / 36.1.0 的 apksigner 0.9 均成功，现有输出模式各解析出 1 条原证书。更新后的严格解析器再次配合真实 aapt2 badging 通过。这说明已验证的工具格式可识别，不代表失败 run 的实际 APK 已被验证；没有读取任何 keystore。

## 实施

1. 原 key 改为还原到 `RUNNER_TEMP/narra-original-dev.keystore`，同一固定路径用于 `always()` 清理。路径通过 `GITHUB_ENV` 导出 `NARRA_DEV_STORE_FILE`，没有新增仓库 Secret / Variable，也不再依赖默认 HOME 路径。
2. `app/build.gradle` 仅新增签名相关的 17 行：仅 `GITHUB_ACTIONS=true` 且有显式文件时创建 `narraDevCi`，校验文件绝对路径及存在性，并绑定 debug。release、baseline、benchmarkRelease、nonMinifiedRelease 块与修改前逐字比较未变，默认 `signingConfigs.debug` 未被改写。
3. 编译前运行无配置 / 任务输出缓存的 `:app:signingReport`。捕获输出后只验证唯一 debug 的 config=`narraDevCi`、alias=`androiddebugkey`、实际路径与还原路径一致、SHA-256 为原证书。仅输出匹配标记与公开指纹，其他变体 / 原始报告 / 主题信息不写日志。
4. 签名 assemble 增加 `--no-build-cache`，防止不同签名的任务输出进入本次 APK；保留依赖缓存和其余全部门禁。这个开关是防御性确定性边界，并非已证实的缓存根因修复。
5. APK 校验输出预期 / 实际 SHA-256、证书记录数量、不同指纹数量，以及选用的 build-tools / apksigner 版本。仍要求恰好 1 条原证书；缺失 / 非法格式与不同指纹分开失败，重复相同指纹记录仍拒绝，不通过去重放宽门禁。来源报告增加 `apksigner_version`。

固定原证书仍为 `3a6c57eec0641bc00830252eb821d62c9e9b2f639c5989d0bc42b29b843546e8`。

## 验证

- `pwsh -NoLogo -NoProfile -NonInteractive -Command ". './.trellis/tasks/11-06-github-actions-r2-dev-release/research/github-style-startup-wrapper.ps1'"` 退出 0。
- [完整回归日志](signing-binding-startup.txt)：`-File` 和 GitHub wrapper 各 26 项模块 + 11 项真实入口，共同一组 **37 个用例执行两次，74 次通过**。新增用例覆盖报告目标 / 非目标变体、路径 / 配置 / alias / 指纹错误、缺失 / 重复 debug，以及 APK 指纹 / 数量安全诊断。
- actionlint 1.7.12、PowerShell 解析、定向空白检查通过。静态检查确认签名报告在 assemble 前、四项 Android 门禁及 artifact 顺序保留、路径导出与清理一致、CI-only 条件及默认 baseline signer 保留。
- 使用现有 Groovy 4.0.29 的 `SourceUnit.parse()` 对 `app/build.gradle` 只做语法解析，通过；没有执行构建脚本、Gradle 配置、APK 构建或读取私钥。
- 更新后的 APK 解析器只读验证旧公开 APK 成功，输出记录数 1、不同指纹数 1、原证书 SHA。
- 当前 `docs/updates/dev.json` 原始 SHA-256 仍为 `bfbb8d97279e6eda1a4b1e5a7005d27f0345e464cfca68d6d29494ee72541963`。

## 交接边界

- 代码改动仅在 `app/build.gradle` 签名配置、dev workflow、发布模块及模块测试；技术设计与发布规范同步。没有 Kotlin / UI / 版本变动，也没有 commit / push 或任何远端写入。
- signingReport 的真实 Gradle / AGP 配置执行、实际新 APK 指纹以及之后的 R2 / Pages 仍待主会话触发。74 次离线用例和语法检查不冒充这些结果。
- 下一次 CI 若签名绑定不符，应在编译前退出；若最终仍失败，安全指纹、记录数和工具版本应能区分实际错误证书与格式 / 数量问题。
