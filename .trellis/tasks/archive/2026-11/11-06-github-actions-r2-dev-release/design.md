# v2.3 dev 自动发布设计

状态：已实施并通过真实云端发布验收。需求及验收映射见 [prd.md](prd.md)，平台与源码依据见 [续接证据](research/resumption-and-release-contract.md)。

## 边界与取舍

这是一个端到端发版交付，保持单一任务；构建、凭据、上传及清单发布互为前置，不拆成独立发包任务。

- 新增仅从 `0401lucky/Narra` 的 `main` 手动触发的 dev 发版 workflow。一次触发后全程自动，普通 push / PR 继续走现有质量流程。
- 首包 `2.3.0-dev / 20300`；输入版本必须与源码提交的 Gradle 配置一致，拒绝任意 shell 文本、额外版本后缀或渠道。
- 保留 Pages 元数据与 R2 APK 的既有地址契约。本轮不修改 Android 更新 UI、URL 白名单或持久化逻辑。
- CI 通过 AWS CLI 的 S3 接口访问现有 R2 桶，满足桶级读写授权；使用明确版本的 CLI 并记录版本，不增加账户管理权限。
- 复用仓库 PowerShell 发布工具约定；把新增验证 / 发布辅助放入 `tools/`，让副作用步骤可注入假适配器以测试失败顺序。元数据生成继续复用 `update-app-update-json.ps1`，需要传入发行说明时使用文件参数，避免在命令文本中拼接正文。

## 数据流与 job 顺序

1. **预检**：锁定触发时 `main` 的完整 SHA，核对目标版本、渠道、必需配置、现有清单及发布对象；非本仓库 / 非 main 直接失败，不接触签名或 R2 写入。
2. **构建与验证**：在 `ubuntu-24.04 / JDK 17` 还原原 dev keystore，核验证书和私钥签名，并用 Gradle signingReport 确认 debug 实际绑定该文件及原证书；从锁定提交分别执行 `assembleDebug`、JVM、Lint、截图 verify，禁止 record 基准。
3. **产物冻结**：使用 workflow 明确安装的 build-tools `36.1.0` APK / 签名工具检查实际包名、版本及签名；不按镜像中的最高版本漂移。计算 SHA-256，产生 `release-info.json`，上传明确文件列表的 Actions artifact。
4. **R2 发布**：单独 job 下载上一步 artifact，再次核对哈希和来源。检查远端同名对象；首次上传后回拉并验 SHA，再检查公开 URL 的 HTTP 状态、大小、类型和完整内容哈希。
5. **清单提交**：只有步骤 4 成功后才生成新 `dev.json`、候选记录和已发布文档。以新鲜的 `main` 为基线重查版本、只提交允许的发布文件，以普通快进 push 更新 main。
6. **Pages 部署**：显式调用可复用的 Pages workflow，把含新清单的 docs 部署到现有站点。该步骤不依赖 bot push 再触发其他工作流。
7. **公开验收**：有界重试读取既有清单 URL，检查 app_id、channel、版本、URL、SHA 和可选更新字段；汇总源码 SHA、产物、R2 key、Pages 部署及公开地址。

构建和发布拆成 job，便于重跑失败 job 时复用原构建 artifact。失败时不自动重新编译并覆盖同版本对象。

## 签名与凭据契约

| 配置 | 存放位置 | 使用范围 |
| --- | --- | --- |
| `NARRA_DEV_KEYSTORE_BASE64` | 本仓库 Actions Secret | 构建 job 还原旧 dev keystore |
| `NARRA_DEV_STORE_FILE` | 每次 run 的 `GITHUB_ENV` | 指向 `RUNNER_TEMP` 内已还原的原 key，不新增仓库配置 |
| `R2_ACCESS_KEY_ID` | 本仓库 Actions Secret | 仅 R2 发布 job |
| `R2_SECRET_ACCESS_KEY` | 本仓库 Actions Secret | 仅 R2 发布 job |
| `CLOUDFLARE_ACCOUNT_ID` | 本仓库 Variable | 构造核实过的 R2 endpoint |
| 旧证书 SHA-256 | 仓库发布契约 / 非秘密配置 | 还原前后及最终 APK 的固定校验值 |
| `GITHUB_TOKEN` | GitHub 按 job 提供 | 最小范围的读仓库 / 发布文档 / Pages 权限 |

固定证书指纹：`3a6c57eec0641bc00830252eb821d62c9e9b2f639c5989d0bc42b29b843546e8`。

