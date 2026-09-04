# 语音识别子系统

## Goal

为应用新增语音输入能力：用户按住按钮说话，语音被转写成文本填入输入框。支持两条通道 —— Android 系统识别（离线免配置）与大模型转写 API（OpenAI 兼容 `/audio/transcriptions`）；并在设置中提供开关、引擎选择与独立的「语音识别 API」配置页。

## Background

项目已有完全对称的**语音合成（TTS）**子系统可直接参照：

- 模型：`model/VoiceSynthesisSettings.kt`（含 `enabled` / `apiKey` / `baseUrl` / `normalized()` / `hasUsableCredential()` / `stripSensitiveFields()`）
- 存储：`AppSettingsStore` 以 `voiceSynthesisSettingsType` 序列化 JSON，`apiKey` 单独走 `SecureKeys.voiceSynthesisApiKey` 加密
- 网络：`data/repository/tts/MimoTtsClient.kt`，裸 OkHttp + `suspendCancellableCoroutine`，日志经 `SensitiveTextRedactor`
- 设置页：`ui/screen/settings/VoiceSynthesisSettingsScreen.kt`，路由 `AppRoutes.SETTINGS_VOICE_SYNTHESIS`
- 音频落盘：`data/repository/AudioFileStorage.kt`

语音识别按同一骨架建设，风险集中在**录音采集**与**运行时权限**（这两项应用目前完全没有：`AndroidManifest.xml` 未声明 `RECORD_AUDIO`）。

## Requirements

### 设置与配置

1. 新增「语音识别」设置页，视觉与信息层级对齐 `VoiceSynthesisSettingsScreen`：
   - 总开关 `enabled`（关闭时所有输入框不显示麦克风按钮）；
   - 引擎选择（分段控件）：`系统识别` / `大模型识别`；
   - 「语音识别 API」二级页入口。
2. 新增「语音识别 API」二级页，配置项：`baseUrl`、`apiKey`、`model`、`language`；提供「测试连接」按钮给出成功/失败的中文反馈。
3. `apiKey` 必须走 `SecureValueStore` 加密存储，不得写进 DataStore 明文；日志输出经 `SensitiveTextRedactor`。
4. 设置首页新增入口卡片，紧邻既有「语音合成」入口。

### 识别能力

5. `系统识别` 通道基于 Android `SpeechRecognizer`，支持中间结果（partial）实时回显。
6. `大模型识别` 通道：录音成文件后 multipart 上传至 `{baseUrl}/audio/transcriptions`（OpenAI 兼容），字段 `file` / `model` / `language` / `response_format=json`，解析 `text` 字段。
7. 设备不支持系统识别（`SpeechRecognizer.isRecognitionAvailable()` 为 false）时给出中文提示并建议切换到大模型通道。
8. 选择 `大模型识别` 但凭据不全时，麦克风按钮置为不可用并提示去配置 API。

### 输入入口

9. 麦克风按钮接入以下输入框（本次范围）：
   - 角色扮演输入框 `ui/component/roleplay/RoleplayInputBarComponents.kt:87` 的 `RoleplayInputBar`；
   - 朋友圈发布框 `MomentComposerPage` 与朋友圈评论输入框。
10. 识别结果**追加**到输入框已有文本末尾，不覆盖用户已输入内容；识别完成后不自动发送。
11. 录音交互为「按住说话，松手识别」，并提供取消手势（上滑取消，参照微信心智模型）。

### 权限与状态

12. `AndroidManifest.xml` 声明 `RECORD_AUDIO`；Android 11+ 需声明 `<queries>` 中的 `android.speech.RecognitionService` intent，否则系统识别在部分机型不可用。
13. 首次使用时内联申请录音权限；被永久拒绝时给出引导去系统设置的中文提示。
14. 按钮需呈现四个状态：空闲 / 录音中（实时振幅波形）/ 识别中（加载态）/ 失败（可点击重试）。

## Constraints

- **不改 Room schema、不升数据库版本**：所有配置进 DataStore + SecureValueStore。
- 依赖新增须先登记到 `gradle/libs.versions.toml`；优先使用平台 API（`MediaRecorder` / `SpeechRecognizer`），不引入第三方录音库。
- Gson 一律用 `AppJson.gson`；Compose 订阅一律 `collectAsStateWithLifecycle()`。
- 依赖装配只在 `di/AppGraph.kt` 内以 `by lazy` 完成，不在 ViewModel/Composable 里 new。
- 录音临时文件写 `cacheDir`，识别结束后立即删除，避免占用与隐私残留。
- 全部用户可见文案为简体中文。

## Acceptance Criteria

- [ ] 设置 → 语音识别页可开关、可切换引擎、可进入 API 二级页；配置项重启应用后仍在。
- [ ] `apiKey` 在 DataStore 中不可见（以 `stripSensitiveFields()` 语义验证），单测覆盖加密读写往返。
- [ ] `VoiceRecognitionSettings.normalized()` / `hasUsableCredential()` 单测覆盖：空白裁剪、非法 baseUrl 拒绝、引擎为 SYSTEM 时不要求凭据。
- [ ] 大模型通道以 MockWebServer 单测覆盖：成功返回 `text`、HTTP 4xx/5xx 错误、超时、响应体缺 `text` 字段四种路径，错误信息为中文。
- [ ] 总开关关闭时，角色扮演与朋友圈输入框均不出现麦克风按钮。
- [ ] 真机验证：系统识别与大模型识别两条通道都能把中文语音写入角色扮演输入框，且结果追加而非覆盖既有文本。
- [ ] 真机验证：拒绝录音权限后有中文提示、不崩溃；上滑取消不产生任何文本。
- [ ] `./gradlew.bat app:compileDebugKotlin`、`./gradlew.bat app:testDebugUnitTest`、`./gradlew.bat app:lint` 通过。
- [ ] `ChatDatabase.CURRENT_VERSION` 未变化。

## Out of Scope

- 普通聊天（Chat）页输入框的语音入口 —— 用户明确本次只要角色扮演与朋友圈两处，后续再铺开。
- 流式实时转写（边说边出最终文本）、说话人分离、语音指令控制。
- 语音识别结果的自动发送、自动翻译。
- 长录音分片上传（本次限制单次录音时长上限，超时自动停止）。
