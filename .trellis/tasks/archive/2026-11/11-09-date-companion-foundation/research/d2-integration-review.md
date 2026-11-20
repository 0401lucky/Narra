# Research: D1 → D2 接线核对

- Query: 当前 CompanionRepository / RoomCompanionRepository / CompanionTurnCoordinator 是否支持父设计的三开场、接受、直接开始、继续、免费补保存、线上返回与同 run 新 encounter。
- Scope: 对 D2 调用路径的独立静态核对，不替代 D1 全范围审查。
- Date: 2026-11-09。
- Snapshot: D1 实施者仍在写代码和运行自己的验证；下列为本次读取的当前快照建议，不是最终失败结论。本研究未改产品、未运行测试/Gradle。
- 依据：父 `.trellis/tasks/11-09-date-companion/design.md` §1–5；缩短源码路径均相对 `app/src/main/java/com/example/myapplication/`。

## Findings

### 1. 当前接口已能支撑的调用

| 用例 | 可行序列与当前结果 | 证据 |
| --- | --- | --- |
| 我靠近/角色靠近 | `createEncounter(context, APPROACH/INVITE, fixedEncounterId)` 得 DRAFT；`beginTurn(OPENING,input=null,fixedTurnId)`；`generate(attemptId,stream)` 保存 artifact，返回 Generated，不落 canonical 消息 | `data/repository/roleplay/RoomCompanionRepository.kt:29`、`:72`、`:107`；`conversation/CompanionTurnCoordinator.kt:108` |
| DRAFT 保存预览 | claim/heartbeat/saveGeneratedArtifact 都调用 current；current 只拒绝 ENDED，并不要求 ACTIVE，因此 DRAFT 的网络与 artifact 保存可正常接线 | `RoomCompanionRepository.kt:168`、`:190`、`:301` |
| 预览重新生成 | 同一 OPENING turn 调 `newAttempt`，旧 RESULT_READY 取消，新 activeAttempt 成为唯一可接受候选；不可再 begin 第二个 OPENING turn | `RoomCompanionRepository.kt:87`、`:126`、`:140`、`:146` |
| 接受与连点 | `acceptOpening(attemptId)` → `commitAttempt(acceptOpening=true)`；首次在同事务落消息并激活，立即重复已 COMMITTED 调用返回 alreadyCommitted，不重复插消息/安排 posthook | `RoomCompanionRepository.kt:208`、`:225`、`:251`；`CompanionTurnCoordinator.kt:81`、`:126` |
| 直接开始 | `createEncounter(context,DIRECT,fixedEncounterId)` 直接 ACTIVE；无需 begin OPENING 或请求模型 | `RoomCompanionRepository.kt:39` |
| 输入 | `beginTurn(INPUT,typedUserMessage,descriptor,fixedTurnId)` → generate；input 初始 waiting_turn，完整结果落盘后自动尝试 canonical 事务 | `RoomCompanionRepository.kt:91`；`CompanionTurnCoordinator.kt:108` |
| 继续 | `beginTurn(CONTINUE,input=null,...)` 创建短 CONTROL 行；D2 的 stream 按 turn.kind 恢复留白语义，不能把普通文字“继续”按字符串识别成控制 | `RoomCompanionRepository.kt:101`；`model/CompanionTurn.kt:31` |
| 当前有效场的免费补保存 | `retrySave` 先用同进程有界 artifact 缓存；否则读取 RESULT_READY/COMMITTED 并尝试提交，不调 stream | `CompanionTurnCoordinator.kt:70`、`:86` |
| 同 run 新场 | createEncounter 只绑定传入 MemoryRuntimeContext、插 encounter/结束旧 encounter；没有调用 restartScenario 或重绑 run | `RoomCompanionRepository.kt:29`、`:348` |
| 已提交经历回线上 | canonical 消息标记 OFFLINE_DIALOGUE，来源资格按 message/turn/encounter 关联，不按当前场景媒介 | `RoomCompanionRepository.kt:226`；`data/local/companion/CompanionSourceStorage.kt:17` |

**结论**：不存在“DRAFT 无法保存预览”的接口问题。OPENING 保存和接受已经分开，直接开始也已独立。D2 应持有逻辑 encounter/turn/attempt ID；接受重试直接调用 acceptOpening，不重新 beginTurn。

### 2. 接线前需要收敛的两项边界

#### R1：离场/新场结束旧场后，已落盘结果不能免费补保存

当前 `endEncounter` 调 close，把 encounter 设 ENDED；close 仅取消 NOT_STARTED/REQUESTED，RESULT_READY 原文仍存在。但 `current` 无条件拒绝 ENDED，导致 retrySave → commitAttempt 返回 ENCOUNTER_CHANGED。

- 证据：`RoomCompanionRepository.kt:55`、`:306`、`:352`；父设计§4第5–6条明确离场后仍可在恢复入口补保存。
- 序列：A ACTIVE 的 INPUT 已 RESULT_READY、canonical 保存失败 → D2 离场调用 endEncounter(A)，或显式新场 B 触发 closeActive(A) → recoverEncounter(A) 能列出 RESULT_READY → retrySave(A) 仍被拒绝。
- 从线上见面返回还会恢复 ONLINE_PHONE；`validContext`（`:329`）额外要求当前 OFFLINE_DIALOGUE，因此即使暂不 endEncounter，回线上后补存也被当前媒介检查拒绝。
- 这是“有恢复产物但没有恢复提交授权”的矛盾，不建议通过重新激活旧场或创建新收费 attempt 绕过。

主会话已确认的修正方向：分离**新请求/新回合权限**与**只提交已收到产物权限**。ENDED 不能发新请求；原角色/身份/space/run/generation、消息及引用修订仍有效时，旧场完整 RESULT_READY 可通过专用提交守卫补存，但不重激活、不关掉当前新场、不恢复旧页面。当前 mode 变化本身不应等同 run/身份失效。

