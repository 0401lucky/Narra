# P06 世界书与共享数据页：技术设计

## 1. 范围与检查点

用户已批准创建本任务并明确选择“同时迁移共享数据页”。保持一个P06任务，按共享接入 → 列表/书目详情 → 编辑与关键词/弹层 → ContextTransfer整页串行推进；最终统一独立检查、视觉接纳和提交，不重启已归档四批父任务。

生产目标为世界书目录下`WorldBookListScreen.kt`、`WorldBookBookDetailScreen.kt`、`WorldBookEditScreen.kt`、`WorldBookEditSections.kt`、`WorldBookContentFullscreenSheet.kt`，以及`ui/component/worldbook/KeywordChipInput.kt`、`settings/ContextTransferScreen.kt`。`SettingsComponents.kt`仅允许本批必要的默认兼容样式接口扩展。所有权覆盖的是实际UI声明，不把纯分组/匹配/IO函数顺带改写。

实施实测补充：资料库忙碌按钮受`NarraButton.kt`中统一0.45禁用透明度影响，实际文字对比为1.848:1；世界书编辑顶部保存和详情操作也复现同类问题。为满足原W2验收，在`bounceClick`、`NarraButton`/`NarraOutlinedButton`/`NarraFilledTonalButton`及`AnimatedSettingButton`增加默认仍为0.45的显式`disabledAlpha`，`SettingsTopBar`透传`actionColors`和`actionDisabledAlpha`。本批禁用按钮传1并使用可读禁用配色，其他调用保持旧默认。该新增共享源的原始Git提交证明见[补充来源](research/source-extensions.json)，原before清单不重写，after/repeat同时核对14项源文件。

真实入口与完整锚点见[列表/详情研究](research/entry-list-detail.md)、[编辑/保存研究](research/editor-contracts-tests.md)、[共享资料库研究](research/context-transfer.md)。日常入口来自“我 → 资料库”；SettingsScreen只存在未消费的回调签名，本批不恢复设置首页新入口。

## 2. 所有权与共享接口

同一`trellis-implement`所有者统一处理目标UI、SettingsComponents、截图登记与精确源码守卫，避免各页面分别造包装。独立`trellis-check`接手后再检查/自修；每个阶段只有一个Gradle执行者。

| 所有者 | 设计 |
| --- | --- |
| 现有NarraUi/NarraShapes/Material scheme | 直接消费六档间距和既有形状/语义色；不改全局主题或历史别名默认 |
| SettingsTopBar、Group两个重载、Divider、ListRow | 复用已有padding、shape、行数/间距、heading参数；两个Group重载保持透传 |
| SettingsPageIntro、SettingsSectionHeader | 按需要增加显式padding/间距、标题行数/heading和内容色参数；默认保留当前4/8、4/2/6dp及两行/原颜色行为 |
| SettingsHelpButton → SettingsDescriptionDialog | 本批需要的窗口shape、文字动作色和有界内容/触控参数必须透传到实际Dialog；默认仍与旧8dp/primary/正文规则一致，title/description键及取消语义不变 |
| SettingsStatusPill、AnimatedSettingButton | 必要shape/padding/颜色选项保留999dp、12/6dp，或按钮8dp、20/14dp、min52dp旧默认；主/次按钮分支均透传，本批显式选Narra值 |
| bounceClick、NarraButton、NarraOutlinedButton、NarraFilledTonalButton | 新增显式disabledAlpha，默认0.45保留；P06禁用态透传1与实际禁用文字/表面配色，TopBar也显式转发，点击禁用和其他按钮动画保持 |
| SettingsHintCard | 资料库notice/冲突需迁移时仅补必要shape/padding显式参数；正文实际在palette.surface，传入颜色只装饰色条/圆点，不能误配阅读文字 |
| NarraAlertDialog | 直接使用已交付的shape/actionContentColor；本批不扩其默认，破坏性确认仍优先error |
| KeywordChipInput | 只迁移输入/chip的UI声明；保留分词、正则识别、IME/分隔符提交、未提交draft及整chip单一移除回调 |

上述是允许的最小接口范围，不要求机械增加所有参数；必要参数在修改前按研究列出的全部消费者清点并固定旧值。若小型页面组合更清楚，可组合现有Narra primitives，但由同一所有者集中复用，不创建第二套主题/表单/帮助系统或全局迁移开关。

