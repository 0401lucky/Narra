# Research: D5 实施阶段与文件所有权

- Query: 给已批准D5提供最小接口冻结/并行边界，重点让翻译缓存键与实际HTTP消费同一配置快照并保留旧接口ABI。
- Scope: internal；补核当前Provider/翻译/语音源码与父设计§8。仅写本研究文件，不激活D5。
- Date: 2026-11-19。
- 前置: D4仍在定向验证；主会话完成D4并显式切换当前任务后才派发D5实施。基础代码事实及旧40项选择器见 `d5-voice-readiness-2026-11-19.md`。

## Findings

### 1. 翻译快照入口：旧ABI保持，配置只解析一次

当前事实（生产路径相对 `app/src/main/java/com/example/myapplication/`）：

- `data/repository/ai/AiTranslationService.kt:40`有三个旧方法：`translateText`、`translateTextStream`、`translateStructuredSegments`；`:58`构造器含已有可注入API/factory与dispatcher。新功能不需要改变这些签名/default语言或增加旧接口的必需抽象方法。
- `translateText:89`每次内部调用 `requireConfiguredSettings:234`；随后读 `AppSettings.resolveFunctionProvider/Model(TRANSLATION)`。配置选择规则在 `model/AppSettings.kt:97`/`:124`：用途明确分配的enabled Provider优先，随后active Provider；模型继续沿该Provider的用途模式解析，不手工挑列表首项。
- `requestCompletionText:256`已经接收显式baseUrl/apiKey/model/protocol/provider与messages；实际协议/错误处理可复用，不复制另一套HTTP栈。
- `buildOpenAiTextUrl:533`使用 `ApiServiceFactory.normalizeBaseUrl`，Chat Completions沿 `resolvedChatCompletionsPath()`，Responses为 `/responses`。`model/ProviderSettings.kt:54`/`:72`/`:76`负责实际协议、mode和路径；`ApiServiceFactory.kt:373`还处理安全URL及Gemini OpenAI兼容路径。Anthropic沿规范化base URL和 `AnthropicApi.kt:15` 的相对 `messages` 路径，不能拿OpenAI自定义路径替代。

建议先冻结一个**附加接口**，以下名字为边界示意：

```kotlin
interface SnapshotAiTranslationService {
    fun freeze(settingsSnapshot: AppSettings): TranslationRequestSnapshot
    suspend fun translateText(
        snapshot: TranslationRequestSnapshot,
        text: String,
        targetLanguage: String,
        sourceLanguage: String,
    ): String
}
```

- `DefaultAiTranslationService`实现附加接口，新增入口调用同一个既有request/response helper；snapshot入口不再读取SettingsStore。旧 `AiTranslationService`、既有构造器和三个方法保持；旧fakes不新增方法也能编译。旧点调用若内部委托新helper，仍保持原guard、默认语言与协议行为。
- freeze从一份AppSettings对象解析effective Provider、规范化base/最终endpoint、protocol、OpenAI mode/path、用途model；请求需要的凭据仅留在内存快照中。持久缓存key只提取无凭据字段，不能序列化整个Provider/AppSettings。
- 翻译key覆盖实际trim后的输入、source/targetLanguage、用途Provider身份、最终endpoint/protocol/mode/model和翻译prompt/builder版本；HTTP messages也从这份快照/输入生成。当前 `buildTranslationRequestMessages:447`有固定system指令并trim用户文本，不把Provider UI中未发送的thinkingBudget误当有效请求参数。
- AppGraph可持有一个private Default实例，旧 `val aiTranslationService: AiTranslationService` getter类型不变，另暴露附加接口供D5；不用调用端向具体实现强转，也不改变旧接口的JVM方法描述符。
- 最小验证是“冻结A → Settings变B → snapshot请求仍用A endpoint/model/body/key”；同时跑原三协议/路径/错误测试。取消继续通过`:603`保留CancellationException。

### 2. MiMo 当前真正有效的字段

以 `data/repository/tts/MimoTtsClient.kt:32` 的实际request序列化为准，端点用 `model/VoiceSynthesisSettings.kt:85`/`:94` 的安全normalize及chat-completions解析。

| 模式 | model | messages | audio |
| --- | --- | --- | --- |
| 预置 | `mimo-v2.5-tts` | assistant实际trim文本 | `format=wav`、实际preset voiceId（空值为mimo_default） |
| 文本设计 | `mimo-v2.5-tts-voicedesign` | user实际trim Prompt + assistant文本 | `format=wav`，没有预置voice字段 |
| 克隆 | `mimo-v2.5-tts-voiceclone` | user实际instruction（现旧协调器传空）+ assistant文本 | `format=wav`、本次真实sample data URI |

统一SpeechRequestKey包含实际文本/语言/翻译版本、有效MiMo endpoint/model、角色有效音色模式与参数、format、Prompt/instruction及**实际样本字节digest/MIME版本**；多个内存/磁盘消费者只用这一份key。克隆data URI/Base64和Key不进cache名/日志。旧hash仅路径/MIME/大小（`VoiceSynthesisCoordinator.kt:234`）不能复用。

当前没有speed/pitch/emotion或PCM参数；D3 `voiceEmotion`单独保留metadata，不凭枚举拼未文档化MiMo字段或读出控制标签。VoiceDesign同Prompt不保证固定音色，继续现有近似克隆文案。

### 3. 先冻结哪些接口

由主会话派发单一接口作者先写新的feature领域契约，其他实施者先只读。建议集中在新增 `model/CompanionVoice.kt`（示意路径），不修改D4 `CompanionSpeechUnit`/正文helper：

