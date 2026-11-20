# D1 场次与可靠回合基础：技术设计

以 [本块PRD](prd.md) 与 [父设计](../11-09-date-companion/design.md) 为权威。D1已通过独立检查与设备验收；实际接口及新合同见 [陪伴规范](../../../../spec/backend/companion-foundation-guidelines.md)，证据见 [交接记录](research/handoff.md)。

## 依赖输入

父任务最新完整规划已获后续实施批准。接口差异先回到父设计协调，不在本块建立平行数据或备用状态单例。

## 数据流与合同

父设计§2–4为权威。使用三张附属表和messages两个增量字段，selectedAttempt代表有效旧答、activeAttempt代表当前尝试。仓储核心接口为beginTurn、claimAttempt、saveGeneratedArtifact、commitAttempt、cancelAttempt与受控编辑/删除；协调器提供retrySave和acceptOpening。网络在事务外，canonical/资格/ingress同事务。UNKNOWN请求不因lease过期自动重发。统一sourceEligibility覆盖所有消费者，INPUT待提交与CONTINUE空操作不是可忽略的一般空洞。完成证据复用既有归一化规则，不新建Provider体系。

2026-11-10独立检查细化：来源关联必须核实selectedAttempt实际存在、属于当前turn且为COMMITTED，不能仅判ID非空。重复提交仅在canonical仍对应原attempt及原提交版本时返回同一回执；后续重生成、人工修订或删除使该版本失效时返回TURN_CHANGED，不能返回新答冒充旧回执或恢复已删正文。临时产物的容量限制同时涵盖请求中的占位与已收到待补保存结果；失去续租/执行权必须取消对应collector。

未提交INPUT编辑后保留失效attempt的恢复指针，供用户显式沿原turn创建新attempt，并重新冻结最新输入/来源修订；旧state/claim/CAS仍失效。activeAttemptId非空不等于网络正在执行，调用方同时读取state。已有canonical的回合编辑继续清空active指针，删除输入不允许恢复。

## 写入与协调范围

以下源码路径相对app/src/main/java/com/example/myapplication；新类型/目录是计划位置，已有类的详细行号见父research。

- model/ChatMessage.kt、AssistantReply.kt、PromptCompletionResult.kt及待新增Companion模型。
- data/local/chat/MessageEntity.kt、RoomConversationStore.kt、data/local/companion/、主库迁移/注册/schema。
- data/local/memory/MemorySourceStorage.kt、RoomMemoryArchiveRepository、摘要覆盖和兼容来源投影。
- ConversationAssistantRoundTripRunner/AiGateway完成事件适配与新CompanionRepository/Coordinator；AppGraph装配。

你不是唯一在代码库工作的执行者。实施时核对最新差异、保留用户与其它任务改动，所有跨块文件在明确交接后串行写入；不回滚或覆盖他人文件。

## 兼容与失败策略

只通过新companion link/契约选择新路径。既有MemoryProcessingJob仍只做记忆加工；旧retry回退功能不替换为新reroll。资料缺失新关联图时保守保留/拒绝机器消费，不剥link伪装legacy。

schema、消息映射、sameSource/validSource/摘要资格与主流事件为高风险。回退不降schema、不删除附属行；可停用新调用，但保留linked读取/保守资格。D1未验收前不得接新生产入口。

## 验证设计

新增CompanionRepository/Turn/SourceEligibility/CompletionEvidence等测试（名称由实现落定）；真实Room固定旧库升级、两个owner claim、文件库关闭重开、CONTROL_NOOP连续覆盖。高风险共享来源改动须包含v2.4记忆相关联合回归。

每个本块AC都需绑定行为证据；请求计数、源ID/修订、仓储结果、实际UI回调和错误/取消路径优先于实现镜像断言。遵循父计划的合成数据、隔离库及真实效果单独标注规则。

## 上下游交付

输出本块实际接口/文件范围、错误/恢复约定和验证结果；更新本块research中的implementation/check证据供下一块引用。公共contract发生实质变化时同步父/相关子设计，不只通过代理消息约定。
