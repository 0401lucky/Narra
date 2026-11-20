# 2.4.1-dev 云端发版续接

2026-11-20。恢复会话 `01a1f690-3086-7f91-993e-e1ef8bb90bf8` 最后一条发版指令；
该指令此前因 `invalid_encrypted_content` 没有执行。用户本轮再次明确要求推送 GitHub、
通过 Actions 构建、上传 R2 和发布内置更新，并明确把已有 UI 改进一并提交和打包。
沿用已有 D6 任务及 2.4.1 范围，不新建重复任务。

## 源码与提交

- 恢复时 D6 `delivery-source.json` 的 1531 项原字节全部一致，无缺失或漂移。
- 完整交付源码包含尚未提交的 2.4 记忆依赖、D1–D6 和当前 UI/主题/资源/测试；
  Android、baselineprofile 与 UI 资源在该清单之外的遗漏为 0。
- 核心提交 `5a62efab0299773a3d158a2f7a4c5d8c0bc90eb2`：504 路径，
  包含已验收源码/UI 与 14 份相关规范，不包含 APK、临时截图、日志、Web 或宣传工程。
- 版本准备提交 `74d152d9cf4873a6c7ca16c81aebdf1811d5c527`：6 个版本/发行说明文件，
  目标 `2.4.1-dev / 20401`；`dev.json` 和上一实际产物信息保持原值。
- 固定 v53/v55/v56 schema JSON 和 S3 SQL 精确补 `text eol=lf`；连同原 v54，
  暂存 blob 与原文件字节及 SHA-256 全部一致。不修改期望哈希或全局 Git 配置。

具体范围与独立复核见 [源码清单](release-scope-2026-11-20.json) 和
[发布前审查](release-review-2026-11-20.md)。

## 已取得验证

- 继承对应源码的工程证据：最新去重 JVM 2956/2956、原生 9/9、52 行工程账本，
  编译/双 APK/baseline/Lint；本次没有把旧本地 APK 当作新版产物。
- 发布脚本模块 28 项、真实入口 11 项，两套分别在 `pwsh -File` 与 GitHub wrapper
  下通过，共 78 次断言；候选版本、发行说明、旧可选更新清单和暂存范围检查通过。
- 独立复核确认原 dev 证书、固定 build-tools 36.1.0、R2 路径与 7 个发布文档门禁保持。

## 当前进度与完成条件

本地提交和 GitHub 推送已完成。从 `main` 手动触发 `dev-release.yml`，输入
`version=2.4.1`；实际 [Actions 44734464478](https://github.com/0401lucky/Narra/actions/runs/44734464478)
锁定提交 `74d152d9cf4873a6c7ca16c81aebdf1811d5c527`。原证书/私钥、Gradle debug
签名配置、版本预检与发布脚本门禁已在云端通过，当前正在 assemble。

首次 Git 连接重置来自 Git 未使用当前系统代理；主会话检测现有代理后只用命令级
`http.proxy` 参数重试成功，未调整全局设置。必须继续按实际结果记录：

1. 云端 assemble、JVM、Lint、截图 verify 和原签名验证通过，冻结唯一 Actions artifact。
2. R2 上传/回拉及公开 APK 的状态、大小、MIME 和 SHA-256 校验通过。
3. 工作流提交 7 个发布文档、显式部署 Pages，并确认旧公开清单返回 20401 与同一 APK。
4. 主会话复核公开产物并归档本轮工程任务；不夹带其它未跟踪工程或诊断文件。

本地 53 项截图中 52 项差异尚未接纳；云端 verify 保留，不自动 record、修改基准或删测试。
真实模型/MiMo、物理设备音效和真机覆盖安装仍未实测。当前不宣称发版完成。
