# D5 独立检查（工程通过）

2026-11-19，原生 `trellis-check /root/voice_check`。按主会话分配只读全范围源码与验证证据，唯一写入为本检查记录及审计 JSON；不运行 Gradle、formatter、设备、真实模型/TTS，不覆盖三条实现线或用户改动。

最终全范围工程检查通过，没有剩余已确认产品问题。原JUnit XML最新107/107、15类；原instrumentation逐status重建8/8、3类，runner完成。类型检查、baseline编译、dev/test APK与guard后Lint通过。最终源码与设备第10轮对应，旧271保护项无漂移；真实MiMo/物理设备效果和新界面视觉接纳单列未测。

## Findings (fixed)

### F1：声音语言长度上限不一致

- 本检查发现 `CompanionVoiceViewModel.savePreferences` 允许并提示 80 字，但实际 `CompanionVoiceCodec` 只接受 60 字，61–80 字会先被 UI 接受再在保存层失败。
- root 在 `CompanionVoicePreferences` 冻结唯一 `MAX_LANGUAGE_LENGTH = 60`，A codec 与 C VM 共用并同步文案；已按当前源码确认不再各设上限。主会话第05轮包含这三源，执行结果待原始报告。
- 本检查没有直接改三源，修复由唯一文件 owner 完成。

### F2：来源观察异常后失去持续取消能力

- `CompanionVoiceController` 初始来源 collector 捕获普通异常后结束，但 Session 仍可接受新的显式播放。新请求的起始/准备守卫仍校验来源；已经开始播放后，来源修改或删除缺少持续观察，不能立即停止本段音频。
- A按第05轮结束后的授权修复唯一 `sourceObserver` Job/epoch：观察失效时自动播跳过，下一次显式请求重建；观察事件返回后核run generation，旧观察catch/finally按epoch拒绝新观察污染，收藏播放独立于来源监听。
- 本检查已复核新代码和单回归 `sourceObserverFailure_explicitPlaybackRestoresObservationAndDeletionStopsCachedAudio` 的首故障gate、恢复订阅、缓存复播与删源立即stop，TTS/publish只各一次；第07轮Controller22方法原XML通过。没有直接修改A源码。

### F3：取消清理失败会留下成功缓存

- `RoomCompanionVoiceRepository.cancelPublicationLocked` 先删除文件再写 PENDING；当 `publisher.delete` 抛出 IOException，catch只记录日志，已提交的READY行仍可被lookup命中。
- 对已发布sidecar但仍PENDING的同类失败，后续recover也可把已取消产物提升READY。应先持久化取消标识，再尽力清文件，lookup/recover明确排除取消产物，并维护清缓存/孤儿策略。
- B先持久CANCELLED tombstone，再尽力删除文件；lookup/recover/attach/旧publish/迟到收藏均拒绝取消缓存。实际已有收藏按独立回执保留，取消不把收藏重新升为READY。删除失败时清理保留marker，异常作为原取消的suppressed脱敏记录。DB与文件均无法写时仅当前实例内存guard，不宣称重启保障；没有schema变更。
- 已独立核对 `cancelDelete_reopenRejectsAudioAndKeepsFavorite` 的真实文件、PENDING/READY两分支、删文件失败、关闭/重开、lookup/recover/late挂接拒绝及收藏/原文保留。第07轮该类7方法原XML已独立复解析通过。

### F4：停止后仍显示补保存按钮但动作无效

- `stop(clearAudio=false)`保留已持久PENDING回执，却清掉lastRequest；UI仍提供“重试保存音频”，而retry因lastRequest为空直接返回。
- A新增纯回执补存分支：无旧请求时只retryAttachment，以独立work/generation守卫更新PENDING→ATTACHED；停止、离场或新选择取消且旧结果/finally不能污染新状态，不创建VoiceRun、不翻译/TTS/prepare/start。
- 已独立核对扩展原attachmentFailure回归，保留普通重试的prepare/start/完成断言，并覆盖stop→仅补保存及旧补存迟到拒绝。未增加method/port；第07轮Controller22方法原XML已独立复解析通过。

