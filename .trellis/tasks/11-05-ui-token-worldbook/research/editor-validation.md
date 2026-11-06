# Research: 编辑验证、Fake与截图矩阵

- Query：延续[editor-contracts-tests.md](editor-contracts-tests.md)的验证、Fake、截图与消费者研究。
- Scope：internal，仅静态规划；完整路径缩写与主契约见该研究入口。
- Date：2026-11-05

### 8. 现有测试能证明什么、不能证明什么

| 测试 | 已有源码覆盖 | 编辑迁移仍缺 |
| --- | --- | --- |
| WorldBookViewModelTest:20、:62、:103、:122 | 整书重命名/删除成功与失败 | 单条删除成功/失败、单条保存失败、挂起中的状态/顺序、consumeMessage；不能借用整书失败测试声称条目失败已覆盖 |
| WorldBookViewModelTest:141、:161 | entries 随 Flow 更新；单条保存成功消息及条数 | 完整payload/原ID/createdAt/extras保留、保存异步返回/失败后草稿 |
| WorldBookRepositoryTest:29、:48、:68、:86、:111、:139、:158、:177、:199、:219、:243、:262、:290 | 换行、源名称/ID trim、extras、长度与安全过滤、时间、概率、作用域查询映射 | UI回调/导航/触控；Fake DAO也不是真实SQLite执行 |
| TavernWorldBookAdapterTest:21、:52、:69、:99、:137、:148、:161 | 未知字段、概率、长度、稳定ID、无uid回退 | 新编辑的UI映射；外观不能改变这些格式约束 |
| ContextTransferCodecTest:18 | 合成bundle编码/解码，bookId、次级匹配、插入顺序、书名 | 只作格式回归，不作设备文件导入证据 |
| KeywordChipInputTest:10–51 | 逗号/换行、去重、正则逗号/转义、外观识别 | 实际IME Done、未提交draft、disabled、长chip删除命中、标签与48dp |
| WorldBookMatcherTest:606–735 | previewHit 空输入、命中/未命中、selective、alwaysActive | 编辑页试命中按钮和结果语义；不要求本轮重写Matcher |
| SettingsComponentsTest:30 | 设备宿主合成长说明、固定关闭、不触发行操作 | 11个真实编辑入口和本批局部opt-in |
| NarraAlertDialogTest:59、:124、:158、:188 | 大字体长内容、禁用确认、取消、系统返回、外部点击 | 世界书原条目ID、确认后回调顺序、编辑Sheet取消 |
| ProviderSharedDialogCompatibilityTest:21、:53 | 共享默认/显式shape与动作色、破坏性色优先级、取消 | 可复用方法，不是世界书结果 |

静态全文检索未找到调用 WorldBookEditScreen / WorldBookContentFullscreenSheet 的现有 JVM 或 androidTest UI 测试，也没有 WorldBookEditExpandedState 测试。ScreenshotCases / UiTokenSourceInventory 尚未登记世界书编辑页；既有Settings/Roleplay测试的 onOpenWorldBookSettings 空回调或计数回调不等于编辑页覆盖。

### 9. Fake 隔离与最小行为矩阵（仅后续实施建议）

**隔离方式**

- 页面/截图优先直接渲染真实 WorldBookEditScreen，传固定 entry、角色、会话、existingBookNames 和本地捕获回调；不装配 ChatApplication/AppGraph，不访问生产数据库、API、图片或用户文件。
- 使用固定 ID/bookId、createdAt/updatedAt、合成正文/中文长标题/长关键词、无头像URI的角色。用已有条目的固定时间避免随机 ID/时间污染截图；新建截图不点击主保存，身份生成用行为断言检查。
- 会话菜单使用 DateUtils 相对时间和 System.currentTimeMillis（WorldBookEditScreen.kt:694、:739）。只固定fixture时间还不够；应固定宿主当前时间/日期并恢复，记录统一时区/locale，不改产品时钟来稳定图片。
- VM测试实例化真实 WorldBookViewModel(FakeWorldBookRepository(...))，按现有 MainDispatcherRule/runTest/advanceUntilIdle 模式**主动订阅** uiState；WhileSubscribed(5000) 没有 collector 时不能假设投影状态更新。结束时取消collector/清理owner。共享 MainDispatcherRule 实际定义在 T/viewmodel/ChatViewModelTest.kt:2328，当前没有单独 MainDispatcherRule.kt，不应在实施上下文中引用不存在的文件。
- 现有 Fake 的 upsert/deleteEntry 立即成功且不走Room规范化；failNextBookMutation 只作用于rename/deleteBook（FakeWorldBookRepository.kt:40、:49、:65）。使用测试局部 WorldBookRepository by delegate 包装，对 upsert/deleteEntry 加调用记录 + CompletableDeferred gate + 可控throw，避免真实等待、真实数据与网络。
- 隔离Fake证明回调/时序，真实 RoomWorldBookRepository + RecordingWorldBookDao 证明规范化/映射，二者不可互替；RecordingWorldBookDao 目前是测试文件 private 类，不可直接跨文件引用。
- 真实导航集成有额外接缝问题：registerSettingsDataRoutes 必须接 AppGraph，AppGraph:179、:435 直接懒建Room仓储，没有现成世界书依赖注入口。纯本地仿写NavHost的回调只能证明Screen契约，不能声称验证生产装配。若需要真实路由执行，计划应明确是否抽取最窄的参数化生产绑定，或留设备测试；不要为测试构造真实AppGraph。

