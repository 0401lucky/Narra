# D5 验证范围与记录规则

2026-11-19；本文件是执行前计划，实际结果写在 `validation/*/validation.json` 与原始报告，不把静态方法数或已有源码称为通过。

- root 唯一执行 formatter、Gradle 和设备。三条实现线只编写测试，代码冻结后集中验证，不并行写构建输入。
- 定向运行新 runtime/HTTP/key、发布恢复/收藏/LRU、声音 ViewModel 测试，以及必要旧语音40方法与迁移registry/S3相邻守卫。最终selector以真实文件和XML为准，不使用 `*Companion*Test` 重跑D4全部。
- 编译main/JVM/AndroidTest和baseline（S3新固定55资产）；只构建本地dev/test APK，无正式release、版本参数或更新JSON变更。
- 授权设备仅 `Narra_Memory_V24 / emulator-5580`，已通过 `adb devices -l` 和 `emu avd name`核对。使用合成fixture、FakeTTS/翻译或MockWebServer；不测试用户真实库、凭据或付费模型。
- 设备定向覆盖固定55→56保留数据与新表、声音Stage/Reader/库实际动作和合法短音频MediaPlayer prepare/start/stop。Fake回调验证与原生系统播放器验证分别记录，物理设备/真实MiMo质量单列未测。
- 当前交付整段音频响应与有界分段串行朗读，当前单元状态不冒充逐字音频时钟字幕。
- 旧D4 149 JVM / 33设备按class+method最新去重结果只作为前置；旧34截图差异保留未接纳，本轮不record/替换或反复运行D4。声音控件真实平台交互按改动风险验证。
- 每轮保存精确argv、exit、时间、原日志、JUnit XML或instrumentation method/status、源码前后SHA和实际APK/Lint产物。汇总按class+method取最新结果，不累加重复执行。编译失败、初始化错误、零测试与runner失败如实保留。
- 新失败交唯一文件owner修复，后续只针对改动和失败复验；无新变化/不确定性不追加整组轮次。
