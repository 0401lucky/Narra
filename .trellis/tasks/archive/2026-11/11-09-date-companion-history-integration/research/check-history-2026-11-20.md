# D6 历史、原文管理与 TXT 静态检查

2026-11-20，原生 `trellis-check /root/d6_history_check`。恢复原 D6 已批准任务；读取完整 hook 保存文件、当前任务合同、历史/UI/D5 交接和相关规范后，检查实际生产与测试路径。主会话唯一执行 Gradle、formatter、截图和设备，因此本记录不是编译或 JVM 通过回执。

## 检查范围与既有修补

原历史实施清单的 16 条源码/测试路径，加上主会话交接的 `data/local/chat/ConversationDao.kt`，共 17 条。DAO 是上一检查者的 pending canonical SQL 适配，不属于资料代理。相对实施交接的当前源码变化及最终 SHA 应由主会话基于 D6 起点重新绑定，不能继续使用 `history-source-hashes.json` 当最终验证哈希。

- `readCanonicalAll` 独立包含已经保存、尚未完成的 INPUT/CONTINUE 原文；默认 `readAll/page/readWindow/locate` 仍要求完整 Reader 来源。DAO 的 `includePendingInputs` 尾参数默认 `false`，不放宽声音、模型或提取资格。
- pending 分支同时核实角色/persona/space/run、单聊绑定、反向输入 ID/所见修订、真实 ACTIVE/ENDED encounter、无已提交答复与原始来源身份。未知类型、坏关联、未接受开场和外身份不进入管理/TXT。
- 当前历史支持全授权仓储读取、真实空场与未分场组、日期/月查询、删除值游标、只读旧档和批次 CAS；TXT 保留完整 raw/BOM/时间/说话人/控制类型，SAF 取消或流失败不报成功。既有测试覆盖媒体及未知附件字段保留、来源失效、迟到生成/声音/提取和独立人工版本/收藏。

## 已修复的具体问题

### 1. 操作预检之后的授权失效会保留旧原文

`CompanionHistoryViewModel` 原来只在 `requireScope` 返回 null 时清空窗口。预检通过后，仓储自己的后续校验可能失败；普通异常、mutation 的 Conflict 或 exporter 包装的 Failed 会保留旧窗口/草稿/选择。

修复：失败路径重新确认 scope，失效或无法确认时撤销代次并清空旧原文及操作状态；mutation 和 export 返回后再次校验授权。普通 IO 错误在 scope 仍有效时继续保留窗口。新增 4 个 VM 回归，分别覆盖读取预检后的失效、mutation 冲突期间失效、export 读取失效以及一般 IO 保窗口/脱敏。

### 2. 已知 turn 的损坏消息标签可能被降成 legacy

新批量 API 原来通过 `getMessageTurn(messageId)` 找到反向 turn，却没有核对它与 `messages.companionTurnId` 一致；legacy 分支也没有拒绝反向关联。已有完整来源证据的空 link + legacy 行可能进入历史，改写时不触发原 turn 的 CAS 递增。

修复：DAO 的 legacy 分支增加反向关联 `NOT EXISTS`；新批量 API 拒绝不同的非空 link、带 legacy 标签的已关联行以及仍被 turn 引用的空 link 行。新增真实 Room 方法同时检查 INPUT/ASSISTANT 的 missing-link 和空-link+legacy，要求 Reader/管理不读取、批量改删拒绝、原文/修订/turn 保持。

本代理修改 5 条源码/测试路径：

- `app/src/main/java/com/example/myapplication/data/local/chat/ConversationDao.kt`
- `app/src/main/java/com/example/myapplication/data/repository/roleplay/RoomCompanionRepository.kt`
- `app/src/main/java/com/example/myapplication/viewmodel/CompanionHistoryViewModel.kt`
- `app/src/test/java/com/example/myapplication/data/repository/roleplay/CompanionHistoryManagementRepositoryTest.kt`
- `app/src/test/java/com/example/myapplication/viewmodel/CompanionHistoryViewModelTest.kt`

未修改其它所有者文件、旧 schema/PNG、发布参数/更新 JSON、真实用户库或凭据；未提交、推送、发布或归档。

## 已执行的静态验证

- 定向 `git diff --check`：exit 0，无空白错误；仅 DAO 工作树 LF/CRLF 提示。
- 17 条实际源码独立扫描：无 trailing whitespace。JUnit 源码声明为 Repository 14、Export 6、ViewModel 10，共 30 方法；这是声明数量，未执行。
- 当前 DAO 完整 SQL 在固定 v56 的 58 表 schema 上使用内存 SQLite `EXPLAIN QUERY PLAN`：exit 0、20 个计划节点、14 个参数；尾参数默认 false、反向 link 守卫存在。此项只证明 SQL 可解析，不代替真实 Room/JVM 行为验证。

主会话最小新领域 selector：

```text
com.example.myapplication.data.repository.roleplay.CompanionHistoryManagementRepositoryTest
com.example.myapplication.data.repository.roleplay.CompanionTextExportTest
com.example.myapplication.viewmodel.CompanionHistoryViewModelTest
```

相关旧兼容 selector：`CompanionReadingPaginationTest`、`CompanionReadingViewModelTest`、`CompanionRepositoryTest`。后续全量 JVM、编译/Lint、SAF/历史原生故事及实际视觉验证仍由主会话执行并绑定源码。真实模型/物理设备表现和既有 34 张视觉基准接纳仍单列。

## 交接状态

本代理释放全部 17 条源码/测试路径给主会话及后续全范围检查。当前没有已确认但未修复的本领域代码问题；Lint、TypeCheck、JVM 和设备均未由本代理运行。主会话应将 canonical 资格分离、反向关联守卫与授权失效清窗口的实际合同同步到相应规范。
