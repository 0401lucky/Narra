# D5 语音、翻译与收藏交接

2026-11-19，v2.4.1，现有任务 `11-09-date-companion-voice`。完整D5工程与真实UI接线已通过独立验收，任务标记completed并留在原目录，不归档。完整批准方案已直接实施，没有新建任务或重新确认需求。

## 实际产品与接口

- Stage 本拍点播、列表朗读选段、Reader 跨场次有序选区、独立角色声音库和偏好已接真实 UI。声音库从角色详情、会话和 Reader 可达，删除源消息/场景后仍有独立入口。默认陪伴语音与自动播关闭，不改变旧 VOICE_MESSAGE 后台开关。
- `model/CompanionVoice.kt` 定义 `CompanionVoicePreferences`、owner、两种 key、segment/clip、文件回执、收藏/用量和 `CompanionVoiceSession`/Player ports。角色 `voice` 小映射通过既有 expectedRevision CAS 保存；语言上限共用 `MAX_LANGUAGE_LENGTH=60`，未来版本及未知字段保留。
- `CompanionVoiceController` 统一拥有翻译、TTS、发布回执、prepare/start、队列和停止。模式/角色/选句/有效配置/来源/离场变更撤销旧代次。只有本代次实际 start 成功才 PLAYING；D4 BeatPlayer 仍只控制本地显示。
- 自动播只处理新来源台词，并读取关联 `turn.kind`，只允许 INPUT/CONTINUE，排除 OPENING。首挂载、先 RESUMED 后首 bind、恢复/换模式静音建立基线，后台宿主不自动发请求。
- `CompanionVoiceSegments.from` 消费完整 D4 Unit/range，偏移针对显示 text，rawSpan/rawText 作出处；按完整 grapheme 在约800 UTF-16单元内分句/段，超长单簇保留。所选旧场与旁白顺序、speaker/voiceEmotion 和源修订不丢失，不改 canonical。
- AppGraph 提供 `companionVoiceRepository` 与 `createCompanionVoiceSession(scope)`；UI/ViewModel只依赖领域 ports。生产绑定复用 `rememberCompanionVoiceBinding`，独立路由为 `AppRoutes.roleplayCompanionVoice(assistantId)`，参数编码/解码配对，不进入主生成运行。

## 翻译、有效参数与缓存

- `SnapshotAiTranslationService.freeze(AppSettings)` 解析一份实际 TRANSLATION Provider/model/规范化端点/protocol/mode，显式 snapshot 请求复用原三协议 helper，不二次读取 SettingsStore。旧 AiTranslationService 三方法/default语言、Default 构造器和旧 getter 类型保留。
- `CompanionTtsRequestFactory.freeze` 使用已有 MiMo preset/design/clone 和角色音色，忽略旧后台 enabled。克隆样本本次读取一次，HTTP Data URI 和样本 SHA/MIME 来自相同实际 bytes；同路径同大小替换内容不串缓存。
- `CompanionSpeechRequestKeys` 统一长度前缀 SHA-256，覆盖实际 trim 文本、语言、翻译配置键、endpoint/model、角色/音色模式、format/voiceId/promptHash/clone样本hash+MIME/instructionHash及builder版本。内存/磁盘/收藏共用同一 key，凭据、负载和样本路径不进持久参数。
- 新冻结入口拒绝认证 userInfo/query/fragment 和路径中的当前 Key，旧 URL 行为不改。翻译失败提供重试翻译或显式读原文，不静默换语种。
- 翻译缓存为当前 Session 内存 LRU，128项/256000字符；音频为持久缓存。重启后译文内存缓存不保留，已有收藏直接使用原音频/朗读文本，不重新翻译/TTS。

## 资源、恢复与收藏

- `PrivateCompanionVoiceFilePublisher` 位于 `data.repository.voice`，目录为应用私有 `filesDir/companion_voice`。有界解码→私有临时文件→完整WAV容器/采样对齐或MP3帧验证→hash/长度→原子发布音频及sidecar。
- `RoomCompanionVoiceRepository` 管 claim、PENDING/READY、owner/pin、回执与引用，不持有网络生产回调。文件和 Room 分阶段，attachmentState=PENDING 的有效持久回执可由 retryAttachment/recoverPending/重开 lookup 免费补挂接。
- 已持久音频收藏零翻译/TTS；未有音频的“生成并收藏（可能计费）”为显式动作，串行保存所选各段。收藏存实际音频、原/朗读文本、语言、完整来源版本及音色身份，回放历史产物不改用当前音色。
- 收藏只 RESTRICT 引用音频，不 FK 级联到消息/回合/场景。isSourceCurrent复用真实来源图，删改源收藏保留并标UNAVAILABLE；取消收藏只减引用，不删原文。
- 音频默认64MiB上限，可设1..512MiB；每段播放或收藏完成释放owner后执行LRU。清理跳过收藏及活动pin/claim。回执扫描只读，仓储合并完整引用后清超24小时无引用孤儿，坏sidecar不误删收藏。
- 源观察异常/意外结束停止依赖源的声音，下一次用户显式请求重建唯一观察Job；auto不在失去观察时继续，收藏独立。停止后保留的PENDING回执重试仅补存，零prepare/start，不恢复已停止队列。
- 取消先写现有state列的CANCELLED tombstone，再尽力删除；lookup/recover/旧publish/补挂接/晚到收藏拒绝已取消产物。删除失败不能重新变成功缓存。

