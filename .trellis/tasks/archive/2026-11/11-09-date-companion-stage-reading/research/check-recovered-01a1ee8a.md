# D4 最终独立检查

2026-11-19，`/root/d4_recovered_check`。**D4 本轮源码、工程和原生行为检查通过，当前未留确定产品问题；旧截图基准仍待视觉接纳，verify 整体不能记为通过。** 本轮收口D4，D5生产未开始，不代表整个2.4.1已发布或全部交付。

## 最终范围与保护

- 最终源码绑定 `resume-2026-11-18/d4-final-lint-screenshots-46/source-after.json`：1460项源码，当前工作树独立重算零漂移。相对1425项D4起点，为35新增、15修改、零删除，共50条任务路径。
- 281项保护文件仅两处已授权差异：`RoleplayScreen.kt`原默认-null adapter与`RoleplayDialogueBubbles.kt`正式陪伴flag/annotated适配；其余279项（含旧schema、PNG、发包参数和更新JSON）保持。原用户及其它任务改动未回滚。
- 四个既有F5生产源码的备份SHA、必要hunk与已知IDE换行逆向核验均通过，见 `recover-2026-11-19/rendered-text-four-source-inverse-30.json`。当前四源SHA与该证明仍一致；格式差异逐块比较Kotlin词元并保留字符串/注释，不以整文件删空白掩盖修改。
- 主会话唯一执行Gradle、设备与formatter。本代理直接清理指定URI/私有Composable风格，并只为最后Visual方法增加观察性取证；源码写入均按明确所有权，不提交、推送、发布或归档。

## 最终修复复核

- Reader当前/候选用途及SQL前置过滤、真实Room134条合法混合来源、UTF-8 keyset、删游标与展开窗口已验证；阅读授权不扩大模型scope，浏览不切当前运行。
- 私有图片复制的取消清理由实际target所有者负责，未来raw/未知kind引用参与完整canonical保护；CAS冲突保稿，共享素材不误删。
- Player唯一时钟/source代次、迟到回填守卫、完整grapheme与生命周期保持。正式Stage只消费合法完成的D3投影，显示、菜单、重播与选段不新增主文本请求。
- 删除首可见来源及用户拖动中窗口更新的恢复所有权已修复，真实同指针回归和原像素offset强断言保留；进度固定末端、操作横滚、窄屏200%/IME/48dp与Back层级已通过。
- F5正式正文不再二次清洗标签/实体或按UTF-16硬切；纯段分配保留字符/空白并共享Player完整簇边界。旧零参段分配、旧三参annotated ABI/default保留，五处段缓存及四渲染路径五调用透传身份flag；纯空白中间Text原样append。实际Stage/Reader TextLayout核验字面标签、emoji/组合/ZWJ簇、300实体空格、raw/source及零误调用。
- 两项误判已由真实取证纠正：单Text全文查询不能代表逻辑长文未显示；Reader程序索引跳转不能代表已取得UserInput历史位置。现按真实段/owner坐标及触控+稳定itemId验证。新Visual名称用例在编辑和IME后确认真实受控草稿往返，再保留原最终草稿不丢/Back/字体/48dp断言；不宣称未写出的正常轨迹直接捕获了某个覆盖回调。
- `companion-stage-reading-guidelines.md`已只读复核，F5纯显示/ABI/cache、真实段查询/坐标、长文分别首尾滚动及历史UserInput驱动合同与实际源码一致。

## 最终验证

| 项目 | 原始证据与真实结论 |
| --- | --- |
| JVM | 独立解析12/13/16b/19/25/28/31原XML，按class+method最新覆盖后 **149/149、20类通过**；不是一次运行149，也不累计重跑次数。Player27在28已过且其源码未再改；最后45受影响JVM在31已过。 |
| 编译/双APK | 31产品/JVM/AndroidTest编译与dev/test双APK成功；最后仅测试变更在44重新编译/打包成功。最终dev APK SHA `758bd1a63b760af96f5591b1f1cff393d91669ce9e56b86cbda066b33b94c80b`，test APK SHA `e19c5699a70b91ab6e804b64ccde4242d33abd704424a4272410bee60c80ef3d`。未改发包版本或上传。 |
| 原生 | 38完整33方法为32过/1失败；其余32绑定38，最后Visual方法在45 **1/1通过**。独立解析原报告按class+method最新覆盖后 **33/33、3类通过**，不是单轮33全过。真实APK枚举只作选择器数量证据，不作通过证据。 |
| 当前Lint | 46实际执行 `lintAnalyzeDebug`、AndroidTest/UnitTest分析和`lintDebug`。原XML独立解析为 **0 Error/Fatal、55 Warning、3 Hint**；未把旧告警记作零告警，也未作全局抑制。 |
| 旧截图verify | 46实际旧35方法：**1匹配、34基准失败，0error/skip**，联合命令exit=1。不是截图verify通过。68个新候选中34 actual与D3对应actual逐张独立读字节比较，**34/34 SHA一致、像素差异0**，无新增D4像素回归；旧baseline未record或替换。旧外观接纳继续待定。 |
| 源码对应 | 最终1460源前后及当前工作树无漂移；所有历史失败保留，并以真实修复/定向复验覆盖。核心、测试、图片/配置与同源raw/source合同均已全范围复核。 |

