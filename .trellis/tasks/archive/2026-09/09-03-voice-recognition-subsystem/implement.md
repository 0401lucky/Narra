# 语音识别子系统 — 执行计划

分 5 个阶段，每阶段结束都能编译通过、可独立回滚。上下文阅读顺序：`implement.jsonl` → `prd.md` → `design.md` → 本文件。

---

## 阶段 1：模型与存储（无 UI）

- [ ] 新建 `model/VoiceRecognitionSettings.kt`：`VoiceRecognitionEngine` 枚举、`VoiceRecognitionSettings` 数据类、`normalized()` / `hasUsableCredential()` / `isReadyForInput()` / `stripSensitiveFields()`、`normalizeVoiceRecognitionBaseUrl()`、`requireSecureVoiceRecognitionBaseUrl()`、`resolveTranscriptionEndpoint()`、`DEFAULT_TRANSCRIPTION_MODEL = "whisper-1"`。
- [ ] `model/AppSettings.kt` 新增 `voiceRecognition` 字段（默认值构造）。
- [ ] `data/local/AppSettingsStore.kt`：
  - `PreferencesKeys.voiceRecognitionSettingsJson`
  - `SecureKeys.voiceRecognitionApiKey`
  - `voiceRecognitionSettingsType` TypeToken
  - 读取路径注回加密 apiKey（照 `voiceApiKey` 的位置补一行）
  - `saveVoiceRecognitionSettings(settings)`：JSON 前先 `stripSensitiveFields()`
- [ ] `AiSettingsEditor` / `SettingsPersistenceCoordinator` / `SettingsViewModel` 各补直通方法（照 `saveVoiceSynthesisSettings` 那条链找）。
- [ ] 单测 `VoiceRecognitionSettingsTest`、`AppSettingsStoreVoiceRecognitionTest`。

**验证**：`./gradlew.bat app:testDebugUnitTest --tests "*VoiceRecognition*"`

**审查点 A**：确认 apiKey 未出现在 DataStore JSON 中（单测断言，不靠肉眼）。

---

## 阶段 2：识别服务与录音

- [ ] `AndroidManifest.xml` 加 `RECORD_AUDIO` 与 `<queries>` 的 `android.speech.RecognitionService`。
- [ ] 新建 `data/repository/asr/SpeechRecognitionService.kt`：`RecognitionEvent` sealed interface + `SpeechRecognitionService` 接口。
- [ ] 新建 `data/repository/asr/AudioRecorder.kt`：`MediaRecorder`（MIC/MPEG_4/AAC/16kHz/单声道）写 `cacheDir`，暴露 `start()` / `stop(): File` / `cancel()` / `maxAmplitude()`；60 秒硬上限、0.5 秒下限常量。
- [ ] 新建 `data/repository/asr/SystemSpeechRecognitionService.kt`：`callbackFlow` 包 `SpeechRecognizer`，主线程创建，`awaitClose { destroy() }`，error code → 中文映射表。
- [ ] 新建 `data/repository/asr/RemoteSpeechRecognitionService.kt`：独立 OkHttpClient（connect 15s/read 60s）、multipart（`file`/`model`/`language`/`response_format`）、`Bearer` 头、`AppJson.gson` 解析 `text`、`SensitiveTextRedactor` 日志、`suspendCancellableCoroutine` + `Call.cancel()`。
- [ ] `di/AppGraph.kt` 以 `by lazy` 装配两个 Service 与 `AudioRecorder` 工厂。
- [ ] 单测 `RemoteSpeechRecognitionServiceTest`（MockWebServer 五条路径：200+text / 200 缺 text / 401 / 500 / 超时）。

**验证**：`./gradlew.bat app:testDebugUnitTest --tests "*SpeechRecognition*"` + `app:compileDebugKotlin`

**审查点 B**：确认错误信息全为中文、apiKey 不进日志、协程取消能真正中断 HTTP 请求。

---

## 阶段 3：控制器与麦克风按钮

- [ ] 新建 `viewmodel/VoiceInputController.kt`：`VoiceInputUiState`（`visible`/`enabled`/`phase`/`amplitude`/`partialText`/`elapsedMillis`/`message`）+ 状态机（`startRecording` / `stopAndTranscribe` / `cancel` / `dismissMessage`），按 `settings.engine` 选服务，`finally` 删临时文件。
- [ ] 新建 `ui/component/voice/VoiceInputButton.kt`：四态外观、`Canvas` 8 根振幅条（`spring` 平滑）、按住/松手/上滑取消手势（`pointerInput` + `detectDragGestures`）、录音秒数、内联权限申请与永久拒绝引导。
- [ ] 单测 `VoiceInputControllerTest`（Fake Service + `kotlinx-coroutines-test`）：正常流、取消不产出文本、过短被拒、失败落 Failed。

