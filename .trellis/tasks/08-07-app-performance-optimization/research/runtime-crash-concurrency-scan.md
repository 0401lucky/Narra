# Research: 运行时崩溃、并发与资源风险扫描

- Query: 扫描应用业务层、数据层和 Android 系统组件中的未捕获异常、协程取消误处理、并发覆盖、主线程阻塞、IO/内存峰值与 Room 一致性风险；为 2.2.6-dev 性能专项提供只读证据和后续验证优先级。
- Scope: mixed
- Date: 2026-08-11

## Findings

### 结论摘要

- 代码级确认了两组可到达默认未捕获异常处理器的根协程路径：应用启动任务，以及悬浮翻译服务/无障碍服务的根收集与重译任务。后者当前被 Manifest 禁用，因此是“功能一旦启用即暴露”的潜伏风险；前者会在正常应用启动时执行。
- 代码级确认了翻译取消竞态、悬浮翻译旧流式帧残留、四处宽泛捕获误吞 `CancellationException`、跨表删除非原子，以及“编辑旧快照”与“发送新消息”并发覆盖等稳定性风险。
- 主线程同步 `DownloadManager.query()`、音频样本 `readBytes()+Base64`、设置流重复 Keystore/JSON 解析，以及无大小上限的 TTS 响应读取/解码是优先测量热点。
- Room 全表 Flow、四识别器 OCR、屏幕截图多份 Bitmap 同驻属于高价值候选，但当前没有足够运行时数据证明它们是主要瓶颈；本任务不应据此直接引入 Paging、schema 或全局线程模型改造。
- 当前没有这些关键系统组件的专项测试，也未发现 StrictMode、全局协程异常观测、已接入的崩溃上报或内存峰值基准；现有 `AppLogger` 默认仍只写 Logcat。

### 扫描范围与判定口径

- 扫描目录：`viewmodel/`、`data/`、`context/`、`roleplay/`、`system/`、`conversation/`、`phone/`，约 254 个 Kotlin 文件，并回读关键实现、Manifest、Gradle 版本目录和测试。
- 主要模式：独立 `CoroutineScope`、根 `launch`、`SupervisorJob`、宽泛 `Throwable`/`runCatching`、`Job.cancel()`、冷 Flow 重复收集、同步文件/ContentProvider/Binder IO、无界响应读取、Room 全表查询、跨仓储多步写入、Bitmap 生命周期。
- “已确认”表示源代码存在完整的失败或竞态通道，不表示已在真机获得崩溃栈或性能数字。
- “性能热点候选”只说明工作量或内存形态可疑；是否进入本版本优化必须由 Trace、宏基准、故障注入或大数据 fixture 证明。

### Files found

#### 协程、服务与启动

- `app/src/main/java/com/example/myapplication/di/AppGraph.kt`：应用级依赖图与独立启动协程作用域。
- `app/src/main/java/com/example/myapplication/ChatApplication.kt`：在 `onCreate` 期间触发启动任务。
- `app/src/main/java/com/example/myapplication/system/translation/ScreenTranslatorService.kt`：悬浮翻译、截图、OCR、流式翻译与前台服务生命周期。
- `app/src/main/java/com/example/myapplication/system/translation/SelectionAccessibilityService.kt`：选中文本监听及设置流收集。
- `app/src/main/java/com/example/myapplication/viewmodel/TranslationViewModel.kt`：手动翻译任务和 UI 状态。
- `app/src/main/java/com/example/myapplication/system/moments/MomentsAutoGenerationWorker.kt`：WorkManager 自动生成任务。
- `app/src/main/java/com/example/myapplication/data/repository/AppUpdateRepository.kt`：更新检查与缓存回退。
- `app/src/main/java/com/example/myapplication/roleplay/script/AndroidRoleplayScriptEngine.kt`：JavaScript 沙箱执行和脚本禁用策略。
- `app/src/main/java/com/example/myapplication/conversation/VoiceSynthesisCoordinator.kt`：角色语音生成、文件编码与消息状态回写。

#### 设置、网络与内存

