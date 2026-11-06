# 自动发版实施交接

实施代理：`dev_release_implement`。用户已在完整方案后批准继续，任务为 `in_progress`。

## 实施范围

- 新增 `.github/workflows/dev-release.yml`：本仓库 main 手动发版；构建、R2 发布、显式 Pages 调用和最终公网验收分为独立 job。
- 更新 `.github/workflows/deploy-pages.yml`：增加 `workflow_call`；共用 Pages 并发组，获得锁后读取最新 main，发布前核对清单单调性。
- 新增 `tools/dev-release.psm1` 与 `tools/dev-release.ps1`：校验源码 / 版本 / 原签名 / 来源 artifact，执行受限桶的 S3 条件写入、回拉和公开下载检查，最后提交固定范围文档。
- 新增 `tools/tests/dev-release.tests.ps1`：无框架依赖的离线发布测试，文件写入全部在独立临时目录。
- `tools/update-app-update-json.ps1` 增加可选 `OutputPath`，保留原调用入口并支持先写入临时清单。
- `gradle.properties` 版本变更为 `2.3.0`，保留旧 Pages 更新 base URL。

主会话拥有版本文档、发行说明、Secrets、提交推送及真实云端发布。实施代理没有改写当前线上 `dev.json`、配置凭据、提交、推送或执行任何 R2 写入。

## 关键实现约束

- 构建 job 前置校验 repository / main / workflow_dispatch，签名只在还原步骤注入；R2 凭据只在 publish job 检查，不阻止独立产出已签名 artifact。
- 固定旧证书 SHA-256：`3a6c57eec0641bc00830252eb821d62c9e9b2f639c5989d0bc42b29b843546e8`。实际 APK 包名、版本及唯一 signer 由 aapt2 / apksigner 检查。
- artifact 绑定源码 SHA、run ID、原构建 attempt；重跑失败发布 job 可复用原 artifact，新内容不得覆盖同版本对象。
- R2 使用 AWS CLI `2.31.19`，官方安装包 URL 已 HEAD 200；设置请求 / 响应 checksum 为 `when_required`，不需要账户管理权限。
- [Cloudflare S3 API 文档](https://developers.cloudflare.com/r2/api/s3/api/) 明确支持 `PutObject If-None-Match: *` 的原子条件写入；412 竞争返回后读取实际赢家对象并比对 SHA。403 不能走首次上传分支。
- 更新清单只在 R2 回拉和公网 APK 完整验证后提交；提交前再次检查 main 与线上版本。main 发生竞争则停止，重跑失败 job 从最新 main 重新核对，绝不 force push。
- 只允许七个发布文件：CHANGELOG、版本功能记录、版本策略、发布约定、当前候选记录、dev.json 和 dev-release-info.json。版本功能记录仅替换已约定标记之间的内容。
- 公网清单使用旧客户端的原 URL 验证；不能以 cache-busting 专用地址代替实际旧入口验收。

## 实施阶段验证

| 检查 | 结果 |
| --- | --- |
| PowerShell AST 解析 | 通过（四份 PS 源码 / 测试） |
| `pwsh -NoProfile -File tools/tests/dev-release.tests.ps1` | 实施交接前最终 19 / 19，通过 |
| actionlint `1.7.12`，两份修改 workflow | 通过；本地工具 ZIP 按官方 checksums 文件校验后运行 |
| 原生命令适配器调用 `git --version` | 通过，参数通过 ProcessStartInfo.ArgumentList 传递 |
| 更新协议 JVM | 18 / 18 XML 通过，无失败、错误或跳过；详见下方 |
| Android Lint | 通过；55 Warning、3 Hint，与既有基线计数一致 |

离线测试覆盖版本与 Gradle 配置、旧客户端地址与可选更新 schema、APK signer / SHA、403 / 404、同版本冲突、原子写入竞争、验证失败时清单零提交、提交前降级复查、失败发布复用对象、来源 run 绑定、job output 注入及隔离文档更新。自查发现 job output 名称最初不允许 `apk_sha256` 中的数字，已修正并补了针对性用例。

实际 Gradle 命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.myapplication.data.repository.AppUpdateRepositoryTest" --tests "com.example.myapplication.system.update.AppUpdateUrlPolicyTest" --tests "com.example.myapplication.viewmodel.AppUpdateViewModelTest" :app:lintDebug --console=plain
```

- `AppUpdateRepositoryTest`：12 / 12。
- `AppUpdateUrlPolicyTest`：3 / 3。
- `AppUpdateViewModelTest`：3 / 3。
- 整条命令退出 `0`，`BUILD SUCCESSFUL in 7m 13s`；44 个任务中 23 执行、21 up-to-date。源码及测试编译完成，没有调用 `assembleDebug`。
- 命令日志：`research/local-gradle.log`；三份原始单测 XML 归档于 `research/local-jvm/`，汇总为 `research/local-validation.json`。

## 检查交接与剩余边界

- 主会话已将 PS / YAML / 测试文件编辑权交给独立检查代理。实施代理的 Gradle 已完成，没有在途命令，构建执行权也可转交检查代理；实施阶段工具结果与后续检查修复应分开记录。
- 当前没有 Kotlin / UI / 截图变更；工作区原有 `snapshots/manifest.md` 与其他任务差异未回滚或暂存。
- 本地没有构建新 APK。正式 APK、Linux 运行、R2 和 Pages 的真实端到端验收仍待主会话触发。
- main / Pages 的历史绿灯不等同新工作流验收成功；ADB 无连接设备的状态由主会话确认，覆盖安装与用户数据保留不能写成已实测。