独立汇总证明：`recover-2026-11-19/check-final-method-counts.json`、`check-final-scope-lint-46.json`、`check-actual-images-vs-d3-46.json`；当前Lint/XML、旧截图原XML/log/候选及D3对照在46目录。PRD/执行计划/父矩阵/2.4.1版本记录/交接/journal由主会话同步。真实付费模型/TTS、D5生产与外部发布未在本轮执行。

## 历史复核记录

以下保留各检查点当时的发现、失败和未验状态；最终状态以上述本轮证据为准。

2026-11-18，`/root/d4_recovered_check`。本记录先保存第13/14轮检查点；主会话正在修复第14轮原生失败，最终新快照与复验结果尚待补充。**当时不代表 D4 最终验收通过。**

## 范围与权限

- 已读取完整保存的原生 hook、`trellis-check` 技能、D4 PRD/design/implement 与完整14条审查清单；按实际 UI、Room、来源、配置和取消边界读取专题规范及上下游交接。
- 独立重算初轮1458项源码与 `resume-2026-11-18/d4-resources-13/source-after.json` 零漂移；相对 `continue-2026-11-18/baseline/source-hashes.json` 为46项 D4 新增或修改路径，不将整工作区归为 D4。
- 本代理仅写本检查记录；源码保持只读，Gradle、设备、formatter 由主会话唯一执行。父任务、spec、版本、旧PNG、schema、发包参数和更新JSON未由本代理修改。
- 281项保护文件独立核对仅 `RoleplayScreen.kt` 的已授权最小adapter不同，其余280项未变。

## Findings (fixed)

本代理未自修源码。此前 F1–F3 的原作者修复已通过当前代码和对应测试证据复核：

- **F1 阅读用途过滤**：`RoomCompanionReadingRepository.allowed()` 同时检查当前与候选绑定用途；连续用途仅纳入连续候选，独立/legacy仅限本conversation/run。`ConversationDao.listCompanionReadingPage()`在keyset/limit前加入`b.purpose = :purpose`。实际Room用例混入同空间不同用途、角色/persona/群聊、待处理与坏selected attempt来源，并保留134个合法记录；独立范围及绑定运行变化同样有实际通过结果。
- **F2 复制取消所有权**：`LocalImageStore.copyToAppStorage()`在拥有实际target的层级捕获取消，包括IO结果返回时的prompt cancellation，以`NonCancellable + dispatcher`清理本次target并重新抛出取消。实际ContentResolver EOF取消及编码file URI回归仍存在并通过；旧`LocalImageStoreTest`12项也已复验。
- **F3 未来raw引用保护**：`AppSettingsStore.companionImageReferences()`从完整原JSON收集字符串引用，未知视觉版本/kind/扩展不会因typed投影消失；`CompanionImageReferences.read()`加入这些引用并规范化canonical路径。引用无法完整核对时停止已发表文件的物理清理。真实私有文件/未来raw与旧场景删除回归已通过，不以字符串留存代替文件留存。

## 已核对的合同

- Player拥有唯一typingJob/generation，复制拍快照，完整source/raw/beat变化使旧timer失效；补全不推进下一拍，父scope取消/close不可复活。中文、emoji、组合文字、GB9c、同步观察者重入与不可取消迟到结果的27个实际JVM用例保留。
- Stage/Reader/素材操作不持有主文本模型请求协调器。正式Stage队列由现有已完成、非streaming、有效D3 metadata、支持的protocol/parser及非空beats守卫；原消息/输入/附件/快捷回调仍由原主屏拥有。显示/素材变更不写canonical或原source revision。
- Reader使用独立授权scope与值游标；UTF-8 ID比较对应SQLite BINARY，删游标不依赖查回游标行，刷新保留展开窗口。导航到新Reader不调用`enterScenario()`，不会把阅读scope授予模型历史。
- 素材角色默认/本场覆盖、套目标表情→套默认→旧本地/远程→头像/姓名、PORTRAIT/BACKGROUND/DECOR分用途与运行时加载回退均在当前链路；配置逐字段兼容、未知高版本只读、expectedRevision CAS，失败保稿。
- SpeechUnit保持D3原source/修订、beat、原UTF-16 span、rawText、显示text及独立voiceEmotion；Reader跨来源选区在源修订/删除后失效。当前没有接声音网络或TTS。
- 原Stage/Reader真实长按→同指针拖动取消、默认列表及HUD当前gate/历史label路径已审；320dp/200%组合主屏和删除锚点还须以修复后原生结果为准。