`WorldBookListStyle.kt`的筛选/时间/关键词、`buildWorldBookBooks`、VM、导航注册、Repository、adapter/codec、schema、AppGraph、权限与URI读写默认只读。未被生产调用的bookSpineColor不迁移。已有共享消费者中P02/P05/P10/P11及其他未迁移页面保持原默认。

## 3. 行为与数据契约

| 路径 | 必须保留 |
| --- | --- |
| 列表筛选与分组 | scope/status/book三维AND；清筛选保留搜索，搜索空态只清搜索；先筛条目再分书卡，打开详情仍显示全书；既有ID、排序和keys不变。状态标签不变成开关，不新增复制/批选/排序菜单 |
| 书目管理 | 管理为页内展开，rename须非空/不同名且非isSaving，按钮/IME同参数；删除先确认，取消只关，等待真实entries变空才返回。真实空书路由会pop，不登记为可长期停留的正常截图 |
| 编辑保存/删除 | 主保存onSave→立即back，删除确认delete原ID→back；返回不保存。无VM编辑草稿、无编辑isSaving/message接口，不加失败留页、保存成功回调、未保存确认或新错误恢复 |
| 现有消息过滤 | 列表仅显示“删除”、详情仅显示“重命名”；其他消息含entry保存成功/失败按原延时消费。不能把VM产生message等同于页面可见 |
| 编辑字段 | 旧entry.copy保留id/bookId/createdAt/extras；仅原字段转换和updatedAt更新。书内新建仍只传bookName、由既有规则派生bookId；标题/正文非空门槛、数字回退/概率夹紧、作用域ID规则、关键词未提交draft保持 |
| 全屏正文 | 独立二级草稿；确认仅回写主content并关Sheet，取消/dismiss不回写、不持久化、不离开主编辑页 |
| 试命中 | 仍为现有纯previewHit，不代表完整注入判定；只保原结果更新/清除条件，不新增自动刷新或匹配策略 |
| 数据页入口/section | 世界书入口不预选WORLD_BOOK，不自动开chooser；备份/角色/世界书先来源Dialog，记忆/预设直接文件；5种导出文件名与section不变 |
| 数据预览/确认 | 预览是页内卡片：只显示正计数、安全notice、总冲突数和前5项；预览不写入，取消清pending，确认按原规则合并，头像应用/merge被捕获的失败保留preview/pending。成功合并后refreshCounts不在该恢复保证内 |
| 选择器/URI | null结果无IO/回调；保留CreateDocument/OpenDocument/PickVisualMedia、MIME、BOM/16MiB边界、脱敏错误及关流。返回不等于撤销导入，isBusy不扩成系统选择器和全部IO的全流程事务 |
| 格式/媒体/权限 | bundle版本/六类数组、Tavern JSON/PNG/预设、section过滤、ID/未知字段规范化、头像仅确认保存、事务边界、内置身份和缺会话分段策略原样；不申请新权限、不改数据/凭据 |

作用域、预算、深度等以真实控件为准；本条目编辑没有单条token预算、独立插入位置枚举或扩展JSON编辑器。角色级扫描配置属于其他页面。

## 4. UI与无障碍设计

- 页面、字段、卡片、筛选、折叠段、提示与窗口实际消费令牌；文本与真实承载面至少4.5:1，含固定动态深色、焦点标签、选中/只读/忙碌状态。
- ContextTransfer的只读计数/预览行可局部使用正常阅读色，仍无OnClick/箭头；不把只读内容伪装成禁用按钮，也不改共享disabled默认。
- 320×640dp/200%下顶栏动作独立可达，长书名/长关键词可读；固定区不能挤空正文。列表摘要允许原摘要截断，完整正文在编辑/全屏Sheet阅读，不把长文塞进列表卡。
- 筛选chip、折叠、开关/帮助、输入/错误和busy使用真正节点的名称/角色/状态；整chip移除、帮助与折叠各只触发一个原回调。真正touch bounds至少48dp，不由控件类型推定。
- 末项按bookId/entryId/section和真实滚动边界定位，unclipped与touch两种边界都完整可达；分别验证正文内部与外层页面滚动。数据页首/末资源组的两个按钮、预览确认/取消均完整可见。
- 全部18个帮助入口（列表1、编辑11、资料库6）做行为覆盖；两种编辑Popup、正文Sheet、两类删除确认、资料库来源Dialog均捕获真实窗口。系统文件/照片选择器只测试launcher合同与结果，真机效果另列。

