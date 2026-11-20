# 陪伴语音、翻译与独立收藏

## 1. 适用范围

修改 D5 点播、自动播、跨场选区、MiMo 参数、翻译缓存、音频发布/恢复、收藏或清理时阅读。
正文/来源沿用 [正文表现](./companion-presentation-guidelines.md) 与
[演出阅读](./companion-stage-reading-guidelines.md)，不另建 canonical 或模型历史授权。
本块工程与效果验证记录在 `.trellis/tasks/11-09-date-companion-voice/research/`；
源码接口存在不代表真实 MiMo 音质或物理设备效果已验收。

## 2. 接口与持久位置

以下源码路径相对 `app/src/main/java/com/example/myapplication/`。

```kotlin
// model/CompanionVoice.kt
interface CompanionVoiceSession {
    val state: StateFlow<CompanionVoiceState>
    fun updateContext(owner: CompanionVoiceOwner, settings: AppSettings, units: List<CompanionSpeechUnit>)
    fun select(selection: CompanionSpeechSelection?)
    fun playSelection(selection: CompanionSpeechSelection, settings: AppSettings, automatic: Boolean = false)
    fun offerAutomatic(units: List<CompanionSpeechUnit>, settings: AppSettings)
    fun generateAndFavorite(selection: CompanionSpeechSelection, settings: AppSettings)
    fun favoriteCurrent()
    fun playFavorite(favoriteId: String)
    fun retry()
    fun readOriginal()
    fun regenerateExplicitly()
    fun stop()
    fun close()
}

// data/repository/ai/CompanionTranslationSnapshot.kt，旧 AiTranslationService 保持
interface SnapshotAiTranslationService {
    fun freeze(settingsSnapshot: AppSettings): TranslationRequestSnapshot
    suspend fun translateText(snapshot: TranslationRequestSnapshot, text: String,
                              targetLanguage: String, sourceLanguage: String): String
}

// data/repository/roleplay/CompanionVoiceRepository.kt
suspend fun publish(claim: CompanionAudioClaim, b64Audio: String, clip: CompanionVoiceClip,
                    isCurrent: suspend () -> Boolean): CompanionVoiceAudio
suspend fun retryAttachment(audio: CompanionVoiceAudio): CompanionVoiceAudio
suspend fun recoverPending(): List<CompanionVoiceAudio>
suspend fun favorite(audio: CompanionVoiceAudio): CompanionVoiceFavorite
suspend fun clearCache(): CompanionVoiceCacheUsage
suspend fun trimCache(maxBytes: Long): CompanionVoiceCacheUsage
```

- 角色小映射使用 `CompanionCharacterSettings.voice` / `CompanionSettingsPatch.voice`，
  经 `updateCompanionSettings(assistantId, expectedRevision, patch)` CAS 写入；顶层和 voice
  的 schemaVersion 均为 1，未知字段和未来版本保留，不降级覆盖。语种输入上限统一使用
  `CompanionVoicePreferences.MAX_LANGUAGE_LENGTH = 60`，UI与codec不各造一份范围。
- Room 主库 55→56 新增 `companion_voice_resources`、`companion_voice_favorites`，
  当前 cacheKey 可空且唯一，资源带 PENDING/READY、owner/lease、hash/长度与 clip 快照。
  收藏只 RESTRICT 引用音频，不 FK 级联到消息/回合/场景。
- AppGraph 装配同一个 Default 翻译服务，保留旧 `aiTranslationService` getter 类型，
  另提供 snapshot 接口；`createCompanionVoiceSession(scope)` 按宿主创建 Session。
  实际原生播放器位于 `system/audio`，ViewModel 不反向依赖 UI/MediaPlayer 实现。
- 独立 `AppRoutes.roleplayCompanionVoice(assistantId)` 编码/解码配对，LIBRARY owner
  使用独立标识；删源后仍可从角色详情进入声音库，不进入旧场景生成运行。

## 3. 跨层合同

### 用户授权、来源与声音 owner

- 陪伴 enabled/autoPlay 默认 false，与旧 `VoiceSynthesisSettings.enabled` 的
  VOICE_MESSAGE 后台合成完全独立；缺 Key/禁用音色/缺设计或克隆配置保留文字。
- Stage/列表点播、Reader 选区都消费完整 D4 Unit/range；range 针对显示 text，
  rawSpan/rawText 仅作出处。每个生成分段保留 source/revision/beat、原/朗读文本、
  语言和 voiceEmotion；不再次清洗已解析正文。按完整 grapheme 在约 800 UTF-16
  单元内分句/段，超长单簇完整保留，跨场次与所选旁白顺序不丢失。
- 自动播仅新增台词且真实 `getTurn(source.turnId).kind` 为 INPUT/CONTINUE，OPENING
  与旁白不自动播。首挂载、先 RESUMED 后首 bind、恢复或换模式都静音建立已见基线；
  不能依赖 Compose effect/Settings 流的先后顺序推断“新增回复”。