## Findings (not fixed)

第14轮原生实际30个唯一方法全部完成：26过、4失败、0跳过、runner无额外错误。以下问题由主会话分配的UI实施者修复，本代理不在并行写入期间自修：

1. 完整RoleplayScreen在320dp/200%组合中的正文不可见（Stage测试743行）。
2. Stage→LIST后正文末项首行未完整进入视口（Stage测试541行）。
3. 完整RoleplayScreen HUD方法的Back导致`NoActivityResumedException`（Stage测试389行）。
4. Reader删首可见来源后等待正确邻居锚点超时（Reader测试110行）。

第14轮证据在 `resume-2026-11-18/d4-recovered-device-14/`，不可将ADB退出0或已生成PNG当作这些方法通过。旧截图verify与候选接纳仍待主会话的后续真实结果。

## Verification

- **Lint/TypeCheck/双APK：第13轮通过。** 独立核对原`gradle.log`中的实际`lintDebug`、产品/JVM/AndroidTest及Baseline编译、Debug/AndroidTest APK和`BUILD SUCCESSFUL`。UI解除冻结后的新源码不得直接沿用这些结果。
- **JVM：125个唯一方法的最新证据通过。** 独立解析第12轮原XML为125执行/3资源失败，第13轮为资源+LocalImage22执行/0失败；按class+method覆盖后为125通过。不是一次运行125全过，也不累计成147。
- **原生：第14轮26/30，仍失败。** 源码前后一致，完整runner结果及候选已保存。修复后复验待补。
- **视觉：未接纳旧基准。** 本检查不替换PNG、不record，不把旧像素漂移或新的候选称为已验收。

## 最终复核待办

收到修复后freeze清单后，复核Stage/Reader及相应测试增量没有减断言或绕开真实UI；按新源码哈希核对工程、原生与截图报告。主会话需按新合同同步spec和2.4.1版本记录、AC/父矩阵与D5纯SpeechUnit交接。D4和整个2.4.1不能提前记为完成。

## Reader 第一组修复复核

已读取 `reader-repair-recovered.md` 并核对冻结SHA：Screen为`CF36D39485B9047CCAA2BC7D7568D26C957495CF7B4F623FDB203BB3A0E13509`，Test为`A9A9123EA32ED7B112C2200845976EB0DF054102BC17F68B2C7083B8685B5D2F`。恢复状态可观察、正文可见项过滤、窗口协调期间禁止独立layout恢复均已落地；删除邻居的item/offset=0/非跟底/39条断言保留，新增恢复后真实滚动及再插入记录保锚。未运行设备，不能记该修复通过。

本组另有需处理的滚动竞态，已及时报告主会话：items变化时若用户仍拖动/惯性滚动，协调门会在等待`isScrollInProgress=false`期间禁止捕获新anchor，随后无条件恢复更新瞬间的旧anchor，从而撤销用户在等待期间继续滚动的位移。现有测试均先滚动完成再更新items，没有驱动“拖动中插入→继续拖动→松手”的序列。修复应保留最后用户位置的所有权，并绑定对应实际交互证据，不仅保证旧anchor可恢复。

新增 `companion-stage-reading-guidelines.md` 的7节合同、接口/数量/文件大小/UTF-8游标/source/字符/取消/raw引用/SpeechUnit及index/presentation入口已只读核对；最终UI预算、返回与锚点仍以修复后源码/设备为准。

## Stage 与 Reader 跟进源码复核

