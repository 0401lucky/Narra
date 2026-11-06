# v2.3 dev 发版全范围终审

检查日期：2026-11-06。角色：`trellis-check`。本轮只审查、读取远端状态和记录证据，唯一新增文件为本记录；未操作凭据、浏览器、Git 或其他任务文件。

## 结论

`2.3.0-dev / 20300` 的真实 Actions → R2 → Pages 发布链已经成功，原客户端清单地址已返回正确版本、APK URL、SHA-256 和可选更新字段。未发现新的生产代码阻塞项。

检查覆盖本任务全部工作流、发布脚本、测试、构建签名配置、版本文档与公开发布结果。按照 `get_context.py --mode packages`，当前为单仓库、`backend` 规范层；已加载其索引 Quality Check、质量规范、`check.jsonl` 对应规范及 PRD / design / implement。没有重复已经通过的完整 Gradle 或其他高成本验证。

## 固定来源与实际结果

| 项目 | 核对结果 |
| --- | --- |
| [源码提交](https://github.com/0401lucky/Narra/commit/c34e8e12565487499799ad81fa07a313397de678) | `c34e8e12565487499799ad81fa07a313397de678` |
| [发布 run](https://github.com/0401lucky/Narra/actions/runs/42200205327) | `42200205327`，attempt `2`，`completed / success` |
| 原构建 | attempt `1`；API 显示原始构建于 `2026-11-06T10:23:35Z` 成功 |
| 原 artifact | `dev-2.3.0-42200205327-1`，ID `13494522764` |
| APK | `Narra-v2.3.0-dev-20300-dev.apk`，`80357328` 字节 |
| APK SHA-256 | `cc6b3aa9946add66dccf58926d23fbceae4d894b15179d20dfb47b143e6b3eab` |
| 包名 / 版本 | `com.narra.app.dev` / `2.3.0-dev` / `20300` |
| 原证书 SHA-256 | `3a6c57eec0641bc00830252eb821d62c9e9b2f639c5989d0bc42b29b843546e8` |
| [publish job](https://github.com/0401lucky/Narra/actions/runs/42200205327/job/127928515829) | `success`，完成于 `2026-11-06T13:50:30Z` |
| [Pages job](https://github.com/0401lucky/Narra/actions/runs/42200205327/job/127928873978) | `success`，完成于 `2026-11-06T13:51:07Z` |
| [最终 verify job](https://github.com/0401lucky/Narra/actions/runs/42200205327/job/127929161009) | `success`，完成于 `2026-11-06T13:53:18Z` |
| [发布文档提交](https://github.com/0401lucky/Narra/commit/30746e3363091f879f9cbd2a8e70c8c78a38f9aa) | `30746e3363091f879f9cbd2a8e70c8c78a38f9aa`，父提交为上述源码 SHA |

原 APK 的下载、大小、完整 SHA-256、实际包信息和唯一原证书已独立复验，详见 [原产物检查](resumed-artifact-check.md)。本轮检查再次确认所有发布代码、测试、两个 workflow、`app/build.gradle` 和 `gradle.properties` 与已通过 CI 的源码提交无差异。

发布 attempt 2 的日志明确下载原 artifact ID，随后在 `13:50:26Z` 报告“R2 和公开 APK 验证通过”及上述提交。来源报告继续保留构建 attempt `1`；原构建未重新运行，没有产生新的同版本 APK。

## 权限、身份与发布顺序审查

- 发版只接受 `0401lucky/Narra`、`main`、`workflow_dispatch`。输入版本、Gradle 配置、发行说明、实际 APK、run / source 逐层核对；最终冻结检查源码工作树未漂移。
- 构建 job 只有 `contents: read`；publish 才具备 `contents: write`；Pages 限定 `contents: read`、`pages: write`、`id-token: write`。没有引入 PAT、正式 release 发包或更改仓库全局默认权限。
- 原私钥仅在构建 job 还原到 `RUNNER_TEMP`，通过证书、私钥签名、Gradle 实际签名配置和最终 APK 四层检查。清理步骤成功；artifact 只有 APK 与已定义来源报告字段。
- R2 两项 Secret 只映射到 publish job 的 AWS 环境变量。主会话通过 Cloudflare UI 核对专用令牌 `narra-dev-actions-20261106` 为仅 `narra-updates` 的 Object Read & Write；本代理没有读取密钥或独立进入控制台。代码另外固定桶和 `dev/` key，未把桶级权限误称为前缀级权限。
- `Publish-DevApk` 仅在明确 404 时条件 PUT，`If-None-Match: *` 防止覆盖；403 和未知错误停止。412 后回拉检查实际赢家内容，同版不同 SHA 拒绝发布。
- 实际顺序为来源校验 → R2 HEAD / 条件 PUT → R2 完整回拉验大小及 SHA → 公网 HEAD / 完整 GET 验 MIME、大小及 SHA → 再次核对 main 与版本单调性 → 七文件提交 / 普通 push → 显式 Pages → 原地址最终清单校验。
- `Publish` 的生产函数直接进行上述下载和哈希检查，离线假适配器只在测试进程存在；真实 job 成功不能被误解为仅上传命令成功。
- Pages 所有入口共用非取消并发组，获得锁后 checkout 最新 main，校验发布提交祖先和公开版本单调性；没有依赖 bot push 自动触发下一个 workflow。
- 原构建 artifact 可用于更晚 attempt；来源 run、source、大小、SHA 和原证书继续校验，没有放宽到其他 run 或未来构建 attempt。

## 文档、测试及范围审查

已直接检查发布模块 / 入口、Pages workflow、主发版 workflow、Gradle 签名差异、JSON 生成工具及主要失败路径测试。测试实际驱动生产入口、模块导入与闭包，仅替换最底层 Git / S3 / HTTP / 等待操作。

测试覆盖错误来源、缺失凭据、403、坏公网字节、同版冲突、原子上传竞争、main 推进、push 失败后原 artifact 重试、Pages 祖先 / 防降级以及强制更新字段拒绝；失败路径检查 Git 写入次数为零及旧清单保留。

通过 GitHub commit API 独立核对 bot 提交只包含设计中的七个文件：

1. `CHANGELOG.md`
2. `版本功能添加/优化.md`
3. `docs/版本策略.md`
4. `docs/发布与更新约定.md`
5. `docs/发布候选记录.md`
6. `docs/updates/dev.json`
7. `docs/updates/dev-release-info.json`

版本记录的自动改写仅限 `NARRA_DEV_RELEASE_STATUS` 标记区。自本任务起点 `21e93841` 到源码提交 `c34e8e12`，没有 `app/src` 或 `baselineprofile` 改动；没有混入 v2.4 实施、客户端 URL 放宽或新数据库迁移。主会话保留本机已有 v2.4 工作区内容，负责最终定向提交范围。

两处收尾文档同步已由主会话完成并经本代理定向复核：`implement.md` 已记录实际分步 Gradle 入口及 attempt 2 成功结果，候选记录已改为来源 JSON 的 ISO 带时区时间，并记录发布提交和 `13:53:18Z` 的最终完成时间。版本记录中 2.3 已标为已发布，2.4 仍明确保留为规划且未实施；状态标记区唯一，无冲突标记或未合并 index 条目。相关文档的 `git diff --check` 通过，生产代码依旧与冻结源码提交一致。

## 质量证据

| 检查 | 结果 |
| --- | --- |
| Android Lint | 同源码真实 build job 成功 |
| Kotlin / 测试编译 | 同源码 assemble / JVM 成功 |
| JVM 与截图 verify | 同源码真实 CI 成功，无 record 或基准替换 |
| 发布离线回归 | 同源码真实 CI 两种启动方式各 39 项，共 78 次通过 |
| actionlint / PowerShell 解析 | 已有固定验证器修复检查通过；本轮对应文件与该源码相同 |
| 实际 APK 身份、来源、原证书和 SHA | 独立本机检查通过，参见原产物报告 |
| R2 / 公网完整 GET | 真实 publish job 内完整回拉及哈希检查通过 |
| 旧 URL 公网清单与 APK HEAD | 本代理独立通过，见下节 |

## 最终公网独立验证

本代理于 `2026-11-06T13:54:48.4239833Z` 读取原客户端使用的精确清单 URL，未给清单增加缓存 query：

`https://0401lucky.github.io/Narra/updates/dev.json`

- HTTP `200`。
- `app_id=com.narra.app.dev`、`channel=dev`。
- `latest_version_name=2.3.0-dev`、`latest_version_code=20300`。
- `minimum_supported_version_code=0`，保持可选更新。
- `apk_sha256` 与原 artifact 完全一致。
- APK URL 为 `https://download.lsa1230.dpdns.org/dev/Narra-v2.3.0-dev-20300-dev.apk?v=20300`。
- `published_at` 对应 `2026-11-06T13:50:12.4747251Z`（北京时间 `21:50:12.4747251+08:00`）。
- APK HEAD 返回 `200`、`Content-Length=80357328`、`Content-Type=application/vnd.android.package-archive`。
- 原生产 `Assert-DevMetadata` 及目标版本 / SHA / URL 的附加一致性断言全部通过。

Pages 刚完成时曾短暂读到旧版本；在其缓存传播后，原地址与最终 verify 均成功。本记录以最后取得的真实结果为准，没有通过换地址绕过原入口验证。

## 验证边界

没有设备覆盖安装或用户数据保留的实测证据；主会话记录当时 `adb devices -l` 无设备。原签名与包名一致是覆盖升级的必要条件，不能替代设备实测。其余最终任务勾选、版本记录同步、Git 提交和归档由主会话负责；本代理没有执行这些操作。