| 最小行为 | 输入/动作 | 断言 |
| --- | --- | --- |
| E-B1 新建门槛 | 标题/正文空白；GLOBAL/ATTACHABLE；空ASSISTANT/CONVERSATION | 保存禁用/无回调；满足现有非空条件才启用；不添加关键词必填 |
| E-B2 完整payload | 固定已有id/bookId/createdAt/extras；改标题/正文/名称/所有可编辑字段 | callback保留身份/未编辑字段/正文换行；名称trim；仅updatedAt变化 |
| E-B3 数字边界 | 负数、空、溢出、概率-1/101 | 优先级/顺序非法0、概率非法100并夹紧；不改变canSave |
| E-B4 关键词 | 实际输入逗号/全角逗号/换行/Done；含正则逗号；未提交draft | 顺序/去重/删一token；不自动提交draft；selective关闭保留但禁用次级输入 |
| E-B5 作用域 | 四模式、长角色名、空角色/会话、选择会话、同名会话不同ID | GLOBAL/ATTACHABLE清ID；其他当前规则保持；按ID回写，空候选无Popup；挂载只触发导航回调 |
| E-B6 折叠与说明 | 默认/五段切换/高级二级展开；逐一打开11个帮助 | 默认前两段展开；草稿不因收合丢失；帮助不折叠/切换开关；关闭无业务回调 |
| E-B7 试命中 | 真实按钮+合成文本，命中/未命中/常驻/次级组合 | 展示生产previewHit结果；不调用save/delete；只改待测文本时清空结果，不悄悄新增自动刷新 |
| E-B8 主保存时序 | Gate挂起upsert，再成功/失败 | 页面onSave先于back；VM isSaving true→false；成功写一次/失败不改存储，固定message；不虚构成功回调 |
| E-B9 返回/失败回流 | 顶部返回/系统返回；保存返回列表/书详情 | 返回不保存；真实路由允许提前pop；保存message被当前关键词过滤/3秒消费作为既有基线，不断言出现错误面板 |
| E-B10 删除身份/取消 | 先改草稿title，再打开删除；取消/外点/系统返回/显式确认 | 文案与ID为原entry；dismiss不调用delete/back；确认delete(ID)→back；Gate失败保留原存储 |
| E-B11 全屏草稿 | 从主正文打开，修改含末尾换行/清空；确认/关闭/平台dismiss后重开 | 确认只回写主草稿，空内容允许回写使主保存禁用；取消保持旧主正文；无持久化或主导航 |
| E-B12 长表单/真实触控 | 320×640dp/200%，展开全部/滚尾；开关/帮助/末尾删除/保存 | 标签/选中状态可读，所有操作真实touch bounds≥48dp且完整可见；相邻帮助/开关/chip不误触、不双回调 |
| E-B13 真实窗口 | 两Popup/Sheet/删除/11说明打开与未打开 | 未打开root查询必须失败；打开的正确root/操作/字体尺寸受测；不回退底页 |
| E-B14 现有草稿恢复边界 | 配置重建/收合；延迟entry到达或无效ID只作调查基线 | 能力按代码区分，不把rememberSaveable说成已实现异步加载/失败保稿；任何修正先单独收敛范围 |

建议只为上述具体风险补测试，不为每个令牌值逐行镜像实现。相关既有Repository/Keyword/Matcher/格式回归仍执行；未改业务层不必扩到全部匹配/设备数据库迁移专项。

### 10. 编辑与弹层截图建议

以下为待父计划收敛的**编辑子矩阵**，不是已登记或已接纳清单；列表/详情矩阵由另一研究负责。窗口种类必须全覆盖；11帮助入口的行为全覆盖，可选最长真实文案作代表视觉，不必把同一容器11次重复截图。