- 一个 Session generation 管翻译、TTS、文件结果、prepare/start 和下一段。换句、
  mode、角色、有效配置、源修订或离场撤销旧任务；父 scope 取消也立即失去所有权，
  不等待 NonCancellable 旧响应。每次异步恢复、prepared 和 start 前后均复查。
- 来源观察使用唯一 Job 与独立 observer epoch。普通异常或意外结束使依赖源的声音
  失效，自动播不能在没有监听时继续；下一次用户显式播放才重建监听。每次事件捕获
  当时的 run，再检查返回时的 generation，不能把监听启动时的声音代次固定到以后。
  收藏回放独立于源是否仍可用，不因原文监听失败而要求重新生成。
- listener 在 prepare 前安装；仅实际 start 成功且代次仍有效才 PLAYING。
  UI 只显示当前朗读单元和阶段，不用 D4 打字时钟作声音进度。

### 同源冻结配置与缓存

- 新 snapshot 从一份 AppSettings 解析 TRANSLATION Provider/model、规范化 base/
  实际 endpoint、protocol/mode 与 builder 版本；snapshot 请求不二次读 SettingsStore，
  使用既有三协议 request helper。旧翻译接口三个方法、默认语言和构造器 ABI 保持。
- `CompanionTtsRequestFactory.freeze` 使用现有 preset/design/clone MiMo 模型与安全凭据。
  本次克隆样本读取一次，真实发送字节 SHA-256/MIME 和 Data URI 来自同一份 bytes。
  同路径、同大小换内容仍生成新键；不能沿用旧 VoiceMessage profile/path hash。
- `CompanionSpeechRequestKeys` 统一采用长度前缀字段与排序参数的 SHA-256：实际 trim
  文本、source/target 语言、翻译配置键、endpoint/model、角色、音色 mode、format、
  voiceId/promptHash/clone sampleHash+MIME/instructionHash、builder 版本均纳入。
  所有音频消费者使用同一 SpeechRequestKey，不另造内存/磁盘键。
- credential、Provider 完整配置、Data URI、Base64 与私有样本路径不进入持久 key/参数。
  新冻结入口拒绝带 userInfo/query/fragment 的认证 URL，不改变旧全局 URL ABI。
- 翻译缓存是当前 Session 内存的有限 LRU（128 项/256000 字符），失败和迟到结果不入缓存；
  UI 用量/清理明确为“音频缓存”。翻译失败停在错误，可重试翻译或显式读原文，canonical 不变。
- 当前 MiMo 只发送已有 format/voice/messages/model，不添加 speed/pitch/emotion/PCM 字段。
  voiceEmotion 只保留 metadata；VoiceDesign 同 Prompt 不保证相同音色。

### 文件、回执与独立引用

- `PrivateCompanionVoiceFilePublisher` 先有界 Base64 解码，在私有 `.audio.part` 写入并
  校验完整 WAV/MP3、hash/长度，再发布最终音频和可恢复 sidecar；仅文件头不是完整证明。
  WAV 检查 RIFF/fmt/data 与采样对齐，MP3 检查完整 Layer III 帧。
- 文件和 Room 分阶段。有效文件/sidecar 后挂接失败保留 attachmentState=PENDING；
  retryAttachment/recoverPending/重开 lookup 免费补挂接，不持有 TTS 回调。
  Room 已提交但 sidecar marker 失败时，不能把实际已提交回执当成重新生成理由。
- 停止/关闭面板后保留的合法 PENDING 音频仍可显式补保存；没有 lastRequest 时
  retry 只执行受当前 generation/work 控制的 retryAttachment，不翻译、TTS、prepare/start
  或恢复用户已停止的队列，迟到结果不得回填新选择。
- 首次没有合法持久副本时，当前 Session 可保留一个有界响应供再保存，进程死亡后
  不承诺恢复；已发起但无副本的 PENDING 需要显式重新生成，可能重复计费。
- isCurrent 在发布/Room 最后恢复点复查，取消清理由知道真实 target 的层执行；
  被当前 claim/pin 的 PENDING 不让后台提前提升 READY，取消 READY 在锁释放前撤销。
- 撤销必须先持久化现有 state 列的 CANCELLED tombstone，再尽力清理文件；delete
  失败不能留下可命中的 READY 或可恢复的 PENDING。lookup/recover/补挂接/晚到收藏/
  旧 publish 都拒绝 tombstone。若取消标志和文件清理均无法落盘，当前实例的取消集合
  只能保护当前进程，不能无条件保证进程死亡后的取消标志恢复；错误证据须保留。
