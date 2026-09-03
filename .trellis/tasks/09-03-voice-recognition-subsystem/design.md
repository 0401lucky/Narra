# 语音识别子系统 — 技术设计

## 1. 边界与分层

```
UI 层
 ├─ ui/component/voice/VoiceInputButton.kt        麦克风按钮（四态 + 波形 + 手势）
 ├─ ui/screen/settings/VoiceRecognitionSettingsScreen.kt      总开关 + 引擎选择
 └─ ui/screen/settings/VoiceRecognitionApiScreen.kt           BaseUrl/Key/模型/语言 + 测试连接

ViewModel 层
 └─ viewmodel/VoiceInputController.kt   录音+识别的状态机（非 ViewModel，是可被 remember 的控制器）

data/repository/asr 层
 ├─ SpeechRecognitionService            接口：transcribe / 可用性
 ├─ SystemSpeechRecognitionService      Android SpeechRecognizer 包装（Flow 出 partial/final）
 ├─ RemoteSpeechRecognitionService      OkHttp multipart -> /audio/transcriptions
 └─ AudioRecorder                       MediaRecorder 采集到 cacheDir，暴露振幅

model 层
 └─ model/VoiceRecognitionSettings.kt   设置数据类 + 引擎枚举 + baseUrl 规范化

存储层
 ├─ data/local/AppSettingsStore.kt      新增 JSON key + SecureKeys.voiceRecognitionApiKey
 └─ model/AppSettings.kt                新增 voiceRecognition 字段

装配
 └─ di/AppGraph.kt                      by lazy 装配两个 Service 与 AudioRecorder 工厂
```

**关键边界**：`VoiceInputButton` 只认 `VoiceInputController` 暴露的 UI 状态，不直接触碰任何 Service 或权限 API。这样朋友圈与角色扮演三处入口共用一套逻辑，未来接普通聊天只是多写一行调用。

## 2. 数据契约

### 2.1 设置模型

```kotlin
enum class VoiceRecognitionEngine(val storageValue: String, val label: String) {
    SYSTEM("system", "系统识别"),
    REMOTE_MODEL("remote_model", "大模型识别");
    companion object { fun fromStorageValue(value: String): VoiceRecognitionEngine }
}

@Immutable
data class VoiceRecognitionSettings(
    val enabled: Boolean = false,
    val engine: VoiceRecognitionEngine = VoiceRecognitionEngine.SYSTEM,
    val apiKey: String = "",
    val baseUrl: String = "",
    val model: String = DEFAULT_TRANSCRIPTION_MODEL,   // "whisper-1"
    val language: String = "zh",
) {
    fun normalized(): VoiceRecognitionSettings
    fun hasUsableCredential(): Boolean   // engine==REMOTE_MODEL 时要求 apiKey + baseUrl 非空
    fun isReadyForInput(): Boolean       // enabled && (engine==SYSTEM || hasUsableCredential())
    fun stripSensitiveFields(): VoiceRecognitionSettings
}
```

`baseUrl` 规范化复用 `system/network/normalizeSecureHttpBaseUrl`，错误文案：
- 空 → 「语音识别 Base URL 为空」
- 无 scheme → 「语音识别 Base URL 必须以 http:// 或 https:// 开头」
- 非 https 且非本机 → 「语音识别 Base URL 必须使用 https://，本机调试地址除外」

端点解析函数 `resolveTranscriptionEndpoint(baseUrl)`：若 baseUrl 已以 `/audio/transcriptions` 结尾则原样返回，否则追加 —— 与 `resolveMimoChatCompletionsEndpoint` 同构，避免调用方拼 `/`。

### 2.2 存储

- `AppSettings` 新增 `val voiceRecognition: VoiceRecognitionSettings = VoiceRecognitionSettings()`。
- `AppSettingsStore`：
  - `PreferencesKeys.voiceRecognitionSettingsJson = stringPreferencesKey("voice_recognition_settings_json")`
  - `SecureKeys.voiceRecognitionApiKey`（新增常量）
  - 新增 `voiceRecognitionSettingsType` TypeToken
  - 读取时把 `secureValueStore.getString(SecureKeys.voiceRecognitionApiKey)` 注回 `apiKey`；写入时先 `stripSensitiveFields()` 再序列化 JSON，`apiKey` 单独进 SecureValueStore —— **完全照 `voiceSynthesis` 的既有代码路径**。
  - 新增 `saveVoiceRecognitionSettings(settings)`，并在 `AiSettingsEditor` / `SettingsPersistenceCoordinator` / `SettingsViewModel` 各补一层直通方法（沿用语音合成那条链的形状）。