- Stage四文件仅在正式视口插槽且大字/小高度时启用紧凑输入；`compactInlineEditor=false`的原3行及Clip默认保留，input原值和完整编辑入口仍在。紧凑建议通过原输入「AI帮写」打开有界Dialog，复用原`RoleplaySuggestionSection`和生成/应用/清除三回调，打开/Back不调用生成。IME期间收起工具栏/chrome是局部显示状态。LIST增加真实触控与TextLayout/ScrollBy定位，原首末行与无省略断言保持。
- 生产`CompanionRouteContent`确实在ACTIVE安装BackHandler；单独渲染完整Screen的两个用例补等价宿主owner，HUD/IME/Dialog先关闭、最后离场与零请求断言未削弱。
- Reader跟进已加入真实UserInput代次与新窗口layout总数/key/index校验；更新时仍在运动或等待期间有新用户滚动时不恢复旧anchor，layout observer不在运动结束后补旧恢复。此前报告的用户位移回撤路径在本版源码中关闭，不能仅此就记设备通过。
- 新增`onePointerDrag_windowUpdateAndContinuedDrag_keepTheLastUserPositionAfterRelease`确为同一指针down→前插20条→继续move→停住/up；真实layout与回调source/item/offset同时核对，松手前后严格比较，并核验80条不重漏及零读取/刷新/离场回调。Reader现在11方法，整组三类设备预期31方法。

以上仅为修改后源码复核；最终SHA以主会话6文件formatter后的冻结清单为准，相关21 JVM、编译/Lint/双APK、31设备与截图verify尚待主会话实际报告。

## 2026-11-19 最终 Stage 三文件静态复核

已读取最新`stage-repair-recovered.md`和Stage/Test/InputBar三文件。23轮原始诊断确认原窄屏204字实际被既有阅读组件分为85+85+34三个Text，576×200px视口内首行620..706完整位于596..796。此前从全文单节点查询失败推断正文预算归零不成立，不能保留该推断为确认根因。LIST的原144字/9行高度大于视口，新测试用真实首尾两次滚动及TextLayout owner坐标核验，23轮已实际通过。

最新测试在initial/IME/快捷每次检查重新枚举当前Beat内unmerged Text与当前node.id，重复内容不作唯一文本匹配；按实际节点顺序拼接TextLayout的原text严格等于204字，逐行无省略，再量当前可见完整行。不会跨重排复用旧id。进度固定于外层Row末端，动作独立横滚，补进度四边/整行与滚入按钮48dp证据。新增快捷Dialog高度在其内容中读取LocalWindowInfo并按实际density换算，旧API/default/全局Dialog未改。这组局部修改未发现额外确定阻塞，仍待新源实测。

**全范围另发现显示保真阻塞，已报告主会话，当前未修复/未运行复现。** Stage复用的`RoleplayDialogueBubbles`和`RoleplayReadingMode`仍把已解析的`companionBeat.content`交给`toLongformParagraphs()`，其委托的`RoleplayLongformMarkupParser.splitDisplayParagraphs()`再次使用`stripSupportedTags=true`并按UTF-16下标硬切无标点长文：

- D3实体单次解码后应作字面的`<narration>…</narration>`会被再次剥标签，显示与已解析Beat不一致。
- `"中".repeat(143) + "😀" + "后".repeat(200)`在SmartParagraphHardMax=144处可确定切开高/低代理；末段大于48字，tiny merge不会救回。Player的完整grapheme前缀不能阻止两个独立Text中的坏字。

应限定正式companionBeat采用不再清洗协议的纯显示段分配，并保证每个段边界是完整grapheme；旧非陪伴默认可以保持。若按是否有Beat切换支路，remember缓存键须包含该身份条件。回归应驱动实际Stage/Reader中的长emoji边界和已解码字面标签，不只重复Player字符串断言。源码维持主会话冻结，本代理未改共享渲染文件。

## F5 修复及空白补修的独立复核（27–32）

已读取`rendered-text-fix.md`、新增纯段分配/JVM用例和实际五处消费路径。`CompanionDisplayParagraphs`不trim/解码/剥标签，保留所有字符；48/92/144、标点优先及20字短尾策略的切点均来自唯一grapheme ends，超长单簇仍保留且循环严格推进。Player只提升既有函数为internal，逆转这一词后原始SHA与旧Player一致；原零参段分配和两个旧三参annotated builder仍存在，默认不变。

初次补修仍留下全空白段问题：旧annotated `isBlank`早退会丢失通过300个`&#32;`一次解码得到的中间空白Text。已及时报告，六文件补修加入显式parsed重载，只有true保留全空白；Character第二层remember也含flag，Character及思想/旁白/对白两分支共五个调用透传。Mapper直接拷贝Beat，quote builder按原子串append，正式路径未发现其它trim/剥除。

八个JVM方法保留旧/false空白行为并核验true、字符/实体/空白、UTF-16独立性、组合/ZWJ/旗帜/Indic/Prepend/CRLF及超长簇推进。Stage和Reader原有F5方法扩展真实实体空格正文，从实际TextLayout精确拼接且显式核验非空的纯空白中间Text；标签/完整簇/raw/source/零误调用强断言保持，原生数量仍为33。

