# v2.3 dev 自动发布执行计划

当前阶段：in_progress；用户已批准完整方案，本文件按实际进展勾选。使用 [PRD](prd.md) 的 R1–R6 / AC1–AC5 验收。

2026-11-06 续接：实现、本地检查、`c34e8e12` 的真实云端 build 和原产物独立
检查均通过。R2 凭据已配置，`42200205327` attempt 2 复用原产物完成 R2、Pages
和旧更新入口验证，正在第 6 节同步验收与归档记录。详见
[续接记录](research/resumption-20261106.md)和[最终审查](research/final-release-check.md)。

## 0. 启动与协作边界

- [x] 用户审阅本次完整方案后，执行 `task.py start`；现有“全自动 + 本仓库 Secrets”授权继续沿用。
- [x] 实施代理读取 `implement.jsonl`，检查代理读取 `check.jsonl`，主会话负责凭据、外部发布、最终文档 / 提交及收尾。
- [x] 代理任务首行注明本任务路径；实现与检查按顺序交接，避免两个代理同时改发布脚本或执行 Gradle。
- [x] 保存当前 Git 状态，只操作明确文件；启动时 155 项历史变动及 v2.4 任务不属于默认暂存范围。

## 1. 发布脚本及可验证契约（R1–R5）

- [x] 检查 `tools/update-app-update-json.ps1` 的跨平台路径 / 编码；复用其渠道映射、JSON 生成及哈希计算。
- [x] 新增 `tools/` 下的 dev 发布辅助，职责覆盖版本 / URL / 来源 / 签名 / 哈希检查、S3 适配和清单前置门禁。仅在确有需要时为原工具补充发行说明文件或输出路径参数，保持原入口兼容。
- [x] 独立出可测试的纯校验和外部调用边界，避免把复杂发布规则全写进 YAML 或拼接 shell 命令。
- [x] 以离线假存储、假下载和假提交覆盖成功、403 / 404、SHA / 签名错误、降级、同版本冲突、推送竞争及失败 job 重试。核心断言是“验证未完成时清单写入次数为零”。
- [x] 文档化同版本 APK 不保证重复编译字节一致；重试发布必须能复用成功构建的原 artifact。

## 2. Actions 与 Pages 接入（R1/R2/R4/R5）

- [x] 新增 `.github/workflows/dev-release.yml`：仅本仓库 main 的 `workflow_dispatch`，输入版本与 Gradle 配置一致，固定 dev 并发组。
- [x] 构建 job 使用 `ubuntu-24.04 / JDK 17`，锁定完整源码 SHA；还原并验证旧签名，运行 JVM / Lint / 截图 verify / debug 构建，检查 APK 真实元数据。
- [x] 上传最小 artifact 文件集：APK 与 `release-info.json`（包含无秘密的验证结果）；清理临时签名，保持普通 PR 流程无签名 / R2 Secrets。
- [x] 实现发布 job：通过桶级 S3 凭据访问 R2，完成回拉 SHA + 公开下载验证后再更新清单和允许的发布文档；真实执行验收见第 5 节。
- [x] 改造 `.github/workflows/deploy-pages.yml` 增加 `workflow_call` 和共同的串行部署规则；不依赖 `GITHUB_TOKEN` push 自动触发 Pages，阻止旧 checkout 回退清单。
- [x] 实现尾部 job 的有界公开清单轮询，按最终字段判断发版完成；正常网站发布入口继续可用，真实发版尾部验收见第 5 节。

## 3. 版本与文档准备（R6）

实施及审核范围：