- 收藏已有音频只挂接/保存引用，零翻译/TTS；“生成并收藏（可能计费）”是显式动作，
  可串行保存所选各段。收藏回放使用已保存音频/原读文本/语言，不采用当前音色重合成。
- isSourceCurrent 复用实际消息/场次/turn/COMMITTED attempt 图并核完整版本/hash；
  修订/删源时收藏保留且实时 UNAVAILABLE。取消收藏只减引用，不删除原文或立即删音频。
- 音频默认上限 64 MiB，可设 1..512 MiB；有限 LRU 按 lastUsedAt,id 回收无收藏、无活动
  claim/pin 的资源。每段真正结束后释放该 owner 再 trim，不能一直 pin 整队列使限额无效。
- 回执扫描只读。仓储合并全部收藏、READY、合法待补挂接和活动 pin IDs 后调用
  `cleanupOrphans(retainedAudioIds)`，只清超 24 小时且本层无引用文件；坏 sidecar
  不能推出“没有收藏”。清理成功后 Session 撤销普通选区旧 currentAudio，UI 不保留假可收藏状态。

## 4. 校验与错误矩阵

| 条件 | 必须观察到的行为 |
| --- | --- |
| 默认关闭、缺 Key/可用音色 | 无隐式翻译/TTS，正文和可行动配置反馈保留 |
| 开场/首挂载/后台回填 | 不自动读历史或 OPENING，不由列表位置猜种类 |
| 翻译失败/空译文 | 不静默读原文，提供重试或显式原文动作 |
| 旧翻译/TTS/prepared/finally 迟到 | 不出声、不发布成功缓存、不覆盖新代次 |
| 文件残缺、路径/长度/hash 不符 | 不 READY，不进入成功缓存，错误保留文字 |
| 已发布音频 Room 挂接失败/重开 | 消费相同回执补存，TTS 调用数不增加 |
| 首次无副本且进程死亡 | 如实提示恢复限制，未知结果不自动再收费 |
| 清缓存/删源/取消收藏 | 收藏音频保留/出处失效/仅减收藏引用分别成立 |
| 损坏 sidecar 但 Room 仍有收藏 | 实际音频保留，不按有效回执列表误删 |
| 缓存上限与长队列 | 播放中 pin，完成释放后能实际回收，收藏始终排除 |

## 5. 正常、旧数据与异常案例

正常：用户选择旧场旁白和本场台词，按显示范围依次翻译/生成/播放，状态只在实际
start 后为 PLAYING，来源原文/revision 不变。旧数据：没有陪伴 voice 偏好的角色
维持静音和旧列表；旧语音消息仍播放原产物。异常：合法音频已经落盘但挂接失败，
重试只补存；源被删除后独立收藏仍可播放原声音，并标出处失效。

## 6. 必需验证

- Controller：关闭/缺配置、开场过滤、跨场旁白/有界分段、迟到翻译/TTS/prepared/
  父取消、start 失败、停止不继续、内存和持久免费补存、收藏实际调用数、pin 后释放限额。
- MockWebServer：冻结 A 后 Settings 变 B，实际 endpoint/model/body 和 key 仍为 A；
  TRANSLATION 用途/三协议；同大小同路径克隆替换后 HTTP 仍发冻结 bytes，键包含其 digest。
- 真实 DataStore：voice 独立默认、CAS、未知字段/未来版本、缺角色与旧安全凭据保留。
- 真实文件型 Room close/reopen：PENDING→READY、故障/取消、音频校验、收藏/来源观察/
  pin/LRU/清缓存及坏 sidecar 引用保护；不能用内存库替代持久恢复。
- 55→56：registry/new schema、固定 55 所有旧表逐字段保留和新索引/约束，S3 固定
  source55→target56 SQL/identity/SHA/builder/FixtureSpec 相邻守卫；旧 schema 字节保留。
- Compose：生产 binding→VM→Session 的 Stage/Reader/声音库动作、首 bind 时序、
  翻译失败选择、收藏/清理/CAS/生命周期；另用合法短 WAV 检查实际原生 prepare/start/stop。
  Fake UI、真实本地 WAV、真实 MiMo 音质/物理设备效果分别记录，不互相代替。

## 7. 错误与正确做法

```kotlin
// 错误：键取 A，HTTP 又读到 B；拿到音频响应就宣称播放成功。
val key = keyFrom(settingsA)
legacyTranslation.translateText(text)
state.value = state.value.copy(phase = CompanionVoicePhase.PLAYING)

// 正确：键和 HTTP 消费同一份冻结配置，prepared 后仍核 owner。
val snapshot = snapshotTranslation.freeze(settingsA)
val key = CompanionSpeechRequestKeys.translation(snapshot, text, target, source)
val spoken = snapshotTranslation.translateText(snapshot, text, target, source)
// Session 只有在本代次实际 player.start() 成功后设置 PLAYING。
```