- `app/src/main/java/com/example/myapplication/data/local/AppSettingsStore.kt`：DataStore 设置流、JSON 解码与敏感值迁移。
- `app/src/main/java/com/example/myapplication/data/local/SecureValueStore.kt`：SharedPreferences、Android Keystore、AES/GCM 与 Gson。
- `app/src/main/java/com/example/myapplication/data/repository/ai/AiTranslationService.kt`：翻译流累积与网络调用。
- `app/src/main/java/com/example/myapplication/viewmodel/AppUpdateViewModel.kt`：下载状态轮询。
- `app/src/main/java/com/example/myapplication/system/update/AndroidAppUpdateController.kt`：同步 `DownloadManager` 查询。
- `app/src/main/java/com/example/myapplication/viewmodel/SettingsViewModel.kt`：语音样本测试请求构造。
- `app/src/main/java/com/example/myapplication/viewmodel/RoleplayViewModel.kt`：从 Main 作用域启动语音生成。
- `app/src/main/java/com/example/myapplication/data/repository/tts/MimoTtsClient.kt`：TTS HTTP 响应读取与 JSON 解析。
- `app/src/main/java/com/example/myapplication/data/repository/AudioFileStorage.kt`：Base64 音频解码与落盘。
- `app/src/main/java/com/example/myapplication/system/translation/MediaProjectionCaptureEngine.kt`：MediaProjection、ImageReader 与 Bitmap 复制。
- `app/src/main/java/com/example/myapplication/system/translation/MlKitOcrEngine.kt`：四套 ML Kit 文本识别器串行执行。

#### Room 与消息并发

- `app/src/main/java/com/example/myapplication/data/repository/ConversationRepository.kt`：会话、手机快照和观察记录清理。
- `app/src/main/java/com/example/myapplication/data/repository/roleplay/RoleplayRepository.kt`：剧情删除/重启及跨域清理。
- `app/src/main/java/com/example/myapplication/data/local/chat/ConversationDao.kt`：会话和消息全量查询。
- `app/src/main/java/com/example/myapplication/data/local/chat/MessageEntity.kt`：消息正文及多列 JSON 大字段。
- `app/src/main/java/com/example/myapplication/data/local/RoomConversationStore.kt`：Room Entity 到领域模型的全量映射和 JSON 解析。
- `app/src/main/java/com/example/myapplication/data/local/memory/MemoryDao.kt`：记忆、摘要及摘要分段全量查询。
- `app/src/main/java/com/example/myapplication/data/local/phone/PhoneSnapshotEntity.kt`：按会话逻辑关联的手机快照表。
- `app/src/main/java/com/example/myapplication/data/local/phone/PhoneObservationEntity.kt`：按会话逻辑关联的手机观察表。
- `app/src/main/java/com/example/myapplication/data/local/memory/ConversationSummaryEntity.kt`：按会话逻辑关联的摘要表。
- `app/src/main/java/com/example/myapplication/viewmodel/ChatViewModel.kt`：普通聊天编辑、发送和整体快照替换。
- `app/src/main/java/com/example/myapplication/viewmodel/RoleplayScenarioActionSupport.kt`：角色扮演编辑和场景重置编排。

#### 配置、测试与规范

- `app/src/main/AndroidManifest.xml`：翻译 Service/AccessibilityService 的启用状态。
- `gradle/libs.versions.toml`：协程、DataStore、WorkManager、Room、OkHttp、ML Kit 版本。
- `app/src/test/java/com/example/myapplication/viewmodel/TranslationViewModelTest.kt`：手动翻译基础测试。
- `app/src/test/java/com/example/myapplication/viewmodel/AppUpdateViewModelTest.kt`：更新 ViewModel 三条主路径测试。
- `app/src/test/java/com/example/myapplication/conversation/VoiceSynthesisCoordinatorTest.kt`：语音成功/失败等基础测试。
- `app/src/test/java/com/example/myapplication/data/repository/tts/MimoTtsClientTest.kt`：TTS 协议、URL 与错误响应测试。
- `.trellis/spec/backend/error-handling.md`、`ui-state-guidelines.md`、`quality-guidelines.md`、`network-guidelines.md`、`database-guidelines.md`、`conversation-message-mutation-guidelines.md`：本次风险判定使用的项目契约。

### 已确认的崩溃与稳定性风险

#### P0：应用启动根协程没有异常边界

