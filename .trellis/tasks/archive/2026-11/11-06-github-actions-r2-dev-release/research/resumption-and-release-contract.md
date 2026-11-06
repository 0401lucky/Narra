# 续接与发布契约证据

核对日期：2026-11-06。用于恢复规划及实现 / 检查代理的共同上下文。

## 1. 历史与当前进展

- `trellis mem extract 01a1ae46-9b8b-7133-a200-93742b2fb152 --phase all --json` 恢复了主会话 78 条对话。
- 用户明确要求：“推送到 github”“构建包我想用 github action 去构建”“上传到 cloudflare r2 上去弄内置更新”“记得推到 main”。
- 用户随后批准新建任务，选择“全自动发布，配置仓库 Secrets”；原 dev 签名及受限 R2 凭据的本仓库托管已获得授权。
- 上次停在：P06 已归档、`main` 已推送，发布任务只有 PRD 和占位 JSONL；尚无 design / implement 文档、发布 workflow 或 CI Secrets。
- 续接时 `get_context.py` 仍报告 planning，工作区已有 155 项未提交变动。不要批量暂存、重置或整理这些历史归档 / 其他任务文件。

## 2. 远端只读核查

`gh api repos/0401lucky/Narra/commits/main` 与本地 `origin/main` 均为：

`21e9384194bc2fd439b4976bcf94bc378d9b2d2b`。

