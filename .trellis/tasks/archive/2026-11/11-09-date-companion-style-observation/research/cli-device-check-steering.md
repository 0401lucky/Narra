Active task: .trellis/tasks/11-09-date-companion-style-observation

主会话补充一个具体核对方向：Compose Dialog 新 AndroidComposeView 会提供
自身平台 LocalDensity/LocalConfiguration，父层仅覆盖两项 locals 的合成夹具
未必影响独立窗口的 view.context。现有 longValue 两项在100%下可能不溢出。
请先核对真实节点的 density.fontScale，避免继续扩大检查。

若证实测试未覆盖窗口，可让 narrow 夹具在具有 fontScale=2、320dp/600dp 的
configuration Context 创建 ComposeView，并在 Activity 上挂载该真实根：
createConfigurationContext -> ContextThemeWrapper -> ComposeView.setContent /
activity.setContentView。这样 DialogWrapper 所取 LocalView.current.context
也来自200%平台配置，无需改宿主或设备系统设置。保留原文本、滚到底、
末字段可见、窗口320dp、48dp、关闭/Back全部断言；增加对 Dialog 内容布局
节点真实 fontScale 的断言，证明图片实际200%，不能只靠外层locals或名称。
具体生命周期/主题/测试Rule API请按当前Compose实现核对，不照抄不兼容代码。

这只是主会话的定向技术建议，你仍需确认真实原因和最小自修；不要改共享
NarraAlertDialog，除非确认产品本身丢用户字体偏好并先报告。主会话等待你
在 research/device-fix-2026-11-18.md/通道给出源码释放及验证选择器，随后
马上定向编译和单项设备复验。其它36设备/JVM/Lint均不重复无变化全量。
