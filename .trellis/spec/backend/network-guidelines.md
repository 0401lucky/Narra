# 网络与协议规范

## 边界与数据流

网络调用遵循：

```text
SettingsStore / ProviderSettings
    → ApiServiceFactory
    → Retrofit API 或 OkHttp 流式 Call
    → 协议 Support / Parser
    → 领域响应或 Flow<ChatStreamEvent>
    → Repository / ViewModel
```

- Retrofit 接口只声明端点和 DTO，位于 `data/remote/`。
- Provider 选择、协议分流、兼容降级和流事件归一化由
  `data/repository/ai/` 负责。
- UI 和 ViewModel 不拼接 URL、不创建 OkHttpClient、不解析 SSE。

## Base URL 与认证

- 所有 Provider Base URL 先经过 `ApiServiceFactory.normalizeBaseUrl()`；
  调用方不要自行补 `/`。
- 远端 Provider 使用 HTTPS；HTTP 只允许本机/loopback 测试地址。
- OpenAI Compatible 使用 `Authorization: Bearer`；Anthropic 使用
  `x-api-key` 与 `anthropic-version`。认证头只在客户端工厂注入。
- 动态兼容端点使用 Retrofit `@POST + @Url`；固定端点使用相对路径，参考
  `OpenAiCompatibleApi.kt`、`AnthropicApi.kt`。
- 更新下载还需遵守 `AppUpdateUrlPolicy` 的 host、端口、路径、渠道和扩展名
  白名单，不能仅依赖 HTTPS。

## 客户端与超时

- 普通请求、长任务、流式请求使用不同超时配置；不要为了单个调用临时修改
  全局客户端。
- `ApiServiceFactory` 按规范化 URL 和 API Key 缓存客户端。缓存字段包含敏感
  信息，禁止输出到日志或调试 UI。
- 429 重试由 `RateLimitRetryInterceptor` 处理。新增重试逻辑前必须评估
  POST 是否幂等，避免重复副作用。

## 协议与序列化

- OpenAI Chat Completions、OpenAI Responses、Anthropic Messages 必须在
  `ProviderApiProtocol` / `OpenAiTextApiMode` 分支中显式处理。
- Retrofit 非流式接口返回 `Response<T>`；调用方检查 `isSuccessful`，安全地
  读取并归一化错误体。
- Gson 默认使用 `AppJson.gson`。只有外部协议确有不同命名策略时才能创建
  专用 Gson，并在对应 Repository 内局部封装。
- DTO、请求构造和协议解析分别放在 model、Gateway support、protocol support
  中；不要在 ViewModel 手写 JSON。

## 流式响应

- 流式请求使用 OkHttp + SSE 行解析，不通过 Retrofit converter。
- 只处理 `data:`，识别 `[DONE]`，再由 OpenAI/Responses/Anthropic 的解析器
  转成统一 `ChatStreamEvent`。
- 流读取在 IO dispatcher 执行；循环内检查协程活跃状态。
- 协程取消时调用 `Call.cancel()`，`CancellationException` 原样传播，
  `finally` 中关闭 response/call。
- 网络 `IOException` 通过 `GatewayNetworkSupport` 转换为可读异常，但不能把
  DNS、取消或协议错误混成同一文案。

## 兼容降级

- 已有兼容策略包括：400 时移除不支持的 reasoning/采样参数重试、图片编辑
  JSON 失败回退 multipart、Responses API 独立适配。
- 降级必须有明确触发条件，不能对所有异常无差别重试。
- 新增协议能力优先拆到独立 Support/Parser，不继续扩大 `AiGateway.kt`。

## 错误与敏感信息

- HTTP 失败可保留状态码、request-id、retry-after 等安全上下文。
- 错误体、异常和日志必须经过 `AiErrorRedaction` 或
  `SensitiveTextRedactor`；禁止记录 API Key、Authorization、完整请求体、
  Base64 图片/音频或用户私密提示词。
- 用户可见文案使用简体中文，并提供可行动的配置或网络提示。
- 不要把 `throwable.message` 未经处理直接传到 Snackbar、Toast 或悬浮窗。

## 测试要求

- 使用 MockWebServer：`@Before` 启动，`@After` 关闭。
- 覆盖成功、4xx/5xx、非法 JSON、超时/取消、协议分支和兼容重试。
- 使用 `takeRequest()` 校验 path、header、body；敏感错误测试必须断言密钥、
  Bearer、Base64 等没有泄漏。
- 代表测试：`AiGatewayTest.kt`、`MimoTtsClientTest.kt`、
  `ApiServiceFactoryTest.kt`、`AppUpdateRepositoryTest.kt`。

## 场景：MiMo 文本设计音色试听与近似固定

### 1. Scope / Trigger

- 适用于角色音色的 `VOICE_DESIGN` 试听、试听音频播放，以及把最近一次
  试听保存为 `VOICE_CLONE` 样本的跨 UI、网络、文件和播放器流程。
- MiMo VoiceDesign 不返回可持久化的音色 ID；相同 Prompt 再次请求也不能
  视为同一个音色。产品文案和实现都不得宣称“官方固定音色”。