### 2.3 识别服务契约

```kotlin
sealed interface RecognitionEvent {
    data class Partial(val text: String) : RecognitionEvent      // 仅系统通道会发
    data class Final(val text: String) : RecognitionEvent
    data class Failed(val message: String) : RecognitionEvent    // message 已是中文
}

interface SpeechRecognitionService {
    fun isAvailable(): Boolean
    /** 系统通道：内部自行采音，忽略 audioFile。远程通道：读取 audioFile 上传。 */
    fun recognize(audioFile: File?, settings: VoiceRecognitionSettings): Flow<RecognitionEvent>
}
```

两个实现共用这一个接口，`VoiceInputController` 按 `settings.engine` 选择注入哪一个 —— 引擎差异不外泄到 UI。

## 3. 关键数据流

### 3.1 系统识别（无需落盘）

```
按下 → 权限检查 → SystemSpeechRecognitionService.recognize(null, settings)
     → SpeechRecognizer.startListening(RecognizerIntent)
     → onPartialResults → Partial(text) → 输入框实时预览（灰字）
     → onResults       → Final(text)   → 追加进输入框
     → onError         → Failed(中文映射)
松手 → recognizer.stopListening()；上滑取消 → recognizer.cancel() 且不落文本
```

`SpeechRecognizer` 是**主线程 API**：必须在 `Dispatchers.Main` 上创建与调用，Flow 用 `callbackFlow` + `awaitClose { recognizer.destroy() }` 包装。`onError` 的 code 需映射为中文：`ERROR_NO_MATCH` → 「没听清，再说一次？」、`ERROR_NETWORK` → 「网络不可用」、`ERROR_INSUFFICIENT_PERMISSIONS` → 「未获得录音权限」等。

### 3.2 大模型识别（先落盘再上传）

```
按下 → 权限检查 → AudioRecorder.start(cacheDir/asr_<uuid>.m4a)
     → 录音中每 100ms 读 maxAmplitude 驱动波形
松手 → AudioRecorder.stop() → File
     → RemoteSpeechRecognitionService.recognize(file, settings)
        multipart: file / model / language / response_format=json
        Header: Authorization: Bearer <apiKey>
     → 解析 {"text": "..."} → Final(text)
finally → file.delete()（成功失败都删）
```

`MediaRecorder` 配置：`AudioSource.MIC` + `OutputFormat.MPEG_4` + `AudioEncoder.AAC` + 16kHz 采样 + 单声道 —— 转写模型的通行输入规格，且体积远小于 WAV。

单次录音上限 **60 秒**，到点自动 stop 并提示「单次录音最长 60 秒，已自动结束」；最短 **0.5 秒**，过短直接丢弃并提示「说话时间太短」。

HTTP 客户端沿用 `MimoTtsClient` 的形态：独立 `OkHttpClient`（connect 15s / read 60s）+ `suspendCancellableCoroutine` + `Call.cancel()` 响应协程取消；日志经 `SensitiveTextRedactor`，**绝不打印 apiKey 与音频内容**。

### 3.3 状态机

`VoiceInputController` 暴露：

```kotlin
data class VoiceInputUiState(
    val visible: Boolean = false,         // = settings.enabled
    val enabled: Boolean = false,         // = settings.isReadyForInput()
    val phase: Phase = Phase.Idle,        // Idle / Recording / Transcribing / Failed
    val amplitude: Float = 0f,            // 0f..1f 驱动波形
    val partialText: String = "",
    val elapsedMillis: Long = 0L,
    val message: String = "",             // 中文提示/错误
)
```

`Failed` 态保留最后一次录音结果不可重放（音频已删），重试 = 重新录音。这是有意的取舍：留存音频以支持重试会带来隐私与清理负担，收益不足。

## 4. 权限与清单

`AndroidManifest.xml`：

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />

<queries>
    <intent>
        <action android:name="android.speech.RecognitionService" />
    </intent>
