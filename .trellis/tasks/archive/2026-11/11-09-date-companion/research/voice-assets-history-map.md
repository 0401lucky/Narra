# Research: 声音、素材、历史导出与可选生态

- Query：v2.4.1 见面陪伴能复用哪些 Narra 声音/翻译/素材/导出能力，哪些仍须实现或由用户决定范围。
- Scope：internal；对照用户指定的跨项目参考手册，不调用参考项目服务。
- Date：2026-11-09。
- 方法：只读当前工作区源码、测试与规范；没有构建、执行测试、调用真实模型/TTS、访问私人素材或凭据。这里的“已有”指生产接线事实，不是本轮运行验收。
- 产品边界更新：用户已确认升级现有线下扮演及完整规划（PRD R5/R6）；最终D5纳入MiMo、可选翻译与收藏，PCM流式/逐字音频时钟字幕不纳入。下文可选项是研究时的范围盘点，最终合同以父/子设计为准。

## Findings

### 1. 当前能力与真实入口

| 领域 | 状态与证据 | 对 2.4.1 的含义 |
| --- | --- | --- |
| TTS 服务与角色音色 | **已具备**：`app/src/main/java/com/example/myapplication/model/VoiceSynthesisSettings.kt:17` 默认关闭，独立 Key/Base URL、全局及按 assistantId 的音色；`:61` 解析继承/预置/文本设计/克隆/禁用。当前具体实现是 MiMo。 | 复用现有设置与认证，不照搬手册的 MiniMax/Fish/ElevenLabs 列表；陪伴独立开关及朗读语种尚无对应字段。 |
| RP 语音消息合成 | **已接生产**：`ui/navigation/NavigationViewModelOwners.kt:97` 注入协调器；`viewmodel/RoleplayRoundTripExecutor.kt:715` 完成回复后启动 voice 阶段，`viewmodel/RoleplayViewModel.kt:282` 启动协程。 | 并非只存在于旧 Chat 页面；但其对象是语音消息卡片，不是普通线下台词。上述缩写路径均相对 `app/src/main/java/com/example/myapplication/`。 |
| 可合成内容 | `conversation/VoiceSynthesisCoordinator.kt:186` 只接受 `ChatActionType.VOICE_MESSAGE`、非空 actionId/text，跳过 READY/GENERATING；`:50` 只处理同会话的已完成 assistant 消息。 | 无普通旁白/台词逐拍自动朗读，无按用户选择的多场次内容串行播音；不能把已有协调器直接当陪伴播放器。 |
| 音频传输与文件 | `data/repository/tts/MimoTtsClient.kt:176` 获取完整响应中的 Base64 音频；`:203` 可取消 OkHttp Call；`data/repository/AudioFileStorage.kt:20` 保存应用私有音频，`:78` 校验 WAV/MP3 文件头。 | 可复用完整音频合成/校验。未发现 PCM 分块播放、字符时间轴或音频时钟字幕，不能承诺实时流式声音。 |
| 点播与释放 | `ui/component/roleplay/RoleplayActionMessageCards.kt:404` 每张卡片创建 controller，`:457` 离开 composition 后 dispose；`RoleplayVoicePlaybackController.kt:49` toggle，`:105` prepared 后 start，`:155` 校验活动 session。 | 已有单播放器准备/停止/迟到回调防护；卡片之间没有会话级唯一音频所有者，不能据此宣称快速换台词/模式全局互斥。 |
| 翻译 | `data/repository/ai/AiTranslationService.kt:42` 提供文本接口，`:94` 使用 `ProviderFunction.TRANSLATION` 对应配置，支持已有文本协议。 | 通用服务可复用，但当前 TTS 协调器和 RP 阅读页没有“选外语→翻译→播音”接线，亦无声音标签保真专用协议。 |
| 音频缓存 | `conversation/VoiceSynthesisCoordinator.kt:83` 记录音色 profile hash，`:186` 跳过已有音频；`model/ChatMessagePart.kt:1009` 从消息 metadata 读取文件路径。 | 这是消息产物持久化，不是按有效合成请求复用的陪伴缓存；不能把 profile hash 误称完整缓存键。 |
| 设置试听缓存 | `ui/screen/settings/VoiceSynthesisSettingsScreen.kt:1487` 仅按 Base URL/预置 voiceId 建试听键，文本是固定试听文案。 | 仅供设置页固定试听；不能套到任意台词、语种、音色模型的动态缓存。 |
| 声音收藏 | 扫描 main 中 voice/audio favorite 与“语音收藏/声音收藏”等模型和入口，未找到独立实体、收藏仓储或陪伴收藏动作。 | 若本轮包含，须明确独立资源所有权、来源版本、删除/清缓存语义与“未有音频时是否允许先付费合成”。 |
| 图像文件存储 | `data/repository/LocalImageStore.kt:20` 将选择器 URI 复制到应用私有目录；`:90` 删除时验证路径位于该目录。`di/AppGraph.kt:212` 真实装配。 | 可复用素材导入/引用/安全清理；不要把图片二进制复制进播放状态或角色文本。 |
| 背景/立绘 | `model/RoleplayScenario.kt:13` 起仅有场景背景与双方各一张本地/远程图；`ui/screen/roleplay/RoleplayScreenContent.kt:157` 角色图回退到 assistant.avatarUri；`ui/component/roleplay/RoleplayPortraitLayer.kt:49` 是头像卡片 spec。 | 已有场景图与人物展示，不是按情绪键切换的全身立绘/服装套装系统。未发现 sprite 字典、skin set、各套缺图回退或缩放/位移配置。 |
| 原生 RP 阅读 | `ui/screen/roleplay/RoleplayReadingMode.kt:70` 接收当前 `List<RoleplayMessageUiModel>`、标题、背景、行高与关闭回调。 | 是当前会话显示投影；没有导出、朗读或跨场次检索回调。详细阅读交互由 entry-ui 研究负责。 |
| 旧聊天导出工具 | `ui/screen/chat/ChatUtilitySheets.kt:329` 组 Markdown、`:550` 创建文本分享 Intent；`ChatScreenLaunchers.kt:131` 用 SAF 导出当前 uiState.messages。 | 可以提炼格式化/SAF 写文件模式，但这些属于旧 Chat 屏；`ui/navigation/AppNavHost.kt:65` 仅在活动 benchmark 会话注册旧 Chat 导航，不能算 RP 已支持“导出全部见面”。 |
| 资料库 JSON | `model/ContextDataBundle.kt:3` 明确包含角色、世界书、记忆、摘要、预设与 memoryArchive；`viewmodel/ContextTransferViewModel.kt:344` 从仓储组装。 | v2.4 记忆资料可复用；不是完整 RP 会话/所有原文/立绘/声音备份，也不是只分享外观的白名单格式。 |
| 地理定位 | main manifest 未声明精确/粗略/后台定位；main 搜索未发现 LocationManager、FusedLocationProviderClient 或 Geolocation。 | 当前没有手册的真实 GPS/隐私圈/地图供应商链路。陪伴核心无需新增这些权限。 |