| 候选组 | 参数与真实状态 | 截图前断言 |
| --- | --- | --- |
| editor-new-required | 320×640dp / 200%，浅/深；空标题正文，默认折叠 | 顶部标题/返回/禁用保存完整；已有必填提示实际可见 |
| editor-existing-top | 360×800dp / 100%，浅/深；固定长标题正文、源书名 | 编辑值为fixture，旧ID/extras不变；正文扩展入口可见 |
| editor-rules-narrow | 320×640dp / 200%，浅/深；长chips、正则、三匹配模式与次级状态 | 模式标签/选择可读，关键词控件滚入视口且真实删除命中区域完整 |
| editor-scope-narrow | 320×640dp / 200%，浅/深；长角色名及空会话状态（由父矩阵确定一组主候选） | 作用域chips可读；空态不伪造Popup，选择/禁用保持既有规则 |
| editor-advanced-tail | 320×640dp / 200%，浅/深；高级打开，实际预览结果，已有条目滚到底 | 滚动位置到真实末尾；删除整个操作区和警示可见；顶部保存仍可达 |
| editor-body-sheet | 320×640dp / 200%，浅/深；长正文滚到尾 | 独立Sheet root，真实末尾文字、取消、保存并返回全部可达；字体来自窗口 |
| editor-delete-dialog | 320×640dp / 200%，浅/深；原条目长标题 | 独立Dialog root、确认/取消完整，长消息按需要滚尾，确认用error |
| editor-rule-help | 320×640dp / 200%，浅/深；实际“命中规则”或“匹配模式”最长说明 | 打开真实说明Dialog，必要时滚尾，“知道了”仍可点；文案不足滚动时如实记录 |
| editor-book-popup | 320×640dp / 200%，浅/深；多条长书名、末项 | 独立Popup root，真实书名末项完整可选，关闭不写入主保存 |
| editor-conversation-popup | 320×640dp / 200%，浅/深；长/空标题、多会话、固定相对时间 | 独立Popup root，按fixture ID对应末项，时间/locale固定；不误选底页标签 |

若以上每组浅/深各1图，是20张编辑候选上限建议；scope在多个子状态拆分或help增加代表时重新精确登记，不先写死P06总图数。主要对比度另用应用三主题的浅/深与固定动态浅/深共8种实际scheme行为/颜色检查；无需为全部状态做主题笛卡尔积截图。

**捕获方法**

- 复用 ScreenshotStageTest:53 的 Application::class / SDK35 / NATIVE 与真实 Compose页面；默认宿主360×800dp、xhdpi/density2；窄宿主必须同时设置 NarrowQualifiers 和 RuntimeEnvironment.fontScale=2（ScreenshotFixture.kt:34–41；ProviderSettingsRenderTest.kt:42）。
- 页面捕获 StageTag；Dialog/Sheet root 必须位于 StageTag 之外。可复用 ProviderSettingsRenderTest.kt:178 的 isRoot + 标题子节点 + isDialog + 排除StageTag 模式；给Popup单独使用实际 isPopup 语义匹配，不能照搬只找isDialog的helper。
- 每次捕获先断言标题/真实操作/root存在，再核对窗口TextLayout的density/fontScale与边界。对同名文字必须限定所在root；截图前未打开窗口必须失败。
- 长LazyColumn用真实列表索引/最后item和canScrollForward等Lazy语义证明到尾，不能把普通ScrollState的max算法盲搬到LazyColumn。完整操作同时检查 getUnclippedBoundsInRoot 和 touchBoundsInRoot 是否落在可用视口，避免仅assertIsDisplayed通过。
- 正文控件内部滚动与外层页面滚动分开；展开/收合的动画先稳定，再捕获。需要关闭焦点或避免水波时前后用同一策略；不得删除产品动画、隐藏控件或修改生产文本。
- 全屏正文截图可证明无IME或明确模拟insets下的布局，不等于真实键盘行为。真实IME顶起、文字光标/选择、系统返回先收键盘、Sheet手势关闭与系统栏触控需设备补证。
- 规则命中说明若真实文字在该宿主无需滚动，不声称滚尾已覆盖；可额外用共享帮助宿主的合成长文行为测试覆盖滚动，不篡改生产说明制作before。
- 实施前新增捕获用例，用 verify 缺图actual保存真实before和固定参数/哈希；再迁移生成after候选。人工接纳前新增项/PNG计数守卫应继续缺图失败；不record、不verifyAndRecord、不自动接纳。
- 只把文案/root/触控/颜色/回调的宿主证据作为宿主结论；设备编译、源码阅读、前序图数不能替代P06设备、CI或性能验证。

