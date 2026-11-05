# P11 独立代码与证据审查（2026-11-05）

最终结论：产品契约审查和接纳后的本机门禁全部完成，真实截图159/159、完整JVM1826/1826通过，最后Lint无新增警告。接纳后的实际复验发现并修正了一个E11截图捕获稳定性问题；没有修改产品。用户已分别接纳32组效果及其中5张稳定修正，当前可进入具体提交清单审阅，未由本检查提交/归档。

接纳前初审只新增审查记录与[独立审计脚本](check_evidence_20261105.py)，基于未变源码的有效报告复核；接纳后实际运行最终门禁及下述诊断/重捕，并只修改E11截图类的私有切Tab步骤。产品、行为测试真实触控、spec和基准不由本检查修改，全部Gradle现已结束并交还主会话。

## 接纳后验证与捕获稳定修正

1. 用户接纳32组效果后，主会话写入32张，158张清单SHA为`6178162c6955c476905de98897e0ffdc81ea8726c2808ae6882844ee58d31c49`。首次[全截图](accepted-screenshots-20261105/attempt-1/summary.json)实际159项中155通过、4项像素差异，退出1，67秒；全部差异只位于“模型”Tab按压水波区域，计数守卫已通过。
2. 同参数4项复跑仍失败；推进Compose与主Looper各1000ms后，actual与前轮仍分别有14922/5306/15920/5353像素变化，证明不是固定旧基准差异。未通过反复随机重试取得假绿，也未record、降低阈值或覆盖基准。
3. 仅将`ProviderSettingsScreenshotE11Test`私有`selectModelsForCapture`改为调用真实Tab的`SemanticsActions.OnClick`，等待Compose空闲；所有共用行为测试的真实触控路径和产品动画保留。主工作区语义探针32项中27项与接纳图一致，5项只去除按压瞬态。
4. 在`D:/p11-capture-stability/20261105`创建`ac22bb27`隔离worktree，先仅叠3个捕获测试文件与`sdk.dir`，保持旧126张基准，取得[稳定before](capture-stability-20261105/before/manifest.json)。再叠8个当前生产修改文件及只为原字节一致的未改SettingsComponents，取得[稳定after](capture-stability-20261105/after/manifest.json)和[重复after](capture-stability-20261105/after-repeat/manifest.json)。每轮33项仅因32张原图缺失和计数守卫失败；原始before/after目录均保留。
5. 两轮after的32张PNG全部逐字节一致，均包含12个真实窗口；before/after的尺寸、字体、窗口和滚尾策略相同。原27张E11不变，5张变化是models浅深、models-tail-narrow浅深、models-empty-dark。用户明确接纳这5张后，由主会话替换五图并保留其他153张；初次接纳记录及清单已另存，当前清单SHA为`b31439fd162a2323a62edc0fea0bc289b827635a15dbc45d3cd6899f2c7e1ec7`。

| 最终验证 | 实际结果 |
| --- | --- |
| [全截图](accepted-screenshots-20261105/summary.json) | 159/159，15 suites，0失败/错误/跳过，退出0，63秒；2 tasks执行/33 UP-TO-DATE。真实像素比较，日志为run-final.log。 |
| [完整JVM](accepted-full-jvm-20261105/summary.json) | 1826/1826，245 suites，无selector，0失败/错误/跳过，退出0，129秒；1 task执行/33 UP-TO-DATE。使用稳定捕获代码，不比较像素；随后只改5张PNG/文档，未重复JVM。 |
| [捕获修正后Lint](capture-stability-20261105/lint-final/summary.json) | 退出0，112秒；3 tasks执行/30 UP-TO-DATE。单测Lint分析真实执行，主分析/报告因结果未变而复用；0 errors、55旧warnings、3 hints，无新增/删除。 |
| 构建/AndroidTest | 产品与设备测试未改，复核原quality-verified的实际成功打包与编译，不无理由重跑。APK与9个产品/共享源码SHA保持不变。 |
| [最终审计](check-final-accepted-audit-20261105.json) | 当前158图匹配新清单，产品/捕获源码与稳定来源一致，APK/版本配置未变，manifest原P10及历史正文原字节不变。 |

