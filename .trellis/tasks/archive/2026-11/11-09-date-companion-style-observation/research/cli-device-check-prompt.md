Active task: .trellis/tasks/11-09-date-companion-style-observation

你是 Trellis check worker，直接做定向检查与必要自修，不派发其它代理。沿用
已批准 v2.4.1 D1–D6，用户明确继续，不新建任务、不重新确认。完整D3独立
检查/JVM37/编译/Lint已完成；当前只处理一个剩余观测设备失败。

先读 AGENTS.md、此任务 check.jsonl 的六项及 prd/design/implement，尤其
research/device-fix-focused-handoff.md，不重做全范围或读取全部旧历史。

17个旧AndroidTest ABI已经按真实javap确认，主会话定向重编译成功；现在
d3-device-06 的18项（17 ABI + 模板EditableText）全部通过。产品源码未改，
完整D3设备按方法去重已有36/37通过，不加兼容构造器、不重复修模板。

唯一剩余：CompanionObservationPanelTest.narrowLargeFont_longObservationScrollsToEndAndKeepsCloseTouchTarget
第270行 range.maxValue=0；48dp已通过。外层假 LocalDensity/fontScale=2可能
没有进入独立 Dialog AndroidComposeView；正常字体下现有观测内容可能正好
放得下。请确认实际布局节点/窗口的 density.fontScale，保证真实320dp/200%
和长观测滚动/末项可见/48dp/关闭/Back行为。不能弱化滚动断言或延长文本
却继续将实际100%图片命名为200%。如果只是测试环境不跨窗口，修夹具并
真实覆盖平台字体、恢复；如果确认产品丢失应用字体偏好，才做最小生产修复。

所有权：可修改上述观测AndroidTest及必须的隔离测试辅助；若必须改
NarraAlertDialog等共享产品先在channel报告原因/最小范围。可写本任务
research/device-fix-2026-11-18.md。当前设备/编译已完成，源码修复窗口释放。
你不是代码库唯一执行者，保留v2.4/用户/其它UI agent全部改动；不得改12个
漂移文件、主题、截图基准/inventory、构建版本或更新JSON。不提交/推送发布。

主会话是唯一Gradle/formatter/设备执行者，禁止你运行这些。使用合成数据与
隔离环境，不调真实付费文本模型/TTS；你提供最小复验命令，主会话执行并
把失败结果交你继续修复。完成代码后在channel明确释放源码，给出实际文件
哈希、根因、受影响的最小复验范围与可续接记录，区分已实现和待验证。