- `AppGraph.kt:101` 创建 `CoroutineScope(SupervisorJob() + Dispatchers.IO)`。
- `AppGraph.kt:401-402` 的 `scheduleStartup` 直接对该作用域调用根 `launch`，没有 `CoroutineExceptionHandler`，也没有每项任务的 `try/catch`。
- `AppGraph.kt:422-437` 启动三条独立任务：敏感数据迁移、Room 内置预设写入、长期设置流收集。
- `ChatApplication.kt:47` 在正常应用启动时调用 `launchStartupTasks()`，不是仅测试或隐藏入口。
- `AppSettingsStore.kt:607-640` 的迁移同时执行 DataStore 事务、Keystore/SharedPreferences 读写和 JSON 解析；`presetRepository.ensureBuiltInPresets()` 也可抛 Room/磁盘异常。
- Kotlin 官方说明：根 `launch` 的异常按未捕获异常处理；`SupervisorJob` 只隔离子任务失败，不负责消费异常。因此任一启动任务异常都有进入线程默认未捕获处理器并结束 Android 进程的代码通道。
- 建议等级：当前可达，优先级 P0。后续实现应给每项启动任务设置明确的失败策略、脱敏日志和可恢复性；不能只添加 `SupervisorJob` 或只记录全局异常后继续假定初始化成功。

#### P0（功能启用前必须处理）：悬浮翻译根协程可因网络或 DataStore 异常终止进程

- `ScreenTranslatorService.kt:39` 使用 `CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)`。
- `ScreenTranslatorService.kt:50-65`、`:73-84`、`:101-111`、`:122-135` 均从该作用域直接启动根协程，未设置统一异常边界。
- `ScreenTranslatorService.kt:73-78` 在同一根任务内先持久化语言，再调用 `retranslateLast()`；`:81-84` 也直接调用重译。
- `ScreenTranslatorService.kt:380-393` 的截图重译执行网络翻译，没有本地 `try/catch`；断网、协议异常、配置错误均可向根 `launch` 冒泡。
- `ScreenTranslatorService.kt:455-459` 的设置持久化最终调用 DataStore 写入；`AppSettingsStore.kt:455-467` 没有把写入 `IOException` 转成可恢复结果。
- `ScreenTranslatorService.kt:101-111` 和 `SelectionAccessibilityService.kt:26-42` 都在 Main 根协程中直接收集 `settingsFlow`；`AppSettingsStore.kt:156-302` 没有 `.catch`，DataStore 官方契约允许磁盘读取异常从 `data` Flow 抛出。
- 结论：网络重译、设置读写和设置流读取均存在完整的未捕获异常通道。`SupervisorJob` 不会把这些异常变成普通失败。
- 当前暴露面：`AndroidManifest.xml:43-55` 将两个组件都声明为 `android:enabled="false"`；全仓未发现 `PackageManager.setComponentEnabledSetting`，也未发现外部调用 `ScreenTranslatorService.startService()`。因此当前产品入口不可达，但一旦后续启用组件，该风险立即变为用户可触发 P0。

#### P1：手动翻译取消旧任务时会污染新任务状态

- `TranslationViewModel.kt:191-192` 取消旧 `translationJob` 后立即在同一 `viewModelScope` 启动新任务。
- `TranslationViewModel.kt:200-238` 使用 `runCatching` 包住挂起翻译，并在 `onFailure` 中无条件写入 `isTranslating=false` 和错误文案。
- `runCatching` 会捕获 `CancellationException`。如果旧任务在新任务已经写入 loading 后恢复取消，旧任务会关闭新任务的 loading，并可能显示取消异常或“翻译失败”。旧任务成功返回的极端竞态也没有 generation id 防止覆盖新结果。
- `TranslationViewModelTest.kt:28-149` 只覆盖语言切换、成功保存历史、历史回填和未配置模型；没有“快速连续翻译”“取消后启动新翻译”或 stale result 测试。
- 建议等级：P1。需要区分取消与失败，并使用任务身份/结构化所有权保证只有当前请求可以写全局状态。

#### P1：悬浮翻译流式 UI 更新脱离父任务，旧译文可覆盖新译文

