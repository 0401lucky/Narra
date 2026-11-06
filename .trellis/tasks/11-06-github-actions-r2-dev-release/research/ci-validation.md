# 真实 CI 验证

## 首次工作提交

- 工作提交：`95460b5887a52178a7a9a317f7182f46dad79512`，已推送 `main`。
- 只提交本任务 30 个文件；已有 v2.4 工作区段未进入 index，原文保持不变。
- [Dev Release 42178061696](https://github.com/0401lucky/Narra/actions/runs/42178061696)：2026-11-06 07:00 UTC 手动触发，版本输入 `2.3.0`。
- 预检、JDK / SDK 与原 dev keystore 恢复 / 证书验证成功；随后 `Test release guards` 失败，尚未执行新 APK 构建或 R2 / 清单写入。

## 首轮发布测试失败

- GitHub pwsh 使用 `pwsh -command ". '{0}'"` 启动临时 wrapper，wrapper 再调用发布测试脚本；本机原验证使用 `pwsh -File`。
- 首轮为 12 项通过、7 项失败。主要错误为闭包中找不到脚本私有 `Assert-True`，另有读取前态时对象缺少 `app_id`。
- 检查代理已在本机用相同 wrapper 精确复现；根因为 `GetNewClosure` 创建动态模块后的依赖 / 变量可见性，而非 App 或 Gradle 编译。
- 已修复显式依赖捕获和生产模块导入作用域；统一启动门禁在 `-File` 与 GitHub wrapper 两种方式下各通过 30 项用例。见 [修复与复验](pwsh-startup-fix.md)，等待新提交的真实发版复跑。

## 同提交其他门禁

- [Deploy Pages 42178000137](https://github.com/0401lucky/Narra/actions/runs/42178000137)：成功，仍发布有效旧清单。
- [Android Quality 42178000109](https://github.com/0401lucky/Narra/actions/runs/42178000109)：截至 07:17 UTC，整个 workflow 成功；Linux assembleDebug、完整 JVM、Lint 及截图 job 均成功。

## 外部条件

原签名 Secret 与 Cloudflare Account ID Variable 已配置。R2 两项受限 S3 Secrets 仍未配置，Chrome 原生连接待用户通过插件页恢复；不能把尚未运行的上传及公网更新验证记为完成。
