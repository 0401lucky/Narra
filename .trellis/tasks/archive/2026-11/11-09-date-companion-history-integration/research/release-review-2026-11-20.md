# 2.4.1-dev 发布前独立审查

2026-11-20。工程源码、已授权 UI 改进及版本准备可以提交并触发既有 Actions。
未发现遗漏 Android/UI 资源或签名、版本、发布顺序的工程阻塞。固定来源换行保护缺口
已由主会话补齐。52 项本地截图差异仍未接纳，云端截图 verify 与最终公开清单验证
必须取得实际结果后才能确认发布完成。

本报告按主会话分工只读审查，仅写本文件；未运行 Gradle、Git、设备、截图捕获或
发布操作，未改源码。用户最新要求已授权工程归档、推送、云端构建/R2/更新及现有
UI 改进，旧 PRD/交接中的“未授权提交/归档”是历史状态，不能再作为当前权限阻塞。
该授权不等于接纳新的截图基准。

## Findings (fixed)

- File: `.gitattributes`
- Issue: 根规则仅固定 54.json/v54.sql，嵌套规则仅固定 v48–52.sql；53/55/56 的
  schema JSON 与 SQL 存在原始 SHA 合同但缺少换行保护。Windows 后续检出可转成
  CRLF，使原始字节断言及 S3 fixture 校验失效。
- Fix: 主会话根据本审查建议，对 53/55/56 的三份 JSON 和三份 SQL 精确追加
  `text eol=lf`，回报六份 `check-attr` 均通过、原文件字节未改。未改旧 schema、
  identity、SHA 常量或全局 Git 配置。
- Evidence: 只读字节检查显示 48–57.json、v47–56.sql 当前均为 LF。
  `56.json` 原 SHA 为 `d5e2dff049818be64c7ab4b3bf2b767b99f1bc0546756f10a333ca05e903eab7`，
  与 `ContextTransferSchemaContractTest`、`ContextTransferMigrationTest` 一致；
  `v56.sql` 原 SHA 为 `57da481f5082135f40b13bdbc4ac722c5b217e02030f3f1e485d84b92e59af51`，
  与 `LegacyUpgradeFixtureBuilder.SCHEMA_ASSET_SHA256` 一致。当前 Ubuntu 构建并未因
  换行失败，此修复处理的是检出可复现性缺口。

## Findings (not fixed)

- File: `research/validation/15-screenshot-verify/validation.json`、
  `comparison-with-d4.json`、`final-validation-summary.json`
- Issue: 截图验证实际失败 52/53，`acceptance=pending`。可以确认 34 项 actual 与
  D4 逐字节一致，D4 交接又记录这些图与 D3 一致；另外 18 项 E05 没有 D4 before。
  这些证据证明既有差异的延续，尚不能证明 52 项全部属于已接纳的产品变化。
- Why not fixed: 本轮未授权视觉基准接纳，且主会话独占截图/云端执行权。
  `.github/workflows/dev-release.yml` 仍在冻结 artifact 前运行全截图包 verify，
  没有 `continue-on-error` 或 record。目标 Ubuntu 若失败，应按实际结果审查
  actual/compare、源码与场景，不自动重录基准、删除用例或绕过计数守卫。

| 截图类 | 本地通过 | 本地像素差异 |
| --- | --- | --- |
| OfflineRoleplayScreenshotE04Test | 1 | 13 |
| PageScreenshotH01Test | 0 | 18 |
| PageScreenshotH02Test | 0 | 3 |
| SessionSidebarScreenshotE05Test | 0 | 18 |

## 源码与 UI 范围

读取 `release-scope-2026-11-20.json`：原 delivery manifest 内有 491 条 dirty，
本轮版本参数更新后为 492 条；其中 UI/资源/相关测试/截图登记文档路径 90 条。
D6 的 75 工程路径仅是相对 D6 起点的增量，完整包还需要未提交的 v2.4 记忆依赖
与 D1–D5，包括模型、Room/迁移、AppGraph、协议/Gateway、ViewModel 和入口接线。
不能只提交 D6 增量。

独立读取完整 1531 项 manifest 后，对磁盘逐文件枚举 `app/src/main`、`debug`、
`baseline`、`test`、`androidTest` 和 `baselineprofile/src`，六组 manifest 外文件均为
0。`app/src/main/res` 的 48 文件全部包含，未发现需追加的 UI 图像、XML、字体或
其它打包资源。当前 dirty 资源包括 `strings_roleplay.xml` 和 debug loopback XML；
其对应源码/源集配置也在清单内。没有 dirty PNG 候选混入本轮范围。

