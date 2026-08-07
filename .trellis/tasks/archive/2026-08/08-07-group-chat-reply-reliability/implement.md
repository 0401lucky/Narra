# 群聊多角色回复可靠性：实施计划

## Completion Definition

AC1-AC8 全部满足：正常、部分失败、全失败、后处理异常、RoundTrip 外异常、取消和
会话切换均结束于一致状态；已完成正文不被副作用异常覆盖；窄测和全量质量门禁通过。

## Ordered Checklist

1. **加载开发规范与确认基线（覆盖 AC1-AC8）**
   - 使用 `trellis-before-dev` 读取网络、错误、日志、质量和跨层规范。
   - 重新核对 `research/repository-evidence.md` 的源码行号及当前测试基线。
   - 运行现有群聊成功/重试测试，确认改动前行为。

2. **先补失败矩阵测试（AC1-AC6）**
   - 正常两角色顺序成功，speaker 元数据和 Loading 清理正确（AC1）。
   - 保留现有“瞬时 5xx 后重试并继续”测试（AC2）。
   - 新增“首角色成功、第二角色连续失败”和“三角色中间失败”测试（AC3）。
   - 新增“全部角色失败”测试（AC4）。
   - 注入 AFTER_ASSISTANT/观察/协议后处理异常，验证正文仍为 COMPLETED（AC5）。
   - 注入成员读取、用户消息写入异常，并覆盖取消、场景/会话切换（AC6）。

3. **隔离生成提交与后处理（AC5）**
   - 在 `RoleplayRoundTripExecutor` 中明确“完成消息已持久化”的提交点。
   - 将协议、脚本、观察等后处理放入独立失败域；取消重新抛出，普通失败不回写
     ERROR，不触发生成重试。
   - 对一致性副作用保留错误结果，并通过安全 Notice/日志反馈。

4. **补群聊总流程终态兜底（AC3、AC4、AC6）**
   - 在 `RoleplaySendActionSupport` 的群聊总流程处理 RoundTrip 外异常。
   - 仅当前有效 run/会话可恢复 `isSending`、`streamingContent` 和最终 Error/Notice。
   - 清理当前回合残留 Loading，保留已完成和已失败回合，不影响后续 Room 同步。
   - 保持 `GROUP_TURN_MAX_ATTEMPTS = 2` 和串行执行，不新增全局重试。

5. **增加脱敏阶段日志（AC7）**
   - 记录 participant/assistant id、turn index、attempt、stage 和安全错误类别。
   - 为成功、重试、最终失败、取消、后处理失败和总流程异常分别建立日志断言或
     可验证 Fake sink。
   - 审查日志中不存在正文、提示词、完整响应、密钥、附件路径或 Base64。

6. **运行窄测并修复回归（AC1-AC7）**
   - 运行 ViewModel、RoundTrip Runner、Planner 和相关 AiGateway 协议测试。
   - 检查每个测试的请求次数、消息 ID/状态、speaker、Notice/Error、
     `isSending`、取消传播和跨会话隔离。
   - 每个单元测试命令由执行环境设置 60 秒上限；超时只报告“未取得结果”。

7. **执行全量质量门禁（AC8）**
   - 依次运行 `testDebugUnitTest`、`lintDebug`、`assembleDebug`。
   - 检查 `git diff`，只保留该子任务业务改动、测试和必要规划记录。
   - 若实现与设计边界发生实质变化，先回到规划更新并重新获取批准。

## Validation Commands

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.myapplication.viewmodel.RoleplayViewModelTest" --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.myapplication.conversation.ConversationAssistantRoundTripRunnerTest" --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.myapplication.roleplay.RoleplayGroupReplyPlannerTest" --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.myapplication.data.repository.ai.AiGatewayTest" --console=plain
.\gradlew.bat testDebugUnitTest --console=plain
.\gradlew.bat lintDebug --console=plain
.\gradlew.bat assembleDebug --console=plain
```

## Acceptance Mapping

- AC1 → 步骤 2、4、6。
- AC2 → 步骤 2、4、6。
- AC3 → 步骤 2、4、6。
- AC4 → 步骤 2、4、6。
- AC5 → 步骤 2、3、6。
- AC6 → 步骤 2、4、6。
- AC7 → 步骤 5、6。
- AC8 → 步骤 7。

## Risky Files / Rollback Points

- `RoleplayRoundTripExecutor.kt`：完成提交边界是最高风险点；每次调整后先运行 AC5
  窄测，禁止让后处理异常重新进入生成失败路径。
- `RoleplaySendActionSupport.kt`：保持串行与稳定 Loading id；异常兜底不得覆盖其他
  run 或新会话状态。
- `RoleplayViewModel.kt` / `RoleplayObservationSupport.kt`：只有确实需要调整发送终态
  所有权时才修改，避免扩大观察流行为变化。
- `AiGateway.kt`：默认不修改；没有独立兼容性证据时不得收紧 `[DONE]`。
