# dev 云端发布与更新契约

## 1. Scope / Trigger

修改 Actions 发版、签名、R2 上传、Pages 更新清单或发布脚本时阅读本规范。
主入口为 `tools/dev-release.ps1` 与 `tools/dev-release.psm1`；发版步骤由
`.github/workflows/dev-release.yml` 编排，网站部署由 `deploy-pages.yml` 复用。
客户端下载协议仍遵守 [网络规范](./network-guidelines.md)。

## 2. Signatures

```powershell
# 人工指定已提交到 main 的版本，触发后自动完成。
gh workflow run dev-release.yml --repo 0401lucky/Narra --ref main -f version=2.3.0

# 离线检查；模块和真实入口均覆盖 -File 与 GitHub pwsh wrapper 启动。
pwsh -NoProfile -File tools/tests/dev-release-startup.tests.ps1
```

`dev-release.ps1` 要求 PowerShell 7.2+，接受 `-Mode`：`Preflight`、
`VerifyKeystore`、`FreezeArtifact`、`Publish`、`PreparePages`、`VerifyPublished`。
按模式使用 `-Version`、`-RepoRoot`、`-ArtifactDirectory`、`-KeystorePath`、
`-ExpectedSha256`、`-ExpectedCommit`。`Publish` 要核对 CI 上下文及工作区，
不能把主会话的脏工作树当作 runner 发布目录。

## 3. Contracts

- 只从 `0401lucky/Narra` 的 `refs/heads/main` 手动触发；版本输入不带 `-dev`，
  必须与已提交的 Gradle 配置和 `docs/updates/dev-release-notes.json` 一致。
- dev 包名为 `com.narra.app.dev`，versionCode 为 major × 10000 + minor × 100 + patch。
  版本边界和 APK 文件名由 `Get-DevVersion` 统一生成，不能另行拼接一套规则。
- 签名 Secret 为 `NARRA_DEV_KEYSTORE_BASE64`。还原 keystore 和 APK 实际签名均需
  匹配现有证书 `3a6c57eec0641bc00830252eb821d62c9e9b2f639c5989d0bc42b29b843546e8`。
  构建前用 runner 当前 JDK 执行 `keytool -list` 核验证书，再用 `-certreq` 验证私钥
  可以解锁并签名；密码走已有环境变量，CSR 仅捕获后丢弃，不写文件或发送请求。
  私钥还原到 `RUNNER_TEMP/narra-original-dev.keystore`，路径经 `GITHUB_ENV` 导出为
  `NARRA_DEV_STORE_FILE`；这不是新的仓库 Secret / Variable。私钥不进入缓存或
  artifact，临时 keystore 在 `always()` 步骤按同一路径清理。
- `app/build.gradle` 仅在 `GITHUB_ACTIONS=true` 且存在显式路径时创建 `narraDevCi`
  signing config，并只绑定 debug；本地默认、release、baseline 使用原配置。
  构建前无配置缓存地运行 `:app:signingReport`，捕获正文后只检查唯一 debug 块的
  config、alias、绝对路径和证书 SHA-256；只输出匹配标记及公开指纹，不打印整份报告。
- R2 仓库 Secrets `R2_ACCESS_KEY_ID` / `R2_SECRET_ACCESS_KEY` 只在发布 job 映射为
  `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY`。Object Read & Write 凭据只支持
  S3 API，不能为复用 Wrangler REST 上传而扩大到管理权限。
- `CLOUDFLARE_ACCOUNT_ID` 为非秘密 Variable；`R2_JURISDICTION` 默认为 `default`，
  endpoint 由 `Get-DevR2Endpoint` 校验。固定桶 `narra-updates`，可写 key 限 `dev/`。
  桶级凭据不等于前缀级凭据，脚本的路径限制也必须保留。
- 构建 artifact 只含 APK 与 `release-info.json`。来源报告使用 schema 1，记录
  repository、source_sha、run_id / run_attempt / run_url、version / version_name /
  version_code、application_id、channel、apk_name / apk_size / apk_sha256、
  signing_certificate_sha256、built_at、工具版本（包括 `apksigner_version`）和 release_notes。
- APK 身份校验必须保留唯一原证书限制。失败区分无法解析、多个证书记录和不同指纹，
  诊断只输出预期 / 实际 SHA-256、记录数、不同指纹数以及所选 build-tools / apksigner
  版本；不得把重复记录直接去重后放行，也不输出原始证书主题、完整工具输出或 CSR。
- `Assert-DevArtifact -CheckRun` 绑定当前 run / source，允许使用较早 attempt 已完成
  构建的 artifact。重试发布不能要求原产物的 attempt 等于最新 attempt，也不能
  默默重编译后覆盖同版本对象。
- `docs/updates/dev.json` 沿用现有 schema、Pages URL 和可选更新字段
  `minimum_supported_version_code=0`；APK 仍使用现有 R2 自定义域名。
- 先验 APK 身份与签名，再上传 / 回拉 / 公开下载验哈希，随后提交清单，最后显式
  部署 Pages 并读取公开清单。日期写入表示已验证 APK 的清单已提交，整体完成仍需
  `VerifyPublished` 成功。
- 自动发布只暂存 `Write-DevReleaseDocuments` 返回的七个文档路径；主会话提交仍需
  排除用户原有修改。不能把 runner 的干净工作树假设套用到本机工作区。
- `GetNewClosure()` 创建独立动态模块，不继承调用脚本的私有函数，也不会自动捕获
  所有上层变量。闭包所需 helper scriptblock、数据和前态先绑定到当前局部变量；
  生产入口显式 `Import-Module ... -Scope Global`，让发布函数可被这些闭包解析。
  不能用全局测试桩或跳过模块导入来补齐生产入口原本缺少的依赖。