### 11. Related specs 与后续验证边界

- .trellis/workflow.md:352、:372：研究落盘；:436、:450：规划审阅、用户批准后才进入实施。当前不得 task.py start。
- .trellis/spec/backend/index.md:13：UI→Navigation/VM→Repository→Store；依赖只由AppGraph装配。
- .trellis/spec/backend/directory-structure.md:46：各层职责与共享逻辑边界。
- .trellis/spec/backend/ui-token-contract.md:24–40：实际消费默认令牌、共享默认兼容、真实主题/承载面；:51–63：窄屏、独立root、末项完整touch bounds、焦点/动态色；:81–87：源码/截图门禁。
- .trellis/spec/backend/ui-state-guidelines.md:44、:66、:93、:120：rememberSaveable、消息消费、共享帮助、Dialog取消语义。原编辑页已有不完整加载/失败接线，应明确差异而非把规范当已实现。
- .trellis/spec/backend/error-handling.md：“状态恢复”“协程取消与资源清理”；本报告标识旧runCatching和失败反馈缺口，不在视觉计划中隐式扩修。
- .trellis/spec/backend/database-guidelines.md:58、:64：领域模型/Entity边界及Repository规范化不可绕过。
- .trellis/spec/backend/quality-guidelines.md：手写Fake、MainDispatcherRule、真实UI回调、最小充分测试、before与人工接纳；不因规划研究运行产品测试。
- .trellis/spec/guides/cross-layer-thinking-guide.md：回调/异步/消息落点和真实窗口必须沿完整链路核对。

实施批准后的验证建议：现有 WorldBookViewModelTest、WorldBookRepositoryTest、TavernWorldBookAdapterTest、ContextTransferCodecTest、KeywordChipInputTest、WorldBookMatcherTest 加本批真实编辑行为/颜色/窗口测试；共享接口改动补 SettingsComponents/NarraAlertDialog 兼容回归；截图仅用限定 ui.screenshot 的 verify，按最终计划执行 lintDebug / assembleDebug / 必要androidTest编译。本文没有运行或宣称这些命令通过。

### 12. External references / versions

- 本次没有联网查询外部资料，也未读取用户世界书数据。外部库事实仅依据本地 gradle/libs.versions.toml:2–32：AGP9.1.0，Compose BOM2026.02.01，Compose compiler2.2.10，Navigation Compose2.9.7，Lifecycle2.9.1，Room2.8.4，coroutines1.10.2，JUnit4.13.2，Roborazzi1.76.0，Robolectric4.17。
- Material3 的具体版本由BOM管理（gradle/libs.versions.toml:49、:56）；未运行依赖解析，不能将另一版本API行为当作本项目的实测结果。ModalBottomSheet/ExposedDropdownMenu/AlertDialog 的最终窗口/IME行为留给真实宿主与设备验证。
- 本研究文档已做静态结构/主要路径行号检查，11个帮助、2个Popup、Sheet与删除Dialog共15个入口ID齐全；这只验证研究产物，不是产品测试结果。

## Caveats / Not Found

- **需要主会话明确记录的产品边界**：主保存立即退出、保存失败无可见提示且无VM草稿，是现有事实。P06若维持“外观与无障碍、保留业务”，建议保留并登记后续缺陷；若用户要求失败留在编辑页/保稿/成功后返回，属于事务与导航行为变更，需要单独确认范围，不能混入本次UI令牌方案。研究代理不向用户自行提问。
- 没有发现独立预算/插入位置/深度/扩展JSON编辑器，也没有保存错误Dialog、未保存确认Dialog、编辑页loading UI或成功回调。不要新增对应截图/验收声称原来已存在。
- 没有发现现有编辑页/全屏正文的真实Compose测试；既有关键词测试是纯函数。现有Fake没有条目失败/延迟注入，且不等于真实Repository规范化。
- entry延迟到达、无效ID、ASSISTANT↔CONVERSATION沿用scopeId、旧预览未随字段变更清空等只作静态风险，不声称复现或本任务必修。
- 宿主无法证明TalkBack实际焦点顺序/播报、真实IME与返回优先级、设备系统栏/手势命中、生产Room IO和物理性能；必须区分后续设备补证与本地截图/编译。
- 列表/书目详情只为导航和消息过滤读取对应片段，其整页外观研究由另一代理负责。本文所有写入仅限本文件；未加载implement/check JSONL，也未改产品/规格/任务状态。