- `ScreenTranslatorService.kt:321-322` 会取消旧 `translationJob` 并启动新任务。
- `ScreenTranslatorService.kt:336-354` 在 IO 上收集流，但每次增量都通过独立 `serviceScope.launch` 投递 UI。它们是 `translationJob` 的兄弟任务，不会随旧翻译任务取消。
- `AiTranslationService.kt:142-153` 对每个 delta 都把完整 `StringBuilder` 转为新字符串并发出；累计文本越长，复制总量趋近 O(n²)。随后每帧又创建一个 Main 任务。
- 确认后果：快速重译/切换文本时，旧任务已排队的 UI 帧仍可执行，覆盖新任务的 loading 或新译文；高 token 频率还会放大 Main 队列和字符串分配。
- 建议等级：P1。后续应让 UI 更新属于当前翻译 Job，或带 run id 丢弃旧帧，并合并/节流显示帧；不能继续为每个 token 创建独立兄弟协程。

#### P1：四处宽泛捕获把协程取消转成普通业务结果

- `MomentsAutoGenerationWorker.kt:22-30` 使用 `runCatching` 捕获整个 `CoroutineWorker.doWork()`，任何取消都被转换为 `Result.retry()`。WorkManager 官方说明停止 `CoroutineWorker` 时会取消协程并传播取消信号；应用不应把该信号当普通重试失败。
- `AppUpdateRepository.kt:54-77` 捕获所有 `Throwable`，将取消转换为缓存结果或 `UNKNOWN` 检查结果，调用方可能在已取消页面继续应用旧检查状态。
- `AndroidRoleplayScriptEngine.kt:71-80` 捕获所有 `Throwable`，取消会被记录成脚本错误、加入禁用集合并继续当前编排，而不是立即结束父任务。
- `VoiceSynthesisCoordinator.kt:105-147` 用 `runCatching/getOrElse` 捕获取消，先记录为 TTS 失败，再尝试把消息 part 写成失败；后续挂起点可能再次抛取消，但错误日志和部分状态副作用已经发生。
- 这些实现与 `.trellis/spec/backend/error-handling.md:12,32-48,87`、`ui-state-guidelines.md:87-88` 明确冲突。
- 建议等级：P1。所有宽泛捕获必须先重抛 `CancellationException`；WorkManager 停止场景、页面离开、切换会话和主动取消都要有专项测试。

#### P1：跨表删除和剧情重启不是原子操作

- `ConversationRepository.kt:323-340` 依次删除会话、手机快照和手机观察；中途失败不会回滚已经完成的步骤。
- `PhoneSnapshotEntity.kt:6-18`、`PhoneObservationEntity.kt:7-24`、`ConversationSummaryEntity.kt:7-19` 没有指向会话表的 FK/CASCADE，仅依赖 Repository 手工清理。
- `RoleplayRepository.kt:241-256` 删除剧情时依次清理会话、在线元数据、日记、邮箱、经济数据、场景和图片文件。
- `RoleplayRepository.kt:328-354` 重启剧情时先删除旧会话和多域数据，再创建新会话/开场消息，最后写新 Session；任一步失败都可能留下“旧数据已删、新 Session 未建”或部分孤儿数据。
- 上述流程没有调用 `database.withTransaction`。这与 `.trellis/spec/backend/database-guidelines.md:48-56` 的跨表原子清理契约不一致。
- 建议等级：P1。数据库范围内先建立单一事务边界并做故障注入回滚测试；文件删除属于事务外副作用，应在提交后执行并记录可重试清理，而不是假定能随 Room 回滚。

#### P1：编辑旧消息与发送新消息可以并发整体覆盖

- `ChatViewModel.kt:607-638` 在函数入口基于当前内存消息准备 `rewoundMessages`，随后异步调用 `replaceConversationSnapshot()`；编辑期间没有 `isEditing` 门禁，也没有与发送共用互斥锁。
- `ChatViewModel.kt:415-435,1701-1785` 的发送路径可以在编辑协程未落库时从旧状态构造新 round trip，并立即更新 UI；随后编辑任务仍可能用旧快照整体替换数据库和 UI。
- `RoleplayScenarioActionSupport.kt:171-208` 同样在普通 `scope.launch` 中读取内存 Flow、构造旧快照并整体替换；入口只检查当时的 `state.isSending`，没有阻止编辑启动后紧接着发送。
- 确认后果：快速“编辑 → 发送”或双击编辑可产生 last-writer-wins，导致刚发送消息丢失、摘要与消息不一致，或 UI/Room 短暂分叉。
- 建议等级：P1。编辑、删除、重试、发送应共享会话级 mutation 串行化契约；至少用可控 dispatcher + fake repository 测试两种交错顺序。

