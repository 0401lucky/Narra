# Research: P06 规划独立静态审阅

- Query: 当前世界书与共享 ContextTransfer 全页规划的范围、数据/取消/失败契约、截图计数、隔离探针、共享默认和实施边界是否准确且可执行？
- Scope: internal；planning 静态审阅；仅写本文件，不修改产品、spec 或主任务文档，不读取 implement/check JSONL。
- Date: 2026-11-05
- Active task: .trellis/tasks/11-05-ui-token-worldbook
- Review snapshot: 主规划首读版本为 prd.md 45行、design.md 86行、implement.md 52行。主会话随后完成三份研究的机械拆分并修正文案；下列记录初审发现及末次静态复核状态，旧研究锚点同时给出小节名。

## Findings

### 1. 审阅结论

**未发现新的产品范围或截图矩阵阻断项。** 用户已决定同时迁移共享数据页；规划未借此扩展匹配、合并、格式、权限、持久化或错误恢复策略。新消息“继续”仍不能替代最终摘要审阅后的实施批准。

初审提出三处非阻断文案问题。末次复核N2/N3已修正，N1的主规划/验证矩阵已修正，研究状态表尚可同步一句限定。已知研究体积门禁的字节问题也已通过分片解决。所有 Popup、ActivityResult/ContentResolver 和固定时间探针均未运行，当前只确认方案有明确验证与失败边界，不确认它们已可运行。

### 2. 已知门禁及复核（分片字节问题已解决）

**B1：初审时研究文件超过单文件32768字节的注入上限；分片字节问题现已解决，引用完整性由主会话另行校验。**

初审期间只读文件元数据为：

| 文件 | 字节数 |
| --- | ---: |
| research/entry-list-detail.md | 41486 |
| research/editor-contracts-tests.md | 44147 |
| research/context-transfer.md | 33003 |

主会话采用主契约 + validation/consumers 分片，不改全局注入上限。末次只读元数据确认：entry-list-detail.md / entry-list-detail-validation.md为18999 / 23011字节；editor-contracts-tests.md / editor-validation.md为27597 / 17067字节；context-transfer.md / context-transfer-validation.md为17853 / 15663字节，均在上限内。后续小幅文案修改以主会话最终校验为准。

仍需主会话确认所有必要片段被引用、相互链接与planning状态正确，并重跑任务静态校验。本研究角色未读取JSONL，不能替主会话证明注入清单已完整；不能把这一角色限制报告成新的产品阻断。

这不是新增产品问题，也不要求向用户重新确认“是否迁移资料库”。

### 3. 非阻断发现与精准改法

**N1：把“确认失败保留预览/pending”限定到现有捕获范围。**

- 位置：prd.md:22 的 W6；design.md:42 的“数据预览/确认”；context-transfer.md 原§5“副作用、确认、失败与权限契约”及原§10“取消与确认”。
- 当前概括是“确认失败保留preview/pending供重试”，容易被理解为确认流程任意阶段失败都受保障。
- 真实代码：app/src/main/java/com/example/myapplication/viewmodel/ContextTransferViewModel.kt:212 的 runCatching 只包 applyImportedAssistantAvatar + mergeImportedData。:214 的 onSuccess 先清两项 pending，:217 再调用 refreshCounts；该刷新异常不在前述 runCatching 内，不能保证恢复 busy 或保留 pending。原研究§5已指出 init/refreshCounts 的旧恢复缺口，主概括应与之对齐。
- 建议文字：“确认阶段头像应用/合并的受捕获失败保留preview/pending；不承诺导入已成功后计数刷新失败的恢复，也不承诺导出生成失败或跨资源原子回滚。”
- 不改 VM。新增失败测试在头像应用/合并的明确失败点注入；不能把测试范围扩为原实现没有的任意确认失败恢复。
- 复核：prd.md:22、:34，design.md:42及context-transfer-validation.md:43、:49已按此限定修正。context-transfer.md:88的“确认”状态表仍采用概括文案，建议也加“被捕获的头像应用/合并失败”，避免脱离同节refreshCounts caveat时被误读；不影响当前主设计的准确性。

**N2：列表研究的真实导航测试承诺与主设计选择不一致。**

- 位置：entry-list-detail.md 原§9“最小行为验收”第5项（首读:165）要求“真实导航 + Fake 数据变化证明等 entries 变空才 pop”。
- 主设计 design.md:77 已明确本轮不改导航/DI，只采用真实 Screen 回调 + 独立 VM Fake + 静态生产接线，且不把手抄 NavHost 称为生产集成测试。该方案适合本轮纯外观范围。
- 真实代码：SettingsDataNavRoutes.kt:86 的空条目 effect 决定 pop；其函数 :23 依赖 AppGraph；AppGraph.kt:435 懒建生产 RoomWorldBookRepository，没有既有世界书 Fake 注入口。
- 建议把研究该项收敛为：“Screen确认仅回调目标bookId；独立VM/Fake验证删除结果；静态核对生产空条目effect。未执行生产导航集成，设备/接缝集成证据另列。”如未来要执行真实生产路由测试，必须另列最窄接缝，不为本轮悄悄新增领域DI。
- 复核：entry-list-detail-validation.md:44已明确Screen/Fake与静态接线的区别，本项已解决。

