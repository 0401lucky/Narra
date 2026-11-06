# PowerShell 启动作用域修复与复验

## 真实失败

- [首个 Dev Release run 42178061696](https://github.com/0401lucky/Narra/actions/runs/42178061696) 的源码为 `95460b5887a52178a7a9a317f7182f46dad79512`。
- 还原并校验原 dev 签名已通过，`Test release guards` 为 12 通过 / 7 失败，因此没有生成或上传新 APK。
- 六项失败找不到测试私有函数 `Assert-True`；提交前版本复查用例因读取到空前态，报缺少 `app_id`。
- 本地使用与 GitHub 相同的 `pwsh -Command ". '<step.ps1>'"`，让 wrapper 再调用原测试脚本，复现完全相同的 12 / 7。证据：[wrapper](github-style-module-wrapper.ps1)、本机日志 `pwsh-module-wrapper-before.txt`。此前单独 `pwsh -File` 通过不能证明该启动方式通过。

## Bug Analysis: 闭包依赖了启动方式的隐式作用域

### 1. Root Cause Category

- **D / Test Coverage Gap**：只从 `-File` 验证测试进程，没有覆盖 Actions dot-sourced wrapper 带来的额外脚本作用域。
- **E / Implicit Assumption**：误把 `GetNewClosure()` 当作能够保留所有父级函数和变量的词法闭包。它实际创建独立动态模块；`New-FakeR2` 内的私有 `Assert-True`、上层 `$apk` 及另一个回调中的 `$old` 并不都是当前局部捕获值。

### 2. Why Previous Verification Missed It

- `-File` 入口把部分测试状态放在闭包仍可访问的位置，掩盖了 wrapper 下的私有脚本作用域差异。问题可在 Windows 同样复现，不是 Android 或仅 Linux 的行为。
- 原入口测试为了保留假 I/O，跳过了生产脚本的 `Import-Module`。即使其 [wrapper](github-style-entry-wrapper.ps1) 运行 11 / 11 通过（本机日志 `pwsh-entry-wrapper-before.txt`），也无法据此证明实际导入范围正确。
- 一项网络失败测试只断言“抛出了异常”，使 helper 缺失也可被误算成网络失败。本次同时收紧为目标阶段和错误原因断言。

### 3. Prevention Mechanisms

| 优先级 | 机制 | 实施结果 |
| --- | --- | --- |
| P0 | 显式闭包依赖 | 在创建假 S3 / 下载闭包前，将断言 scriptblock、fixture 字节和预期 URL 绑定到当前局部变量；版本复查也先绑定旧前态 |
| P0 | 生产函数可见性 | `dev-release.ps1` 使用 `Import-Module ... -Scope Global`，明确生产闭包可解析的导出范围 |
| P0 | 实际导入回归 | 入口测试先清理宿主预载的模块导出，再执行真实 `Import-Module` 及其作用域选项，随后才安装 Git / S3 / HTTP / 等待桩；不会由宿主代替入口补齐依赖 |
| P0 | 两种启动方式 | 新增 `dev-release-startup.tests.ps1`，模块与实际入口套件均在 `-File` 和 GitHub wrapper 两种方式下运行；发版 workflow 改调该门禁 |
| P1 | 失败原因断言 | GET / 公开下载失败用例同时验证实际到达的阶段和对应错误，保留清单零写入断言 |

### 4. Systematic Expansion

- 已检查生产 `Publish` 的 Git、读清单、上传和提交四个闭包，并保留其原 source / run / attempt、原子写入、同版冲突、main 竞争及七文件限制。
- 真实 `Publish`、`PreparePages`、`VerifyPublished` 均在清除宿主导出、执行真实模块导入后通过两种启动方式。没有通过强制全部使用 `-File` 绕过问题，也没有增加生产测试开关。
- 旧 `dev-release.tests.ps1` 默认仍可运行模块与入口测试；新增 `-SkipEntryTests` 只让启动门禁将两个套件分别置于受控启动方式中，避免重复调用。
- `tools/dev-release.psm1`、更新 JSON 生成器、Kotlin、Gradle、APK 元数据和当前线上 JSON 均未为此次修复改变。

### 5. Knowledge Capture

- 已更新 `.trellis/spec/backend/dev-release-guidelines.md` 的统一检查命令、动态闭包 / 导入作用域契约、真实导入隔离及失败原因断言。
- 仓库没有 `src/templates/markdown/spec/`，没有创建不属于此 Android 项目的模板副本。
- 提交和后续真实发版仍由主会话完成；检查代理没有 commit / push、配置凭据或操作真实 R2。

## 最终验证

实际执行的是新的 Actions step wrapper，而它内部再次分别运行两套启动方式：

```powershell
pwsh -NoLogo -NoProfile -NonInteractive -Command ". './.trellis/tasks/11-06-github-actions-r2-dev-release/research/github-style-startup-wrapper.ps1'"
```

常规本地统一入口：

```powershell
pwsh -NoProfile -NonInteractive -File tools/tests/dev-release-startup.tests.ps1
```

最终 [wrapper](github-style-startup-wrapper.ps1) 和本机日志 `pwsh-startup-after.txt` 对应已清除宿主旧导出的真实导入测试。日志保留在本机研究目录并遵守仓库忽略规则，真实云端失败可从上方 Actions run 查阅。

| 启动方式 | 模块用例 | 真实入口用例 | 结果 |
| --- | --- | --- | --- |
| `-File` | 19 / 19 | 11 / 11 | 30 项通过 |
| GitHub dot-sourced wrapper | 19 / 19 | 11 / 11 | 30 项通过 |

合计为同一组 30 个用例运行两次，共 60 次通过，退出码 0；不是新增 60 个独立用例。

- actionlint 1.7.12 检查两份发布 / Pages workflow 通过。
- PowerShell 源码、测试语法及定向空白检查通过。
- 本次只改 PowerShell / workflow / 规范，不重复运行本地 Gradle；先前本地 Kotlin / JVM 编译、18 项更新回归及 Lint 结果保持其原验证边界。
- 新提交的真实 GitHub 发版门禁仍需主会话再触发，本地作用域复验不等同真实 Linux、签名产物、R2 或 Pages 验收。