[隔离来源记录](capture-stability-20261105/isolation-provenance.json)包含Git基点、每个覆盖文件SHA、SettingsComponents原字节对齐说明及清理结果。96张PNG均按冻结清单复核后，经过绝对路径边界检查删除临时worktree，目录与Git登记都已消失。没有切换/还原主工作区产品，没有用after充当before。

## 接纳前初审记录

以下保留首次独立审查与候选阶段的证据，最新门禁和新增捕获修正以上节为准。

## Findings (fixed)

接纳前初审无需追加自修；接纳后新增的截图测试修正见上节。下列实现阶段修正已由独立检查确认：

- fixed dynamic-dark的偏暗primary直接作为文字时低于4.5。P11焦点标签及普通文字动作已使用实际onSurface，主实底按钮保留primary/onPrimary，破坏性确认保持error。
- 超长标题曾只给主表单留下28dp。标题现按实际窗口高度40%有界滚动，返回位于标题滚动区外；长标题测试检查滚至真实max、完整标题布局和保存/返回的真实触控。最后定位器使用heading后代，避免匹配同名输入框。
- 首轮能力窗口入口被FAB挡住时，真实窗口守卫拒绝捕获。完整before在产品修改前以可见末项编辑按钮重取；未用底页或after代替before。
- AndroidTest的DpRect宽高已改为边界差值；标题/模板窗口使用LocalWindowInfo消除3条新增Lint警告，未加suppress。

## Findings (not fixed)

无本批代码阻断。初审时视觉接纳及完整门禁尚未完成，现已取得上节的最终结果；具体提交/归档由主会话继续。真机读屏、系统返回/窗外触控、IME/系统栏、实际材质、D01、物理性能及新视觉远端CI仍缺真实证据，未把宿主或编译结果升级为设备结论。

## 代码与行为

| 范围 | 独立核对 |
| --- | --- |
| 列表与入口 | P10的SettingsNavGraph、P05的RoleplayNavGraph仍指向原Provider路由。列表保持空草稿ensure、凭据键控自动健康检测与pending新ID等待。新增名称、搜索与状态UI只消费令牌/语义色。 |
| ID、Tab与显隐 | `rememberSaveable(providerId)`和`rememberSaveable(provider.id)`保留；新增标题ScrollState按provider.id隔离。没有改协议、mode、URL、key、模型排序/override或导航参数。 |
| Config | 启用行是唯一toggle回调，子Switch只绘制。真正输入节点提供标签；保存中禁重；最后一个Provider不能删。删除仍是原显式删除后返回，未新增业务状态机。 |
| 保存与返回 | ProviderPersistenceTest实际点击页面并驱动真实SettingsViewModel，Fake写入门槛验证成功/失败和完整草稿。ProviderNavigationTest通过真实registerSettingsProviderRoutes/NavHost，证明写入尚未结束时已经pop，随后失败仍保稿。 |
| Models与弹层 | 选择、能力编辑、移除独立命中；pendingFetchProviderId严格归属。抓取搜索/选择/确认、手工空ID/trim/显示名回退、能力取消/显式恢复自动、清空取消/确认均驱动真实UI回调。 |
| 默认兼容 | NarraAlertDialog只增加shape和actionContentColor；默认仍为历史cardRadius/primary，破坏性确认优先error。共享布局、动画、Insets和关闭回调不变；NarraInfoDialog及其他消费者默认不变。SettingsComponents没有修改。 |
| 无障碍与颜色 | 200%/320×640宿主检查未裁切布局及实际touch bounds≥48dp、相邻动作隔离、Tab角色/selected、输入error、fetch的禁用与加载语义。八种scheme的测试读实际TextLayout和像素承载面，不只镜像颜色常量。健康状态保留文字。 |
| 网络隔离 | 截图使用固定UiState、最终health map与本地回调；保存与导航测试显式注入Test API，更新仓储/下载控制也为Fake；没有用户数据或真实密钥。生产健康检测触发条件未变。 |

## 截图、滚尾与守卫