### 2. 声音与缓存的设计约束

以下是规划建议，不代表已批准新功能范围。

1. 继续沿用 **文本生成、翻译、TTS、角色音色、陪伴播音开关** 的独立含义。现有 `VoiceSynthesisSettings.enabled` 是后台语音消息合成开关；将其直接视为“所有面对面台词自动出声”会改变旧设置语义。
2. 复用 MiMo 和现有音色配置即可实现可选点播；没有技术证据要求为基础陪伴增加第三家服务。若要手册的 ElevenLabs PCM/字符时间戳，属于新增 Provider/协议与验收范围，不应按“已有 TTS”估算。
3. 定义统一朗读单元：当前来源消息 ID + 源版本 + 稳定 beatId/内容摘要 + 角色身份；正文、视觉情绪、声音情绪/表演标签、实际朗读文本分别保留。
4. 新缓存的有效键需覆盖实际朗读文本、目标语言/翻译版本、Provider 端点标识、模型、音色配置与声学参数；密钥不写入缓存名、日志或导出。克隆样本内容变化不能仅靠路径相同被误命中。
5. 原有语音消息是已经生成的历史音频；“改音色后播放旧消息仍播原声音”与“以当前设置重新合成这句台词”是两种产品行为，应显式区分，不能静默覆盖历史消息产物。
6. 新陪伴音频资源由会话级控制器统一拥有，快速选新句、切模式、离开、换角色均递增播放代次，取消旧翻译/合成及播放器；存储完成也不能越过代次抢播。
7. 现有底层 HTTP 支持取消，controller 支持旧回调拒绝；但 `VoiceSynthesisCoordinator.kt:105` 的 `runCatching` 同样接住取消，`:132` 走通用失败分支。不能直接复制到新流程，应明确取消传播、状态恢复和产物清理。
8. 合成成功后保存/附着失败要保留可补存状态；当前按 messageId/actionId 命名音频、随后更新 metadata（`:115`、`:119`）并非一个跨文件/数据库事务。新功能需要来源版本复核与孤立资源处理。
9. 当前无持久声音收藏所有权。若纳入，收藏必须保存实际朗读文本/语言与音频快照；清缓存不能删除收藏，取消收藏不能删除源原文，编辑/删源后的标注与保留规则必须可解释。
10. 禁用、无音色/Key、翻译失败、音频文件缺失均保留文字；必须区分“未合成”“生成失败”“保存失败”“播放失败”，不将未开始播放称作成功。
11. 语种翻译是额外文本请求，TTS 可能多批；点击继续与点击本地下一拍/重播的计费边界不同。取消不承诺上游停止收费。