对于从未接受的 DRAFT 开场，离开/被新场替代后仍须拒绝把废稿补成经历；不能将上述旧场补存放宽为“任意 ENDED OPENING 可接受”。可依已激活历史或曾存在 canonical 的提交证据区分。

#### R2：旧 DRAFT 的迟到接受会结束更晚的新 ACTIVE

DRAFT 创建时 activeKey 为 null，也没有冻结当前 active slot 的 revision/新场选择代次。acceptOpening 的 canonical 事务在激活前无条件 closeActive(context)，只比较 turn/input/context，不知道用户已经开始了更晚的新场。

- 证据：`RoomCompanionRepository.kt:39`、`:251`、`:301`、`:348`；`data/local/companion/CompanionEntities.kt:21` 没有激活 CAS 字段。
- 序列：A ACTIVE → 预览 B DRAFT 并得到完整产物 → 另一次入口直接开始 C ACTIVE → 老窗口/恢复入口 acceptOpening(B) → B 的 context/turn 仍合法，事务结束 C 并把 B 设为 ACTIVE。
- 同样可能出现在并行 B、C 两份 DRAFT 先后接受；unique activeKey 只确保最后一个 active，不能识别哪个接受已过期。
- D2 的 encounter/generation UI 检查仍应保留，但不能作为持久存储侧唯一约束。

主会话已确认的修正方向：对 DRAFT→ACTIVE 增加持久 active-slot/launch generation CAS，或受控 supersede 旧 DRAFT。预览本身不提前结束 A；B 接受时仅能替换其被允许替换的活跃状态，发现更晚选择则返回 ENCOUNTER_CHANGED。重复接受同一已提交 attempt 仍幂等。

### 3. D2 的接线顺序与责任

1. **进入前**冻结 originScenarioId、originConversationId、originMode、run/persona，并保证目标为 SINGLE/OFFLINE_DIALOGUE。来自线上时先等待模式切换成功，再读取真实 MemoryRuntimeContext；不能自己拼 runId 或用 restartScenario 取“新场”。
2. **新角色场景**需要跳过旧 greeting 自动 seed。现有 `viewmodel/RoleplayScenarioActionSupport.kt:86` 和 `RoleplayRepository.kt:806` 会补/保存静态开场；D2 创建路径应使用明确无 seed 的入口或构造路径，不能先保存后删除来伪装“预览不落经历”。旧会话已有 greeting 保留。
3. **准备/预览**固定 encounterId/turnId；generate 返回 Generated 后通过 getAttempt 读取原文。重感知沿同一 OPENING turn 的 newAttempt，UI 接受按钮绑定所见 attemptId，替代/离场后的旧按钮失效。
4. **接受**只调用 acceptOpening，Saved 后才允许输入/继续。NeedsSave 表示产物或 canonical 尚未保存，不能假进入；OPENING 的 retrySave 只会恢复至 Generated，所以“接受时保存失败”的恢复按钮应再次调用 acceptOpening，不能把 Generated 当接受成功。
5. **直接开始**仅 createEncounter(DIRECT)，不造 OPENING 控制行；创建成功后输入/继续走各自 typed kind。
6. **普通回合/补保存**generate 和 retrySave 分开按钮；恢复列表保留 attempt 的状态/完整性证据。只允许明确用户动作产生新收费 attempt，不能把 RESULT_READY 恢复写成 generate。
7. **暂离与结束**页面离开取消本页协程/语音并释放所有权；是否 endEncounter 与产物恢复按 R1 修正后的契约接线。补存成功可以刷新记录/提示，但绝不能导航回旧场。
8. **回原线上**使用 D2 所有的 origin 返回状态，仅在同 scenario/conversation/run 且尚未被用户另改模式时恢复原mode；D1 模型当前没有 origin 字段，需由入口/导航状态持有并按进程恢复要求持久化。不是 D1 另造线上会话的责任。
9. **普通旧会话点击**恢复既有会话与当前 encounter；只有用户显式新场才 createEncounter 新 ID。重复 compose/导航恢复不能每次随机造新 encounter。

### 4. 需由实施者补的定向验证

- DRAFT claim→RESULT_READY→接受：预览零消息/零来源，接受一次；重复接受不增消息或 posthook。
- preview B 后 DIRECT C / 并行两个 preview：迟到 B 不结束 C、不成为 active；预览退出不结束原 A。
- A RESULT_READY 后 end/切新场/回线上：原绑定有效则只补 canonical；网络调用数不变、当前 active 不变；run/persona/source 变化仍拒绝。
- 未接受 DRAFT 被放弃后不可借“补保存”发布；原 INPUT/CONTINUE 语义与控制行保持。
- D2 接受保存失败恢复不需重新生成，直接开始不 seed greeting，origin 返回不覆写其它场景或新用户选择。

## Caveats / Not Found

- 两项建议已及时发送主会话与 `/root/d1_foundation_implement`；主会话指定上述修正方向。
- D1 实施者随后回复将采用 `current(forCommit)` 放开 ENDED 非 OPENING 已收产物，并在创建新场时事务 supersede 同归属旧 DRAFT、补 Room 回归；本报告尚未将该回复视为已修复/通过。另已提醒当前模式与原绑定校验需要分开。
- 未检查 D1 的完整来源安全、所有迁移、其它消费者和全部测试，未运行任何验证命令；这些继续由实施者与独立 checker 负责。
- D1 文件可能随后变更。本报告中的行号和状态描述针对读取时快照；修复后应只定向复读上述路径更新结论，不将本报告当最终门禁结果。