#### P1：设置 Flow 存在未捕获 DataStore 读取异常通道

- `AppSettingsStore.kt:156-302` 直接 `context.dataStore.data.map`，未在 `map` 前处理 `IOException`。
- Android DataStore 官方 API 明确说明 `data` Flow 在磁盘读取失败时会抛 `IOException`，官方示例使用 `.catch { if (it is IOException) emit(emptyPreferences()) else throw it }`。
- 该 Flow 被多个 `stateIn`/`collect` 根任务长期收集，例如 `ChatViewModel.kt:155`、`SettingsViewModel.kt:141,161`、`AppRoot.kt:51`、`ScreenTranslatorService.kt:101-111`、`SelectionAccessibilityService.kt:37-42`、`AppGraph.kt:429-437`。全仓共有 49 处文本引用。
- 判断：DataStore 读取异常到根 collector 的传播通道在代码层成立。实际文件损坏/IO 故障发生率和各 scope 的最终崩溃栈尚未通过故障注入验证，但不应把它仅视为性能问题。

### 性能热点候选

#### Main：下载状态每秒同步查询 ContentProvider/Binder

- `AppUpdateViewModel.kt:232-254` 在默认 `viewModelScope` 上每秒调用 `queryDownload()`。
- `AndroidAppUpdateController.kt:53-76` 同步执行 `DownloadManager.query()`、游标移动和列读取，没有 `withContext(Dispatchers.IO)`。
- 这属于确定的 Main 同步平台调用；单次耗时和是否造成可见卡顿需用主线程 Trace/StrictMode 测量。
- 同一路径没有局部异常边界；平台查询若抛异常，会从 `viewModelScope.launch` 冒泡。`AppUpdateViewModelTest.kt:30-112` 的三个测试未覆盖查询异常、并发监控、取消或安装 Intent 异常。

#### Main：声音克隆样本先全量读入并 Base64，再检查上限

- `SettingsViewModel.kt:640-663` 在 `viewModelScope` 中先构造测试请求；`:943-970` 使用 `file.readBytes()` 和 `Base64.encodeToString()`，完成分配后才检查 10 MB Base64 上限。
- `VoiceSynthesisCoordinator.kt:155-173,202-231` 在调用 `MimoTtsClient.synthesize()` 前执行同样的文件读取和编码。
- `RoleplayViewModel.kt:273-278` 从 Main 启动 Coordinator；真正进入 IO 的位置在 `MimoTtsClient.kt:130-149`，因此前置文件读/编码仍继承 Main。
- `VoiceCloneSampleStorage.kt:21-24` 的上限约等于 7.5 MiB 原始数据，但上述两条路径在分配完整 `ByteArray`、完整 Base64 字符串后才判断；异常路径或外部篡改路径还可能传入更大文件。
- 候选影响：主线程停顿、瞬时同时持有原始字节和约 4/3 大小字符串、低内存设备 OOM。

#### Main/CPU：每个设置 collector 重复 Keystore 解密和 JSON 解码

- `AppSettingsStore.kt:156-302` 是未共享的冷 Flow transform；每次 collector 收到设置快照都会执行多次敏感值读取、Provider/Search/Voice/Assistant/History/Moments JSON 解码和完整 `AppSettings` 构造。
- `SecureValueStore.kt:27-116` 的读取链包含 SharedPreferences、Base64、Android Keystore、AES/GCM 和 Gson；虽然解密异常被 `runCatching` 吞为缺省值，但 CPU/Keystore 调用仍实际发生。
- `settingsFlow` 自身没有 `flowOn`、`shareIn` 或进程级 `stateIn`。多个 ViewModel 各自 `stateIn`，另有 Compose/Service 直接收集和大量 `first()`，会重复执行 transform。
- 代码可以确认重复计算结构；具体有多少 collector 同时活跃、transform 是否落在 Main、Keystore/JSON 各占多少时间必须用 Trace/计数器验证后再决定共享缓存或 dispatcher 策略。

#### 内存/OOM：TTS 响应和 Base64 音频没有输入大小门禁

