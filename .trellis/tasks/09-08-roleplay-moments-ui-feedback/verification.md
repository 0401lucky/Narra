# 2.2.12-dev 反馈修复验证记录

## 授权与环境

- 用户在完整设计摘要后回复“开始吧”，已授权两项实现与相关验证。
- 代码核查基线为 main / bdfcc03b；目标版本为 2.2.12-dev，本轮不发布更新。
- Windows 11，Gradle 9.3.1，JDK 21.0.9；没有修改本机全局配置。
- 启动现有 Codex_Pixel_7_Large AVD，使用 emulator-5556；后台无窗口运行，未清空 AVD 或应用数据。
- 设备为 Android 16 / API 36，1080×2400、420 dpi；启动已确认 sys.boot_completed=1。测试日志暂存于系统临时目录 narra-2.2.12-ui-validation-20260908。

## 实现与审查进度

- R2 评论预览局部改动及三项新设备用例已完成，定向 git diff --check 通过。
- R1 已完成配置、Store 解码、设置入口、气泡视觉链路和设备回归源码，已冻结并交给检查代理。
- 第一轮集中检查：:app:testDebugUnitTest :app:lintDebug :app:assembleDebug 成功，退出码 0，总耗时 7m23s。日志为 build-unit-lint.log。
- JVM 结果：202 份测试报告，1534 项测试，0 失败、0 错误、0 跳过。
- AndroidTest 初次编译发现 DpRect.height 扩展未解析，检查代理改用 bottom - top 计算行高；同批次重跑通过。
- :app:connectedDebugAndroidTest 成功，耗时 2m44s，包含 AndroidTest 编译 / 打包和 emulator-5556 上的 18 项测试，0 失败、0 错误、0 跳过。日志为 device-tests-2.log，结果为 app/build/outputs/androidTest-results/connected/debug 下的测试 XML。
- 设备类：MomentsScreenTest、RoleplaySettingsContentTest、RoleplayOnlineBackgroundBubbleTest、RoleplaySceneContentTest。
- 新设备用例全部定稿后再次运行 :app:lintDebug，通过，退出码 0，耗时 1m14s；日志为 final-lint.log。

## 最终审查

- 检查代理完成两项需求的全部源码及测试审查，未发现未修复产品逻辑问题。
- 两处测试修正：线上测试页面限定 360dp 以稳定宽度断言；评论测试使用 bottom - top 计算 DpRect 行高。上述最终测试源码已通过编译和设备执行。
- 背景图、页面控件、线下逻辑、旧 JSON 默认值、保存失败与 false 保存回读均有对应源码检查及回归覆盖。
- 验证 APK 沿用当前 2.2.11-dev / 20211 构建标识；本任务功能归入尚未发布的 2.2.12-dev，未上传 APK 或修改更新 JSON。

## 视觉复核

Gradle 设备执行结束会清理测试安装及其截图目录；为完成视觉核对，使用同一已验证 APK 单独执行五项取图用例，覆盖普通玻璃、主题气泡、深色 / 引用 / 流式错误文字、紧凑评论及大字体评论。截图保存在系统临时验证目录中，不纳入源码提交。

五项取图用例全部通过，耗时 24.033s；这是前述 18 项用例中的定向复跑，不另计独立用例数量。已取回并查看六张 PNG：普通玻璃与主题气泡的背景一致、气泡材质和形状按开关变化；紧凑评论及 1.6 倍字体无行间遮挡；深色引用和流式错误文字可读。

截图目录为临时验证目录下的 images，文件为 moments-four-compact-comments.png、moments-comments-large-font.png、online-background-glass-default.png、online-background-theme-bubbles.png、online-background-noir-quote.png、online-background-theme-streaming-error.png。

## 本地功能提交

- R1：b35cc403e12f3a13d645aa145fe0e9a20d562356，feat(roleplay): 支持线上背景玻璃气泡开关。
- R2：4f24dc80bb71ea256ac176efdf6f0feb43d7ebc0，fix(moments): 收紧评论预览间距并保留手势交互。
- 本轮不推送远端或发布更新；已有无关未跟踪文件未纳入本次修改。
