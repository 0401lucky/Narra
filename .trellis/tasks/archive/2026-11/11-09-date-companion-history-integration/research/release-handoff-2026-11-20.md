# 2.4.1-dev 发布交接

2026-11-20。用户要求的 GitHub 推送、Actions 构建、R2 和内置可选更新已全部完成，
同时纳入现有 UI 改进和已验收的 2.4 记忆依赖。D1–D6 工程按本轮授权归档。

## 实际产物与发布

- 源码：`18ec00c42837b995df05d55deea759651606ccc9`。
- [Dev Release 44745192024](https://github.com/0401lucky/Narra/actions/runs/44745192024)：
  build、publish、pages/deploy、verify 全成功，完成时间 `2026-11-20T13:51:09Z`。
- 清单发布提交：`e7d3f2f7bf168938a37b8d10091241c965eac544`。
- APK：`Narra-v2.4.1-dev-20401-dev.apk`，83,029,801 字节；包名 `com.narra.app.dev`。
- SHA-256：`f9df232f89f755571b49a66e15d16d20cfe7b6921463f15c909c6c1dda53dad2`。
- 唯一原证书：`3a6c57eec0641bc00830252eb821d62c9e9b2f639c5989d0bc42b29b843546e8`。
- R2：`narra-updates/dev/Narra-v2.4.1-dev-20401-dev.apk`。
- [公开 APK](https://download.lsa1230.dpdns.org/dev/Narra-v2.4.1-dev-20401-dev.apk?v=20401)。
- [公开更新清单](https://0401lucky.github.io/Narra/updates/dev.json)：20401、同一 URL/SHA、
  `minimum_supported_version_code=0`。继续可选更新。
- 本地云端包：`app/build/outputs/apk/debug/Narra-v2.4.1-dev-20401-dev.apk`。

## 验证与真实失败处理

- [Android Quality 44744946541](https://github.com/0401lucky/Narra/actions/runs/44744946541)
  与 Dev Release 的 debug、完整 JVM、Lint、截图均通过。
- 独立读取截图原始 XML：222/222，0失败/错误/跳过；221 PNG 全 unchanged，
  0 changed/added/recorded。207张用户接纳候选按原字节替换，14张原图保留。
- 首个云端 JVM 的12个失败并非源码漂移；41同步空闲通知回归使旧代码明确失败，
  修复LAZY登记/Job所有权/空闲发布后42两类22/22通过。原21条等待、HTTP、来源、
  恢复断言与fixture保留；原始失败和红绿证据均保留。
- 定向IDE格式化的两个Kotlin文件原字节未变；43的唯一字典差异是同时同步的版本
  文档，属于已授权bookkeeping，不记作formatter改码或源码全字典稳定。
- 两种PowerShell启动各39项、共78次发布守卫通过；固定actionlint1.7.12通过。
- 主会话再次下载公开R2 APK，验证HEAD、大小、MIME和完整SHA；本地固定36.1.0
  aapt2/apksigner再次确认包名、版本、唯一原证书。GitHub大artifact本地下载网络未完成，
  已停止本轮下载进程；交付包来自同一已验证R2对象，不把未完成下载算作证据。

更多绑定见 [最终本地核验](release-verification-2026-11-20.json)、
[UI接纳](ci-ui-acceptance-2026-11-20.json) 和 [竞态根因](idle-race-root-cause-2026-11-20.md)。
原D6的2956 JVM/9原生属于工程阶段的历史源码证据，未累加到本次CI统计。
真实模型/MiMo、物理音效和真机覆盖安装未由本轮发版流程实测。
