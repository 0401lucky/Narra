# 独立检查记录

检查日期：2026-11-06。检查代理：`dev_release_check`。本记录只证明本地代码审查与定向验证，不代表真实 Actions → R2 → Pages 发版完成。

后续首个真实发版暴露了 `-File` 与 GitHub PowerShell wrapper 的作用域差异。
下文保留初次审查记录；最新修复、60 次双启动复验和真实 CI 失败证据见
[PowerShell 启动作用域修复](pwsh-startup-fix.md)。
第二轮已通过 Linux 启动检查，随后出现打包失败；独立 Gradle 调用、当前 JDK 私钥
签名预检及 66 次复验见 [packageDebug 后续调整](package-debug-followup.md)，打包根因仍待真实堆栈确认。

## 结论

当前实现可交给主会话定向提交并进行真实云端验证。未发现尚未修复的阻塞代码问题；本轮补齐了实际发布入口的失败注入覆盖。真实签名产物、Linux 门禁、R2 凭据权限及公开清单生效仍由首个真实发版 run 验收。

## 已修复的审查发现

### 1. 实际入口编排缺少回归覆盖

- 原测试验证 `Invoke-DevPublication` 等模块函数，但未执行 `dev-release.ps1` 的 `Publish`、`PreparePages`、`VerifyPublished`，无法证明 Git 编排、模块调用闭包和尾部轮询的实际组合行为。
- 新增 `tools/tests/dev-release-entry.tests.ps1`，直接执行上述真实入口。只在独立测试进程替换 native Git / AWS、HTTP 和等待边界；真实模块函数、`GetNewClosure`、元数据生成及七文件提交编排继续运行，不为生产脚本增加测试开关。
- 原统一入口 `tools/tests/dev-release.tests.ps1` 以独立 `pwsh` 子进程运行新增用例，测试桩不会污染随后执行的构建步骤。临时 APK、仓库和下载目录均在唯一隔离目录内，递归清理前检查绝对路径和目录名前缀。
- 新增 11 项入口用例覆盖：完整成功顺序、缺失 R2 配置、错误源码 / run / 未来 attempt、最新 main 版本变化、403、公开坏字节、上传后 main 推进、push 竞争后复用旧 attempt 产物、Pages 祖先 / 降级 / 同版本冲突，以及最终公开清单的成功重试和失败上限。
- 失败断言检查真实仓库清单仍为旧版且 Git stage / commit / push 次数为零；push 自身失败则检查远端状态未推进，重试不重复 PUT。

### 2. 发布日期措辞与实际阶段不一致

- 原 `CHANGELOG.md` 新条目称公开清单最终验证后才填写发布日期，实际自动文档提交发生在 APK 公开下载通过之后、Pages 部署之前。
- 主会话已将文案改为“发布日期随已验证 APK 的清单提交记录，内置更新生效由 Pages 部署和最终公开清单检查确认”。候选记录明确保留 Pages / 最终验收尚待执行的状态，不增加第二次自动提交。

## 逐项审查结果