**N3：extras 上限单位应为字符，不是KiB。**

- 位置：editor-contracts-tests.md 原§4“身份与格式不能被外观迁移顺带改写”（首读:123），写为“按16KiB上限”。
- 真实代码：ContextImportNormalization.kt:12、:153–154 使用 16 * 1024 与序列化 JSON 字符串的 length 比较，不是编码后的字节长度。
- 建议改为“16384个字符串字符（当前length计量）”；不能据此引入新的 UTF-8 字节上限。ContextTransferScreen.kt:506、:542、:559 对导入文件的16MiB限制则确实按字节计算，二者应区分。
- 主规划只要求保留既有规范化，没有提出改阈值，因此属于研究文字修正。
- 复核：editor-contracts-tests.md:123已明确String.length≤16384，并与文件16MiB上限区分，本项已解决。

### 4. 已核对一致的范围与行为

| 审查点 | 结论与证据 |
| --- | --- |
| 完整产品范围 | prd.md:17、:22 / design.md:7覆盖列表、详情、编辑、关键词/正文和共享资料库全页；备份/角色/世界书/记忆/预设五组均纳入，其他模块独立页面不纳入 |
| 编辑保存与取消 | design.md:36–40准确保留onSave→back、删除原ID→back、返回不保存、无VM草稿/编辑isSaving/message接口、全屏只回写草稿；与WorldBookEditScreen.kt:143、:577、:585相符 |
| 消息过滤 | 列表“删除”与详情“重命名”过滤及其他消息延时消费准确；未误承诺保存失败当前页可见（WorldBookListScreen.kt:87；WorldBookBookDetailScreen.kt:66） |
| 关键词未提交文本 | design.md:24、:38保留独立draft、IME/分隔符提交、整chip唯一移除回调；KeywordChipInput.kt:132、:143、:163不会在主保存时自动提交draft |
| 身份与未知字段 | 原entry.copy保留id/bookId/createdAt/extras及既有Repository规范化，书内新建仍只传bookName；未新增移动分组/整书事务或单条预算/深度字段 |
| section与格式 | ContextTransferViewModel.kt:36定义的5种section/文件名与Screen:206–276五组一致；ContextDataBundle.kt:3仍为version1/六类数组，section过滤:565、Tavern特例、头像/预设/分段策略保持 |
| 系统选择器与取消 | Screen:68、:90、:110确为CreateDocument/OpenDocument/PickVisualMedia，null先return；三组来源Dialog、两组直接文件；没有新增权限、自动WORLD_BOOK预选或真实系统chooser截图 |
| 预览与失败 | preview为页内卡，正计数/notice/总冲突+前5条准确；取消清pending；受捕获确认失败保持可重试数据；不承诺跨DB/头像/Settings整体回滚（Screen:396；VM:156、:208、:236、:265） |
| busy范围 | 按VM预览/导出生成/确认状态禁用当前按钮；不扩到系统chooser、组合协程URI读写全程，不把导航返回等同于job取消 |
| 只读计数语义 | design.md:51正确要求无OnClick/箭头。SettingsListRow.kt实际位于SettingsComponents.kt:360；:397仅在enabled且onClick非null时加clickable。当前这些调用enabled=false只影响文字色，不产生Disabled语义；局部恢复阅读色不必添加点击动作 |
| 共享接口与所有权 | design.md:11–28将同一实现所有者、默认兼容、包装转发、两按钮分支、已有Group两重载写明；只是有需要才加局部样式参数，不要求机械添加全部参数，无新的领域API |

### 5. 截图数量的只读核对

对 research/screenshot-matrix.json 的解析与计数结果：

| 项目 | 结果 |
| --- | ---: |
| 唯一ID / cases | 63 / 63 |
| worldbook + editor + transfer | 27 + 20 + 16 |
| WorldBookScreenshotE06Test + ContextTransferScreenshotE06Test | 47 + 16 |
| stage + dialog（含正文Sheet）+ popup | 45 + 14 + 4 |
| 独立窗口 / 其中Popup | 18 / 4 |
| 常规360×800dp/100%/density2 | 25 |
| 窄屏320×640dp/200%/density2 | 38 |
| 帮助入口行为覆盖（不等于窗口PNG数） | 列表1 + 编辑11 + 资料库6 = 18 |

全部独立窗口均采用窄屏/200%参数；正文Sheet正确归为平台Dialog捕获目标，两个下拉各浅深一张构成4个Popup场景。

数量补充：ScreenshotCases.kt:262 的现有登记分族合计156；ScreenshotBaselineGuardTest.kt:147另允许2张历史MessageBubble基准。只读磁盘计数为158张。因此新增后是 **219个登记场景 + 2张历史图 = 221 PNG**；219个登记测试 + MessageBubbleScreenshotTest.kt:92、:117的2个历史测试 + 1个计数守卫 = **222项截图检查**。主规划总数正确，不应把登记表本身写成221。

