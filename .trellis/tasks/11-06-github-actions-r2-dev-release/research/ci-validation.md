# 真实 CI 验证

## 首次工作提交

- 工作提交：`95460b5887a52178a7a9a317f7182f46dad79512`，已推送 `main`。
- 只提交本任务 30 个文件；已有 v2.4 工作区段未进入 index，原文保持不变。
- [Dev Release 42178061696](https://github.com/0401lucky/Narra/actions/runs/42178061696)：2026-11-06 07:00 UTC 手动触发，版本输入 `2.3.0`。
- 预检、JDK / SDK 与原 dev keystore 恢复 / 证书验证成功；随后 `Test release guards` 失败，尚未执行新 APK 构建或 R2 / 清单写入。

## 首轮发布测试失败

- GitHub pwsh 使用 `pwsh -command ". '{0}'"` 启动临时 wrapper，wrapper 再调用发布测试脚本；本机原验证使用 `pwsh -File`。
- 首轮为 12 项通过、7 项失败。主要错误为闭包中找不到脚本私有 `Assert-True`，另有读取前态时对象缺少 `app_id`。
- 检查代理已在本机用相同 wrapper 精确复现；根因为 `GetNewClosure` 创建动态模块后的依赖 / 变量可见性，而非 App 或 Gradle 编译。
- 已修复显式依赖捕获和生产模块导入作用域；统一启动门禁在 `-File` 与 GitHub wrapper 两种方式下各通过 30 项用例。见 [修复与复验](pwsh-startup-fix.md)，等待新提交的真实发版复跑。

## 同提交其他门禁

- [Deploy Pages 42178000137](https://github.com/0401lucky/Narra/actions/runs/42178000137)：成功，仍发布有效旧清单。
- [Android Quality 42178000109](https://github.com/0401lucky/Narra/actions/runs/42178000109)：截至 07:17 UTC，整个 workflow 成功；Linux assembleDebug、完整 JVM、Lint 及截图 job 均成功。

## 外部条件

原签名 Secret 与 Cloudflare Account ID Variable 已配置。R2 两项受限 S3 Secrets 仍未配置，Chrome 原生连接待用户通过插件页恢复；不能把尚未运行的上传及公网更新验证记为完成。

## 启动方式修复后的重跑

- 修复提交：`acb51b748dc481ba32b6f7daccfc10089347c305`，已推送 main。直连 GitHub 超时后，使用系统已有代理进行单次推送成功，没有改变全局 Git 配置。
- [Dev Release 42181383716](https://github.com/0401lucky/Narra/actions/runs/42181383716)：新提交的真实发版运行，目标仍为 `2.3.0`。
- 原签名恢复 / 校验及新的两种 PowerShell 启动门禁均已在 Linux 成功。组合 Gradle 调用随后在 `:app:packageDebug` 失败，日志只有 `PackageAndroidArtifact$IncrementalSplitterRunnable`，没有根因堆栈；未上传 APK 或清单。
- 相同提交的普通 Android Quality `42181377835` 全部成功；因此发版流程改为已有成功路径的 assemble → JVM → Lint → 截图独立调用，全部启用 `--stacktrace`。并增加当前 JDK 的私钥签名预检；本机原私钥解锁及签名探针也已通过。
- 本次根因尚未确认，不能把并发或内存冲突当作已证实结论。详见 [打包后续检查](package-debug-followup.md)。

## 分步调用后的重跑

- `afde0f10` 的 [Dev Release 42185037764](https://github.com/0401lucky/Narra/actions/runs/42185037764) 已通过 JDK 17 私钥签名预检、两种启动方式、APK 构建、完整 JVM、Lint 和截图 verify。
- 最终冻结阶段的证书检查失败；该次未保存 artifact，也未进入 R2 发布。实际 packageDebug 执行过，不能归因于缓存；未保留失败 APK，证书差异根因仍未确定。
- 已增加 CI-only `narraDevCi` 明确绑定临时目录中的原 key，并在构建前校验 Gradle signingReport；最终诊断输出公有指纹与记录数、工具版本。继续保留单一原证书要求。见 [签名绑定后续检查](signing-binding-followup.md)。
- 两种 PowerShell 启动方式各 37 项通过；后续必须继续取得真实云端 APK 验证结果。

## 证书输出协议漂移

- `59905a88` 的 [Dev Release 42192056230](https://github.com/0401lucky/Narra/actions/runs/42192056230) 已通过 Gradle 原签名绑定和全部 Android 门禁；冻结阶段使用镜像最高的 build-tools 37.0.0，未能解析证书记录。
- 真实旧公开 APK 在官方 37 工具中验证成功，但证书行改为 `V2 Signer: ...`，而版本命令仍报 0.9；这已解释零记录错误，不能视为实际错误证书。
- 已固定验证工具为 36.1.0 并显式安装，真实旧 APK 的完整调用链通过，官方 Linux / 本机验证 jar 哈希一致。下一轮仍须核验新产物；详见 [验证器固定版本](verifier-pin-followup.md)。
