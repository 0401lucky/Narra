# Research: D5 语音接口与持久资源前置

- Query: 核实 D4 SpeechUnit 交接以及现有 MiMo、翻译、音频文件、播放器和迁移接口，给下一块提供可实施的复用边界。
- Scope: internal；只读生产源码、测试和已批准父/D5设计。唯一写入为本研究文件。
- Date: 2026-11-19。
- 状态: D4 仍由主会话验证冻结源码；本研究不激活 D5，不代表工程、设备或真实声音验收通过。

## Findings

### 1. 文件与可复用接口

以下生产路径相对 `app/src/main/java/com/example/myapplication/`。

| 文件与定位 | 当前接口/行为 | D5 复用边界 |
| --- | --- | --- |
| `data/repository/tts/MimoTtsClient.kt:32`、`:147`、`:203` | `MimoTtsRequest.preset/voiceDesign/voiceClone`；`suspend synthesize(apiKey, request, baseUrl): MimoTtsResult`；完整 Base64 响应，取消实际 `Call`，响应体上限 16 MiB | 可直接装配底层 TTS；没有内存/磁盘缓存、PCM流、音频时间戳。当前实际声学字段只有 `audio.format/voice`，模型由请求确定。 |
| `model/VoiceSynthesisSettings.kt:17`、`:57`、`:61`、`:110` | 旧后台开关、独立 Key/端点、全局及按角色音色；`hasUsableCredential()` 为 `enabled && Key非空`，`profileForAssistant()`解析继承 | 新陪伴开关须独立；不要要求旧后台 `enabled=true` 才能点播。继续复用原安全凭据与角色音色，不改 Key。 |
| `data/repository/ai/AiTranslationService.kt:40`、`:89`、`:234`、`:603` | `translateText(text,targetLanguage,sourceLanguage)`；内部读取最新 Settings，解析 `TRANSLATION` Provider/model，支持 Chat Completions、Responses、Anthropic；取消异常原样传播 | 可复用请求/协议/脱敏。没有翻译缓存或显式设置快照入参；D5 有效键与最终HTTP必须来自同一冻结配置。 |
| `data/repository/AudioFileStorage.kt:23`、`:51`、`:70`、`:114` | Base64/bytes → `SavedAudioFile(path,mimeType,fileName)`；可指定目录；Base64上限10 MiB字符；按WAV/MP3签名识别后直接写最终文件 | 可复用编码、文件类型与私有目录模式；现存实现没有临时→最终发布、hash/长度回执、取消后清理或Room状态。签名识别不证明完整可播放音频。 |
| `conversation/VoiceSynthesisCoordinator.kt:41`、`:105`、`:186`、`:234` | 仅为已完成assistant消息的 `VOICE_MESSAGE` part 合成；GENERATING→保存文件→READY metadata；旧READY跳过；Profile hash仅模式/音色/Prompt/克隆路径/MIME/大小 | 保留旧语音消息行为；不要把它当普通台词/队列/缓存。`runCatching`也接住取消，且未提供已保存文件挂接失败后的持久恢复回执。 |
| `ui/component/roleplay/RoleplayVoicePlaybackController.kt:19`、`:49`、`:104`、`:155` | 注入 `RoleplayVoicePlayerFactory`；listener先注册再prepare；活动session才允许 `start()`，start成功后报告Playing；stop/dispose一次释放、拒绝旧回调 | 可复用底层状态/测试模式；没有会话统一翻译/合成/队列generation，也没有 `play(path)` 的显式队列完成接口。 |
| `ui/component/roleplay/RoleplayActionMessageCards.kt:404`、`:588` | 每卡remember自己的controller；Android MediaPlayer factory/adapter是文件private | D5应由一个会话owner使用可注入播放adapter；若提取既有adapter须保留旧卡片生命周期与默认行为，不让多卡controller拥有新队列。 |
| `di/AppGraph.kt:427`、`:434`、`:438` | 已装配 `aiTranslationService`、`mimoTtsClient`、旧 `voiceSynthesisCoordinator` | 新仓储/文件发布/Controller/ViewModel继续在AppGraph装配，UI不创建网络客户端或Room。 |

### 2. D4 当前 SpeechUnit 与接线点