- `MimoTtsClient.kt:158-176` 无条件调用 `response.body.string()`，先把完整响应载入字符串，再由 Gson 构造包含 Base64 音频的对象；没有 Content-Length 或流式字节上限。
- `AudioFileStorage.kt:91-107` 清理字符串后无上限地解码 Base64，至少同时持有响应字符串、规范化字符串和解码后的 `ByteArray`。
- 远端服务、代理或错误配置可返回异常大的正文；这是外部可控的 OOM 输入面。现有 `MimoTtsClientTest.kt:31-250` 未覆盖超大响应或大小拒绝，`VoiceSynthesisCoordinatorTest.kt:57-250` 未覆盖最大样本/最大音频负载。
- 建议在解析和解码前设置协议级最大字节数，并用略小于/等于/略大于边界的测试验证；上限数值需结合真实语音时长和设备内存确定。

#### 内存：屏幕截图同时持有 ImageReader 缓冲、原始 Bitmap 和裁剪 Bitmap

- `MediaProjectionCaptureEngine.kt:56-68` 按全屏 RGBA_8888 创建 `ImageReader`，`maxImages=3`。
- `MediaProjectionCaptureEngine.kt:122-148` 在持有 Image 的同时创建带 row padding 的 `rawBitmap`，再创建全屏 `croppedBitmap`，成功后才回收 raw；创建裁剪图失败或 continuation 已取消时存在额外回收压力。
- `ScreenTranslatorService.kt:240-246` 只在 OCR 成功返回后回收截图 Bitmap；OCR 抛异常或取消时没有 `finally` 回收。
- 以 1440×3200 为例，单张 ARGB_8888 约 17.6 MiB，不含 row padding、ImageReader 三缓冲和 ML Kit 内部副本；真实峰值必须按目标设备分辨率测量。

#### CPU/内存：同一全屏图依次运行四个 OCR 识别器

- `MlKitOcrEngine.kt:24-36` 同时持有 Latin、Chinese、Japanese、Korean 四个 `TextRecognizer`，并对同一 `InputImage` 逐个 `Tasks.await`。
- 这可能提高首次模型加载、native 内存和单次 OCR 时延；多语种覆盖收益与成本没有基准数据。
- 组件当前禁用，无法从日常路径获得数据；启用前需在低/中/高内存设备上记录首次与热启动 OCR 时延、PSS/Java/native heap 和失败后的资源回收。

#### Room：消息与记忆 Flow 全量查询、全量映射和多列 JSON 解析

- `ConversationDao.kt:13-29` 的会话/消息观察与列表查询没有 `LIMIT` 或分页。
- `MessageEntity.kt:23-45` 每行包含正文、reasoning、attachments、parts、citations 等多个 JSON/文本大字段。
- `RoomConversationStore.kt:56-58,207-257` 每次消息 Flow 发射都会重新映射整表，并逐行解析 reasoning、附件、parts、citations JSON。
- `MemoryDao.kt:10-14,35-56` 对记忆、摘要和摘要分段也存在多处全表查询。
- 候选影响：长会话单行更新触发 O(n) 读取/对象重建/JSON 解析，老用户数据库可能放大 Compose 重组前的 CPU 和 GC。
- 当前缺少消息规模分布、Flow 发射耗时和分配数据。依据任务 `prd.md:47,66,74` 与 `design.md:92`，本轮不能仅凭静态扫描引入 Paging 或 schema 改造。

### 需要运行时证据后才能进入优化的项目

1. `AppSettingsStore.settingsFlow`：在 AppRoot、Chat、Settings、Roleplay、Translation 同时活跃时记录 transform 次数、线程名、Keystore/JSON 分项耗时；对比单一共享快照前后，但不要先改全局线程模型。
2. Room 长会话：用 100、1,000、5,000 条消息且包含真实大小 parts/reasoning/citations JSON 的 fixture，记录单行更新后的查询、映射、GC 和帧耗时。
3. MediaProjection/OCR：按 720p、1080p、1440p 和目标最高分辨率记录 Java heap、native heap、PSS、截图到 OCR 完成时间；分别测首次四模型加载和热路径。
4. TTS：用受控 MockWebServer 响应测试 1 MiB、8 MiB、16 MiB 级正文，以及 Base64 解码峰值；不能调用真实远端服务。
5. 下载轮询：用 Perfetto/方法 Trace 或 StrictMode 验证每秒 Binder/游标查询在不同系统版本上的 Main 占用和异常行为。
6. 翻译竞态：可控 dispatcher 下构造“旧任务取消回调晚于新任务 loading”“旧流式 UI 帧晚于新任务结果”两种交错，验证 stale write。
7. 编辑/发送一致性：Fake Repository 在 `replaceConversationSnapshot` 前挂起，随后发送新消息，再恢复编辑，确认是否丢失新消息；Room androidTest 使用故障注入验证删除/重启全事务回滚。

