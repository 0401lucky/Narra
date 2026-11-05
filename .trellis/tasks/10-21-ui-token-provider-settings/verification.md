# P11 提供商配置验证记录

状态：2026-11-05 P11实现、独立检查、32组视觉及5张稳定帧修正接纳全部完成。159/159真实截图、1826/1826完整JVM通过，必要Lint无新增警告，158张PNG与产品源码哈希未变；当前等待具体工作提交清单确认。功能归属未发布v2.3，构建配置与更新清单保持2.2.12-dev，未暂存、提交、推送或发包。

[32组前后与差异审阅页](review/index.html)已标记接纳，每组原图、参数和SHA-256均可查看。用户明确回复“接纳这32组效果（推荐）”，已仅复制这32张已审阅after进入snapshots，原126张不变，共158张；具体提交清单仍单独审阅。

## 人工接纳（2026-11-05）

- 用户原话及32张精确SHA保存在[接纳记录](research/visual-acceptance.json)，接纳前审阅数据与after清单哈希均保留。
- [158张完整冻结清单](research/accepted-png-hashes-20261105.txt)SHA-256：`6178162c6955c476905de98897e0ffdc81ea8726c2808ae6882844ee58d31c49`。没有record、改变比较阈值或删除用例。
- 检查代理重新接手唯一Gradle所有权，先完整截图verify并保存本轮报告，再无selector运行完整JVM；主会话不并发Gradle。产品源码未变，最新已通过的Lint/dev/AndroidTest编译不重复。
- 接纳后首轮实际159项中155通过；4项失败为Models常规浅/深、Models末项窄屏深色、Models空态深色，差异分别20926/11406/3312/2697像素，全部位于底部“模型”Tab水波纹区域。原126及其余新图通过，计数守卫通过；失败JUnit/actual/compare保存在`research/accepted-screenshots-20261105/attempt-1/`，未替换基准或放宽阈值。
- 检查代理继续确认瞬态并修正E11捕获等待；真实触控行为测试和产品动画保留。若稳定捕获产生与已接纳after不同的图，需在ac22bb27隔离副本以同策略补取before、保存来源，再只把变化后的候选交用户审阅，未变化的已接纳图不重复确认。
- 最终修正仅在E11截图类私有`selectModelsForCapture`使用真实Tab语义OnClick。推进Compose/主Looper各1000ms仍不能稳定水波，等待探针保留；行为测试真实触控与产品动画未改。在ac22bb27隔离副本补取32张before，再叠当前产品与未改SettingsComponents原字节获取两轮after，32张SHA完全一致，来源与清理见[隔离记录](research/capture-stability-20261105/isolation-provenance.json)。
- 用户明确回复“接纳这5张修正（推荐）”，仅替换Models常规浅深、Models末项窄屏浅深和Models空态深色5图，其他153张未变。新[完整158张清单](research/accepted-png-hashes-20261105.txt)SHA为`b31439fd162a2323a62edc0fea0bc289b827635a15dbc45d3cd6899f2c7e1ec7`；原接纳记录/清单保存为initial文件，追加[修正接纳记录](research/visual-acceptance-correction-20261105.json)和[5图对照](review-capture-stability/index.html)。主审阅页现采用稳定before/after。
- [完整JVM](research/accepted-full-jvm-20261105/summary.json)1826/1826、245 suites，0失败/错误/跳过，退出0、2分9秒，真实执行测试（1 task执行/33 UP-TO-DATE），无selector且不比较像素。运行期间旧158图未改；结束后才替换5个已确认PNG，产品/测试代码不再改变，最终截图比较另行执行。

## 启动与冻结

- 基点：`main / ac22bb271007c814b6ef49b8499858acc815661c`。启动时暂存区为空，既有历史文件与 manifest 旧 CI 差异保留。
- 已读取父任务、P11规划和上下文清单、UI令牌契约及P10最终验证/独立检查/共享接口记录。P10的127项截图、1755项JVM等仅作前置证据，不计作P11验证。
- `python .trellis/scripts/task.py validate .trellis/tasks/10-21-ui-token-provider-settings` 退出0；随后同路径 `task.py start` 退出0，状态为 `in_progress`，父任务没有启动。
- [126张基准冻结清单](research/accepted-png-hashes-before-p11.txt)逐项匹配P10最终接纳清单；清单SHA-256：`8a63c66d92c94dcd9f19fbcf38980a5bd5f9df436b6b9eb9198dbc1850b7a7fc`。
- [起始工作区与源码哈希](research/workspace-start-20261105.json)保存七个Provider文件、共享组件及版本/更新文件来源；[manifest原差异](research/manifest-preexisting-ci.diff)用于交付前精确排除历史修改。

## 最新证据

