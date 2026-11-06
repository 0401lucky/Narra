# 本次工作提交

用户已批准完整方案、首包发布和推送 `main`，沿用既有提交 / 推送授权。

计划提交：`feat(ci): 支持 dev 云端构建与 R2 自动发版`

## 实现与验证

- `.github/workflows/dev-release.yml`
- `.github/workflows/deploy-pages.yml`
- `tools/dev-release.ps1`
- `tools/dev-release.psm1`
- `tools/update-app-update-json.ps1`
- `tools/tests/dev-release.tests.ps1`
- `tools/tests/dev-release-entry.tests.ps1`
- `gradle.properties`

## 发行说明、规范与记录

- `CHANGELOG.md`
- `docs/版本策略.md`
- `docs/发布与更新约定.md`
- `docs/updates/dev-release-notes.json`
- `版本功能添加/优化.md`：仅本任务的 v2.3 段，已有 v2.4 未提交段不进入 index。
- `.trellis/spec/backend/index.md`
- `.trellis/spec/backend/dev-release-guidelines.md`
- 本任务 PRD、design、implement、JSONL、task.json、commit-plan，以及 research 的 Markdown / JSON 和三份定向 JVM XML。

本次源提交保持 `docs/updates/dev.json` 和当前候选记录为旧发布版本，新的 APK / 清单由真实工作流校验后自动提交。

## 排除项

- 初始工作区的历史任务归档移动 / 删除、截图 manifest、v2.4 规划及无关文档。
- 所有签名文件、凭据值、APK、构建输出、工具二进制和忽略的日志。
- 不使用全量 `git add`，不改写用户工作树；版本记录使用单独的 index 内容，保留工作区的原 v2.4 段。

## 提交前结果

- 独立检查通过：30 项离线发布测试、18 项更新协议 JVM 测试；PowerShell 语法及 actionlint 通过。
- Android Lint 无错误，55 Warning / 3 Hint，与原基线计数一致。
- 新 APK 将由 GitHub Actions 构建；R2 凭据当前仍待 Chrome 连接恢复后配置。构建 job 可独立生成有原签名的 artifact，发布失败可从原 artifact 重试。

## 首次 CI 后的修复提交

提交：`fix(ci): 修复 PowerShell 发布入口的启动作用域`

- 本次仅包含发布入口、两份已有测试、新启动测试门禁、对应 workflow、发布规范及本任务的 CI / 复现记录。
- 收录三份简短 wrapper 作为可执行复现入口；忽略的本机日志不提交。
- 同一组 30 个发布用例在两种启动方式下各通过一次；普通 Android Quality 已对 `95460b58` 完整通过，App / Gradle / 版本配置未因本次修复改变。

## 打包失败后的定向提交

提交：`fix(ci): 分步构建并预检 dev 私钥`

- 仅调整发版 workflow 的 Gradle 调用顺序 / 异常诊断、签名预检及其测试，并同步规范和本任务检查记录。
- 保留全部构建、JVM、Lint、截图和来源验证门禁。增加 3 项假 keytool 用例后，两种启动方式各 33 项通过。
- 同提交普通 CI 已成功；组合调用的 packageDebug 根因未确认，本次复用成功顺序并为后续失败保留堆栈。