### 3. 素材、原文和分享边界

- **显示图不等于视觉输入**：现有场景图/头像消费在 Compose 展示层；新增皮肤不应隐式成为模型对地点或现实画面的认知。需要影响剧情的场景描述应作为明确文本事实处理。
- `RoleplayNoBackgroundSkinSettings` 是应用气泡/界面风格，不是手册的角色服装或表情立绘套装；两者不可合并命名。
- 已有图像导入返回稳定私有文件路径，适合复用；新增角色级立绘集需额外的稳定表情键、套装 ID、删除引用追踪和旧单图兼容。没有 chibi 类型字段，不能仅凭文件名推断素材类型。
- 图片装扮、原文记录、私人恢复包应保持不同导出契约。`ContextDataBundle` 不包含全局 TTS Key，但也没有陪伴装扮专用白名单；不能直接序列化整个新角色配置作为公开分享。
- 历史全文应从持久仓储查询。`data/repository/ConversationRepository.kt:91` 已有按 conversationId 读取原文；新按场次/角色/日期组合仍需由数据方案定义，不能仅遍历阅读页已加载列表。
- `roleplay/RoleplayTranscriptFormatter.kt:21` 服务模型/摘要/记忆消费（调用在 `RoleplayConversationSupport.kt:103` 等），不是保真导出格式；不可直接拿有限长度上下文摘录充当全部原文。
- 新导出需保留原始消息时间、说话人、场次、舞台原文；分页 50 条时“全部”仍查询完整可导出集合。TXT/Markdown 是阅读副本，不承诺恢复原有图像/声音/设置。
- 文件选择器与 MIME 写出可复用原生 SAF 模式；写入取消/失败不可提示成功。历史删改后的 v2.4 记忆失效链路由 data-memory 研究对接，声音收藏需另定义是否为用户独立副本。

### 4. 手册验收映射

| ID | 当前证据 | 建议规划落点与可观察验收 |
| --- | --- | --- |
| U04 | 已有单图回退，无情绪/服装图集 | 若纳入新立绘集：缺表情、失效路径、只有不适用素材时都有稳定占位；换套装不请求文本模型、不变角色事实。 |
| U11 | 已有语音消息关闭/缺 Key 保留文字测试；未有陪伴开关 | 复用设置门禁，补陪伴开启/关闭及错误入口；关闭状态不合成，未启动播放器不展示播放成功。 |
| U12 | 仅历史音频与设置固定试听缓存 | 明确历史音频/重新合成行为后，测试语言/音色/模型/克隆内容变化及同文不同角色不串音；有效新键才复用缓存。 |
| U13 | 单卡 controller 有旧回调门禁与 dispose 测试 | 补整个陪伴会话的换句/换角色/换模式/离开守卫，旧翻译、网络完成及 prepared 均不能抢播。 |
| U14 | MiMo 当前为完整音频；底层 HTTP 可取消 | 基础范围验证取消网络/文件播放和不保存失败产物；只有确认增加真正流式 TTS 时再验 PCM 停播、字幕时钟与残缺流不缓存。 |
| U15 | RP 阅读没有选区播音或跨场次串行读 | 若纳入连续朗读，按用户选中来源/顺序/版本播放，旁白策略明确，控制协议不被读出，不能只取当前场次。 |
| U16 | 旧 Chat SAF 导出取 UI 列表，不是当前 RP 导出 | 新原文导出以仓储为准；50 条 UI 窗口 + 121 条跨场数据验证完整、时间/说话人/舞台内容及取消/IO 失败。 |
| U17 | v2.4 记忆来源失效已存在；声音收藏未实现 | 衔接已有源版本/删除事件；如加声音收藏，验证编辑/删源/清缓存/取消收藏四种不同影响，不暗示能撤回已外发内容。 |
| U18 | 资料库是私有资料导出；无专用陪伴装扮分享 | 默认白名单、合成敏感字段夹具验证无原文/Key/音色样本/路径；若本轮不做公开装扮分享，明确列出延后而非宣称通过。 |
| G12 | 没有真实定位权限/服务 | 若不纳入定位，验证核心陪伴无新增定位权限、无地图库/请求；若纳入则需单独产品/供应商/隐私圈规格，现有代码不能保证。 |

### 5. 可复用测试入口（本轮未执行）

下列测试路径相对 `app/src/test/java/com/example/myapplication/`，设备测试另标：

