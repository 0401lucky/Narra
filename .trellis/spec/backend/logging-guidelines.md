# 日志规范

## 统一入口

业务代码使用 `system/logging/AppLogger.kt`：

- `AppLogger.d`：仅 debug 构建输出的诊断信息。
- `AppLogger.i`：有价值的生命周期或完成事件，谨慎使用。
- `AppLogger.w`：已回退、已吞掉、仍可继续的异常。
- `AppLogger.e`：当前操作失败或需要重点关注的异常。
- `Result<T>.logFailure(tag) { ... }`：仅在失败时懒求值消息，默认 WARN，
  并跳过 `CancellationException`。

日志格式是稳定 tag、简短消息和可选 Throwable；项目没有 JSON structured
logging、request-id 强制字段或 Crashlytics 依赖，不要虚构这些要求。

## Tag 与消息

- tag 使用稳定组件名，例如 `AppSettingsStore`、`WorldBookMatcher`。
- `AppLogger` 会自动截断到 Logcat 的 23 字符限制；调用方仍应保持简短。
- 消息描述动作与失败阶段，不写大段业务内容。
- 记录数量、长度、状态码等诊断元数据，而不是原始敏感内容。例如：

```kotlin
runCatching { decode(rawJson) }
    .logFailure(TAG) { "decode failed, raw.len=${rawJson.length}" }
    .getOrDefault(emptyList())
```

## 自动脱敏

`AppLogger` 在进入 sink 前调用 `SensitiveTextRedactor.redact()`，Throwable
也会转换为脱敏副本。当前覆盖：

- Authorization、api-key、x-api-key 等认证头。
- Bearer 和常见 `sk-`、`rk-`、`pk-`、`sess-` 密钥。
- JSON/query 中 token、password、secret、signature。
- 图片/音频 Base64 data URI。

脱敏是最后一道防线，不是记录敏感数据的许可。调用方仍必须避免构造包含
密钥、完整提示词、聊天内容、图片 URI、本地路径或 SQL 的消息。

## 应记录的内容

- 解析失败后选择安全默认值。
- 兼容降级被触发，以及降级后的最终失败。
- 系统服务、后台任务或下载流程中无法向当前页面直接反馈的异常。
- 关键外部操作的状态码、request-id、retry-after、对象数量或输入长度。
- 不影响业务的清理失败、旧数据迁移失败。

## 不应记录的内容

- API Key、Authorization、Cookie、签名、密码和 Keystore 明文。
- 完整请求/响应体、用户聊天、角色提示词、记忆、世界书内容。
- Base64 图片/音频、文件内容、私有 URI 和本地绝对路径。
- 完整 DataStore/Room 行、SQL 语句、堆栈文本拼接到 message。
- 正常协程取消。

## 日志与业务错误分离

- Logger 不是业务错误通道。用户反馈通过 `UiState`、Snackbar、内联错误或
  明确结果类型传递。
- 日志 sink 失败不能反向破坏业务路径；`LogcatSink` 已用 `runCatching`
  保护 JVM 测试环境。
- 对关键写入失败不能只写日志后假装成功；日志仅补充诊断。

## 现有偏离点

- `MessageBubbleAttachments.kt` 仍直接 `Log.w` 并拼接 URI。
- `TavernCharacterAdapter.kt` 仍有 `println` / `android.util.Log.d`。

它们是待收敛的历史代码，新代码不要复制。除 `AppLogger.kt` 的 Logcat sink
外，不新增直接 `android.util.Log` 或 `println`。

## 测试

- `AppLoggerTest.kt` 验证 sink、懒求值、取消跳过和消息/Throwable 脱敏。
- `SensitiveTextRedactorTest.kt` 验证认证头、JSON/query 密钥、Base64、SQL
  和本地路径。
- 修改脱敏规则时必须覆盖“保留安全上下文”和“不泄漏秘密”两类断言。