**验证**：`./gradlew.bat app:testDebugUnitTest --tests "*VoiceInputController*"`

**审查点 C**：控制器不直接引用任何 Android UI 类型，按钮不直接引用任何 Service —— 边界不能糊。

---

## 阶段 4：设置页与路由

- [ ] `AppRoutes.kt` 加 `SETTINGS_VOICE_RECOGNITION = "settings/voice-recognition"` 与 `SETTINGS_VOICE_RECOGNITION_API = "settings/voice-recognition/api"`。
- [ ] 新建 `ui/screen/settings/VoiceRecognitionSettingsScreen.kt`：说明卡 + 总开关 + 引擎分段选择（含设备可用性状态行）+ API 跳转行（副标题动态显示配置状态）。
- [ ] 新建 `ui/screen/settings/VoiceRecognitionApiScreen.kt`：BaseUrl / API Key（可见性切换）/ 模型 / 语言 + 「测试连接」（按 design.md §5.2 的状态码判定策略，按钮下方小字说明其局限）。
- [ ] `SettingsNavGraph.kt` 注册两条路由。
- [ ] `SettingsScreen.kt` 在「语音合成」入口旁加「语音识别」卡片。

**验证**：`./gradlew.bat app:compileDebugKotlin` + `app:installDebug` 手动走一遍设置页并重启验证持久化

**审查点 D**：视觉与语音合成页对齐（间距/卡片/字号），文案全中文。

---

## 阶段 5：接入三处输入框

- [ ] `ui/component/roleplay/RoleplayInputBarComponents.kt` 的 `RoleplayInputBar` 接入按钮，识别结果**追加**到现有文本末尾。
- [ ] `ui/screen/moments/MomentsScreen.kt` 的 `MomentComposerPage` 接入。
- [ ] 朋友圈评论输入框接入（在 `MomentsDetailContent` / `MomentEngagementBlock` 内定位实际输入组件）。
- [ ] 三处都通过 `settings.voiceRecognition` 控制按钮可见性与可用性。

**验证**：
```
./gradlew.bat app:compileDebugKotlin
./gradlew.bat app:testDebugUnitTest
./gradlew.bat app:lint
./gradlew.bat app:installDebug
```
真机走完 prd.md 的全部手验项。

**审查点 E（最终全量）**：`trellis-check` 全量检查 + 确认 `ChatDatabase.CURRENT_VERSION` 未变。

---

## 回滚点

| 阶段 | 回滚方式 |
|---|---|
| 1 | 删 `VoiceRecognitionSettings.kt`，回滚 `AppSettings` 一个字段与 Store 一条读写链 |
| 2 | 删 `data/repository/asr/` 整个目录，回滚 Manifest 两处与 AppGraph 装配 |
| 3 | 删 `VoiceInputController.kt` 与 `VoiceInputButton.kt` |
| 4 | 删两个设置页，回滚 `AppRoutes` / `SettingsNavGraph` / `SettingsScreen` |
| 5 | 回滚三处输入框的加法式改动 |

每阶段都是加法式改动，`git revert` 单个 commit 即可，不存在需要数据迁移的回滚。

## 已知风险

1. **`SpeechRecognizer` 机型差异**：部分国产 ROM 无内置识别服务或需要联网 Google 服务。缓解：`isAvailable()` 明确置灰并引导切换到大模型通道，不做静默降级（静默降级会让用户以为在用本地识别，实际把音频传了出去 —— 这是隐私问题，必须显式）。
2. **`MediaRecorder` 被其他音频占用**：TTS 播放中开录音可能失败。缓解：录音前若 `RoleplayVoicePlaybackController` 正在播放则先停止播放。
3. **转写 API 兼容性**：各家对 `language` / `response_format` 支持不一。缓解：`language` 留空时不发该字段；响应解析对缺 `text` 给明确中文错误而非崩溃。
4. **手势冲突**：输入框所在区域可能已有滑动手势。缓解：手势只绑在按钮自身而非整行。
