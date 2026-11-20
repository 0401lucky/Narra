# D2 续接实施进度

2026-11-11 从会话 `01a1c5ad-b902-7de0-995f-296d4271b585` 续接。

- D1 已独立/设备验收；保留 schema 55、已有 v2.4/D1 源码和 273 项保护资料。
- 本块原基线有 22 个既有源码改动、9 个新生产文件和 2 个新测试文件。
- 续接首次 `compileDebugKotlin compileDebugUnitTestKotlin --offline --console=plain` 已通过；日志为 `resume-compile-01.log`。尚未宣称行为验收。
- 当前补充重点：DIRECT 无回合时持久返回来源、旧会话恢复 DRAFT、选择 CAS、最终 HTTP 发送前复核、请求局部召回与时间策略、真实 Compose 回调。
- DIRECT 选择将关联到已有无请求准备记录的描述字段，同事务保存明确的 selectedEncounterId；DIRECT 自身不创建 OPENING turn，不增加 schema。该内部标记不进入 messages 或来源。
- 生产代码/测试/Gradle 由 `companion_d2_implement` 独占写入；父任务/规范/版本记录由主会话负责。未提交、未发布、未操作真实用户数据或付费模型。

## 已完成的续接修复

- 恢复红灯：DIRECT 丢线上 origin、普通旧会话不恢复 PREVIEW、原 route 重建误读旧候选，三项已复现后修复。
- 无请求准备记录保存 `entrySelectionId`，同事务进行选择 CAS/淘汰/创建；DIRECT 不制造 OPENING turn，旧返回受完整归属和最新选择限制。
- 提供 Room 单事务 `validateRequest`，在三协议真正调用前、回执准备挂起后复核；零 HTTP 拒绝记 SOURCE_CHANGED，已发送的先前 transmission 保留。
- 补齐未请求 INPUT 的显式恢复、接受保存失败的免费恢复、连点和 A→B 取消隔离。
- 恢复场景说明名字替换、角色时区冻结和请求局部召回；真实文件 DataStore 覆盖 CAS、旧字段、高版本保护与重开。
- 真实 BUILTIN_PRESETS 回归另复现默认/Qwen强制推进及 persona 漏注入：仅陪伴请求适配出厂未改写的 7 个块；用户修改块/自定义预设、Sampler和上下文槽保留。陪伴通过 `includeScenarioPersona=true` 使用 persona 槽；旧路径默认 false。

## 当前证据

- 联合 JVM：24 类 / 361 项通过，见 `implementation-jvm-results.json`。
- 后续预设/身份定向：6 类 / 80 项通过，见 `implementation-preset-results.json`；包括新增 7 项真实内置预设 HTTP。
- 原 35 项截图 verify 通过（E04/H01/H02），未 record 或更换基准。
- 预设修复后的最终 Debug/AndroidTest APK、Baseline/AndroidTest 编译、Lint 与截图均通过；1392 项冻结源码/参考和273项保护资料无漂移。
- 原生 UI 两类共 13 项已编译；隔离设备执行由主会话负责。真实模型/TTS未运行。

JVM按实际 class+test 合并为27类、385项，新增D2共有54项；另有35项截图verify。最终签名/哈希、命令和范围见 `implementation-validation.json`、`implementation-files.json` 与 `implementation-handoff.md`。

作者已释放全部生产/测试及Gradle所有权；所有构建/formatter执行已结束，保留暖Gradle daemon供主会话/checker复用。下一步由主会话独立检查并执行13项原生UI设备验证。