| 检查 | 当前状态 |
| --- | --- |
| E11迁移前32张、12个真实弹层窗口 | 已完整冻结；33项仅缺基准失败，0 errors/skipped，退出1，37秒实际执行 |
| 最新P11行为与源码守卫 | [52/52真实通过](research/provider-final-results/summary.json)，9 suites、0失败/错误/跳过，退出0、1分钟；38个Provider行为+14个源码守卫 |
| 全部受影响用例 | [144个不同用例最新结果均通过](research/targeted-final-status.json)：首轮143通过/1定位器失败，修定位后1/1通过；窗口尺寸API修改后P11/守卫52/52再通过。不是一次144/144全绿运行 |
| Lint、dev构建、AndroidTest Kotlin编译 | [最终quality-verified](research/quality-verified/summary.json)退出0、5分25秒，13 tasks实际执行/47 UP-TO-DATE；0 errors/55旧warnings/3 hints，无新增或删除；实际打包和AndroidTest Kotlin编译 |
| 整包截图与旧图兼容 | [159项真实比较](research/screenshot-final-results/summary.json)，15 suites，126旧图零差异通过；32新图与1计数守卫仅缺基准失败，0 errors/skipped，退出1、79秒 |
| E11候选与来源 | before/after各32图、各12真实窗口；[审阅数据](review/review-data.json)32组均有变化，旧126张SHA不变；after/quality/current九源码SHA一致 |
| 独立检查 | [独立检查结论](research/check-summary-20261105.md)通过，无需产品/测试自修；[原始证据审计](research/check-evidence-audit-20261105.json)通过。未重复未变源码已通过的Gradle检查，执行权已交回主会话 |
| 人工视觉接纳、接纳后截图与完整JVM | 32组及5张稳定帧已明确接纳，共158图；[最终截图159/159](research/accepted-screenshots-20261105/summary.json)、[完整JVM1826/1826](research/accepted-full-jvm-20261105/summary.json)通过，均0失败/错误/跳过 |

## 迁移前捕获检查点

- [完整before清单](research/before/manifest.json)包含32张图片、参数、实际root/测量、产品与测试来源哈希；SHA-256为`d8a9e849084bf478a4ac76fbfe4195bc7ffe6f3537466a2a5e1f581110f4ce3f`。
- [33项运行摘要](research/before-final-results/summary.json)与原JUnit逐项核对：32个E11缺原图、1个计数守卫缺图，没有其他失败或跳过。首轮30图及2个未打开能力弹窗的捕获失败单独保留；在产品改动前修正入口滚动后取得最终完整before。
- [主会话核对](research/before-preflight-20261105.json)：32张哈希一致、12个真实窗口、8个产品/共享来源未变、126张旧PNG不变。列表浅深`662/662`、保存区浅深`2509/2509`、Models尾部浅深`2219/2219`，均为实际滚动值/max。
- 窄屏末项编辑/移除明确绑定最后模型卡，记录未裁切布局与真实touch bounds；操作位于浮动新增与底栏上方。已目视核对列表常规、Config保存、Models末项、能力窗口和抓取Sheet的原尺寸图，未用截图外观代替后续行为/对比度验证。
- 唯一涉及的共享组件为`NarraAlertDialog`，增加可选shape及动作文字颜色，旧默认与破坏性色优先级保留。[修改前补充来源](research/shared-dialog-baseline-20261105.json)与原始提交一致；其兼容验证纳入本批。现有SettingsComponents不需修改。

## 当前行为验证

- 首轮26项中24项通过。源码守卫将透明表达式次数误登记为3，实际为2；改为精确2次。显隐测试误以为取消密码视觉变换后Material的Password语义旗标必然消失；改为检查真实显隐按钮、TextLayout显示结果，以及切换provider后的隐藏状态，未修改产品`rememberSaveable(provider.id)`或新增跨tab持久化策略。
- [37项实际复验](research/targeted-behavior-results/summary.json)退出0、5 suites、0失败/错误/跳过，26秒；命令为`:app:testDebugUnitTest --tests com.example.myapplication.ui.screen.settings.Provider*Test --tests com.example.myapplication.ui.theme.UiTokenSourceGuardTest --console=plain`。
- 该阶段覆盖模板等待/单次导航、字段与provider ID隔离、真实tab/显隐、保存禁重、最后一个不可删除，以及模型选择/编辑/移除隔离、pending fetch归属、手工错误/标签、取消/恢复自动、清空和末项/48dp。尚不等于完整JVM、最终配色验证或视觉接纳。
- [首次颜色与集成检查](research/targeted-colors-initial-results/summary.json)为48项/8 suites，43通过、5失败，0 errors/skipped，退出1。五项均从实际TextLayout及像素承载面复现fixed dynamic-dark的primary `#6750A4`作为文字低于4.5：相对background约2.82、surface约2.66、surfaceContainerHigh约2.08；涉及焦点标签、抓取/模板/恢复自动/手工确认文字。真实保存成功/失败和首次共享形状检查在该轮通过；低对比问题随后局部修正，未改全局scheme或旧消费者默认。
- 上述低对比问题已通过P11局部内容色及共享可选动作色修正。[51项复验](research/targeted-colors-fixed-results/summary.json)退出0、9 suites、0失败/错误/跳过，59秒，主Kotlin/单测Kotlin及测试均实际执行（7 tasks executed / 27 UP-TO-DATE）。新增真实NavHost测试分别证明列表/详情返回仍先启动保存并立即pop，后续写入失败仍保留草稿；没有改成保存成功才离页。
- 首轮质量组合退出1、5分59秒，新增AndroidTest误用`DpRect.width/height`导致编译失败；已改为真实边界的`right-left`与`bottom-top`并最终编译通过。中间`quality-final/`虽退出0但有3条新增屏幕尺寸API警告；改用`LocalWindowInfo.containerSize`后，在`quality-verified/`实际通过并回到55条旧warnings。两份中间报告保留，不借用旧报告宣称通过。
- [长标题压力测试](research/title-stress-before-fix-results/summary.json)复现表单视口仅28dp、保存区域未完整可见；标题现限制在实际窗口高度40%内独立滚动，返回保持独立48dp，滚动状态按provider ID隔离，表单恢复273.5dp。主表单的名称输入与标题文字相同，首轮收敛测试定位到两个滚动节点；改为heading后代后[1/1复验通过](research/title-stress-final-results/summary.json)，产品布局未再改动。新增守卫负向用例只修正函数体内注入缩进，未改变守卫算法。

