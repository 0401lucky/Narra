# D1 只读预审记录

2026-11-09。检查者：d1_check。当前作者仍持有源码与Gradle/formatter所有权；本记录只反映已读代码与明确调用序列，不是D1最终验收。未运行测试或构建，未修改产品代码。

## 上下文与范围

- 已读取完整hook保存文件，并逐份补读未内联的任务产物/研究及相关规范。
- 父方案已获用户实施批准；本次只检查D1，不能把D1当完整2.4.1。
- check-readonly-source.json记录首次预审源哈希：相对实施基线50个变动文件，273保护项无变化。另有迁移方独占的.gitattributes精确换行规则。
- 已读核心Repository/Coordinator/模型/JSON兼容、来源资格与摘要消费、协议归一化/stream tracker；仍继续核对实际网关路径、迁移与运行证据。

## 明确发现

### R1：新有效回合后，旧UNCERTAIN reroll仍可提交（作者已补修，待核证）

读取时RoomCompanionRepository.current没有检查同一encounter的最新turn；beginTurn只阻止NOT_STARTED/REQUESTED/RESULT_READY。

可重现序列：提交A → 为A创建reroll并claim → lease到期recover变UNCERTAIN且保留claimToken → beginTurn(B)被允许 → A旧owner仍可saveGeneratedArtifact/commit，迟到改写A。父设计§4要求新有效回合使旧attempt失效。

已向作者/主会话发送。作者增加同encounter最新turn约束及真实Room newerTurn_rejectsExpiredRerollOfOlderAnswer；不同新encounter的旧场已收产物补存不能受此限制。等待最终源码与运行结果定向确认。

### R2：已接受开场reroll在ENDED后不能补存（作者报告已修，待核证）

读取时current(forCommit)仅允许ENDED且kind!=OPENING，即使OPENING已有canonical、reroll RESULT_READY，也被拒绝。需要区分有已提交原答的开场替换与从未接受的废稿：前者可仅补旧答、不重激活；后者仍拒绝。

主会话已转交作者并报告已按此分型修正、补Room回归。尚未由本检查者运行验证。

### R3：回ONLINE后历史CRUD被显示媒介挡住（作者报告已修，待核证）

读取时editMessage/deleteMessage调用validContext默认requireOffline=true。角色/run/persona仍相同时，仅切回ONLINE_PHONE就无法从历史修改/删除陪伴原文。

CRUD应保持身份/运行/来源revision校验，同时移除当前显示媒介的限制，不要求用户先切回线下。作者已按主会话反馈修正并补回归，等待核证。

## 继续检查的边界（尚非确认发现）

- 已COMMITTED attempt在后续reroll或人工修订后重试的回执身份。
- 续租失败是否取消仍在运行的collector，以及内存产物数量是否在并发下严格有界。
- 来源资格对缺失/错误selectedAttempt关联的保守性。
- 主流实际HTTP所有fallback/tool最后轮与旧消费者行为。
- CONTROL_NOOP在保存、读取、merge与completeInput中的完整链路；运行结果不能只看用例文件存在。

## 已收到的作者验证进展

主会话报告jvm-01已保全：132类1148项，1147通过；AiGatewayCompletionEvidenceTest因6个runBlocking方法非Unit导致初始化失败，18个方法未执行。作者只修新测试签名并定向重跑；新的预审修正后还会冻结并执行相关联合回归。

这些结果待本检查者按原始XML/日志、最终源码与保护项哈希独立重计。设备测试由主会话在最后冻结APK上执行，本检查者不启动或操作AVD。