### 测试与诊断缺口

- 未找到 `ScreenTranslatorService`、`SelectionAccessibilityService`、`MediaProjectionCaptureEngine`、`MlKitOcrEngine`、`MomentsAutoGenerationWorker`、`AndroidRoleplayScriptEngine` 的单元或设备测试。
- `TranslationViewModelTest.kt` 没有取消、快速连续请求或旧结果覆盖测试。
- `AppUpdateViewModelTest.kt:30,52,91` 只有三条主路径，没有同步查询异常、监控任务取消/重启、并发检查和安装 Intent 异常测试。
- `VoiceSynthesisCoordinatorTest.kt:57-250` 没有取消、最大样本和超大音频结果测试。
- `MimoTtsClientTest.kt:31-250` 没有取消、超大响应和响应大小门禁测试。
- 全仓未发现业务 `Channel` 或 `MutableSharedFlow`；本次没有发现 Channel 背压/关闭类缺陷。高频翻译路径使用普通 Flow 加兄弟 `launch`，风险表现为任务堆积而非 Channel 堵塞。
- Manifest 和业务代码中未发现额外 `BroadcastReceiver`；本次没有发现 receiver 生命周期或导出配置问题。
- 全仓未发现 `StrictMode`、`CoroutineExceptionHandler`、自定义默认未捕获异常处理器或 LeakCanary/JankStats。现有性能模块只有启动宏基准和 Baseline Profile。
- `AppLogger.kt:20-23,80-93` 默认 sink 是 Logcat；注释只说明未来可接 Crashlytics/自建通道，当前没有可用于聚合线上未捕获异常、OOM 前兆或协程失败的上报证据。

### 建议分级

#### P0：先阻断进程级异常通道

- 为 `AppGraph.scheduleStartup` 建立每任务明确的异常/重试/降级边界，并验证迁移、预设写入和 DataStore 读取故障不会终止进程。
- 在任何启用悬浮翻译组件的改动之前，统一收口 Service 根协程异常；网络失败展示可恢复状态，DataStore 失败记录并保持服务状态一致，取消必须原样传播。
- 给 `AppSettingsStore.settingsFlow` 增加 DataStore `IOException` 读取策略及故障注入测试；非 IO 异常仍应抛出，避免掩盖编程错误。

#### P1：修复取消、并发覆盖和资源上限

- 修复 `TranslationViewModel`、Worker、更新、脚本和语音路径的取消语义；增加旧任务不得写新状态的 run id/Job 所有权测试。
- 让悬浮翻译流式 UI 帧属于父 translation Job，并合并更新频率；Bitmap 在 `finally` 回收。
- 为编辑/删除/发送建立会话级串行化；把数据库范围的多表删除/重启纳入同一 Room 事务。
- 把同步文件读取、Base64 编码和 `DownloadManager.query()` 移出 Main；在读取/解析 TTS 正文和解码音频前执行大小门禁。

#### P2：测量后单点优化

- 用已有宏基准框架补长会话、设置打开、翻译/语音 mock 路径及内存观测，再依据最大贡献点优化。
- Room 全表映射、设置共享、OCR 模型组合等只在证据显示为主热点时调整；继续遵守本任务“不改 Paging/schema/网络全局策略”的边界。

### External references