| 冻结内容 | 两条实施线必须一致的语义 |
| --- | --- |
| Voice偏好、effective TTS/translation快照、两种统一key | voice/auto默认false、旧后台enabled独立；无凭据持久key；实际HTTP和cache同源 |
| 来源与队列输入 | D4完整Unit/range快照、source版、实际turn.kind、冻结角色owner；逐range有序，显示offset不能当rawSpan |
| 音频仓储port和回执 | cache lookup/生成claim、PENDING/READY、已持久音频和挂接待补存可区别；回执保audioId/key/hash/length/mime及source状态，不暴露Entity |
| 文件发布port | 私有临时target、有效音频/hash/长度、最终发布、失败/取消清理；文件与Room分阶段，不把path返回当全部保存成功 |
| 可注入播放器port | prepare/start/completion/error、stop/release；旧prepared拒绝；只有实际start成功才Playing |
| 收藏与用量port | 收藏实际已有audio零生成；显式生成收藏；source失效保收藏；有限LRU/手清理排除收藏引用 |

接口冻结时明确TTS生产回调由控制器持有、仓储仅负责claim/资源/回执，不能让文件实施者偷偷发网络或让控制器绕过仓储只存path。取消/代次守卫和资源所有者在每个异步边界明确；挂接重试必须消费旧持久回执，不再调用生产回调。

`CompanionRepository.getTurn(id)`现有读取接口在 `data/repository/roleplay/CompanionRepository.kt:30`，可核 `source.turnId` 对应 `INPUT/CONTINUE/OPENING`，无需向D4Unit补开场字段或凭UI位置猜。source资格/修订仍沿D1/D3真实图读取，Reader scope不转成模型授权。

### 4. 分阶段所有权

| 阶段/责任线 | 独占文件或模块 | 完成交接 |
| --- | --- | --- |
| 0：单一接口作者（由root指定） | 新Voice领域DTO/key/port；本次上述freeze规范 | 命名/参数/回执/source与取消合同固定，A/B不得各造平行DTO或各改port |
| 1A：配置、HTTP、controller | 新voice偏好codec与 `CompanionContext.kt` / `CompanionSettingsCodec.kt` 必要voice字段；`AiTranslationService.kt`附加snapshot入口；新请求解析/键、会话Controller/队列、可注入player adapter；相应JVM测试 | CAS/未来版本不降级、HTTP/key同源、单owner/队列/迟到/错误状态通过；不写schema/资源DAO或既有UI |
| 1B：资源、收藏、迁移 | 新Voice资源/收藏Entity/DAO/Room仓储、私有发布实现；`ChatDatabase.kt` / `ChatDbMigrations.kt`仅B接手；registry/真实55库升级/S3固定相邻fixture与文件/Room恢复测试 | PENDING→READY/claim/补挂接/引用与LRU完成；仅新56schema由root KSP生成，旧54/55schema不改；不写设置/翻译/controller/UI |
| 2：root协调的UI集成作者 | 新Voice设置/状态/动作组件；AppGraph、CompanionPresentationRoutes/Stage/Reader/角色设置的必要adapter及新原生测试 | 消费已冻结A/B接口；真实点播/auto/跨场选区/翻译失败重试或显式原文/收藏/用量和停止；保D4已冻结正文与旧默认几何 |
| 3：独立check与root | 只读全范围审查，已确认失败交回唯一文件owner；root唯一Gradle/formatter/设备执行与spec/父矩阵/版本记录 | 原始报告对应最终SHA，D5工程/未验证声音效果分开；之后才交D6 |

0完成后1A/1B才可并行。文件重叠均由root明确转交：1A不写AppGraph、1B不动CompanionSettingsCodec，UI作者等A/B冻结后才写共享adapter；生成schema/formatter/构建也由root串行。各实施者保留工作区他人改动，不回滚或扩大本表责任。

### 5. 必须守住的验收边界

- 新voice/auto默认关；旧背景VOICE_MESSAGE enabled不受反向开启影响；auto只合法台词且OPENING禁自动播；本地显示/选择/菜单仍不自动收费。
- 翻译失败停在明确错误，可重试或用户显式原文；不得静默换语言/改canonical。队列逐range读所选旧场/旁白，不缩成当前场。
- 换句/模式/角色/source/离场提升独立声音generation，取消翻译/TTS/prepare/队列；cancel/error/残缺文件不成成功缓存，迟到不出声。
- 合法持久音频挂接失败保回执，重开/重试免费补存；首次无持久副本的保证有限。收藏已有音频零调用，“生成并收藏”显式执行；清缓存/取消收藏不删原文，删源保独立收藏并标失效。
- 56迁移维护registry/固定55库/schema及S3 source55→target56/identity/SHA，不移除当前相邻守卫或改旧schema。D4原UI回归、40项旧语音JVM、真实文件/Room/HTTP/原生音频按改动分别验；真实MiMo质量与物理设备声音未执行时单列。

## External references

- 无外部查询、真实API调用或凭据读取。本报告仅列当前实际request字段；未增加供应商/参数。后续若扩字段再核供应商当前文档。

## Related specs

- 父 `.trellis/tasks/11-09-date-companion/design.md` §8、D5 PRD/design/implement为权威；本报告只分责任，不另造或删需求。
- `.trellis/spec/backend/network-guidelines.md`、`error-handling.md`：MiMo实际start/取消/脱敏及翻译协议复用。
- `.trellis/spec/backend/database-guidelines.md`：版本单步/固定旧库/S3与分阶段存储。
- `.trellis/spec/backend/companion-stage-reading-guidelines.md`：Unit/raw/display/source范围与独立声音生命周期。

## Caveats / Not Found

- 尚无上述新接口/模块实现；名字只是边界建议，须由root在D5正式激活时决定并冻结。报告不标D4/D5已通过。
- 未写生产/测试/spec/任务状态，未运行Gradle/formatter/设备/模型/TTS，未派子代理；只写本文件。
