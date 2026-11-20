# D6 历史、CAS 管理与 TXT 实施交接

2026-11-19，原生 `trellis-implement /root/d6_history`。承接主会话已经核实的 D5 schema 56/源码/验证交接。**本文件记录实施与静态检查，不是 JVM、设备或最终工程通过回执。**

## 实际接口与范围

- 新 `model/CompanionHistoryManagement.kt`：查询（真实 encounter、ISO 日期/年月、会话、未分场、冻结 ZoneId）、分组、旧档选择、冻结修订写操作、TXT 请求/结果。日期不使用角色叙事时区；History VM 初始使用设备 ZoneId。时间戳为0只标日期/时间未知，不补造日期。
- D4 `CompanionReadingScope` 尾增 `readOnly=false` / `authorizationContext=null`；`CompanionReadingRecord` 尾增真实 `turnKind`。现有默认调用仍是当前独立阅读，不转为 `AllowedCompanionHistoryScope`。
- `CompanionReadingRepository` 扩 `archiveChoices/authorizeArchive/readAll/locate`，旧 Fake 默认方法保留。Room 全量扫描复用原实际来源 SQL、keyset 与 domain 资格，**没有增加 DAO 查询或改 schema**。定位保留目标前上下文及目标之后的完整已授权窗口；删除游标沿原排序值继续。
- 显式旧档按当前入口角色/persona、所选 space/run、真实单聊 binding、未删除空间及来源/已提交关联图读取。scope 同时冻结当前授权 origin；旧档不 `bind/ensure/start/enter`，不改变当前运行，编辑/删除返回只读。未知/删除空间、角色/persona/run 变化拒绝读取；当前窗口失效时 VM 清空旧原文。
- 新 `CompanionHistoryManagementRepository` / `RoomCompanionHistoryManagementRepository(database, reading, companion)`：授权、分组页、仓储全量读、批量 edit/delete。日期与场次分开；同场跨午夜保留 encounter。真正空 DIRECT 场仍以已存在的 encounter 元数据显示0记录；legacy 只按原会话/已知日期显示“旧记录，未分场”。
- D1 `CompanionRepository.mutateMessages` 与 Room 实现：全批次预检原会话/所见修订/合法运行，委托既有来源写钩子与 linked CAS；legacy 同样通过持久来源失效机制。Room 事务中的后续冲突或存储异常整体回滚，不循环提交部分成功。删除同时沿原关联清理入口；不清独立人工版本或声音收藏。
- 新 `CompanionTextExporter(repository, writer)` / `AndroidCompanionTextDocumentWriter(contentResolver)`：在授权仓储全量读之后写 UTF-8 BOM，保留原 timestamp、存储说话人/角色、真实 typed turn 类型、完整 raw 舞台正文。linked 原文走 D3 accessor；legacy 首选未 trim 的 canonical content，不使用摘要 Formatter、舞台显示片段或 UI50条窗口。非文字空原文保留媒体类型/文件名标记，TXT 仍是阅读副本。
- 新 `CompanionHistoryViewModel.factory(scenarioId, characterId, repository, textExporter, settingsStore)`：50条初始窗口、全量计数/组、继续分页、查询/旧档、单条/多条编辑草稿及删除。选择和编辑冻结所见 revision，刷新不偷偷更新旧操作的 CAS。SAF `completeExport(documentUri, requestId)` 必须带实际打开时ID；旧结果不能套用新查询/计划，取消零读取/IO/成功提示，写/flush/close/权限失败不报成功。

所有权代码共16条路径：新增7个生产文件、4个测试/Fake文件，修改5个已存在生产文件。具体本代理 SHA 快照见 `history-source-hashes.json`；它是交给检查者前的快照，formatter/checker 后的最终 SHA 由主会话重新绑定。

新增生产：`model/CompanionHistoryManagement.kt`、`roleplay/companion/CompanionHistorySupport.kt`、`data/repository/roleplay/CompanionHistoryManagementRepository.kt`、`RoomCompanionHistoryManagementRepository.kt`、`CompanionTextExport.kt`、`AndroidCompanionTextDocumentWriter.kt`、`viewmodel/CompanionHistoryViewModel.kt`。

修改生产：`model/CompanionReading.kt`、`data/repository/roleplay/CompanionReadingRepository.kt`、`RoomCompanionReadingRepository.kt`、`CompanionRepository.kt`、`RoomCompanionRepository.kt`。未修改 UI、导航、AppGraph、设置/ContextTransfer、schema、迁移、发包参数、更新清单或旧截图。

## 已写行为测试与待主会话验证

主会话是唯一 Gradle/formatter/截图/设备执行者，本代理未运行这些命令。以下20个新增方法目前**待执行**，不能凭文件存在勾选 AC：

| 实际测试类 | 方法数 | 行为 |
| --- | --- | --- |
| `data.repository.roleplay.CompanionHistoryManagementRepositoryTest` | 8 | 真实 Room：空正式场与未分场、混合授权预期129条（125 legacy+4 linked）、同日两场/跨午夜迟到、全量导出、显式旧档不重绑、外角色/身份/用途/群聊、批次 CAS/存储失败全回滚、删除游标、原文修订→迟到主生成/声音发布/提取拒绝、独立人工新增/人工修订版本与真实私有合成 WAV 收藏保留 |
| `data.repository.roleplay.CompanionTextExportTest` | 6 | UI只读50而全量导出125、完整长 raw/标签/实体/空白与 BOM、时间/说话人/typed、SAF取消零调用、真实 ContentResolver 写/close/null stream/权限失败、协程取消、安全文件名/完整代理对 |
| `viewmodel.CompanionHistoryViewModelTest` | 6 | 初始50/全量计数、冻结修订拒绝删除新版本、多选草稿与明确重选、SAF迟到ID/取消、IO失败保历史与全量重试、角色删除/授权失效清窗口及 NonCancellable 迟到、删除游标/旧档禁止写 |

测试均用合成内容、Fake 或真实隔离 Room/私有临时 WAV，不访问付费模型或真实用户库。仅执行了所改已有文件的 `git diff --check`，无空白错误；另一次符号库存 `rg` 的路径 glob 在 PowerShell 下无效，已按实际文件明确范围，不将该命令写作通过。

建议主会话首轮定向选择以上3类，并复验 `CompanionReadingPaginationTest`、`CompanionReadingViewModelTest`、`CompanionRepositoryTest`（D4 scope/record 扩展及 D1 新批次契约）。原生 History/完整 AppGraph 故事由 UI 代理补齐、主会话执行；全量 JVM 仍留到全 D6 checker 后代码冻结时仅一次。

## 剩余验证边界

- 尚未取得本代理新代码的编译/JVM/XML、SAF真实设备交互、历史新页视觉及跨 AppGraph 完整故事结果。
- 根任务检查者应复核批量事务与 source 闭包、档案 origin/空间/身份守卫、SAF launcher requestId 和新旧阅读 ABI；失败由已分配所有权代理或检查者修复，主会话定向复验。
- 真实模型表达/翻译/TTS、物理扬声器/路由、旧34截图基准接纳均未验证；不能从本地合成 WAV/Fake 推导真实效果。
- 本代理释放全部源码给主会话/原生检查者。未提交、推送、发布、改版本/更新 JSON 或归档。