执行提醒：ScreenshotBaselineGuardTest.kt:65按每个捕获类的声明@Test数核对登记。Popup/缺root/时钟/Resolver等非捕获探针应放独立行为测试类，避免把额外@Test塞入两个47/16捕获类；若它们放进整个ui.screenshot selector，还应单独说明额外检查数量，不能凑222。

### 6. 探针、before/after与执行可行性

- **独立root**：design.md:55、:75、:78和捕获研究正确区分Dialog/Sheet、Popup与系统Activity。Popup不能复用只找isDialog的旧helper；缺root必须失败，系统chooser不能拍底页充数。
- **ActivityResult/Resolver**：test-only RegistryOwner拦截真实launcher并分发合成Uri/null；test ContentProvider/内存stream经过生产私有helpers，足以形成可验证方案。未找到现成设施不等于已失败；没有运行探针也不等于已通过。UI/VM层无需为此公开IO函数或添加领域接口。
- **时间**：列表相对时间、会话Popup的DateUtils、固定locale/时区都列入控制范围。固定时间应明确使用测试宿主/shadow能力，不改系统真实时间或产品算法；在实际文本/窗口读数及重复捕获SHA中证明有效。
- **滚尾**：以bookId/entryId/section定位末项，未裁切布局和真实touch bounds都进入可用视口；分别检查字段内部与LazyColumn。implement.md:16的“value/max”应视为原则，LazyColumn使用其真实索引/可继续滚动边界等证据，不能盲搬普通ScrollState算法。
- **before门禁与after验收分开**：产品未改阶段应证明捕获确为原页面、原窗口、指定数据/滚动状态；此时发现原有对比度/触控/文本裁切缺陷，应作为before事实记录。不要把“所有after a11y断言先通过”暗设为before前提。真实root、隔离和来源门禁始终不能放宽。
- **稳定after与共享回归**：63张同参数重复并逐图比SHA、人工接纳前不record、旧158图异常差异先修共享兼容、捕获修正用原始提交隔离补before，均明确且可执行。
- **边界诚实性**：未改导航选择静态接线校核合理；系统选择器、IME、TalkBack/系统栏、生产IO、物理性能与远端新视觉CI继续单列设备/外部证据。编译、静态阅读和前序结果均不能冒充这些结果。

### 7. 是否需要新的用户产品决定

当前没有。资料库是否整页纳入已由用户明确回答；原保存/取消/失败边界也已有保留方案。N1–N3均可由主会话按真实代码修正文案，不应再让用户为技术文字选择。

仍须先展示完整最终摘要并取得实施批准。若未来探针表明只能修改业务事务、导航DI、URI/权限或错误恢复才能达到额外目标，再按具体失败收敛新范围；现在无需推测性扩大授权。

### 8. Files found / Related specs / External references

- 规划材料：prd.md、design.md、implement.md、research/screenshot-matrix.json、entry-list-detail.md、editor-contracts-tests.md、context-transfer.md、capture-and-shared-contracts.md；复核新增entry-list-detail-validation.md、editor-validation.md、context-transfer-validation.md的对应段落与文件大小。
- 复核源码：ui/screen/settings/ContextTransferScreen.kt、SettingsComponents.kt；viewmodel/ContextTransferViewModel.kt；model/ContextDataBundle.kt、ContextImportNormalization.kt；编辑/导航/仓储锚点沿前次完整静态研究。
- 计数依据：app/src/test/java/com/example/myapplication/ui/screenshot/ScreenshotCases.kt、ScreenshotBaselineGuardTest.kt、MessageBubbleScreenshotTest.kt；app/src/test/snapshots下PNG只读计数。
- 相关spec：.trellis/workflow.md的planning/最终审阅门禁；backend/ui-token-contract.md的共享默认、真实承载面、48dp/200%、独立窗口、稳定before/after；ui-state-guidelines.md、error-handling.md、database-guidelines.md的真实状态与异常边界；quality-guidelines.md的Fake/计数/缺图/人工接纳。
- 无外部网络引用；沿用本地Compose BOM2026.02.01、Navigation2.9.7、Roborazzi1.76.0、Robolectric4.17。未解析或升级依赖。

## Caveats / Not Found

- 审阅只执行文件读取、JSON/数量/尺寸元数据计算，没有运行产品、Gradle、设备、真实chooser或网络，也没有git操作。
- 研究分片的静态字节数已确认合规；JSONL引用完整性由主会话处理。本文不能声称task validate或新探针已通过。
- 主会话拆分文件或按发现修正后，旧行号可能变化；按上述小节标题和对应生产源码锚点定位。没有在本研究中改动其他文件。

## 主会话续接复核（2026-11-06）

资料库主研究的确认状态表已限定到头像应用/合并的受捕获失败，N1剩余文案同步完成；N2/N3保持已解决。续接发现单文件合规后仍有总注入预算降级，已在任务内改为核心清单与必读索引，实际验证两角色均完整加载三份规划正文。详见[续接静态校验](planning-resume-validation-20261106.md)；这项补充由主会话完成，不冒充新的独立审阅或产品验证。