### 2. Signatures

```kotlin
MimoTtsRequest.voiceDesign(text: String, prompt: String)
MimoTtsRequest.voiceClone(text: String, sampleDataUri: String)

onPreviewProfile:
    suspend (VoiceSynthesisSettings, VoiceProfile, String) -> SavedAudioFile

VoicePreviewPlayback.prepareAndPlay(
    path: String,
    onStarted: () -> Unit,
    onCompleted: () -> Unit,
    onError: (Throwable?) -> Unit,
)

VoiceCloneSampleStorage.copyLocalFileToAppStorage(
    context: Context,
    sourcePath: String,
    scope: String,
    displayName: String,
): SavedVoiceCloneSample
```

### 3. Contracts

- VoiceDesign 请求只发送试听文本与非空 Prompt，不发送预置 `voice` 字段。
- 返回的 Base64 音频先保存到 `cacheDir/mimo_voice_design_previews/`，并按
  文件头校验为 WAV/MP3；完整 Base64、Prompt 和 API Key 不进入 UI 或日志。
- UI 只有在播放器实际执行 `start()` 后才能显示“音色测试成功”；仅收到
  网络响应或写入文件不能算试听成功。
- “保存为固定音色”保存最近一次有效试听的原始音频文件，切换 Profile 为
  `VOICE_CLONE`，并明确说明这是声音克隆近似保持，不保证完全一致。
- 持久化设置只保存样本路径、MIME、文件名和大小，不保存 Base64。VoiceClone
  请求时再构造 `data:{mime};base64,{payload}`；仅允许 wav/mp3，编码负载不得
  超过 10 MB。
- 弹层关闭、模式切换、取消或新试听开始时，取消旧 Job、释放播放器并删除
  未持久化的临时试听文件；播放结束只释放播放器，保留最近一次有效试听文件，直到
  用户固定、重新试听或关闭弹层，这样播放结束后仍可保存为 VoiceClone 草稿；用户取消
  固定草稿时删除该草稿样本。
- 声音克隆区域必须提示：样本会发送给当前 MiMo 或第三方中转接口，用户应
  仅使用自己有权处理的音频。

### 4. Validation & Error Matrix

- Prompt 为空 -> 当前弹层显示“需要先填写音色描述”，不发请求。
- API Key、网络或协议失败 -> 通过 `SensitiveTextRedactor` 映射为弹层内错误。
- Base64 非法、空数据或文件头不是 WAV/MP3 -> 试听失败，不创建播放器。
- 播放器创建、准备或 `start()` 失败 -> 不显示成功，释放播放器并显示播放失败。
- 固定样本不存在、为空、格式不支持或编码后超限 -> 不切换 Profile，保留可重试入口。
- 协程取消 -> 清理临时资源并重新抛出 `CancellationException`，不显示失败。

### 5. Good / Base / Bad Cases

- Good：最新 Prompt 合成有效 WAV，播放器开始播放后立即在未关闭的角色弹层
  显示成功；用户可把该试听保存为 VoiceClone 草稿。
- Base：用户只试听、不固定；播放结束后播放器释放但最近一次有效试听文件暂留，关闭
  弹层或开始下一次试听时再清理临时文件。
- Bad：网络已返回 Base64，但文件保存或播放器启动失败；此时必须显示失败，
  不能沿用全局连通性测试的成功消息。

### 6. Tests Required

- `MimoTtsClientTest`：断言 VoiceDesign 请求包含最新 Prompt 和试听文本，且无
  预置音色字段。
- `VoiceSynthesisSettingsScreenTest`：断言 `onStarted` 前没有成功弹窗，开始后
  弹层仍打开且出现成功弹窗；覆盖保存为 clone 草稿和关闭释放播放器。
- `AudioFileStorageTest` / `VoiceCloneSampleStorageInstrumentedTest`：断言
  Base64 解码、WAV/MP3 文件头校验、应用私有目录复制和非法文件拒绝。
- 真机或模拟器验收：确认设备实际发声，播放完成、失败和关闭弹层均无资源残留。

### 7. Wrong vs Correct

```kotlin
// 错误：复用全局连通性测试，收到 Base64 就报告成功并丢弃音频。
onTestSettings(settings.copy(defaultProfile = profile))

// 正确：独立生成试听文件，只有播放器真正开始后才报告成功。
val audio = onPreviewProfile(settings, profile, assistantId)
playback.prepareAndPlay(
    path = audio.path,
    onStarted = { showSuccessDialog = true },
    onCompleted = ::releasePlayback,
    onError = ::showSafePlaybackError,
)
```

## 参考文件

- `data/remote/ApiServiceFactory.kt`
- `data/remote/OpenAiCompatibleApi.kt`
- `data/remote/AnthropicApi.kt`
- `data/remote/RateLimitRetryInterceptor.kt`
- `data/repository/ai/AiGateway.kt`
- `data/repository/ai/ResponseApiSupport.kt`
- `data/repository/ai/GatewayNetworkSupport.kt`
- `system/update/AppUpdateUrlPolicy.kt`