- `conversation/VoiceSynthesisCoordinatorTest.kt:58`：成功写音频/metadata；`:98` 失败保留文本；`:128` 禁用/缺 Key 零请求；`:167` 文本设计；`:204` 克隆；`:250` 失败后重试。
- `data/repository/tts/MimoTtsClientTest.kt:87`：认证与音频解析；`:189` 远端 HTTP 拒绝；`:209` 空音频失败；`:233` 错误脱敏；`:270` 大响应限制；`:292` 读取响应期间取消 Call。
- `ui/component/roleplay/RoleplayVoicePlaybackControllerTest.kt:10`：prepared 前不 start；`:42` 准备中再次点击取消；`:93` 旧错误回调；`:152` dispose 幂等；`:181` 换 key 后旧 controller 不覆盖新状态。
- `data/repository/AudioFileStorageTest.kt:19`：有效 WAV 保存；`:52` 非法 Base64；`:67`、`:75` 大小边界。
- `data/repository/LocalImageStoreTest.kt:42`：scope 规整；`:70`、`:93` 私有路径删除边界；`:104` URI/空值拒绝。
- `data/repository/ai/AiTranslationServiceTest.kt:47`、`:88`、`:172`：Responses/Anthropic/指定翻译模型；`:213`、`:364`、`:396` 脱敏。没有证明声音表演标签翻译保真。
- 设备 `app/src/androidTest/java/com/example/myapplication/ui/screen/settings/VoiceSynthesisSettingsScreenTest.kt:142`：开始播放后才成功；`:209` 保存 clone 草稿；`:259` 关闭释放；`:296` 播放完成保留有效试听；`:335` 播放失败清理。
- 新陪伴测试还须覆盖来源版本 CAS、异步代次、全局音频互斥、缓存配置矩阵、跨场选区、全文导出、资源共享/清理及独立收藏；不能以现有测试文件存在当验收完成。

### 6. 可选范围与决策交接

建议分清三组，而不是默认照搬全部生态：

1. **核心体验依赖**：场次原文可读/导出、现有背景与角色图回退、开关关闭时文字完整；这些不需要新增外部服务。
2. **可复用 MiMo 的本版语音方案**：现有服务、Key/端点、角色音色与完整音频落盘可复用；单句点播/可选逐句自动播音仍需把线下台词接入服务，以及会话音频所有权、缓存/取消与来源协议。它不包含新增供应商或真正流式音频。
3. **需明确附加范围**：跨语种朗读能复用现有翻译服务，但仍是新增“翻译→缓存→播音”产品链路与额外文本费用；真正流式 TTS/字符字幕目前无对应实现；声音收藏目前无实体或入口。这三项不能被“接现有语音”默认包含，也不能未经决定从完整手册范围静默删除。
4. **其它可选范围**：多场次连续朗读、多套情绪立绘、公开装扮分享、真实定位。由主会话结合完整规划确认各自归属；不因手册分阶段就擅自推到其他版本。

不建议把手册旁侧的 SAR 商店、小屋、桌面 3D、云端主动生成或 GPS 当作陪伴先决条件；本主题未寻找/规划这些生态实现。

## External references

- `docs/agent-prompt-reference/date-companion-agent-handbook.md:571`–§12：素材与声音；`:809`–§17：历史、隐私与参考实现已知限制；`:1089`：44 项验收。
- `docs/agent-prompt-reference/01-date-companion.md:446`：D08 的三类情绪/表演通道；`:733`：D13 外语翻译是独立请求。
- 资料分析基线是 SullyOS `f93e28ec3d56451ed8f603597da4da7671dd3c64`、2026-11-05；其中 TS 路径及服务能力不是 Narra 已有实现。本次未查询第三方线上 API 文档，若决定新增 Provider/流协议需另做当前文档核验。

## Related specs

- `.trellis/spec/backend/directory-structure.md`：AppGraph 装配与 Repository/UI 边界。
- `.trellis/spec/backend/network-guidelines.md`：HTTPS、取消、脱敏与 MiMo 试听/克隆的实际成功条件。
- `.trellis/spec/backend/error-handling.md`：取消不得吞成普通错误，写入失败不能假成功。
- `.trellis/spec/backend/database-guidelines.md`、`memory-management-guidelines.md`、`memory-transfer-guidelines.md`：来源/版本、资料与资源的独立生命周期。
- `.trellis/spec/backend/ui-state-guidelines.md`、`ui-token-contract.md`、`quality-guidelines.md`：原生界面、状态/生命周期和必要截图/设备验证。

## Caveats / Not Found

- 缺失项基于所列生产路径与 main 搜索，未进行运行复现；没有把基准测试专用回放或旧 Chat 屏算作活跃陪伴功能。
- `ConversationTransferCoordinator` 负责转账收款，不是文件导出；不能按名字误用。
- 现有语音协调器的取消、补存、缓存键、跨卡互斥均需新功能层核查，不宣称本次发现的静态风险已经触发或已修复。
- v2.4 的记忆源码是当前工作区依赖；保持其改动，不在本研究修改或重新验收。
- 未确认产品范围前不增加服务、权限、素材或音频；上述可选项没有被本代理自行裁剪出版本。