- 原 dev 的标准 Android debug alias / password 已确认。CI 将 key 放在 `RUNNER_TEMP/narra-original-dev.keystore`，通过 `NARRA_DEV_STORE_FILE` 明确传入 Gradle；仅 CI debug 使用独立 `narraDevCi` 配置，本地默认、baseline 和 release 的签名配置保持原样。
- 构建前捕获 `:app:signingReport`，验证唯一 debug 块的配置名、alias、路径和原证书；不输出整份报告。签名 assemble 禁用任务输出缓存，仍复用依赖下载缓存。第三次运行的 package task 实际执行，没有缓存复用证据，此开关只作为确定性边界。
- 最终 APK 校验保留唯一原证书限制，同时输出安全的预期 / 实际 SHA-256、不同指纹 / 记录数量及 build-tools / apksigner 版本，区分解析问题与真实证书不同；不输出证书主题、密钥或 CSR。
- Secret 使用标准输入或受控客户端配置，不把内容拼进命令行或日志；不上传 `.android/`、keystore、临时凭据文件或包含它们的缓存。
- 临时签名文件在 `always()` 清理。构建、R2 和 GitHub 写权限只对需要它们的 job 提供，不对 PR 流程共享。
- 凭据只允许既有桶对象操作。发布脚本固定桶及 `dev/` key 前缀；不创建桶、改 DNS、开放新公网域名或更改仓库全局 workflow 默认权限。
- 已有 Secrets 配置授权有效；若实施时 Cloudflare 登录失效，只解决实际登录阻塞，不重新询问已确定的发布模式。

## 产物与更新清单

首包文件名：`Narra-v2.3.0-dev-20300-dev.apk`，R2 key：`dev/Narra-v2.3.0-dev-20300-dev.apk`。

公开地址使用 `https://download.lsa1230.dpdns.org/dev/<apkName>`；如需版本 query 消除历史 404 缓存，query 不参与对象 key。不可把 `r2.dev` 自动回退地址交给旧客户端。

`release-info.json` 仅包含：源码 SHA、workflow run ID / attempt / URL、构建时间、applicationId、版本名 / versionCode、文件名 / 字节数、APK SHA-256、证书 SHA-256及构建工具版本。通过 job output 固定 artifact 标识，重跑发布时仍使用该标识；不把私钥当作“签名信息”记录。

`docs/updates/dev.json` 保持当前 schema：`app_id`、`channel`、`latest_version_name`、`latest_version_code`、`minimum_supported_version_code`、`apk_url`、`apk_sha256`、`published_at`、`release_notes`。`minimum_supported_version_code` 恒为 `0`。

发行说明来自已提交的 v2.3 变更记录，按结构化文本传入工具；构建、上传或公开验证未完成时，只能记录“待发布”，不能更新当前已发布版本或提前填新 URL。

## 并发、单调性与失败处理

发版使用固定 dev 并发组并关闭运行中取消；Pages 单独使用全入口共享的并发组。不能依赖 GitHub 队列严格按提交顺序执行。

| 场景 | 必须发生的行为 |
| --- | --- |
| 配置缺失、版本不合法、签名不同、构建失败 | 无 R2 写入、无新清单提交 |
| R2 403 / 网络错误 | 报告真实阶段，不能当作 404 后覆盖对象 |
| 同版本对象已存在且 SHA 一致 | 可重新验证并继续未完成的发布；不重复覆盖 |
| 同版本对象存在但 SHA 不同 | 拒绝覆盖；选择原构建 artifact 重试，或另发更高版本 |
| 远端回拉 / 公开 URL 校验失败 | 保留旧清单；对象可留待原 artifact 重试，无自动删除 |
| 最新已发布 versionCode 高于目标 | 拒绝降级或判定当前任务已过期，不写旧清单 |
| 相同版本清单的 APK URL / SHA 不一致 | 拒绝替换，保留冲突证据 |
| `main` 在清单提交时发生变化 | 重新读取并核对版本 / 清单；只重放本次允许的文档变动，冲突或版本变化则停止，绝不 force push |
| Pages 失败或公网清单仍旧 | 整体发版未完成；允许重试部署，保持已验证 APK 和来源报告 |

Pages workflow 增加 `workflow_call`，保留既有 push / 手动网站发布入口。所有入口获得 Pages 锁后从最新 main 制作 docs 产物，部署前拒绝版本倒退；旧事件不能部署旧 checkout。调用方为 Pages job 显式提供 `pages: write` 与 `id-token: write`，不引入 PAT。

上传阶段并不承诺 Git、R2、Pages 跨服务原子事务：中断时最多留下已验证但未被清单引用的 APK，或尚待 Pages 发布的安全清单。任何新清单始终只引用已校验可下载的 APK。

## 文档、验证与回退

- 实施前提交构建版本和待发版说明；真实校验完成后再由发布阶段同步当前分发版本、APK 元数据及候选记录。具体文件见 [执行计划](implement.md)。
- 本地只做脚本定向测试、workflow 静态检查及必要更新协议回归；正式 APK 由 Actions 构建。源码改变后，旧提交的绿色 CI 不作为新提交已通过的证据。
- 云端验证包括现有 JVM / Lint / 截图 verify；不自动接纳视觉基准。
- 失败时停用 / 修复对应 workflow 并重新运行，保留历史 APK。已公布版本不自动降级；修正代码采用更高版本。若需紧急回撤清单，必须另行说明影响并处理。
- 真机覆盖安装和用户数据保留的实测结果单独记录。签名 / applicationId 一致是兼容必要条件，不能替代实际安装验证。
