# SDK 验证器输出格式漂移：复现与固定版本

## 已确认的原因

[第四次 Dev Release 42192056230](https://github.com/0401lucky/Narra/actions/runs/42192056230)，源码 `59905a8859a7cdab6b22a2400c0e93a4f54b6669`，Gradle 原签名绑定及 Android 门禁均通过。FreezeArtifact 选择镜像中最高的 build-tools 37.0.0，apksigner 自报 0.9，返回 `certificate_records=0 / actual_sha256=unparsed`；本机实际失败日志 `verifier-37-failure.txt`。

在独立 TEMP 目录下载官方 Linux 37.0.0 工具包，按 SDK 官方目录校验大小及 SHA-1 `70954e99f4c3d9d46ee70fa32624672fe7cd6ebe`，只提取 apksigner.jar，没有安装或修改全局 SDK。使用同一份旧公开 `2.2.12-dev` APK 实际执行验证：退出 0、stderr 为空、Number of signers 为 1，公开指纹仍是原证书，但其行格式变为：

```text
V2 Signer: certificate SHA-256 digest: 3a6c57eec0641bc00830252eb821d62c9e9b2f639c5989d0bc42b29b843546e8
```

36.1.0 对应的是 `Signer #1 certificate SHA-256 digest: ...`。相同有效 APK 经过真实 37 输出再进入现有 `Assert-DevApkIdentity`，精确复现零记录错误；本机SDK 37 输出证据 `verifier-37-comparison.txt`。这是已确认的验证器输出协议漂移，不是实际错误证书的证据。新云端 APK 的实际证书仍须由修复后的真实 run 验证。

## 最小修复

- `.github/workflows/dev-release.yml` 明确安装 `platform-tools build-tools;36.1.0`。
- `tools/dev-release.psm1` 新增固定版本选择函数，New-DevArtifact 使用该函数返回的工具路径与版本；缺失 / 不完整直接失败，不选择更高或更低的替代版本。
- `tools/tests/dev-release.tests.ps1` 补充更高版本并存仍选择 36.1.0、安装声明对齐及缺失 / 不完整禁止回退的回归。
- 不扩展接受 37 的 scheme 标签，不改变原来的单条原证书要求。APK 源码、版本、Gradle 签名绑定和全部 Android 门禁保持不变。

## 真实验证链与宿主证明

实际执行的是以下生产函数链，输入为既有公开 APK，而非手写签名字符串：

```text
Get-DevVerificationTools(本机 SDK)
→ Invoke-DevCommand(固定 aapt2 / java -jar 固定 apksigner)
→ Assert-DevCommand
→ Assert-DevApkIdentity(实际 badging, 实际 stdout, 2.2.12)
```

固定工具为 36.1.0，所有原生命令退出 0，身份 / 原证书通过，记录数 1、不同指纹数 1；本机输出 `verifier-pinned-chain.txt`。

另外下载并校验官方 Linux 36.1.0 包（SHA-1 `936a0d6bd5ae3e2118a7567dddbc95ff67ed46e9`），其 apksigner.jar 与本机实际测试的 Windows 36.1.0 jar 完全相同，SHA-256 均为 `71e18adf733f5e112d1f062dbe6b0c2eb439a4d7c773d083c42a703c66f56df1`；本机宿主工具证明 `verifier-36.1-package-proof.txt`。两次下载都只在 TEMP 检查，没有读取私钥或执行本地 App 构建。

## 定向检查

- 统一 GitHub wrapper 启动检查通过：`-File` 与 GitHub wrapper 各 28 项模块 + 11 项实际入口用例，即同一组 **39 个用例执行两次，78 次通过**；本机日志 `verifier-pin-startup.txt`。
- actionlint 1.7.12、改动 PowerShell 语法和定向空白检查通过。
- 沿用已经成功的 Android 门禁证据，不重跑未改变的 App / Gradle 构建，也不把旧 APK 检查冒充新的云端 APK 验收。
- 代码仅修改上述三个文件，另同步规范、技术设计及研究记录；没有 commit / push、私钥操作或云端写入。主会话可提交后开启下一次真实 run。