- `model/CompanionReading.kt:71` 的 `CompanionSpeechUnit` 有完整source、beatId、显示text、原UTF-16半开rawSpan/rawText、speaker/name、独立voiceEmotion、contentType及可选languageTag；语言当前默认null。
- `roleplay/companion/CompanionReadingSupport.kt:70` 的 `CompanionSpeechSupport.units(reply)` 只取非空正式beats，ID包含messageId/revision/beatId/rawHash；`units(items):94` 按已授权Reader顺序去重source后展开。
- `select:98` 生成可跨回复的有序ranges，拒绝倒序/空范围/拆代理对；`isCurrent:123` 要求完整Unit快照仍存在。D5应逐range取显示text并保留每Unit来源，不能把rawSpan用于显示text截取，也不能只用合并 `selection.text` 丢掉来源、说话人和单元边界。
- Stage `CompanionDisplayBinding.onSpeechSelection`（`ui/screen/roleplay/CompanionStage.kt:49`）默认空；`ui/navigation/CompanionPresentationRoutes.kt:66` 当前生产binding没有传入声音消费者。Stage在source变化/dispose取消选段（`:150`起），ON_PAUSE/STOP停本地Player；声音必须有独立generation，不能借Player计时代次。
- Reader `CompanionReadingCallbacks.onSelectionChange`（`CompanionReadingScreen.kt:50`）生产接 `CompanionReadingViewModel::select`（`CompanionPresentationRoutes.kt:124`）。VM在`:216`/`:231`对刷新、修订/删除后的选择做 `isCurrent` 过滤。
- `CompanionStage.kt:387` 的共享SelectionPanel目前只有文字预览与关闭，Reader也复用它；尚无点播、生成收藏或朗读动作。D5宜增加显式声音动作/状态适配，同时保留D4原显示点击零请求行为。
- Reader scope只是只读历史授权，不能当模型历史授权；TTS/翻译来源仍核实际source版本及用户已选范围。自动播默认只台词且排除自动开场，手动选区可含旁白/跨场次，按批准D5-R1/R2处理。
- Unit/source本身没有开场kind字段；`model/CompanionTurn.kt:5`/`:51`有 `INPUT/CONTINUE/OPENING`。自动播排除开场应核关联turn.kind，不能从台词类型、列表位置或“是否第一次看到”推断。

### 3. 需要补齐的合同

1. **配置与缓存键**：新增陪伴voice/auto默认关闭偏好，经现有小映射expectedRevision CAS保存。克隆版本应hash本次实际发送的样本内容，不能只看路径/大小；旧Profile hash不含内容，设置固定试听键也仅 `baseUrl|voiceId`（`VoiceSynthesisSettingsScreen.kt:1487`）。统一键覆盖实际发送文本、目标语种/翻译版本、有效端点/协议/模型、角色音色/克隆内容及实际声学参数；缓存名/日志不含Key、Base64、私有路径。VoiceDesign相同Prompt不保证同一个音色，延续既有文案合同。
2. **设置冻结**：翻译服务目前自行读Settings；缓存键若在外层先读一次，随后服务再读会出现配置与HTTP不一致窗口。实施时需同一有效配置快照或等效版本守卫，覆盖参数变化及取消后的迟到返回。这里未运行复现。
3. **唯一声音owner**：新Controller串行负责翻译→TTS→文件/仓储→prepare→start→完成→下一段；换句/模式/角色/源/离场取消Job、播放器并提升generation。每个异步恢复点和prepared都核当前owner，只有start成功报告Playing。旧Controller的session身份门可复用，不能复制旧Coordinator的取消失败分支。
4. **可恢复发布**：独立私有临时文件由知道实际target的所有者清理；合法完整音频核hash/长度后发布。Room使用PENDING/READY及挂接回执；已持久音频补挂接不重新合成，首次落盘失败的限制如实保留。取消/失败/残缺文件不进入成功缓存，实际播放失败仍保留文字与重试反馈。
5. **独立收藏**：main检索未发现 `SpeechRequestKey`、音频资源/收藏实体或仓储。收藏保存实际音频/原与朗读文本/语言/source版本快照；已有音频收藏零TTS，未有音频明确“生成并收藏”。清缓存/LRU排除收藏，删源标失效且保留独立收藏，取消收藏只减资源引用，不删除原文。
6. **声音效果边界**：`voiceEmotion`是D3投影，不等于MiMo支持任意情绪标签；当前请求没有emotion/speed/pitch字段。按有效参数清洗/保存，不读出控制协议，不宣称PCM增量或逐字音频时钟。

### 4. 持久模型与迁移交接

