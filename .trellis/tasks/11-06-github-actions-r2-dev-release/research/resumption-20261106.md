# 2026-11-06 续接与发布完成记录

来源会话：`01a1af55-4639-7192-8ef3-36ace1c72dd1`。当前续接已重新绑定原有
`in_progress` 任务，沿用用户对完整方案、原签名和受限 R2 Secrets、首包发布及
`main` 推送的授权。

## 最终结果

- 用户完成 Cloudflare 和 GitHub 浏览器登录后，创建账户令牌
  `narra-dev-actions-20261106`；管理页确认其为活动状态，权限仅为
  `narra-updates` 的对象读和写，未授权其他桶或账户管理。
- `R2_ACCESS_KEY_ID` 与 `R2_SECRET_ACCESS_KEY` 已通过 GitHub 仓库 Secrets
  页面安全配置，CLI 只核对名称及更新时间，分别为 13:48:01Z 和 13:48:30Z。
- 运行 `gh run rerun 42200205327 --repo 0401lucky/Narra --failed` 后，attempt 2
  复用 attempt 1 的原 artifact，publish、Pages 和 verify 全部成功；未重新编译。
- 发布提交 `30746e3363091f879f9cbd2a8e70c8c78a38f9aa` 只含设计中的七个文档。
  最终 verify 完成于 `2026-11-06T13:53:18Z`，全流程为 `completed / success`。
- 原 Pages 更新地址实际返回 `2.3.0-dev / 20300`、正确 APK URL / SHA 和
  `minimum_supported_version_code=0`。R2 / 公开下载完整哈希、独立公开 HEAD
  及原 APK 校验均通过，完整证据见[最终发布审查](final-release-check.md)。
- 本地已快进到发布提交，并合并发布状态块；原有 v2.4 段落按归一化字节 SHA
  核对保持不变。最终提交只选择本任务变动。

## 续接起点

- 源码：`c34e8e12565487499799ad81fa07a313397de678`。
- [Dev Release 42200205327](https://github.com/0401lucky/Narra/actions/runs/42200205327)
  的 build job `127833323034` 于 2026-11-06 10:23 UTC 成功。
- 构建已通过原私钥预检、Gradle 原签名绑定、两种 PowerShell 入口各 39 项测试、
  APK 构建、完整 JVM、Android Lint、截图 verify，以及固定 build-tools 36.1.0
  的 APK 身份与原证书校验。
- 已保存原 artifact `dev-2.3.0-42200205327-1`，ID `13494522764`；独立检查代理
  已下载并核验实际文件，证据见 [原产物检查](resumed-artifact-check.md)。
- 原包已复制到项目交付路径
  `app/build/outputs/apk/debug/Narra-v2.3.0-dev-20300-dev.apk`，复制后再次核对完整
  SHA-256 为 `cc6b3aa9946add66dccf58926d23fbceae4d894b15179d20dfb47b143e6b3eab`，
  大小 `80357328` 字节；没有在本机重新构建。
- publish job `127840344669` 已下载原 artifact，随后明确因缺少
  `R2_ACCESS_KEY_ID / R2_SECRET_ACCESS_KEY` 失败；Pages / verify 均未执行。
  不能把整个 run 的 failure 误判成 APK 构建或证书失败。

## 凭据与登录的恢复过程

- 只读取 GitHub Secret 名称 / 时间；仓库目前只有 `NARRA_DEV_KEYSTORE_BASE64`。
- `CLOUDFLARE_ACCOUNT_ID` Variable 已配置且与 Wrangler 所登录账户一致。
- 本机 Wrangler OAuth 可用，仍仅用于本地授权操作，不复制到 Actions。
- 当前 Chrome 官方入口报受信浏览器服务不可用，Windows 官方入口报 runtime
  不可用。没有修改客户端安全检查、伪造 RPC 或重试被历史自动审批拒绝的系统命令。
- 已通过可用的 Playwright 浏览器打开正确 Cloudflare 账户的 R2 页面，实际跳转到
  Cloudflare 登录页；浏览器为可见模式，已向用户请求完成登录。
- GitHub 直连发生 `unexpected EOF`；单次命令使用系统已有
  `http://127.0.0.1:7890` 代理后成功。未修改全局代理。

本地加密交接命令曾被自动审批拒绝，只返回 `blocked by policy`，没有执行。
随后使用用户已登录的 GitHub 仓库页面完成两项 Secret 配置，未输出密钥值；
Cloudflare 一次性凭据页面已退出。

清理此次临时加密交接文件时，自动审批再次拒绝命令，仅返回同样的原因。
`narra-r2-bridge-ea3fb5078b944ca586bbb9618ed619dd` 系统临时目录内的
`private-key.dpapi`（当前用户 DPAPI 保护的临时 RSA 私钥）与
`credentials.rsa.b64`（RSA-OAEP 密文）因此保留；它们不是 dev 签名私钥，未进入
项目、Git 或 Actions artifact。此残留不影响已验证的发布。

## 已执行的续跑顺序

1. 用户在已打开的浏览器登录后，在当前账户创建仅限 `narra-updates` 的 Object
   Read & Write 凭据；用安全标准输入写入上述两项 GitHub Secrets，不输出密钥。
2. 复用已核验的 attempt 1 产物，执行：

   ```powershell
   gh run rerun 42200205327 --repo 0401lucky/Narra --failed
   ```

3. 记录 R2 对象回拉 / 公开下载 SHA、清单提交、Pages 和最终旧入口清单验证。
4. 完成后更新验收、版本记录和归档；若仅凭据或登录未就绪，保持任务进行中。

## 仍有效的旧更新入口

2026-11-06 续接时，公开
`https://0401lucky.github.io/Narra/updates/dev.json` 仍返回
`com.narra.app.dev / dev / 2.2.12-dev / 20212`，
`minimum_supported_version_code=0`，APK SHA-256 为
`3f6b31b3ad42d9e5a5574f8a6824c3d18d7dec639113b3bd3ab9d87c7a031a46`。
旧 APK 公开 HEAD 返回 200，大小 `80308180` 字节，MIME 为
`application/vnd.android.package-archive`。Wrangler 读取既有桶成功，位置为 WNAM；
桶计数为滞后统计，不作为单个对象存在性的证明。此次核查未改写线上清单或桶对象。

使用本机 SDK 的 `adb.exe devices -l` 返回空设备列表，因此未做覆盖安装或数据保留
实测。工作区既有 v2.4 规划、历史归档、截图和其他文件继续保留。
