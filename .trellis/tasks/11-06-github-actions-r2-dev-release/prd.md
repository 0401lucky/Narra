# v2.3 dev 发版：GitHub Actions 构建与 R2 内置更新

## Goal

把 Narra dev 包的构建、签名和发布迁到 GitHub Actions，交付可从 Cloudflare R2 下载、可被旧版应用发现并覆盖安装的新版本，后续发版不再依赖本机编译。目标分支为 `main`，本轮归属 v2.3，首包准备为 `2.3.0-dev / 20300`。

## Confirmed facts

- 来源会话：`01a1ae46-9b8b-7133-a200-93742b2fb152`。用户已同意创建任务，并选择“全自动发布，配置仓库 Secrets”，授权把原 dev 签名及受限 R2 凭据交给本仓库 Actions；该授权继续有效。
- P06 实现、基准、归档及 journal 已完成；2026-11-06 续接核实 `0401lucky/Narra` 的本地和远端 `main` 均为 `21e9384194bc2fd439b4976bcf94bc378d9b2d2b`。该提交的 Android Quality 与 Deploy Pages 均成功，见 [续接证据](research/resumption-and-release-contract.md)。
- 现有 CI 构建 debug、运行 JVM / Lint 和截图 verify，没有 APK artifact 或 R2 发布任务，见 `.github/workflows/android-quality.yml:24`、`:64`。
- 当前发布基线为 `2.2.12-dev / 20212`。更新清单位于 `https://0401lucky.github.io/Narra/updates/dev.json`，APK 位于 `narra-updates` 桶，通过 `https://download.lsa1230.dpdns.org` 分发。
- 客户端严格限制清单 host/path 和 APK host/channel；只改清单地址到 R2 或回退到任意 `r2.dev` 地址会被现有客户端拒绝，见 `AppUpdateUrlPolicy.kt:5`、`:7`、`:21`。
- debug 使用本机默认签名，既有证书指纹已记录于 `docs/发布候选记录.md:12`，原会话已比对线上 APK 与本机证书一致。续接时仓库 Secrets / Variables 仍为空，CI 凭据尚未配置。
- Cloudflare 的桶级 Object Read & Write 凭据仅支持 S3 接口；GitHub 的 `GITHUB_TOKEN` 推送不会自动触发后续 push 工作流。这两项约束已纳入 [技术设计](design.md)，来源见续接证据。

## Requirements

- **R1 / 云端发版**：提供仅允许本仓库 `main` 的手动发版入口，指定与已提交版本配置一致的版本；触发后构建、签名、上传和发布全部自动完成。记录源码提交、Actions run / attempt、版本、包名、文件大小、证书指纹及 SHA-256；隔离 dev / release / baseline。
- **R2 / 覆盖升级与凭据**：保持 `com.narra.app.dev` 和现有 dev 签名兼容。签名私钥、密码和 R2 密钥不进入 Git、日志、公开 artifact 或更新清单；R2 凭据只授权 `narra-updates` 对象读写，不扩大到账户管理或其他桶。
- **R3 / 先验证后发布**：云端 APK 必须通过包信息和签名校验；上传后远端回拉 SHA-256 一致，公开链接返回 200 且下载内容一致，才可推进新版本清单。验证失败时保留既有更新入口及有效版本。
- **R4 / 旧入口兼容**：本轮保留现有 Pages 清单地址及 R2 自定义下载域名，自动发布后旧客户端仍可发现新版本。继续只提供可选更新，`minimum_supported_version_code=0`；不要求重装、不迁移用户数据。
- **R5 / 重试与版本保护**：串行发布、拒绝降级、拒绝同版本不同内容覆盖。成功构建产物可用于重试失败的发布阶段；分别报告构建、签名、对象验证、清单提交和 Pages 部署的结果，公网清单未验证成功不得宣称发版完成。
- **R6 / 文档与范围**：同步 CI、构建版本、`CHANGELOG.md`、`版本功能添加/优化.md`、版本策略、发布约定及候选记录；只有 APK 验证通过后才更新线上版本清单和“已发布”记录。只提交本任务相关修改，保留已有 v2.4 规划、历史归档改动及无关文件。

## Acceptance Criteria

- [ ] **AC1 / R1**：`main` 的真实 Actions run 完成云端构建和质量检查；首包为 `2.3.0-dev / 20300`、`com.narra.app.dev`，APK 及无密钥的来源报告可下载。
- [ ] **AC2 / R2**：新 APK 的签名证书 SHA-256 等于既有 dev 证书；检查凭据注入、日志、缓存与 artifact 范围，未扩大仓库 / 桶 / 渠道权限。
- [ ] **AC3 / R3/R4**：R2 回拉与公开下载的 SHA-256 均等于云端 APK；旧 Pages 清单返回新版本、正确 URL / SHA 和可选更新字段。客户端更新协议的定向回归通过。
- [ ] **AC4 / R5**：离线失败注入覆盖验证前不得提交清单、403 与 404 区分、错误签名 / SHA、并发、降级、同版本冲突和发布重试；实际发版 run 与每阶段结果有记录。
- [ ] **AC5 / R6**：发布文档与 `main` 对应提交一致；既有 P06 归档完整，没有把无关工作区变动或 v2.4 实施混入本次交付。

## Out of scope

- 正式 release / baseline 发包、新包名、更新 UI 重构、强制更新。
- v2.4 记忆改造、剩余 UI 扩面，以及把云端检查等同于真机体验 / 覆盖安装实测。
- 清单域名迁移、放宽客户端下载白名单、创建新桶或调整 DNS / 公网域名。
- GitHub Release 依赖、每次普通 push 自动发包、账户级长期 OAuth 复用或管理权限扩展。

## Planning status

本次方案选择沿用客户端地址契约，把变更集中在 CI 自动化。用户在需求、[技术设计](design.md)、[执行计划](implement.md)和上下文清单完成审阅后回复“那就继续吧”，批准实施及首包发布；任务已启动为 `in_progress`。现有全自动发布及 Secrets 配置授权继续有效，云端发布与新 APK 验收的实际结果另行记录。