## Schema 与验证

当前主库 55→56，新增 `companion_voice_resources` / `companion_voice_favorites` 和5个索引。旧55的56个实体逐JSON定义保持，45份旧schema与旧迁移保留；没有操作用户真实库。

- 固定55 schema SHA：`5b33ea781a198962487b9324f054d2a243e0903f49c65ce00bf93b853b696033`，identity：`949d159c39b12196f82d50b2efb5748a`。
- 56 identity：`37d3992a3b6f8544d71ead1e129bccef`，由root KSP导出。
- S3固定 source55→target56；新 `s3-schema-v55.sql` 167语句，SHA：`b98c6e1f893c19c5095e2e6f18b45508d600ef593ce3679a67d9d462e01721ad`。旧v54资产、registry/FixtureSpec相邻守卫保留。

实际命令、退出码、时间、JUnit XML/instrumentation、APK和源码前后SHA在 [validation](validation/rounds.json)。按class+method取最新，不累计重复轮次；D4的149 JVM/33设备只作为前置，没有重跑D4。

| 验证 | 当前实际结果 |
| --- | --- |
| 01/06 IDE格式化 | 28新Kotlin、后续4个修复文件，均无越界变化 |
| 02/03 首次失败 | 双KSP新schema竞争EOF、UI三处类型错误，原失败保留；拆开编译/两个UI小hunk修复 |
| 05 JVM/双APK | 105/105通过，sourceStable=true |
| 07 例外路径定向 | Controller22 + Room7 =29/29通过，最新去重107/107、15类；源观察/取消tombstone/停止后免费补存已绑定新源码 |
| 08 baseline/Lint | 编译与Lint成功，0 error、57 warning、3 hint；相对D4增加的2条仅当前AGP版本提示，无D5声音告警 |
| 09 最后凭据边界 | 两现有方法2/2、最终Debug/test APK/baseline编译/Lint成功，523.451秒、源码稳定；只定向2方法，无全量重跑 |
| 10 隔离设备 | 8/8、3类、33.216秒：6真实Compose + 1本地WAV原生播放器 + 1固定55→56升级；runner OK(8 tests)/CODE -1，源码稳定 |
| 独立检查 | [check.md](check.md)，原XML/原instrumentation最新107 JVM/8设备、最终源/271保护及Lint/APK独立重建核对通过 |

实际测试源码共61个本块新JVM方法，另40个旧语音及6个迁移/S3守卫，共107最新方法。05=105、07=29、09=2是实际分轮执行数，不能累加成136。设备8项是一轮完成，没有因为旧视觉差异扩跑D4。

源码相对起点新增30/修改19/删除0，共49个本块源码/资产/schema路径；版本记录等文档另外同步。起点1461项清单含版本记录文件，最终代码1491项保持测试10-after；收尾版本文档变化不触发代码重验。完整路径/前后SHA见 [独立范围](check-final-scope.json) 与 [验证绑定](check-final-validation-binding.json)。旧271保护项（含45旧schema、旧S3 v54资产/PNG/发包参数/更新JSON）无漂移。

验证及安装的本地APK见 [设备环境](validation/device-environment.json)：

- dev `Narra-v2.3.0-dev-20300-dev.apk` SHA-256：`2c33b68226a4ed1f27cdd60d1af0d13aaa91ee805ef550dc3875ca0ed0527b83`。
- test `app-debug-androidTest.apk` SHA-256：`b48cc27bf46f1b7db255691f3969a36df1f5499b10cdb33c59f962044853c92b`。
- 设备仅 `Narra_Memory_V24 / emulator-5580`，安装命令/回执及所有instrumentation在validation；没有操作真实用户库或付费接口。

D4旧34截图差异与D3逐字节一致的历史证据保留未接纳。本轮没有record/替换旧PNG或重跑D4截图，不把D4历史像素结论当成D5新界面视觉验收。

## 限制与 D6 接手

- 当前仅 MiMo 整段 Base64 响应与按源分段串行播放，没有PCM增量、逐字音频时钟或假字幕。voiceEmotion仅metadata，VoiceDesign同Prompt不保证同音色。
- 真实MiMo波形/译文朗读语种/音色克隆质量与物理设备扬声器、蓝牙/耳机、系统路由/焦点效果未测。Fake/MockWebServer证明调用和合同；本地合成WAV只证明原生准备/开始/停止，不能作为供应商质量结论。
- 首次没有合法持久文件+回执时，当前Session可保留单个有界响应，进程结束后不能保证恢复。若取消标志与文件清理均无法落盘，当前实例取消集合只保证当前进程，不能无条件承诺重启后的取消状态。
- D6导出/资料恢复从仓储查询完整收藏/资源与来源版本，不把Reader已加载窗口当全部历史，不恢复已取消或未知产物，不让旧音频补挂接变成重新合成。保留收藏独立、source失效及完整引用闭包；公开分享继续排除凭据/克隆样本/私有路径/私人原文。
- D6负责最终完整版本矩阵、导出/历史集成、受影响视觉候选和既有基准接纳；不能把本块工程通过当整个2.4.1发布。

版本参数/更新JSON未改，本地dev APK仍使用现有 `2.3.0-dev/20300` 命名，功能归属2.4.1。没有提交、推送、发布或自动归档。