- ScreenshotCases只增加E11的32项与族计数，旧ID/参数未改；真实测试类有32个独立@Test。ScreenshotBaselineGuardTest和StrictRoborazziOptions没有被削弱。
- captureProviderWindow只能选择含真实Dialog、相应标题且不含StageTag的独立root；操作、字体、density、窗口尺寸都在捕获前断言。未开窗口负向用例逐个覆盖六类标题；没有类名断言代替缺窗口检查的问题。
- 两阶段均32张、12个真实窗口，常规360×800dp/100%，窄屏及窗口320×640dp/200%，density=2。前后ID、fontScale、stageDp和target一致，图片SHA与审阅副本一致。
- 窄屏浅深的真实scroll值/max分别为：列表662/662→646/646，保存2509/2509→2199/2199，模型末项2219/2219→2273/2273。末项编辑/移除绑定最后模型卡，raw/touch矩形完整处于视口内，并位于浮动操作和底栏上方。
- 独立目视检查六张总览的全部32组，包括六类弹层浅深；窗口正文/操作确实存在，保存与末项操作可见，未发现捕获错误。常规列表双列与窄屏单列来自令牌边距下原自适应Grid规则，仍需用户视觉接纳。
- 源码守卫对七个Provider文件及共享Dialog完整扫描。requiredDeclarations校验存在/数量，scannedDeclarations=null代表全文件扫描。新增helper名单及负向注入加强移位防护；共享旧间距、未找到Provider提示及透明色均为精确声明/表达式/次数例外。P10的SettingsGroup两个重载规则保持。

## 原始报告与来源

[独立审计结果](check-evidence-audit-20261105.json)由审计脚本实际运行取得，退出0；逐项核对JSON摘要与JUnit、现有PNG、前后图、Lint XML、APK和源码哈希。

| 验证 | 真实证据及本检查复核结果 |
| --- | --- |
| 最新P11/守卫 | [provider-final-results](provider-final-results/summary.json)：52/52，9 suites，0失败/错误/跳过，退出0，1分钟；38项Provider行为、14项源码守卫。主Kotlin、单测Kotlin和测试实际执行。 |
| 受影响回归 | [targeted-final-status](targeted-final-status.json)：144个唯一用例的最新结果逐个映射回XML且通过。首轮144中143通过；长标题定位器修正后1/1通过；窗口API修正后52/52再次通过。不是一次144/144全绿运行。 |
| 真实截图 | [screenshot-final-results](screenshot-final-results/summary.json)：159项/15 suites，126旧图通过；32 E11+1计数守卫仅缺基准，0错误/跳过，79秒，退出1，实际比较。 |
| Lint/dev/AndroidTest | [quality-verified](quality-verified/summary.json)：退出0、5分25秒，13 tasks实际执行/47 UP-TO-DATE。Lint 0 errors/55旧warnings/3 hints，与P10逐项相同；dev打包与AndroidTest Kotlin实际编译。未调用connected任务。 |
| 原基准 | 126张PNG逐项匹配冻结清单，清单SHA `8a63c66d92c94dcd9f19fbcf38980a5bd5f9df436b6b9eb9198dbc1850b7a7fc`。 |
| 当前来源 | after、quality-verified及当前9个源码SHA完全一致，包含未改SettingsComponents。构建/更新配置、暂存区与manifest原CI差异保持。 |

before清单SHA为`d8a9e849084bf478a4ac76fbfe4195bc7ffe6f3537466a2a5e1f581110f4ce3f`，after为`add1fb170e0174f565f8a230445656089eacbf18c9f11971268a938179017b90`。原共享Dialog有单独Git blob/原字节冻结记录。

before源码raw SHA逐项匹配起始工作树记录，起始Git状态中这些文件干净。六个Provider文件的原字节直接匹配ac22bb27；ProviderSettingsScreen及SettingsComponents的冻结值是工作树字节哈希，不宣称等于Git对象哈希。未改SettingsComponents实测有混合换行（597个CRLF/728个LF），规范化后内容与ac22bb27完全一致。没有修改冻结证据或把换行差异当产品漂移。

首次内联审计尝试直接用Git的LF/全CRLF字节匹配全部before raw SHA，因上述工作树换行口径而退出1；确认混合换行与起始干净状态后，最终审计脚本按真实来源分别验证并退出0。该修正只涉及审计判断，没有重写任何源码或冻结记录。

`quality-initial`是编译失败历史轮，`quality-final`是58warnings中间轮；产品构建结论使用`quality-verified`。以上初审阶段仅新增Python审计、包/路径与定向diff检查，接纳后另实际执行了上节记录的Gradle验证及捕获修正。所有阶段均未record、暂存/提交/推送或归档；基准由主会话按两次用户明确接纳写入。
