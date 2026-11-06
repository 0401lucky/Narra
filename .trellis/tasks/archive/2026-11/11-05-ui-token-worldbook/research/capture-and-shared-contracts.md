# Research: P06截图与共享契约

- Query：如何复用前序158张基准与窗口能力，为世界书和共享数据页建立可审阅的实际覆盖？
- Scope：internal；仅规划与静态读取，不运行产品、Gradle或设备。
- Date：2026-11-05

## 当前事实

- 当前main为`43ebda6d`，四批父任务/P11均已完成；P06独立创建为planning，未start。[规划起点](planning-baseline-20261105.json)记录目标源码/构建/更新文件与工作区，158张PNG逐项匹配P11最终清单。规划阶段的哈希不是P06实现前捕获，实施时仍须再次核对。
- `ScreenshotCases.kt:258–275`包含H/B/C以及E04/E05/E10/E11，没有E06；实际前序为158 PNG、159检查。`UiTokenSourceInventory.kt`没有世界书、KeywordChipInput或ContextTransfer目标范围，不能用已有守卫通过宣称这些页已迁移。
- `ScreenshotStageTest.kt:53`提供API35/NATIVE与固定Stage；`ScreenshotFixture.kt`提供360×800dp/100%及320×640dp/200%、density2。当前Provider窗口辅助仅匹配isDialog（`ProviderSettingsRenderTest.kt:178–180`），不能直接拿它捕获编辑器的两个Popup。
- `ProviderSettingsScreenshotE11Test.kt:229`采用截图类私有语义Tab点击进入真实生产状态，共用行为测试仍是真实触控。前序在等待动画与主Looper各1000ms后仍有水波颗粒，最终原始提交隔离补before、两轮32张after同SHA，并对变化5张重新人工接纳。

## P06应遵守的捕获步骤

1. 完成批准后先冻结当时实际已接纳PNG、登记及源哈希。先加E06 fixture/独立测试方法，不改产品；使用verify产生缺图actual取得完整before。
2. 常规页捕获Stage；帮助/确认Dialog及正文Sheet匹配真实独立Dialog root。所属世界书/所属会话菜单用真实isPopup root独立定位，缺失、同名误匹配或捕到Stage必须失败。Popup能力是本批新增探针，不能以P11的Dialog成功作为证明。
3. 固定虚构ID、书名、关键词、extras、角色/会话、预览冲突、文件信息；禁止用户库、用户文件、真实剪贴板、凭据或网络。固定并恢复JVM wall clock、locale、时区，避免相对时间字段漂移；不修改产品时钟。
4. 字体/窗口尺寸必须读取实际布局；长列表按真实末项与滚动边界校验。未裁切布局和touch bounds同时进入可用视口；不能只让Text出现，或只检查viewport>0。正文内部滚动与外层表单分别断言。
5. 导航截图可调用真实语义动作进入静止状态，实际点击/48dp/相邻动作隔离由行为测试覆盖。不得隐藏控件、删动画、改文案、给测试改产品健康/读写策略。
6. after同参数取得，并在用户审阅前对本批全部after重复捕获、逐张核对SHA；不把瞬态固化为新基准。若需更正before，使用原始提交隔离副本记录覆盖文件与来源，不回改主工作区。
7. missing-original/计数守卫失败与真实行为/旧图差异分开报告；新图人工确认后才精确入库，保持零像素门禁。完整截图与完整JVM分别保存真实日志、XML、退出码、测试数和实际task状态。

系统OpenDocument/CreateDocument/PickVisualMedia是外部Activity，不属于本App窗口截图。宿主使用ActivityResult测试接缝验证launch、取消和结果归属；系统UI、IME、读屏、材质与物理性能继续留真实设备证据，不画假系统选择器。

## 共享与源码守卫

- P10已提供SettingsTopBar的horizontalPadding/titleMaxLines/titleIsHeading，以及Group/Divider/ListRow的形状、padding、间距和行数选项；P11已提供NarraAlertDialog的shape/actionContentColor，破坏性确认仍优先error。已有参数直接消费，旧默认和其他调用方保持。
- P06新增公共样式选项必须由同一实现所有者维护，默认精确等于当前旧值，包装层与两个按钮分支完整透传；不引入全局“迁移开关”。具体共享消费者见[入口研究](entry-list-detail.md)和[编辑研究](editor-contracts-tests.md)。
- KeywordChipInput的生产消费者只有编辑页三处，UI令牌/标签/触控可归本批；拆词、正则识别、未提交draft以及IMEDone/逗号/换行提交边界保持原样。
- WorldBookListStyle的筛选、关键词预览、相对时间和未被生产调用的bookSpineColor只作行为/时间证据，不为“清零”改死代码或分组规则。buildWorldBookBooks在角色挂载摘要也有消费者，保持纯逻辑不变。
- SourceGuard的requiredDeclarations只校验存在/数量；scannedDeclarations=null是全文件扫描。为实际迁移声明增量登记；SettingsComponents由legacy转为scanned时同时维护精确默认例外，SettingsGroup=2的现有重载规则不放宽，不加整文件/目录豁免。

## 规划与证据边界

用户已明确把共享数据导入/导出全页纳入，截图不能只展示世界书分组。建议由同一所有者串行推进共享参数、列表/详情、编辑、共享数据页；保持一个现有P06任务，无需重开已归档父任务。

研究代理提供的分段候选数是建议，最终矩阵由design统一去重、按真实状态收敛；同容器的帮助入口逐个做行为，代表性的长文/弹窗做视觉。现有158/159/1826只属于前序，不是P06通过结果。本研究没有截图、构建或设备执行。

## Related specs

- `.trellis/spec/backend/ui-token-contract.md`：实际令牌、颜色承载面、48dp/200%、窗口、原始提交补before与稳定帧。
- `.trellis/spec/backend/quality-guidelines.md`：Fake、缺图/计数/零像素、人工接纳、命令与宿主证据口径。
- `.trellis/spec/backend/ui-state-guidelines.md`：回调、消息和原保存/取消行为。
- `.trellis/spec/guides/code-reuse-thinking-guide.md`：共享所有者与全部消费者。

未使用外部资料或新依赖；当前工具/框架版本沿用仓库，不在本任务升级。
