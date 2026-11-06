# P06 检查点一：测试设施与完整 before

2026-11-06完成。当前仅交付捕获检查点，尚未开始产品 UI 修改。

## 结果与来源

- 有效 before：[`before/manifest.json`](before/manifest.json)，63 张真实生产 Screen 图片，45 张 Stage、14 张 Dialog/Sheet、4 张 Popup，共18个独立窗口。
- 图片、未合并语义 XML、窗口 root、滚动与触控测量、JUnit XML、日志和退出码均保存在 `before/`；每张 PNG 的 SHA-256、实际尺寸、density、fontScale、状态、主题及动作见 manifest。
- Manifest SHA-256：`215ae4f85d88cafe3e47c09e1a24f4883e006a0a05d342cb5fbc83e087877bae`。
- 源提交仍为 `43ebda6dc40a5d21812cd594d10777ce3f2b166a`。冻结的13项产品/构建/更新文件哈希逐项不变；158张已接纳PNG哈希逐项不变。检查证明见 [`post-capture-proof.json`](post-capture-proof.json)。
- 已接纳PNG清单 SHA-256 仍为 `b31439fd162a2323a62edc0fea0bc289b827635a15dbc45d3cd6899f2c7e1ec7`。没有 record、verifyAndRecord、接纳新图、降低阈值、守卫豁免、stage、commit 或 push。

## 测试与执行记录

| 记录 | 实际结果 | 解释 |
| --- | --- | --- |
| `probes/run-08` | 11测试，0失败，退出0 | 普通页、Dialog/Popup正负向、生产相对时间、会话真实ID回写、正文内部末行、3类launcher null、内存JSON/BOM/PNG/导出关流，以及独立Popup像素哨兵 |
| `capture-runs/before-02` | 64测试，64预期缺图失败，退出1 | 63项 missing-original + 原计数守卫缺图；没有行为、窗口或隔离失败。`:app:testDebugUnitTest FAILED` 与 `:app:finalizeTestRoborazziDebug` 实际执行，不是UP-TO-DATE结果 |
| `formatter-capture-tests-final.log` | 9新增测试文件逐项OK，退出0 | Android Studio Panda 2原生formatter、隔离profile、UTF-8、工厂默认Kotlin格式；没有格式化产品或其他批次。启动末尾的What's New连接超时单独保留为工具警告 |
| 静态检查 | 47+16个独立@Test；63 ID与矩阵一致 | `ScreenshotCases`增加E06=63；原计数守卫源码与零像素选项未改。`git diff --check`未发现新增空白错误 |