## 候选与保护范围复核

- [after清单](research/after/manifest.json)SHA-256：`add1fb170e0174f565f8a230445656089eacbf18c9f11971268a938179017b90`。前后宿主、字体、固定状态、打开与滚动步骤保持一致；before始终冻结，未回改主工作区取旧源码。
- 窄屏浅深的实际滚动值/max分别为列表`662/662 → 646/646`、保存区`2509/2509 → 2199/2199`、Models末项`2219/2219 → 2273/2273`。完整末项操作的未裁切布局与touch bounds、模型浮动操作与底栏关系均在清单中保存。
- [主会话候选预检](research/candidate-precheck-initial-20261105.json)确认32组完整且均有变化，已逐组查看六份总览并检查重点原图；此结果是助手检查，不代表用户视觉接纳。常规360dp列表随令牌边距调整进入双列，320dp/200%保持单列，纳入本次审阅。
- [最终来源核对](research/implementation-final-hashes.json)确认当前九份生产/共享源码与after、最终质量报告匹配；126张旧PNG、版本与更新清单、未改的SettingsComponents以及manifest原CI差异保持不变。实现摘要见[交接记录](research/implementation-summary-20261105.md)。
- 独立检查逐段核对八个产品文件、全部测试/守卫/登记及52/144/159原始结果、Lint/编译/APK/九源码哈希和32组视觉，无未解决代码问题。before记录使用工作树原始SHA-256；六文件可直接匹配Git LF字节，ProviderSettingsScreen与SettingsComponents依干净起始工作树及冻结raw哈希证明，不伪称所有raw文件哈希等同于Git对象字节。
- [完整独立审查](research/check-review-20261105.md)已落盘，检查代理在接纳前没有修改产品、测试或基准。用户后续已确认32组视觉，主会话精确接纳后继续完整截图/JVM；通过后分别审阅工作提交、归档及父任务总验收。

## 最终收口结果

- 全截图实际159/159、15 suites，退出0、63秒，2 tasks执行/33 UP-TO-DATE；与129秒的完整JVM分别保存报告，不混淆像素比较与普通JVM运行。
- 截图测试修正后的必要Lint退出0、112秒，实际运行单测Lint分析，其余未变输入复用；仍55条旧warnings/3 hints，无新增。产品与AndroidTest源码未改，不重复dev打包或设备测试编译。
- 最新稳定before/after分别位于`research/capture-stability-20261105/before`和`after`，清单SHA为`908cb617b036c1890e3c4d587ff25492c1314632834f960ba23d735a919a5264`、`b3a0d374cd94ac97ce7aa48220ad68abb35459b0570e0a7587ebab155dd6bbc6`。原始before/after及首次接纳记录完整保留。
- 最终158张清单SHA为`b31439fd162a2323a62edc0fea0bc289b827635a15dbc45d3cd6899f2c7e1ec7`。当前产品九文件、捕获三文件、APK、版本与manifest原正文均复核一致；隔离worktree目录和Git登记已按路径验证清理，其他工作树未改。
- 全部Gradle已结束，独立检查无未修复问题，准备分别提交实现/测试/证据与32张基准；未获得具体提交清单确认前不暂存或提交。

## 证据边界

实现与检查串行，Gradle每阶段由单一所有者执行，并按轮次保存命令、退出码、JUnit数量及真实执行情况。新基准未接纳阶段的缺图失败与真实行为失败分别报告，不record、不降低比较阈值、不豁免计数守卫。

真机读屏、系统返回/窗外触控、IME/系统栏、实际材质、物理性能及新视觉远端CI仍待补。宿主行为、截图、构建或AndroidTest编译不代表设备运行通过。