## 5. E06截图矩阵：63张

稳定ID与参数完整登记于[规划矩阵](research/screenshot-matrix.json)，每图对应独立@Test。初始方案由研究建议合并为27+20+16，采用代表态而非全状态笛卡尔积；所有页面的8种实际scheme配色另由参数化行为/像素检查覆盖。

| 部分 | 数量 | 场景 |
| --- | ---: | --- |
| 列表/书目详情 | 27 | 列表三主题浅深6；详情常规2；列表/详情动态4；筛选首项/列表末项/管理/详情末项200%浅深8；三类列表空态3；列表帮助/整书删除窗口浅深4 |
| 编辑/关键词/弹层 | 20 | 新建必填、已有编辑、命中规则、作用域、高级末端各浅深10；正文Sheet、条目删除、代表帮助、书名Popup、会话Popup各浅深10 |
| 共享数据页 | 16 | 常规三主题浅深6；动态预览2；200%首资源组/末资源组/notice首段/预览确认末端4；来源Dialog和代表帮助各浅深4 |

共18张独立窗口（含4张Popup）；常规360×800dp/100%，窄屏与窗口320×640dp/200%，density2。世界书类47项、数据页类16项，同属E06族。现有登记156项另有2张历史图，新增后为219登记+2历史=221张PNG，加1计数守卫共222项检查；以真实登记和报告为准，不凑数量。probe/负向行为测试放在独立行为类，不改变两截图类47/16的@Test数量。

完整before必须在任何产品修改之前取得；after相同状态/动作/时间/locale/窗口参数。用户审阅前对全部63张after重复捕获并逐张比SHA，避免重演P11瞬态水波。缺图/计数失败与行为失败分开；不record、弱化阈值或自动接纳。捕获前置错误只能用原始提交隔离副本补before并保留来源。

## 6. Fake与验证接缝

- 图片/普通UI行为直接渲染真实Screen，固定entry/bookId、角色/会话、时间、计数、预览/冲突与本地回调；普通Application/ComponentActivity，不创建用户AppGraph或生产Room。
- 世界书VM使用FakeWorldBookRepository并主动订阅WhileSubscribed的uiState；条目延迟/失败使用测试局部委托包装和gate，不改变共享Fake默认。Repository/adapter/Keyword/Matcher现有测试验证各自规则，不冒充UI或真实SQLite。
- 数据页用test-only ActivityResultRegistryOwner拦截真实launcher、dispatch合成Uri/null；以测试ContentProvider/内存stream走现有私有URI helper。先做捕获/IO探针，不能打开用户文件作为替代。
- ContextTransferViewModel已有仓储、avatarSaver、transaction及existingConversationIdsProvider注入口，沿既有Fake构造；任何TestAiServices显式注入不会联网的API provider，不依赖默认factory。
- SettingsDataNavRoutes仍耦合AppGraph：本轮默认不改导航或DI；真实Screen回调+独立VM Fake与静态生产接线组合验证，不把手抄NavHost称真实生产路由测试。若将来确实修改导航，再单独设计最窄test seam。
- 对Dropdown Popup、时间固定和ActivityResult/Resolver尚未运行的接缝先做小探针；失败先解决test-only设施，不绕过窗口/网络隔离门禁，也不趁机扩领域接口。

## 7. 验证、回退与剩余边界

受影响单测包含世界书分组/筛选、Keyword、VM/Repository/adapter/codec/previewHit、ContextTransfer VM及本批真实UI/窗口/配色/共享兼容。生产Kotlin改动按规范进行IDE格式化、Lint、dev构建、AndroidTest编译；接纳后完整截图与无selector JVM分别归档日志/XML/退出码/数量及实际执行情况。

源码守卫扩到实际目标UI声明和新增共享接口；SettingsComponents从legacy迁到scanned时精确登记旧默认，不整文件/目录豁免，SettingsGroup=2继续生效。保留既有158张及所有已完成批次默认；共享变化若引起旧图差异先修兼容，不直接重录。

回退只撤本批UI/样式接入/测试候选，不回滚用户世界书、导入结果、schema或媒体。真机读屏、IME/系统栏、原生选择器、实际材质、物理性能及新视觉远端CI待真实证据，未执行不能记为通过。最终规划摘要已于2026-11-06获用户“ok”批准，task.py start已执行，实施仍遵守完整before、隔离和人工接纳门禁。
