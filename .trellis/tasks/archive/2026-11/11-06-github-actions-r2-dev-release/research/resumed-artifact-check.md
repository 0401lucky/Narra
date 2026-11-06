# 续接后的云端原产物独立检查

检查日期：2026-11-06。角色：`trellis-check`。仅下载和检查既有产物，未修改发布代码、凭据、版本文档、Git 或远端状态。

## 结论

原云端 APK 通过本机独立校验，可以在配置 R2 凭据后用于续跑原 run 的失败发布 job，无需重新构建。此次结果不代表 R2 / Pages 已发布，也不替代设备覆盖安装和数据保留实测。

## 来源与文件集

| 字段 | 实际核查值 |
| --- | --- |
| 仓库 | `0401lucky/Narra` |
| 分支 / 事件 | `main` / `workflow_dispatch` |
| 源码 SHA | `c34e8e12565487499799ad81fa07a313397de678` |
| [Actions run](https://github.com/0401lucky/Narra/actions/runs/42200205327) | `42200205327`，构建 attempt `1` |
| [build job](https://github.com/0401lucky/Narra/actions/runs/42200205327/job/127833323034) | `127833323034`，`success` |
| artifact | `dev-2.3.0-42200205327-1`，ID `13494522764` |
| artifact 保留期限 | `2026-12-06T10:23:30Z`，检查时未过期 |
| 文件集 | 仅 `Narra-v2.3.0-dev-20300-dev.apk` 与 `release-info.json` |
| APK 大小 | `80357328` 字节 |
| 来源报告大小 | `1334` 字节 |
| APK SHA-256 | `cc6b3aa9946add66dccf58926d23fbceae4d894b15179d20dfb47b143e6b3eab` |
| APK 包名 | `com.narra.app.dev` |
| 实际版本名 / versionCode | `2.3.0-dev` / `20300` |
| 实际证书 SHA-256 | `3a6c57eec0641bc00830252eb821d62c9e9b2f639c5989d0bc42b29b843546e8` |
| 实际签名记录 | 1 条、1 个 signer，v2 scheme 验证成功 |
| 固定校验工具 | Android build-tools `36.1.0`，apksigner `0.9` |
| 构建 JDK（来源报告） | `openjdk 17.0.21 2026-10-20` |

GitHub run API、artifact API、下载后的来源报告以及实际 APK 相互对应。`Assert-DevArtifact -CheckRun` 使用从 run API 核对的源码 SHA / run ID / attempt 验证通过；APK 大小和完整 SHA-256 均匹配报告。

来源报告字段全部属于已定义 schema，没有额外字段。artifact 没有 keystore、日志、凭据或其他附加文件；APK ZIP 中未发现 `.keystore` / `.jks` / `.p12` / `.pfx` / `.key` 文件。后者是文件集定向检查，并非对所有字节的秘密扫描。

## 原生复验

使用已安装的本机 SDK：`C:/Users/lucky0401/AppData/Local/Android/Sdk/build-tools/36.1.0`。未安装或改动全局 SDK，未读取任何私钥。

- `Get-DevVerificationTools` 选择固定 `36.1.0`。
- `aapt2 dump badging` 读取实际 APK，确认 `com.narra.app.dev`、`20300`、`2.3.0-dev`；compile SDK 为 36。
- `java -jar apksigner.jar verify --verbose --print-certs` 原生命令返回 0，显示 `Verifies`、v2 scheme `true`、`Number of signers: 1`。
- 原始 aapt2 / apksigner 输出传入现有 `Assert-DevApkIdentity`，严格唯一原证书检查通过。
- `Assert-DevArtifact -CheckRun` 核对来源、版本、文件名、大小、SHA-256、证书和发行说明，通过。
- 本机工具版本与来源报告的 `apk_verification_build_tools` / `apksigner_version` 一致。

安全签名诊断为：`certificate_records=1; distinct_certificates=1`，实际和预期证书指纹均为上表所列原 dev 指纹。没有把去重后的多条记录作为通过条件。

## 云端质量门禁证据

以下结果来自该 run 的 GitHub job API 和日志，均针对上述源码 SHA。没有把先前提交的成功结果算作本轮通过，也未在本机重复 Gradle。

| 门禁 | 结果 / UTC 完成时间 |
| --- | --- |
| 原 keystore 证书及私钥预检 | `success` |
| 两种发布入口回归 | 每种 28 项模块 + 11 项入口测试，均 0 failed；总共 78 次通过 |
| Gradle 签名配置预检 | `config_matches=True; store_path_matches=True`，原证书一致 |
| `:app:assembleDebug --console=plain --stacktrace --no-build-cache` | `BUILD SUCCESSFUL`，10:13:06 |
| `:app:testDebugUnitTest --console=plain --stacktrace` | `BUILD SUCCESSFUL`，10:17:13 |
| `:app:lintDebug --console=plain --stacktrace` | `BUILD SUCCESSFUL`，10:21:56 |
| 截图定向 JVM + `-Proborazzi.test.verify=true` | `BUILD SUCCESSFUL`，10:23:27 |
| APK 冻结及上传 | `success`，10:23:30 / 10:23:32 |
| 临时签名文件清理 | `success` |

因此本次源码的 Kotlin / 测试编译、Android Lint、JVM 与截图 verify 已在真实 Linux runner 通过。未执行 screenshot record，也未修改或接纳基准。

## 尚未覆盖的发布阶段

本次读取时，publish job `127840344669` 因缺少 `R2_ACCESS_KEY_ID` / `R2_SECRET_ACCESS_KEY` 失败，Pages 和最终公网清单检查跳过。凭据配置、受限桶权限、R2 回拉 / 公网 APK 哈希、Pages 清单及最终发布结论由主会话继续验收。

本次未连接设备、未安装 APK、未卸载或清空应用。原包名和原签名一致证明覆盖安装的必要身份条件，不能宣称用户数据保留已经实测。

## 本地交付位置与复现入口

APK：`C:/Users/lucky0401/AppData/Local/Temp/narra-release-artifact-check-42200205327/Narra-v2.3.0-dev-20300-dev.apk`。

来源报告位于同目录的 `release-info.json`。下载直连较慢后，仅在该命令进程使用系统已有代理 `http://127.0.0.1:7890` 重试；未修改代理配置。

```powershell
gh api repos/0401lucky/Narra/actions/runs/42200205327
gh api repos/0401lucky/Narra/actions/runs/42200205327/artifacts
gh run download 42200205327 --repo 0401lucky/Narra --name dev-2.3.0-42200205327-1 --dir C:/Users/lucky0401/AppData/Local/Temp/narra-release-artifact-check-42200205327
gh run view 42200205327 --repo 0401lucky/Narra --job 127833323034 --log
```

本检查未发现 APK / 来源报告不一致或新的生产代码问题。发布完成前应保留原 artifact，重跑失败 job，不重新编译后覆盖同版本对象。