| 文件 | 变动时机与用途 |
| --- | --- |
| `gradle.properties` | 实施时设置 2.3.0；保留当前元数据 base URL |
| `CHANGELOG.md` | 整理已完成的 v2.3 说明，未发布时明确标记 |
| `版本功能添加/优化.md` | 追加本任务进展 / 发布结果，不改写 v2.4 规划 |
| `docs/版本策略.md` | 记录 CI 发版规则，公开验证后推进已发布基线 |
| `docs/发布与更新约定.md` | 说明 main 手动触发、全自动步骤、S3 凭据和旧入口兼容 |
| `docs/发布候选记录.md` | 真实云端产物 / 公开验证后填入来源、签名、哈希、run 和下载链接 |
| `docs/updates/dev.json` | R2 校验全部通过后才能生成并提交新版本 |
| `docs/updates/` 内必要发行说明输入 | 如脚本需要结构化 release notes，只保存无秘密内容 |

不要为适配流水线改 Room、ViewModel、更新 UI 或设备测试。若发现确实必须修改客户端协议，返回规划说明原因后再扩展范围。

## 4. 定向质量检查

- [x] 使用实现选定的 PowerShell 离线测试入口在可用宿主执行，验证发布副作用顺序与失败路径；记录准确命令和结果。
- [x] 对新增 / 修改的 YAML 执行解析与 workflow 静态检查，核对 expression、job 权限、可复用 workflow 权限传递、artifact 名称和并发组。
- [x] 检查普通质量 CI 的 screenshot verify / record 边界未被破坏。
- [x] 构建配置影响更新参数，已运行定向 JVM 回归：

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.myapplication.data.repository.AppUpdateRepositoryTest" --tests "com.example.myapplication.system.update.AppUpdateUrlPolicyTest" --tests "com.example.myapplication.viewmodel.AppUpdateViewModelTest" --console=plain
```

云端正式验证使用项目已有入口，允许在一个构建 job 中串行复用 Gradle 缓存：

```bash
./gradlew :app:assembleDebug --console=plain --stacktrace --no-build-cache
./gradlew :app:testDebugUnitTest --console=plain --stacktrace
./gradlew :app:lintDebug --console=plain --stacktrace
./gradlew :app:testDebugUnitTest --tests "com.example.myapplication.ui.screenshot.*" -Proborazzi.test.verify=true --console=plain --stacktrace
```

上述云端命令必须针对要发布的源码 SHA，按现有工作流分步执行，不以 record 消除失败。

## 5. 凭据与真实发布（主会话，依赖 1–4）

- [x] 主会话复核旧 dev keystore 的证书，确认仅本仓库 Secrets 托管；不读取或展示不相关凭据。
- [x] 在已授权账户中配置仅 `narra-updates` 的 R2 对象读写凭据，核实桶 jurisdiction / endpoint；安全写入所需 Secrets 和 Variable，不导出本机 OAuth。
- [x] 复查 main 和 workflow 权限、定向提交已审核的工作流 / 脚本 / 版本 / 文档，然后推到 main；不 force push，不混入无关变动。
- [x] 首次触发 `2.3.0` dev 发版，修复启动作用域、签名诊断及验证器漂移后，最新 build 已成功。
- [x] 下载 Actions APK / 报告并核对包名、版本、固定证书、大小、SHA 和来源。
- [x] 确认 R2 回拉和公开下载结果。
- [x] 检查 Pages 新清单与 Actions APK 来源一致，取得 AC1–AC4 的真实证据；不能以仅上传成功作为完成标记。
- [x] 2026-11-06 `adb devices -l` 无设备，已记录未实测覆盖安装及数据保留；未卸载或清除用户数据。

## 6. 收尾（R6 / AC5）

- [x] 保存无密钥的 run / 来源 / SHA / URL 结果，更新发版记录；未运行的检查明确标注。
- [ ] 核对变更和最终提交清单，无 v2.4 实施、无关归档或秘密混入；同步远端 main 后再更新本地引用。
- [x] 经独立 Trellis 全范围检查通过；现有 dev 发布规范已覆盖 S3 凭据、bot push / Pages、签名和原产物重试契约，无需写入本次浏览器环境的临时处理方式。
- [ ] 完成 journal 和归档。

回退原则：失败时保留原有效清单和已验证产物；不自动删除 R2 历史对象、不降级线上版本、不替换原签名。核心服务无法访问时报告具体阶段，保留可从原 artifact 重试的记录。