探针命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.example.myapplication.ui.screen.settings.worldbook.WorldBookCaptureProbeTest' --tests 'com.example.myapplication.ui.screen.settings.worldbook.P06PopupPixelGuardTest' --tests 'com.example.myapplication.ui.screen.settings.ContextTransferCaptureProbeTest' --console=plain
```

最终捕获命令与冻结：

```powershell
.\gradlew.bat :app:verifyRoborazziDebug --tests 'com.example.myapplication.ui.screenshot.WorldBookScreenshotE06Test' --tests 'com.example.myapplication.ui.screenshot.ContextTransferScreenshotE06Test' --tests 'com.example.myapplication.ui.screenshot.ScreenshotBaselineGuardTest' --console=plain
python -X utf8 .trellis/tasks/11-05-ui-token-worldbook/research/freeze_capture.py before --run .trellis/tasks/11-05-ui-token-worldbook/research/capture-runs/before-02
```

最终探针摘要及7张原生像素探针哈希见 [`probes/final-summary.json`](probes/final-summary.json)。探针通过后，只有截图专用的Stage清焦点/长关键词定位和IDE格式化变化；新截图路径已由完整before-02实际执行。

## 设施契约

1. 继承现有普通 `Application` / `ComponentActivity` / API35 / NATIVE 宿主，渲染真实 `WorldBookListScreen`、`WorldBookBookDetailScreen`、`WorldBookEditScreen` 和 `ContextTransferScreen`。只传固定虚构数据和本地回调，没有用户AppGraph、Room、真实文件、凭据或AI API。资料库不需要 TestAiServices。
2. 初始虚拟时钟为 `1791950400000`（2026-10-14 12:00 Asia/Shanghai），locale `zh-CN`、时区 `Asia/Shanghai`、density2；常规360×800dp/100%，窄屏320×640dp/200%。Robolectric默认不会拦截所有应用Kotlin类的wall-clock调用；P06宿主只为世界书UI包增加instrumentedPackages，使生产 `System.currentTimeMillis()` 进入shadow。虚拟uptime随真实UI动作推进，各图记录实际值，绝不调用宿主系统时间设置。
3. 时钟恢复放在 `order=-2` 的外层TestRule，断言Activity已DESTROYED后恢复shadow与尺寸/字体。共享 `ScreenshotStageTest` 原默认没有改变，locale/时区仍由其既有恢复逻辑处理。
4. Stage、Dialog、Popup分别匹配真实root，独立窗口匹配必须排除Stage。Popup默认 `fetchImage(SemanticsNode)` 会错误回退到Activity像素；P06只对Popup使用独立AndroidComposeView的native Canvas绘制，校验owner不是Activity且确为PopupLayout，再以Bitmap走同一零像素Roborazzi比较。独立蓝底页/品红Popup哨兵证明底页图片会被拒绝；生产Popup没有测试标记。
5. 世界书尾项用真实Lazy key（条目ID）定位并滚到实际max；Popup末项完整触控和unclipped边界必须落在窗口。书名fixture增加到8项，书名和会话菜单均有正的真实滚动范围。实际48dp测量记录保留，未把全部after无障碍门禁施加到旧UI。
6. 旧TextField公开ScrollBy但不公开VerticalScrollAxisRange。正文通过正式ScrollBy滚动，`LegacyTextFieldScrollProbe`只读当前foundation1.10.4实际VerticalScrollLayoutModifier的offset/maximum/viewport；不设置offset。Sheet自身FocusOwner清焦点并断言Focused=false；实际末行减offset后完整位于内部视口。当前正文offset/max为4749/4749，末行711–795位于795px内部视口，顶部取消与底部保存仍可见。
7. 资料库使用测试ActivityResultRegistryOwner拦截真实launcher，dispatch合成Uri/null；内存ContentProvider给MIME/文件名、注册内存stream走生产私有URI helper，检查关流。没有外部Activity，没有系统chooser假截图，没有新增权限。

## 之前失败如何处置

- `probes/run-01`：在Composition释放前回拨虚拟uptime，字体恢复触发Choreographer循环；线程转储已保存，仅终止已确认属于本轮的worker PID36576。详见其 `termination.md`。
- `run-02/03`：识别并修正应用wall-clock尚未插桩、只读会话字段误按SetText定位的问题；最终会话选择以保存回调验证真实conversationId。
- `run-04`：正文组合matcher找不到VerticalScrollAxisRange；依据真实语义/本地library字节码改为正式ScrollBy与只读内部位置探针。
- `run-06`虽然已有断言10/10通过，人工查看发现两Popup像素来自底页；仅非空像素检查不足，新增像素哨兵和真实owner捕获。`run-07`完成Popup修正，`run-08`完成Sheet自身焦点修正。
- `capture-runs/before-01`的63张旧actual完整保留，但明确不作为有效before。原因与调整见其 [`not-accepted.md`](capture-runs/before-01/not-accepted.md)。这些修正都发生在任何产品修改之前；有效before全量重新取得，没有用after反作before。

## 本批文件

修改 `app/src/test/java/com/example/myapplication/ui/screenshot/ScreenshotCases.kt`。

新增截图宿主/fixture：`P06RenderTest.kt`、`WorldBookRenderTest.kt`、`ContextTransferRenderTest.kt`、`LegacyTextFieldScrollProbe.kt`；新增 `WorldBookScreenshotE06Test.kt`（47项）和 `ContextTransferScreenshotE06Test.kt`（16项）。均位于同一 `ui/screenshot/` 目录。

新增独立行为探针：`ui/screen/settings/worldbook/WorldBookCaptureProbeTest.kt`、`P06PopupPixelGuardTest.kt`、`ui/screen/settings/ContextTransferCaptureProbeTest.kt`。

捕获工具/证据在当前任务 `research/`：`freeze_capture.py`、`before/`、`capture-runs/`、`probes/`、formatter日志、本摘要与最终哈希证明。生产文件、纯逻辑、VM、Repository、导航、权限、构建和更新配置全部保持。

## 主会话建议复核的样本与剩余工作

- [`E06_editor-book-popup-light_actual.png`](before/E06_editor-book-popup-light_actual.png) / [`E06_editor-conversation-popup-dark_actual.png`](before/E06_editor-conversation-popup-dark_actual.png)：实际独立菜单、完整末项及固定50分钟前，不是底页裁片。
- [`E06_editor-body-sheet-dark_actual.png`](before/E06_editor-body-sheet-dark_actual.png)：真实末行、无焦点、取消与保存；对应measurements记录内部边界。
- [`E06_editor-rules-narrow-light_actual.png`](before/E06_editor-rules-narrow-light_actual.png)：长关键词整个chip已滚入视口，三种匹配模式仍可见。
- [`E06_transfer-source-dialog-light_actual.png`](before/E06_transfer-source-dialog-light_actual.png) / [`E06_detail-management-narrow-light_actual.png`](before/E06_detail-management-narrow-light_actual.png)：保留旧UI的动作换行、标题省略和原保存禁用状态，后续实现有真实对照。
- [`E06_list-filters-narrow-light_actual.png`](before/E06_list-filters-narrow-light_actual.png)：真实初始展开的三行筛选首屏，书分组位于下缘；旧UI需要继续滚动才能阅读书卡，未为before修改布局。
- [`E06_transfer-preview-actions-narrow-dark_actual.png`](before/E06_transfer-preview-actions-narrow-dark_actual.png)：真实页内冲突与确认/取消，列表max与两按钮完整边界齐备。

主会话复核本检查点后再继续共享接入/产品UI实现。捕获相关9个文件的哈希已冻结；后续行为测试尽量在新文件中扩展，若改变捕获策略必须处理before来源并记录。

本阶段未声称八组scheme对比、完整18帮助/业务回调、48dp全部验收或后续视觉接纳已通过。Lint、dev构建、AndroidTest编译及最终旧158图/完整JVM回归留在后续产品实现与整合检查点。真实系统chooser、IME、TalkBack、系统栏和物理性能仍需设备证据。

## 已读取的上下文

已读保存的完整hook文件，并补读工具截断部分。三份任务正文 `prd.md`、`design.md`、`implement.md` 完整；已读仓库 `AGENTS.md`、`trellis-before-dev/SKILL.md`、`implement.jsonl`、backend index/目录/UI令牌/UI状态/错误/数据库/日志/质量规范、guides index/复用/跨层指南。

研究已完整逐份读取：`context-loading.md`、`capture-and-shared-contracts.md`、`entry-list-detail.md`、`entry-list-detail-validation.md`、`editor-contracts-tests.md`、`editor-validation.md`、`context-transfer.md`、`context-transfer-validation.md`、`screenshot-matrix.json`，以及启动冻结文件。目标生产Screen/直属组件、现有截图宿主/登记/守卫和P11证据格式也已核对；独立检查代理仍应按索引自行重读。