- 当前 `data/local/chat/ChatDatabase.kt:142` 是55，`:148` 暴露 `ChatDbMigrations.ALL`，AppGraph`:193`注册；最新 `MIGRATION_54_55` 在 `ChatDbMigrations.kt:1625`，D4没有新增表。
- D5设计已明确声音资源/收藏表归D5新增；若开始时仍为55，只前向新增55→56，一次递增1，不改54/55导出schema或既有迁移，不操作真实用户库。Entity/DAO/Room repo、导出56schema、registry和真实固定55库升级用例必须一起维护。
- S3有固定相邻升级合同：`app/src/baseline/java/com/example/myapplication/system/performance/LegacyUpgradeFixtureBuilder.kt:310` 当前source54/target55、SQL为 `src/baseline/assets/benchmark/s3-schema-v54.sql`；`LegacyUpgradeFixtureContractTest.kt:22`严格核当前55及相邻版本。升56后应基于固定55schema新增/切换本轮source55 SQL、identity、SHA、builder和文档/测试；保留旧54文件，不删版本守卫。
- 引用/来源失效查询需独立于message的CASCADE删除：收藏是用户独立副本，不能让删消息的FK顺带删收藏。新文件和Room仍是分阶段，不声称跨存储原子性。

### 5. 真实旧测试入口（本研究未执行）

以下5个JVM class selector当前共40个 `@Test` 方法，仅静态数量，运行后以XML为准：

| Selector（前缀 `com.example.myapplication.`） | 方法数 | 关键现有覆盖 |
| --- | ---: | --- |
| `conversation.VoiceSynthesisCoordinatorTest` | 6 | 成功/失败保文字、关闭/缺Key零调用、设计/克隆请求、失败后重试 |
| `data.repository.tts.MimoTtsClientTest` | 12 | 实际HTTP认证/body/端点、空音频、脱敏、响应限额、body读取中取消Call |
| `data.repository.ai.AiTranslationServiceTest` | 10 | 三文本协议/用途模型/自定义路径、结构化/流式和错误脱敏 |
| `ui.component.roleplay.RoleplayVoicePlaybackControllerTest` | 8 | prepared前不start、取消、完成/错误释放、start失败、dispose/换key拒绝旧回调 |
| `data.repository.AudioFileStorageTest` | 4 | 真实WAV文件保存、非法Base64、精确大小边界 |

新增D5还需实际HTTP键矩阵、同路径同大小不同克隆内容、翻译/合成/prepared迟到、队列停止、文件与Room重开补挂接、收藏生命周期、source修订/删除及独立开关。

旧迁移JVM selector为 `data.local.chat.ChatDatabaseMigrationRegistryTest` 和 `system.performance.LegacyUpgradeFixtureContractTest`；设备文件为 `app/src/androidTest/java/com/example/myapplication/data/local/chat/ChatDatabaseMigrationTest.kt`、`data/repository/VoiceCloneSampleStorageInstrumentedTest.kt`、`ui/screen/settings/VoiceSynthesisSettingsScreenTest.kt`。新原生声音用例需要真实合法短音频和可控准备/停止/离场；未执行的物理设备声音、系统音频及真实MiMo质量须单列。

## External references

- 未访问第三方API或凭据，未调用真实模型/TTS。协议/模型事实来自当前 `MimoTtsClient` / `VoiceSynthesisSettings`；若扩展协议参数，实施前另核当前供应商文档。
- 已批准产品合同见 `.trellis/tasks/11-09-date-companion/design.md` §8以及 `.trellis/tasks/11-09-date-companion-voice/` 下的 `prd.md`、`design.md`、`implement.md`；上次盘点 `.trellis/tasks/11-09-date-companion/research/voice-assets-history-map.md` 本轮已与源码重新核对，未将其中旧“可选”措辞用来删减批准范围。

## Related specs

- `.trellis/spec/backend/network-guidelines.md`：现有MiMo请求、试听实际start成功、VoiceDesign/Clone及取消/脱敏合同。
- `.trellis/spec/backend/database-guidelines.md`：版本单步递增、schema/registry/固定旧库/S3相邻fixture、文件与Room分层。
- `.trellis/spec/backend/error-handling.md`：取消传播、资源清理、用户错误脱敏与失败保文字。
- `.trellis/spec/backend/companion-stage-reading-guidelines.md`：SpeechUnit/display/raw范围、选区/source失效和独立声音生命周期。

## Caveats / Not Found

- 未运行formatter、Gradle、设备或声音服务；此记录不代替D4完成证据，也不提前勾选D5验收。
- 未改生产源码、测试、spec、任务状态、schema/PNG/配置或真实数据；仅写本文件。
- 未找到现有陪伴voice偏好、统一缓存/资源/收藏实体、实际声音UI或会话统一音频focus实现。以列出的当前main检索及接口为依据；不是对所有历史分支的结论。