- Kotlin Coroutines 1.10.2（仓库版本见 `gradle/libs.versions.toml:20`）：[Coroutine exceptions handling](https://kotlinlang.org/docs/exception-handling.html)。根 `launch` 异常按未捕获异常处理；supervision 不等于异常消费。
- Kotlin：[Cancellation and timeouts](https://kotlinlang.org/docs/cancellation-and-timeouts.html)。捕获 `CancellationException` 后应重新抛出，取消是正常控制流。
- Android DataStore 1.1.2（仓库版本见 `gradle/libs.versions.toml:13`）：[DataStore API reference](https://developer.android.com/reference/kotlin/androidx/datastore/core/DataStore) 与 [Preferences DataStore codelab](https://developer.android.com/codelabs/android-preferences-datastore)。`data` 读取可抛 `IOException`，官方示例在 `map` 前用 `catch` 处理。
- Android WorkManager 2.11.0（仓库版本见 `gradle/libs.versions.toml:14`）：[Threading in CoroutineWorker](https://developer.android.com/develop/background-work/background-tasks/persistent/threading/coroutineworker)。Worker 停止时框架自动取消协程并传播取消信号。
- Android：[Media projection](https://developer.android.com/media/grow/media-projection)。MediaProjection 通过 VirtualDisplay 向应用提供的 Surface/ImageReader 输出帧，应用负责图像与会话资源管理。
- Android：[Launch a foreground service](https://developer.android.com/develop/background-work/services/fgs/launch)。前台服务必须经允许的入口启动并及时提升为前台；Android 12+ 与 14+ 有启动及类型/权限限制。
- 仓库相关版本：Room 2.8.4、OkHttp 5.3.0、ML Kit Text Recognition 16.0.1，见 `gradle/libs.versions.toml:17-23`。

### Related specs

- `.trellis/spec/backend/error-handling.md:9-12,20-22,32-48,79,87`：数据/领域异常语义、用户动作边界、取消必须重抛、资源清理和已知反模式。
- `.trellis/spec/backend/ui-state-guidelines.md:37,69,87-88`：长任务 Job、状态恢复、异常脱敏和取消不得显示为失败。
- `.trellis/spec/backend/quality-guidelines.md:37-48,67-71,109-121`：协程测试、网络取消、性能基准、重复点击和边界测试。
- `.trellis/spec/backend/network-guidelines.md:53-62,83-84`：流式 IO、Call 取消、`CancellationException` 原样传播和协议测试。
- `.trellis/spec/backend/database-guidelines.md:32,48-56,110`：FK/CASCADE、跨表原子事务和故障边界测试。
- `.trellis/spec/backend/conversation-message-mutation-guidelines.md:56-57,86-110`：消息关联删除必须在单一 Room 事务中完成，异常整体回滚，禁止旧快照覆盖新 run。
- `.trellis/spec/guides/cross-layer-thinking-guide.md:22-34,103-118`：长任务取消、流式状态、旧请求写入新状态及完整数据流验证。
- `.trellis/tasks/08-07-app-performance-optimization/prd.md:47,66,74-81`、`design.md:40,92`：先测量后优化，本轮不引入 Paging、schema、网络层或全局重试策略变化。

## Caveats / Not Found

- 本报告是静态只读研究；未修改产品代码，未运行真机、模拟器、宏基准、故障注入或内存剖析，因此没有声称具体崩溃率、ANR、P95 帧耗时或峰值 PSS。
- `ScreenTranslatorService` 与 `SelectionAccessibilityService` 当前被 Manifest 禁用且没有启用代码，所以相关崩溃、OCR 和截图风险目前不可从正常 UI 到达；这同时意味着现有产品也无法实际使用该系统能力，不能把“不可达”误判成“实现安全”。
- `SecureValueStore` 的解密和 JSON map 解析大多用 `runCatching` 返回缺省值，因此设置流主要的直接异常来源是 DataStore 读取和未预见的运行时错误；其重复主线程成本仍需 Trace 定量。
- WorkManager 在 Worker 已被标记 stopped 后可能忽略最终返回的 `Result.retry()`；这里确认的是取消被吞和错误语义，而不是断言每次都会真正重新排队。
- Room 查询当前没有分页不等同于已证明性能缺陷。需要真实大数据 fixture 后才能决定限制查询、增量映射、拆列或 Paging。
- 未发现业务 `Channel`/`MutableSharedFlow`，未发现额外 BroadcastReceiver，也未发现已接入的线上崩溃分析 SDK；这些是“本次扫描未找到”，不是对所有生成代码、依赖内部实现或运行时注入的绝对证明。