</queries>
```

`<queries>` 是 Android 11+ 包可见性要求，缺了 `SpeechRecognizer.isRecognitionAvailable()` 会在部分机型误报 false —— 这是系统识别通道最容易踩的坑。

权限申请用 `rememberLauncherForActivityResult(RequestPermission())` 内联在 `VoiceInputButton` 里；`shouldShowRequestPermissionRationale` 为 false 且已拒绝 → 判定为永久拒绝，提示「录音权限已被拒绝，请到系统设置中开启」。

## 5. UI 设计

### 5.1 麦克风按钮

- 空闲：`Icons.Default.Mic`，`MaterialTheme.colorScheme.onSurfaceVariant`，与发送按钮同尺寸对齐。
- 录音中：按钮放大 1.1 倍（`animateFloatAsState`），外圈 `Canvas` 画对称振幅条（8 根，高度由 `amplitude` 平滑插值驱动，`spring` 动画），中央显示秒数；上方浮出提示「松手发送 · 上滑取消」。
- 上滑越过阈值（-80.dp）：提示切为「松手取消」，配色转 `error`。
- 识别中：按钮换 `CircularProgressIndicator`，输入框上方显示「正在识别…」。
- 失败：`Icons.Default.ErrorOutline` + `error` 色，点击回到空闲。

不可用（`enabled=false`）时按钮半透明，点击弹提示：引擎为大模型但缺凭据 → 「请先在设置 → 语音识别 → 语音识别 API 中填写 Key 与 Base URL」。

### 5.2 设置页

`VoiceRecognitionSettingsScreen` 结构（复用 `SettingsGroup` / `SettingsListRow` / `SettingsSwitchRow` 等既有组件，与语音合成页同款间距与卡片）：

1. 顶部说明卡：一句话讲清两条通道的差异（系统识别免配置但中文长句一般；大模型识别质量好但需联网与 Key）。
2. 总开关行。
3. 引擎分段选择（`SingleChoiceSegmentedButtonRow`），选中 `系统识别` 时下方追加一行设备可用性状态（可用/不可用+原因）。
4. 「语音识别 API」跳转行，副标题动态显示配置状态：未配置 / 已配置（`模型 · 语言`）。

`VoiceRecognitionApiScreen`：BaseUrl、API Key（`PasswordVisualTransformation` + 明文切换眼睛图标）、模型、语言四个 `OutlinedTextField` + 「测试连接」按钮。测试连接录 1 秒静音上传成本高且语义怪 —— 改为**只做端点连通性与鉴权校验**：发一个刻意为空的 multipart 请求，凭 HTTP 状态码区分「鉴权失败(401/403)」/「地址错误(404)」/「已连通(400/422，说明端点存在且鉴权通过)」，其余按网络错误处理。这个取舍要在按钮下方用小字说明。

## 6. 测试策略

| 层 | 手段 |
|---|---|
| `VoiceRecognitionSettings` | 纯单测：`normalized()` 裁剪、`hasUsableCredential()` 分支、baseUrl 三类非法输入、`stripSensitiveFields()` |
| `resolveTranscriptionEndpoint` | 纯单测：已带路径 / 未带路径 / 尾部多余 `/` |
| `RemoteSpeechRecognitionService` | MockWebServer：200+text、200 缺 text 字段、401、500、超时 |
| `AppSettingsStore` | Fake `SettingsSecureValueStore`：apiKey 走加密通道、JSON 中不含 apiKey、往返一致 |
| `VoiceInputController` | `kotlinx-coroutines-test` + Fake Service：Idle→Recording→Transcribing→Idle 正常流；取消不产出文本；过短录音被拒；失败落 Failed 态 |
| 系统通道 / 权限 / 波形 | 真机手验（`SpeechRecognizer` 无法在 JVM 单测中可靠伪造） |

## 7. 兼容性与回滚

- 全部新增字段都有默认值，`enabled` 默认 **false** —— 老用户升级后行为零变化，功能需主动开启。
- DataStore 读不到新 key 时走默认构造，无迁移逻辑。
- 不触碰 Room，因此没有 schema 回滚风险。
- 回滚点：功能全部收敛在新增文件 + 少量既有文件的加法式改动（Manifest 两处、`AppSettings` 一个字段、Store 一条读写链、三个输入框各一个按钮、设置页一个入口）。真要撤，删新增文件 + 回滚这几处即可，不留残骸。