## 4. Validation & Error Matrix

| 条件 | 结果 |
| --- | --- |
| 非目标仓库 / 分支 / 事件 | build job 在接触签名前跳过，入口也拒绝上下文 |
| 版本、包名、证书或 artifact SHA 不匹配 | 失败，无 R2 写入和新清单提交 |
| 证书可读但私钥无法解锁 / 签名 | 构建前失败，只报告阶段与退出码，不输出 CSR 或 keytool 诊断正文 |
| Gradle debug 配置 / 路径 / alias / 指纹未绑定原 key | 构建前失败；私钥预检通过不能代替实际 Gradle 配置校验 |
| R2 凭据缺失 | 发布 job 失败，成功构建的 artifact 仍可留用于后续重试 |
| S3 404 / NoSuchKey | 可以首次上传，必须带 `If-None-Match: *` |
| S3 403、认证或网络异常 | 失败，不能当作对象不存在 |
| 同版本对象 SHA 一致 | 重新验证，可继续未完成的清单发布 |
| 同版本对象 SHA 不同、原子上传竞争 | 拒绝覆盖，保留原产物 |
| 回拉或公开下载状态 / 大小 / MIME / SHA 错误 | 不提交新清单 |
| main 在发布期间推进、普通 push 冲突 | 失败并允许原 artifact 重试，不 force push |
| 清单降级或同版本 URL / SHA 冲突 | 拒绝发布旧值 |
| Pages 未包含发布提交、公网验证超时 | 整体发版未完成，保留可恢复状态 |

## 5. Good / Base / Bad Cases

- Good：main 的云端 APK 使用原证书，R2 与公开下载均通过校验，旧清单入口返回
  同一版本 / URL / SHA，工作流汇总发布完成。
- Base：构建成功但发布凭据暂缺；补配置后重跑失败 job，复用原 run 的 artifact。
- Bad：把受限凭据升级为账户管理令牌、把 403 当 404 覆盖对象，或仅因 Git push
  成功就宣称 Pages 更新已生效。

## 6. Tests Required

- `tools/tests/dev-release-startup.tests.ps1`：统一门禁，同一套模块与真实入口测试分别
  用 `pwsh -File` 和 GitHub 的 `pwsh -Command ". '<step.ps1>'"` 启动，wrapper 再调用
  测试脚本。后者多一层脚本作用域，单独 `-File` 通过不能作为其通过证据。
- `tools/tests/dev-release.tests.ps1`：版本 / 上下文、APK 身份、来源 / attempt、
  元数据单调性、403 / 404、原子上传和 GitHub 输出名称；输出字段如 `apk_sha256`
  必须允许合法数字。失败注入同时断言指定阶段 / 原因，不能把 helper 缺失等任意
  异常算作预期网络失败。签名预检用假 keytool 覆盖证书和私钥成功、解锁失败及
  证书不匹配时不得签名；断言密码环境恢复、CSR 与失败诊断不泄漏。签名报告解析还需
  覆盖 baseline 块不干扰 debug、配置 / 路径 / alias / 指纹错误、缺失 / 重复 debug，
  以及 APK 未解析 / 重复记录的安全诊断。
- `tools/tests/dev-release-entry.tests.ps1`：直接运行真实 Publish / PreparePages /
  VerifyPublished，只替换最底层 Git / S3 / HTTP / 等待；覆盖跨模块 closure、
  七文件暂存范围、main 竞争后的原 artifact 重试，以及失败时清单写入为零。
  测试移除宿主预载的模块导出后，执行入口真实 `Import-Module` 和其作用域选项，
  随后才装配 I/O 桩，避免宿主导出掩盖生产闭包作用域错误。
- workflow 变更执行 actionlint 与 PowerShell 语法检查；云端保留 JVM、Lint、
  debug 构建和截图 verify。不得用截图 record 接纳差异。
- 发版沿用现有质量 CI 的独立调用顺序：`assembleDebug` → `testDebugUnitTest` →
  `lintDebug`，随后单独执行截图 verify，全部通过后才冻结 artifact。每次调用保留
  `--stacktrace` 供失败诊断，不用 `--debug` 输出详细构建设置。仅有任务重叠或
  包装层异常时，不能将并行 / 内存竞争写成已证实的根因。
- 签名 assemble 使用 `--no-build-cache`，避免从任务输出缓存取回不同签名的 APK；
  依赖下载缓存仍可复用。缓存命中本身不是 APK 被复用的证据，应先核对 package task
  是否实际执行 / `FROM-CACHE`，不能把防御性开关写成已确认的根因修复。
- 真实发版必须另外记录 Actions run、APK 签名 / SHA、R2 回拉、公开下载和旧
  清单结果。离线假适配器测试不能替代这些结果，也不能替代设备覆盖安装实测。

## 7. Wrong vs Correct

错误：`GITHUB_TOKEN` 提交并 push 后，假设 `on: push` 会自动发布 Pages。

正确：在发版 workflow 中显式调用复用的 Pages workflow：

```yaml
pages:
  needs: [build, publish]
  permissions:
    contents: read
    pages: write
    id-token: write
  uses: ./.github/workflows/deploy-pages.yml
  with:
    release_version: ${{ inputs.version }}
    apk_sha256: ${{ needs.build.outputs.apk_sha256 }}
    publication_commit: ${{ needs.publish.outputs.publication_commit }}
```

网站与发版入口共用 Pages 并发组，在拿到锁后读取最新 main，预检拒绝旧清单。
不能让旧事件携带的 checkout 在新版本之后重新部署。