### F5：已知当前Key嵌入URL路径会进入公开身份

- 新freeze拒绝userinfo/query/fragment，但已知当前apiKey如果出现在URL路径，仍会进入identity.endpoint/toString，TTS身份又会持久到Room。示例使用合成Key `synthetic-secret-token` 与 `https://proxy.test/synthetic-secret-token/v1`，未请求真实服务。
- A在第08轮结束后定向修复：TTS冻结URI及翻译最终endpoint URI均拒绝rawPath或decoded path嵌入当前非空Key；固定错误不包含Key，不动旧接口与普通URL语义。
- 已独立复核原/encoded base path及自定义endpoint path负例、正常loopback与三音色正例，两个已有方法扩断言不增加method；第09轮两个方法原XML均通过、源码无漂移，新的DebugAPK和guard后Lint通过。

## Findings (not fixed)

没有剩余已确认产品问题。真实MiMo音质、物理设备与新UI视觉基准接纳不在Fake/本地合成WAV结果中冒充通过，具体限制见下文与handoff。

## 已独立核对的合同

- 新 voice/auto 默认关闭，旧 `VoiceSynthesisSettings.enabled` 仍只控制后台语音消息；新点播在独立偏好、Key/角色音色等守卫后发请求，不改 canonical。
- 自动播先取新来源，最终按真实 `getTurn().kind` 只允许 INPUT/CONTINUE 的 DIALOGUE；OPENING排除。UI `hasBoundContent` 与恢复静音基线防止生命周期效果先于首次内容绑定时误读旧答。
- Stage、原列表工具栏、Reader 和独立角色声音库都有生产 binding；只在 RESUMED 宿主触发声音，暂停、模式变化、离场停止。Reader保留完整跨场 selection/ranges，不进入主生成运行。
- D4 display offset 与 rawSpan 分开；分段使用同一 grapheme ends，原/朗读文本、Unit、来源版、speaker、voiceEmotion保持。没有再次清洗已显示正文，也未扩 PCM/逐字音频时钟。
- Snapshot 翻译读取一份 TRANSLATION 配置，HTTP和键共用有效端点/协议/模型及 trim 后输入。旧接口三方法、默认语言与构造器保持。请求/key从实际样本字节与MIME版本生成，不以路径/大小代替；凭据不进入身份、持久键或音频元数据。
- 会话 generation 与 player slot 在所有恢复点、prepared/start复核。只有 start 返回成功才 PLAYING；父scope取消通过独立 lifetime Job立即撤销，旧 finally不清新状态。有效音频已落盘的补挂接与已有收藏播放不调用TTS。
- 音频临时文件经完整 WAV 容器/采样帧或 MP3 帧长度验证，再同步写入、原子移动并发布 hash/长度 sidecar；Room状态与挂接回执独立。取消发布在释放仓储锁前撤销 READY，私有 target清理由文件 owner完成。
- 两张声音表独立；收藏只对音频 `RESTRICT` FK，不对消息/回合/场景 CASCADE。实际来源图用于失效状态；删改源保留音频和原/朗读文本快照。清缓存/LRU跳过收藏与活动 pin；每段结束先释放pin再回收。取消收藏不删原文。
- sidecar扫描只读，孤儿清理消费完整Room引用、合法待挂接和pin；坏sidecar不能据此删除仍被收藏引用音频。清缓存成功后由Session.select(null)撤销普通选区旧currentAudio，不另造有效性状态。

## Schema 与保护证据

独立记录见 [check-schema-and-protection.json](check-schema-and-protection.json)：