UI 改进真实涉及 `ChatAppTheme` 的主题背景/surface/container 解析、无背景皮肤、
玻璃/气泡/长文、线上页面与侧栏、导航、陪伴与记忆管理入口，均由 delivery manifest
覆盖。截图宿主使用真实页面和生产主题解析，H01/H02、E04、E05 与这些路径有直接
关系；该关系不能代替每张差异图的原因分析或人工接纳。未发现需要剥离的明确无关
源码。此前全范围检查和本轮发布审查分别提供工程行为与发布契约证据。

主会话随后回报源码/UI/14 个相关 spec 已按具体清单暂存 504 路径，
`diff --cached --check` 通过，未含 APK 或 manifest 外来源。本审查未自行执行 Git，
不把该回执表述成独立重跑结果。后续归档由主会话依当前授权另行记录。

## 版本、签名与发布契约

- `gradle.properties` 当前为 2.4.1；Gradle 和 `Get-DevVersion` 同用
  major × 10000 + minor × 100 + patch，得到 20401。
  预期包为 `com.narra.app.dev`、`Narra-v2.4.1-dev-20401-dev.apk`。
- `docs/updates/dev-release-notes.json` 当前 `version=2.4.1`，发行说明为非空数组；
  `CHANGELOG.md` 的 `## [2.4.1-dev] - 待发布` 唯一。其余自动模板定位及
  `NARRA_DEV_RELEASE_STATUS` 区域均唯一，满足 `Write-DevReleaseDocuments` 的定位合同。
- `docs/updates/dev.json` 保留已发布 `2.3.0-dev / 20300`、原 URL/SHA 和
  `minimum_supported_version_code=0`。策略和发布约定仍保留当前已发布基线，
  新包公开校验前不提前指向 20401，符合发布顺序。
- 工作流手动限定 `0401lucky/Narra` 的 main，并检出锁定源码 SHA。还原原 keystore
  后核验证书和私钥，再核对 Gradle debug 配置；CI 签名只绑定 debug。
  预期唯一证书 SHA-256 保持
  `3a6c57eec0641bc00830252eb821d62c9e9b2f639c5989d0bc42b29b843546e8`。
  APK 验证工具固定 `36.1.0`，多证书/无法解析/不同指纹均拒绝。
- 顺序保持 assemble → JVM → Lint → 截图 verify → APK 身份/签名/来源冻结 →
  R2 条件上传 → 回拉/公开 HEAD 与内容 SHA 验证 → 七文档提交 → 显式 Pages →
  最终公开清单。S3 403 不当作不存在，同版本不同 SHA 拒绝覆盖，main 竞争不强推。
- 自动发布只返回并暂存七份文档：`CHANGELOG.md`、`docs/版本策略.md`、
  `docs/发布与更新约定.md`、`版本功能添加/优化.md`、`docs/发布候选记录.md`、
  `docs/updates/dev.json`、`docs/updates/dev-release-info.json`。
  构建 artifact 仅 APK/来源 JSON，临时签名文件由 `always()` 清理。
- Pages 接收 publication commit/version/SHA，最终旧入口还需匹配
  `2.4.1-dev / 20401`、APK URL/SHA。当前未取得本轮 Actions/R2/Pages 结果，
  不提前写成发布成功。

## Verification

- Lint: pass（引用最终证据：0 errors / 61 warnings / 3 hints；本代理未重跑）。
- TypeCheck: pass（引用当前工程的 Kotlin/AndroidTest/baseline 编译及双 APK 证据；
  本轮版本参数/换行规则/发版文档变化之后由云端重新构建确认）。
- Tests: JVM 最新唯一 2956/2956、原生最新唯一 9/9 pass；本地截图 verify fail，
  1 匹配 / 52 差异，单列 pending。来源为 `research/validation/final-validation-summary.json`
  和实际轮次索引，未累加重复轮次。
- 发布脚本：主会话回报两个真实启动入口各 39 项通过；本代理已读取入口、模块与
  workflow 的实际顺序/校验/七文档代码，未重跑外部 I/O。
- 只读文件完整性：1531 项交付清单、六组源码/资源枚举、固定 schema/SQL 的 LF
  与原 SHA 定向检查通过。delivery 冻结记录继续保留原值；当前已授权版本准备和
  `.gitattributes` 修复应作为发版提交的明确差异，不重写历史验收证据。
- 真机覆盖安装、付费模型质量、物理音效和视觉基准接纳仍是已有记录中的待验边界，
  工程归档和云端成功也不把它们改成已实测。