- 构建只接受本仓库 main 手动触发，checkout 为完整 `github.sha`；最终冻结检查实际 HEAD、版本配置、APK badging、唯一原 signer、文件大小和 SHA。R2 Secrets 缺失不会阻断独立构建 job。
- 原证书固定为 `3a6c57eec0641bc00830252eb821d62c9e9b2f639c5989d0bc42b29b843546e8`。私钥只还原到 runner 的 `.android/debug.keystore`，`always()` 删除；公开 artifact 只选择 APK 与来源 JSON，未加入签名目录或凭据文件。普通 PR 工作流没有新增 Secrets。
- artifact 名称通过构建 job output 绑定 run / attempt。发布再次核对源码 SHA、run ID、原 attempt 不晚于当前 attempt、APK 大小和 SHA；入口测试验证第 2 次发布仍使用 attempt 1 来源。
- S3 固定桶为 `narra-updates`、key 为受验证的 `dev/<apkName>`。只有明确 404 / NoSuchKey / NotFound 才上传，PUT 使用 `If-None-Match: *`；412 后校验实际对象，403 和未知错误失败关闭。R2 回拉与公开 HEAD / 完整 GET 均通过才进入提交。
- 仓库及公网清单都检查单调性、同版 URL / SHA 不可替换；提交前重新 fetch。main 推进或 push 竞争停止并保留原 artifact / 对象，未使用强推或回滚用户变动。
- 自动发布只写七条确定路径：`CHANGELOG.md`、`版本功能添加/优化.md`、`docs/版本策略.md`、`docs/发布与更新约定.md`、`docs/发布候选记录.md`、`docs/updates/dev.json`、`docs/updates/dev-release-info.json`。版本记录只替换标记区域，已有 v2.4 段不在自动改写范围。
- Pages 保留原 push / 手动入口并提供 `workflow_call`，各入口共用非取消并发组；锁内 checkout 最新 main，校验发布提交祖先及公开清单单调性。bot push 后由发布 workflow 显式调用 Pages，不依赖新 push 事件。
- 最终读取原客户端清单 URL，最多 24 轮；元数据校验包含应用、渠道、版本、允许的下载地址、SHA 和可选更新字段。错 SHA 或强制更新字段不会输出完成摘要。
- 主流程 / Pages 各 job 权限符合设计；没有引入 PAT、Actions 写权限、正式 release 渠道、客户端 URL 放宽或 Kotlin / UI 改动。

## 验证

| 检查 | 结果与证据 |
| --- | --- |
| 全部离线发布测试 | `pwsh -NoProfile -NonInteractive -File tools/tests/dev-release.tests.ps1`，退出 0；19 项模块测试 + 11 项真实入口测试全部通过 |
| PowerShell 类型 / 语法可执行性 | PowerShell 7.6.9；5 个 PS 文件的 `Language.Parser.ParseFile` 均零错误，实际执行上述完整测试。仓库未配置 PSScriptAnalyzer，不把解析冒充额外 linter |
| Workflow lint / YAML 解析 | actionlint 1.7.12 检查 `.github/workflows/dev-release.yml` 与 `.github/workflows/deploy-pages.yml`，退出 0 |
| Android Lint | 复用已完成且源码未再修改的实施命令，退出 0；独立解析 `app/build/reports/lint-results-debug.xml`，55 Warning、3 Hint、无 Error |
| Kotlin / JVM 编译与更新回归 | 原始 `research/local-gradle.log` 明确包含 `compileDebugKotlin`、`compileDebugUnitTestKotlin` 和 `BUILD SUCCESSFUL in 7m 13s`；独立解析 `research/local-jvm/`，12 + 3 + 3 = 18 项通过，零失败 / 错误 / 跳过 |
| 空白与范围 | 定向 `git diff --check` 通过；对未跟踪新 PS / YAML 同样检查尾空白。没有重复昂贵 Gradle 或修改 Kotlin / UI / 既有截图 |

复用的 Gradle 命令和完整结果位于 [实施交接](implementation.md) 与 [验证汇总](local-validation.json)。独立检查新增的测试不会改变 Android 编译输入，因此没有重复执行该命令。

## 证据边界与交接

- 当前仓库 `docs/updates/dev.json` 仍为 `2.2.12-dev / 20212`；工作树原始文件 SHA-256 为 `bfbb8d97279e6eda1a4b1e5a7005d27f0345e464cfca68d6d29494ee72541963`，检查未改写它。
- 本轮检查没有配置 Secrets、读取私钥、commit / push、触发 Actions 或写 R2 / Pages。离线测试使用合成字节和固定测试凭据；不能把这些结果当成真实 APK 证据。
- 本地未执行新 APK 构建、完整截图回归或设备覆盖安装。云端 workflow 包含完整 JVM、Lint、截图 verify 和 debug 构建；这些门禁及 APK / 公网结果需针对最终提交实际执行。
- 主会话真实发版成功后再推进任务验收和版本阶段描述；如 Pages / 最终检查失败，保留未完成状态并重跑失败 job。
- 可将 S3 桶级凭据只能使用 S3 API、bot push 必须显式调用 Pages、以及统一离线检查入口记录到 `.trellis/spec/backend/quality-guidelines.md`；当前可执行契约已在发布约定和本任务设计中同步。