| 项目 | 2026-11-06 核查结果 |
| --- | --- |
| [Android Quality run 42165657964](https://github.com/0401lucky/Narra/actions/runs/42165657964) | `completed / success`，源码为上述提交 |
| [Deploy Pages run 42165657971](https://github.com/0401lucky/Narra/actions/runs/42165657971) | `completed / success`，源码为上述提交 |
| `gh secret list --json name,updatedAt` | 空列表；未读取任何密钥值 |
| `gh variable list --json name,updatedAt` | 空列表 |
| `branches/main` | `protected: false`；仅为当时状态，实施前需复查 |
| `actions/permissions/workflow` | 默认 `read`，不能批准 PR；按 job 显式声明必要权限即可，不更改仓库全局默认 |
| 公网 Pages `updates/dev.json` | GET 成功，仍为 `2.2.12-dev / 20212`，APK SHA 与仓库记录一致，可选更新字段为 0 |
| 既有 R2 APK 自定义域名 | HEAD 200，APK MIME，Content-Length `80308180`；本次没有重复下载旧 APK 全文 |

这些 run 证明旧提交现有 CI 已成功，不代表新自动发布链路已验证。

## 3. 仓库契约

| 证据位置 | 对本任务的含义 |
| --- | --- |
| `app/build.gradle:41`、`:81` | 版本来自 Gradle properties，versionCode 为 major × 10000 + minor × 100 + patch |
| `app/build.gradle:93`、`:113` | release 有显式签名；debug 使用默认 keystore，增加 CI 上传不应让 runner 生成新证书 |
| `gradle.properties:19` | 当前元数据 base URL 为 `https://0401lucky.github.io/Narra/updates` |
| `AppUpdateUrlPolicy.kt:5`、`:7`、`:21` | 清单仅允许 Pages host 与 `/Narra/updates`；APK 仅允许既有 R2 自定义域名及渠道路径 |
| `AppUpdateRepository.kt:141`、`:148`、`:153` | 按 versionCode 比较，新版必须有 URL / SHA，仅返回可选更新 |
| `.github/workflows/android-quality.yml:24`、`:64` | 已有构建 / JVM / Lint 及独立截图 verify；不得把截图 record 当作发布门禁 |
| `.github/workflows/deploy-pages.yml:7`、`:14`、`:35` | 当前为 push / 手动触发，pages 组允许取消运行，打包整个 `docs/` |
| `tools/update-app-update-json.ps1` | 可复用 JSON 生成、渠道映射及 APK SHA 计算；当前不验证公开下载、版本单调性或签名，不能直接充当发布门禁 |

表中 Kotlin 文件完整路径分别为：

- `app/src/main/java/com/example/myapplication/system/update/AppUpdateUrlPolicy.kt`
- `app/src/main/java/com/example/myapplication/data/repository/AppUpdateRepository.kt`

既有 `docs/发布候选记录.md` 与 `docs/updates/dev.json` 记录：

- 已发布版本：`2.2.12-dev / 20212`；包名：`com.narra.app.dev`。
- APK：`dev/Narra-v2.2.12-dev-20212-dev.apk`；大小 `80308180` 字节。
- APK SHA-256：`3f6b31b3ad42d9e5a5574f8a6824c3d18d7dec639113b3bd3ab9d87c7a031a46`。
- 签名证书 SHA-256：`3a6c57eec0641bc00830252eb821d62c9e9b2f639c5989d0bc42b29b843546e8`。
- 原会话已验证线上 APK 与本机 keystore 的证书相同；实施时再次验证拟注入 keystore 和最终云端 APK，不能仅比较文件名。

## 4. 平台约束与选择

### R2 凭据

[Cloudflare R2 Authentication](https://developers.cloudflare.com/r2/api/tokens/) 明确说明：

> The Object Read & Write and Object Read only permissions are only supported by the S3-compatible API, not the Cloudflare REST API.

因此，CI 使用仅限 `narra-updates` 的 Object Read & Write Access Key ID / Secret Access Key，通过 R2 S3 endpoint 上传和回拉。不能为套用现有 Wrangler REST 命令而升级到管理员权限。

- 该权限粒度是桶级；脚本再限制可写 key 为 `dev/`，不宣称凭据本身按前缀隔离。
- 正常桶 endpoint 为 `https://<ACCOUNT_ID>.r2.cloudflarestorage.com`，region 为 `auto`；实施前核对既有桶 jurisdiction，再确定 endpoint。
- CLI 403 / 认证失败不是对象不存在；只有明确 404 / NoSuchKey 才进入首次上传分支。
- 公共下载仍走客户端已信任的自定义域名；`r2.dev` 不能直接作为当前客户端的替代地址。
- 本机 Wrangler OAuth 可做已授权的本地核查，不复制进 Actions。

### GitHub 自动化

[GitHub workflow triggering](https://docs.github.com/en/actions/how-tos/writing-workflows/choosing-when-your-workflow-runs/triggering-a-workflow) 说明：`GITHUB_TOKEN` 引发的普通 push 事件不创建后续 workflow run，`workflow_dispatch` / `repository_dispatch` 属于例外。

发布流程因此必须在提交清单后显式执行 Pages 部署。方案采用复用的 Pages workflow（`workflow_call`），避免引入个人 PAT 或额外的 Actions 写权限。

- 现有推送网站的入口与发版调用共用同一 Pages 并发组。
- 同一时间仅部署一个站点；获得锁后读取最新 `main` 的 docs，并核对版本不倒退，避免旧 workflow 的 checkout 恢复旧清单。
- 构建 job 仅 `contents: read`；清单提交 job 仅增加 `contents: write`；Pages job 仅 `contents: read / pages: write / id-token: write`。
- 新版更新清单公开可读且字段匹配后，整个发版流程才算成功。

## 5. 验证范围

规划阶段只恢复历史、核查配置和远端运行状态，不操作 Secrets、桶对象或线上更新清单。

实现阶段必须测试发布顺序和失败路径，并取得一次真实 Actions → R2 → Pages 的成功证据。设备覆盖安装、IME / TalkBack 和物理性能不能用 CI 成功替代；设备是否可用在实施时核查，缺少实测则如实记录。

本次只对规划文件做 JSON / JSONL、上下文路径、Markdown 链接及尾空格检查；七个任务文件检查通过，任务状态仍为 planning。没有重跑 Gradle；新发布代码尚未实施。
