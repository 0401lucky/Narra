# 试点最终检查记录

2026-10-21：已完成冻结代码的全任务范围审查；发现的问题已由实现所有者或检查代理修复。检查代理最后一轮 29 项定向测试全部通过。主会话最终完整 JVM 1642 项通过，AndroidTest 编译、Lint、dev 构建通过（退出 0，4m51s）；截图 verify 的 75 项预期像素差异保持失败，78/78 候选已备人工审阅。结果汇总在 `../verification.md` 和 `quality-final-result.json`。

## 已修复的问题

| 文件 / 位置 | 实际问题 | 修复与证据 |
| --- | --- | --- |
| `RoleplayInputBarComponents.kt` 提及面板 | Material 浅色表面沿用 NOIR 白色正文，实际配对约 1.14:1 | 标题 / 收起 / 候选文字分别使用对应 Material 内容角色；真实展开与选择回调测试通过 |
| `RoleplayOnlinePhoneContent.kt` AI 主操作 | 调整后的强调文字色被同时当按钮背景，再固定黑字；触控高度不足 48dp | 使用 `primary/onPrimary`，最小高度 48dp；大字体展开态真实边界检查通过 |
| `RoleplayDialoguePanelParts.kt`、`ImmersiveGlass.kt` 与消息气泡 | 对比度只计算原始图片，遗漏页面 scrim、sheen 与正文加深；错误文字虽匹配原 errorContainer，实际玻璃变换后表面失配 | 有效玻璃正文按真实叠层及局部黑白边界求字色，并给正文承载面明确的透明度下限；错误气泡使用不透明 Material errorContainer。白 / 灰 / 黑合成和真实白图像素测试通过 |
| `RoleplayOnlinePhoneBubbles.kt` 旁白 / 心声展开 | 中间实现无条件使用 ReadingGlass，关闭有效玻璃仍被 alpha 封顶 / 变暗 | 最终按有效玻璃分支：开启才使用增强阅读玻璃，关闭保留原皮肤透明材质 |
| `RoleplayMessageBubbles.kt` 标签、皮肤解析 | 旁白内容色直接放裸背景；Liquid 浅色文字原有 4.175:1 反例 | 角色标签有明确的 system 内容 / 表面对；皮肤静态副标题同时验算裸背景和旁白表面 |
| `RoleplayDialoguePanelParts.kt` 透明主题路径 | Liquid + 深色 + 白图 + 线上玻璃关闭时仍按原始白图选字色，实际页面遮罩后降至约 3:1 | 检查代理新增真实页面像素测试，先在旧生产代码上运行 1 项、失败 1 项；随后仅调整前景选色，纳入真实 scrim 顶 / 中 / 底及普通玻璃光泽底层，保持原 alpha / 配置 / 几何。用户、角色和旁白的实际渲染对比均通过 |
| `ImmersivePhoneShell.kt`、输入与头部 | 大字体底栏文字和提示宽度溢出；头部 / 输入覆盖列表 | 内容测量、真实可用宽度与有界滚动替代固定补偿；200% 字体下控件完整落在舞台边界，文本无溢出，真实列表与头部 / 输入不相交；新增 / 搜索 / 发送 / 停止 / 快捷玩法 / 跳转回调通过 |
| `UiTokenSourceGuard*` | 初版遗漏 RGB / 十进制 / 命名颜色及共享组件拆文件 | 覆盖明确颜色构造和全部迁移文件 stem；保留按路径、声明、完整表达式与次数的精确例外。12 项正反例与生产绑定全部通过 |
| `RoleplayOnlineBackgroundBubbleTest.kt` | 设备测试仍锁旧 Narra、引用及错误颜色，编译虽能通过，真实运行预期已过时 | 更新为当前 Material 配对并保留背景 / 材质 / 宽度 / 圆角 / 像素断言；新增同 Composition 应用主题及深浅切换和亮暗图片上的真实错误气泡回归。共 5 个设备用例，当前无连接设备，不能记作运行通过 |
| `PageScreenshotH04Test.kt` | 大字体布局修复后视口缩小、真实列表仍初始贴底，旧断言错误地要求首条消息可见 | 保留原 ID / 参数 / 基准与初始位置；验证实际尾条合成消息、发送入口以及头部 / 消息 / 输入边界。6 个 H04 用例全部通过普通测试入口 |

## 兼容与规范审查

- `RoleplayNoBackgroundSkinSettings`、存储枚举、JSON 和 `normalized()` 未改写。包括仍等于历史默认值的圆角、宽度、padding、尾角、玻璃开关均原值传递；已有玻璃谓词保持不变。
- 真实 `backdropState.hasImage` 与有效消息玻璃分离；图片对象没有被清空以切材质。消息列表 `uiItemId` key、自动跟随状态机、媒体协议及原普通 Chat 入口策略未改变。
- 主题缓存以完整 scheme、URI、identity、generation、尺寸与 highContrast 为键，LRU 键仅持有标量 / 主题数据，不强引用 Bitmap。已有真实位图 generation 与 identity 变更测试；未直接覆盖异步 `produceState` 和 URI 加载生命周期。
- `NarraUi` 历史别名保持，试点实际消费新令牌。Material shapes 的共享隐式影响、设置预览和线下调用方仍按任务扩面清单披露，不声称全 App 已视觉确认。
- 已检查新增 `.trellis/spec/backend/ui-token-contract.md` 与索引；令牌、配置优先级、缓存、透明对比和人工接纳契约与最终代码一致。
- 无截图 record / verifyAndRecord、无阈值放宽、无用例删除、无发版 / 提交 / 推送。原有 `snapshots/manifest.md` 外部修改保持不动，不据此视作视觉接纳。

## 检查代理执行证据

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.myapplication.ui.component.roleplay.RoleplayThemeSwitchUiTest.liquidSkinWithGlassDisabled_darkWhiteImage_readsActualScrimmedTransparentSurfaces" --console=plain
# 修复前：exit 1；1 项，1 失败；41 秒。

.\gradlew.bat :app:testDebugUnitTest --tests "com.example.myapplication.ui.component.roleplay.RoleplayThemeSwitchUiTest" --tests "com.example.myapplication.ui.screenshot.PageScreenshotH04Test" --tests "com.example.myapplication.ui.theme.UiTokenSourceGuardTest" --tests "com.example.myapplication.ui.component.roleplay.RoleplaySemanticColorsTest" --console=plain
# 修复后：exit 0；29 项，0 失败 / 错误 / 跳过；44 秒。
```

最后一轮各类：`RoleplayThemeSwitchUiTest` 7、`RoleplaySemanticColorsTest` 4、`PageScreenshotH04Test` 6、`UiTokenSourceGuardTest` 12。摘要为 `check-targeted-after-summary.json`，原始 XML 为 `check-after-*.xml`；反例 XML 为 `check-liquid-before.xml`。定向 `git diff --check` 通过。

## 待接纳与验证边界

- A6 需要当前候选图的人工确认及接纳后最终 verify；与旧基准有预期差异的 verify 失败必须如实登记，不能用普通 JVM 通过代替。
- D01 真机、IME / 系统栏、真实玻璃与物理性能仍无本轮有效设备结果，保持待验收。
- 宿主固定样本、精确静态守卫和缓存键行为覆盖均有边界；不声称任意真实图片、全部 Kotlin 语法或异步图片加载生命周期均已覆盖。