- 270个旧schema/PNG/版本参数/更新JSON保护项没有漂移。
- 55→56，旧55的56个实体定义与新56逐JSON实体完全一致，只增加 `companion_voice_resources` / `companion_voice_favorites` 两表及对应索引/FK；旧schema和旧迁移保留。
- 55固定库设备用例绑定原SHA `5b33ea781a198962487b9324f054d2a243e0903f49c65ce00bf93b853b696033` 与 identity `949d159c39b12196f82d50b2efb5748a`，对旧原文、link、产物、记忆及全部旧表快照逐字段比对，检查空声音表、nullable唯一key、收藏RESTRICT与重开。
- S3 source55→target56、固定55SQL/identity/SHA与builder、BenchmarkContract、registry/LegacyUpgradeFixtureContractTest/FixtureSpec相邻守卫已核，没有撤销版本守卫或操作用户库。

## Verification

以下为本次实际命令的原始结果，本检查独立复解析，未执行的真实效果不记为通过：

- 第01轮只格式化28个D5新Kotlin，26处变化、unexpected为空；本检查没有执行formatter。
- 第02轮 debug/baseline 并行KSP读新56的EOF失败保留；生成后的56已完整可解析，仅新56改变。第03轮UI三个类型错误保留；C定向修复后，第04轮debug产品/JVM/AndroidTest编译与dev/test双APK通过，源码无漂移。
- 第05轮实际105/105 JVM通过并完成打包，validation记录sourceStable=true；本检查已独立复解析保存的原XML。
- 第07轮Controller22+Room7实际29/29、源码无漂移；F2/F3新增方法与F4扩原方法后latest为107/107，不能累计成134项。本检查重建结果与记录无计数差异。
- 第09轮F5两个已有方法原XML均通过，latest仍为107项；Debug/AndroidTest/baseline编译、dev/test打包及guard后Lint通过，源码无漂移。DebugAPK SHA为 `2c33b68226a4ed1f27cdd60d1af0d13aaa91ee805ef550dc3875ca0ed0527b83`。
- 第08/09轮Lint报告SHA均为 `26145f49414728af3ec636d57142d4d80e2c77eda6ca88ebd2500d713e4349da`，独立解析0 Error/Fatal、57 Warning、3 Hint；不记为零告警。原XML/原instrumentation均按class+method取最新，不累加重复执行。
- 第10轮设备8/8、33.216秒，原status逐class+method均为0；`OK (8 tests)`与`INSTRUMENTATION_CODE: -1`完整。涵6 Compose、1真实本地合成WAV系统player、1固定55→56升级；本检查与记录无差异。安装test APK SHA为 `b48cc27bf46f1b7db255691f3969a36df1f5499b10cdb33c59f962044853c92b`，dev/test均与设备环境记录吻合。
- D4的149 JVM/33设备只沿用为前置；旧34截图差异与D3候选逐字节一致且未接纳，不重跑或替换基准。

## 真实效果与交付限制

测试边界为合成数据、Fake/MockWebServer与授权 `Narra_Memory_V24 / emulator-5580`。UI→binding→VM→Fake Session只证明真实回调和状态；合法本地合成WAV的原生MediaPlayer测试只证明系统准备/开始/停止。真实MiMo质量、克隆近似音色、物理设备音频路由/焦点和声音效果单列未测。首次没有任何持久音频副本时不能保证进程结束后的恢复；本版本只交付完整响应和分段串行，译文为有限会话内存缓存。

最终PRD五项AC、implement/design、七节voicespec/数据库56、父矩阵/下一块D6、版本记录与handoff已由root同步，任务completed并仍保留原目录。源码相对起点30新增/19修改/0删除，共49个代码/资产/schema路径；最终代码1491项与第10轮一致，收尾版本文档变化单列，不要求重复代码验证。证据见 `check-final-methods.json`、`check-final-validation-binding.json`、`check-final-scope.json` 和主会话 `validation/final-source-binding.json`。没有提交、推送、发布或自动归档，本块通过不代表整个2.4.1或真实供应商效果已交付。