四原源码的最终保护证明在`recover-2026-11-19/rendered-text-four-source-inverse-30.json`：4个备份原SHA和4个format30当前SHA全匹配；撤销必要F5/blank改动及单列的IDE换行后，4文件与原备份LF字节全相等。相邻rendered块与旧D4阅读包装函数的格式差异逐块比较Kotlin词元，字符串与注释保留，不用整文件粗暴去空白掩盖改动。

当前源码层面F5已闭合。工程28实际71 JVM通过，但整条因Reader错误包级import失败；29修import后的工程通过属于空白补修前检查点。工程31实际45 JVM、三类编译和双APK通过，1460源前后一致。设备32实际33完成、32过/1失败：Stage13（含F5标签/长簇/300空格）、Reader旧11、Visual8通过，唯一Reader新F5方法在`layouts.isNotEmpty()`失败，具体index/item/过滤原因待原作者仅测试诊断。不能以静态闭合或Stage通过替代整个D4验收。最终新源码复验与Lint/35旧截图仍待该真实失败收口；旧失败记录、未接纳基准继续保留。

## Reader F5 诊断35的独立确认

已读取35的原TXT、instrumentation/validation，并目视两张PNG。index0的THOUGHT有46字完整字面标签，filteredLayout=1，实际visible为1/2/3；第一张图还同时显示下面的NARRATION正文。index1请求Lazy2时，实际firstVisible=5、offset=219、visible=5/6，followingBottom=true，目标scopeNodes/rawLayouts/filteredLayouts均为0；第二张图确实停在后面的连续空格正文。该失败不证明正文未渲染或再次清洗，而是新测试未取得历史滚动所有权后，目标项离开Lazy视口。

修正边界已确认：仅新方法使用真实UserInput触控取得回看位置，再按稳定itemId确认目标实际可见；不直接改Fake flag、不加超时、不减所有字符/独立簇/300空格/raw/source/零调用断言，生产代码继续冻结。35本身仍为1方法失败、无源码漂移；等待修正后新冻结及完整33设备，再与最终Lint/旧35截图一起做全范围结论。

## Visual 草稿名称观察性诊断

完整设备38中Stage13及Reader12已全部通过，唯一旧Visual窄屏方法在最后的名称断言失败：期望“窄屏未保存草稿”，实际“原立绘”。未改源同APK单项39再次复现，因此不将其解释为仅整组干扰。原始中文以instrumentation.log为准，汇总error的乱码不影响计数和方法归属。

静态核对中，仅`Fixture.onDraftChange`写入draft；onBack只计数，测试开Dialog以及onDismissDiscard均copy当前state且只变flag。旧方法没有在替换文字后立即确认编辑往返，因此先观察“从未写入”或“IME迟到回填”，不据最后断言擅自改生产。

主会话明确授权后，本代理只改`CompanionVisualSettingsScreenTest.kt`：

- 记录before-name-edit、after-text-replacement、after-ime-shown、after-ime-back、after-host-back-and-dialog-shown、after-discard-dialog-back与失败阶段的EditableText/Focused、portrait draft名称/dirty、IME及Back/丢弃计数。
- `Fixture.onDraftChange`仅新增before→incoming→after字符串轨迹，原`state.value = state.value.copy(draft = it, isDirty = true)`赋值保持原样。轨迹不是可观察UI状态，不改变Fake数据。
- 新轨迹正常仅保留在内存，新增取图仅在实际失败时执行。先将TXT写入合成cache `d4-candidates/visuals-name-state-failure.txt`，再保存同名PNG；诊断失败作为suppressed异常附着，原失败继续抛出，不变为通过。

原输入链、ui.showIme、三次Back、最终草稿不丢、320dp/200%/48dp及旧候选取图操作和全部断言未改，未运行formatter/Gradle/设备。仅观察性变更已冻结，formatter前SHA为`ea43dee5d5d971d8f18fa7347d2312dcb03f6fa3c5fa1c73150d9bd2c7d39758`；原39 SHA为`567b8af9003491d739507ad6b48de0294755c74f38181e466872d4d1076ebc7f`。

逆移除所有诊断增量后与39前原文件逐字节一致，8个@Test名称/数量保持；证明与原文件备份在`recover-2026-11-19/visual-name-diagnostic-static-audit.json`和`visual-name-before-diagnostic.kt.txt`。真实轨迹与最终验收仍待主会话新冻结编译/单项设备结果。
