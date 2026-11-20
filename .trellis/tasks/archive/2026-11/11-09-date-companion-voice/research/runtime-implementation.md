# D5 运行线实现交接

2026-11-19，v2.4.1；作者 `voice_runtime`（原生 trellis-implement）。按 root 冻结的领域接口直接实施，没有创建新任务、改变已批准范围或派发代理。保留工作区已有改动。

## 实际接口

- `CompanionCharacterSettings.voice` / `CompanionSettingsPatch.voice`：新增独立声音偏好，voice/auto 默认关闭；既有 `VoiceSynthesisSettings.enabled` 完全独立。`CompanionVoiceCodec` 逐字段默认、语种长度/上限、未知字段保留、自身 schemaVersion=1 守卫，经原 `updateCompanionSettings(expectedRevision)` CAS 保存。
- `data/repository/ai/CompanionTranslationSnapshot.kt`：附加 `SnapshotAiTranslationService.freeze(AppSettings): TranslationRequestSnapshot` 与显式 snapshot `translateText`。安全 identity 保存实际 normalized base/final endpoint、TRANSLATION Provider/model、protocol/mode、builder 版本；Key 和 sanitized Provider 仅驻内存。`DefaultAiTranslationService` 实现附加接口，复用旧 request helper；旧接口三个方法、默认语言、构造器和既有 JVM 描述符保持。
- `CompanionTtsRequestFactory.freeze(VoiceSynthesisSettings, characterId)`：复用现有 MiMo 三模型/音色规则；本次克隆样本只读取一次，按实际字节 SHA/MIME 构造请求和键，后续文件替换不改变冻结负载。新冻结入口拒绝 URI userInfo/query/fragment，不修改旧全局 URL 规则。
- `CompanionSpeechRequestKeys`：统一 translation/speech SHA-256 键，长度前缀和排序后的有效参数避免歧义；覆盖实际 trim 文本、原/朗读语言、翻译键、endpoint/model/角色/mode、format/voiceId/promptHash/实际 clone 样本 hash+MIME/instructionHash、builder 版本，不保存明文凭据/负载/路径。
- `CompanionVoiceSegments.from`：消费 D4 Unit/range，按显示坐标和完整 grapheme 分段，默认至多 800 UTF-16 单元（超长单簇保持完整）；旁白、旧场与来源版本、rawSpan/rawText、voiceEmotion 均保留，不再次解析或清洗正文。
- `CompanionVoiceController` implements 冻结的 `CompanionVoiceSession`。构造器注入 `scope/repository/translationService/playerFactory/getTurn/synthesize/requestFactory/translationCache`；root AppGraph 负责装配，UI 不创建网络或存储。
- `system/audio/AndroidCompanionVoicePlayerFactory(context)`：MediaPlayer 监听先注册再 prepare，start 成功且本代次仍有效后才 PLAYING；处理系统 speech 音频属性/focus、完成/错误/stop/release，未引入 UI 依赖。

## 状态与恢复

- 一个 Session generation 管理翻译→TTS→claim/publication→prepare/start→下一段；每异步恢复点、prepared/start、来源观察和父 scope 生命周期都撤销旧请求。独立 lifetimeJob 不等待 NonCancellable 旧响应；任何旧结果不能发布缓存或抢播，新 finally 不覆盖新状态。
- `offerAutomatic` 首次只记录历史基线，其后只处理新增来源的台词，并查询实际 `turn.kind`，INPUT/CONTINUE 才可播；OPENING、旁白、缺 turn 不自动读。始终先记已见来源，UI 可用 auto=false 内存 copy 静音建立 resume 基线，不触发配置写入或改变 session context。
- 翻译失败明确保留原文/错误，retry 与显式 readOriginal 分开。译文缓存仅当前 Session 内存，有限 128 项/256000 字符；失败/迟到译文不自动入缓存。音频用量/LRU/清理由声音仓储管理，UI 应称“音频缓存”。
- 有效 `attachmentState=PENDING` 音频保在 state.currentAudio；重试消费旧回执补挂接，无 TTS。第一次文件写失败在当前 Session 保留单个有界 MiMo 完整响应供免费再保存；进程结束且没有持久副本时不保证恢复，未知 PENDING 通过 `needsExplicitRegeneration` 要求显式再次生成。
- 收藏已有音频只补挂接/保存收藏，无翻译或 TTS。显式 generateAndFavorite 串行保存所有已选分段，不播放；缓存已存在则零生成。收藏播放使用历史音频/文本，即使源失效也不重新生成。
- 每段实际播放完成或已收藏后先 releaseOwner，再 trimCache，避免整个长队列都被 pin 而无法回收；finally 幂等释放。音频保留/来源失效/收藏及清理的最终引用语义由 B 仓储承担。

## 文件范围与验证状态

修改 3 份已有生产文件：`model/CompanionContext.kt`、`data/local/CompanionSettingsCodec.kt`、`data/repository/ai/AiTranslationService.kt`。

新增 8 份生产文件：`CompanionVoiceCodec.kt`、`CompanionTranslationSnapshot.kt`、`CompanionSpeechRequestKeys.kt`、`CompanionTtsRequestFactory.kt`、`CompanionVoiceSegments.kt`、`CompanionTranslationCache.kt`、`CompanionVoiceController.kt`、`AndroidCompanionVoicePlayer.kt`。未改 root 冻结合同、AppGraph、UI、Room/迁移、旧 schema、版本参数、更新 JSON 或旧截图。

JVM 新 selector（仅源码静态 `@Test` 数，不是执行通过数）：

| class（前缀 `com.example.myapplication.`） | 静态方法数 |
| --- | ---: |
| `roleplay.companion.CompanionVoiceControllerTest` | 21 |
| `roleplay.companion.CompanionSpeechRequestKeysTest` | 7 |
| `data.repository.ai.CompanionTranslationSnapshotTest` | 5 |
| `data.local.AppSettingsStoreCompanionVoiceTest` | 5 |

合计静态 38：Fake 证明开关/配置、实际 start 时序、跨场旁白及显示/raw坐标、翻译失败两种明确动作、不可取消迟到、父取消、源/模式/音色取消、持久及当前内存免费补存、明确重生成、收藏与自动播；实际 MockWebServer 验证冻结 A 后设置 B 的 HTTP/键、TRANSLATION 用途、三协议，以及同路径同大小克隆替换后的实际 MiMo 请求仍消费冻结字节；真实 DataStore 验证独立偏好/旧安全凭据/版本/CAS保留。

本代理只执行定向源码读取、`rg -c '@Test'` 和已有三文件 `git diff --check`（exit 0，已有 LF→CRLF 提示保留）。未运行 Gradle、formatter、设备或收费模型。工程/实际执行数量由 root 集中验证并记录；此文不宣称测试通过。

## 明确限制

仍为 MiMo 完整 Base64 响应与源分段串行播放；不支持 PCM 增量、逐字音频时钟或假字幕进度。voiceEmotion 只保留 metadata，不新增 MiMo 未支持参数。VoiceDesign 同 Prompt 不保证固定音色。未测真实 MiMo 合成质量、物理设备扬声器/蓝牙/系统音频焦点效果；Fake 验证不能替代真实声音效果。第一次无持久音频时的进程死亡恢复没有无条件保证。
