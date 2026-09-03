# 错误处理规范

## 错误分类

- 输入与配置错误：在边界使用 `require`、`IllegalArgumentException` 或
  `IllegalStateException`，文案为可行动的简体中文。
- 可预期业务失败：使用显式结果类型，例如
  `EconomyOperationResult.Success/Failure`，不要依赖异常表达正常分支。
- 网络、文件、数据库和协议错误：在数据/领域层保留异常语义，由上层决定
  重试、回退或展示。
- 用户可见错误：进入 UI 前转换为安全中文文案，不直接暴露内部异常。
- 协程取消：`CancellationException` 是正常控制流，不记录为错误、不包装成
  普通失败。

## 分层处理

- DAO/Store/Repository 不为“让 UI 好看”而吞掉关键写入失败。
- 兼容性解析允许 `runCatching + getOrDefault/getOrNull`，但重要数据损坏或
  被吞异常要用 `logFailure` 留下脱敏诊断。
- Coordinator/ViewModel 在用户动作边界捕获异常，恢复 loading/sending 状态，
  再写入 `UiState`。
- Composable 不包围 Repository 调用做业务异常处理；它只消费状态和触发
  `onXxx` 回调。

参考：

- `ConversationAssistantRoundTripRunner.kt`：取消与普通失败分开处理。
- `ChatViewModel.kt`：失败消息标记与发送状态恢复。
- `MomentsViewModel.kt`：`runCatching` 后更新 `errorMessage`。
- `ContextTransferCodec.kt`：解析失败记录长度后安全回退。

## 协程取消与资源清理

捕获宽泛异常时先处理取消：

```kotlin
try {
    performWork()
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (throwable: Throwable) {
    // 恢复状态并映射为安全错误
}
```

- OkHttp 流式请求在取消时调用 `Call.cancel()`，并在 `finally` 关闭资源。
- 可取消长任务保存 `Job?`，取消入口要恢复 UI 状态。
- `Result.logFailure()` 会跳过 `CancellationException`；不要在外层再次把它
  写成失败 Snackbar。

## UI 状态与消息

- Chat、Roleplay、Phone 等使用 `errorMessage` 与 `noticeMessage`；Settings
  现有模式使用单一 `message`。
- 一次性消息显示后调用 `clearErrorMessage`、`clearNoticeMessage` 或
  `consumeMessage`，避免旋转或重组重复提示。
- 聊天消息状态统一使用 `MessageStatus.LOADING`、`COMPLETED`、`ERROR`；
  失败时保留可重试消息，不要直接删除用户输入或覆盖历史快照。
- 破坏性操作先确认；字段校验就近内联显示；跨页面错误使用统一 Snackbar。

## 脱敏与用户文案

- 所有用户可见异常先调用
  `SensitiveTextRedactor.throwableMessageForUi(throwable, fallback)` 或等价
  安全映射。
- API Key、Authorization、Bearer、Base64、SQL、本地路径、堆栈和完整
  请求/响应体不能进入 UI。
- HTTP 错误可保留状态码、request-id、retry-after 等安全上下文。
- DNS、超时、配置缺失、协议不支持应映射为不同的可行动文案。
- 用户可见文案使用简体中文，避免直接显示异常类名。

## 状态恢复

每个异步动作都检查：

- 开始前是否防重复提交。
- 成功后是否清理 loading、临时错误和任务句柄。
- 失败后是否恢复按钮/输入可用状态。
- 取消是否不显示错误。
- 乐观写入失败时是否标记 `ERROR`、回滚或保留重试入口。
- 切换会话/场景后，旧任务是否会错误写入新状态。

## 不要复制的现存问题

- 部分旧代码仍直接使用 `throwable.message`，例如悬浮翻译和部分文件流程；
  新代码必须先脱敏。
- `TranslationViewModel` 等少量 `runCatching` 路径未区分取消；不要把该写法
  当作范例。
- 解析失败静默回退很多，新增兼容字段时必须明确默认值、日志和用户影响，
  不能无条件 `getOrNull()`。
- 不要用 `catch (_: Exception)` 掩盖需要修复的写入或协议错误。

## 相关测试

- `ChatViewModelTest.kt`
- `AppUpdateViewModelTest.kt`
- `SensitiveTextRedactorTest.kt`
- `AppLoggerTest.kt`
- `AiGatewayTest.kt`
