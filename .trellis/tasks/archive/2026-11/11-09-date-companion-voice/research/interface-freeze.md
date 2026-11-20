# D5 领域接口冻结与所有权

2026-11-19，v2.4.1；现有任务已激活，用户批准完整 D5，不重新规划。D4 工程前置沿用 149 JVM / 33 原生最新去重证据；旧截图差异保留未接纳。初始源码/旧 schema SHA 在 `validation/baseline-files.json`。

## 冻结接口

- `model/CompanionVoice.kt`：独立默认关闭偏好、显示 owner、统一 key、实际合成参数身份、D4 显示选区/分段、校验文件回执/挂接状态、收藏/用量、声音状态、唯一 Session 和可注入 Player。
- `data/repository/roleplay/CompanionVoiceRepository.kt`：lookup/claim/publication、免费补挂接/重开恢复、owner pin、真实来源校验与观察、收藏及有限 LRU/清理。仓储不生成音频，Session 持有唯一翻译/TTS 回调。
- 翻译在旧 `AiTranslationService` 外增加 `SnapshotAiTranslationService.freeze(AppSettings)` 与显式 snapshot `translateText`；旧接口、构造器、三个方法和默认语言不变。快照请求不二次读取 SettingsStore，缓存只取无凭据身份。
- 请求键作者负责规范化实际 endpoint/model/文本/语言、builder 版本和有效参数；克隆从本次实际发送字节计算 SHA。各缓存消费者只使用领域 key。声音情绪仅保留出处 metadata，不追加未支持 MiMo 字段。
- 语言输入上限统一取 `CompanionVoicePreferences.MAX_LANGUAGE_LENGTH = 60`，UI与codec共用，避免UI接受61–80字而持久化拒绝。该共享常量来自独立check发现后的定向收口。
- 分段以 `unit.text` 的显示范围为坐标，保留完整 Unit/source/voiceEmotion/rawSpan，所选旁白及旧场顺序不丢失。自动播查 `getTurn(source.turnId).kind`，拒绝 OPENING，不由位置或类型推测。
- `PLAYING` 只发生在本代次 player.start 成功之后；换句、模式、角色、配置、源或离场取消翻译/TTS/准备/下一段。已有收藏播放使用音频快照，不重新翻译或合成。
- 文件先私有临时写、完整 WAV/MP3 验证、hash/长度、发布回执，再 Room READY 挂接。合法持久回执挂接失败可重开补存；首次无持久副本不保证进程死亡后恢复，不自动重发未知请求。
- 文件引用清单由仓储合并收藏、READY、合法待补挂接和活动pin后传入 `cleanupOrphans(retainedAudioIds)`；文件层的回执扫描只读。损坏/丢失sidecar不能作为删除仍被Room收藏引用音频的依据，只清理超24小时且本层确认无引用文件。

## 独占所有权

| 作者 | 文件范围 |
| --- | --- |
| root | 上述两份冻结合同；AppGraph 装配；本任务文档/JSONL/验证脚本和日志；规范、父矩阵、版本记录；唯一 Gradle/formatter/AVD；生成新 56 schema |
| voice_runtime（trellis-implement） | CompanionContext.kt/CompanionSettingsCodec.kt 的 voice 小映射及新 VoiceCodec；AiTranslationService.kt 附加冻结接口；新有效请求/key/翻译缓存与 CompanionVoiceController/串行分段；system/audio 的新原生 Player；相应 JVM 测试 |
| voice_storage（trellis-implement） | 新 Voice Entity/DAO、RoomCompanionVoiceRepository、私有文件 publisher；ChatDatabase.kt/ChatDbMigrations.kt 的 55→56；registry/真实固定55库升级/S3 source55→target56 fixture 及必要文档；对应 JVM/Room/设备测试，不写旧 schema |
| voice_ui（trellis-implement） | 新 CompanionVoiceViewModel、设置/声音状态/收藏与动作组件；Stage/Reader/CompanionPresentationRoutes/RoleplayNavGraph/RoleplayScreenContent/AppRoutes 的必要接线；AssistantDetailScreen/SettingsAssistantNavRoutes 的单个独立角色声音入口；新实际 Compose/原生声音用例与必要 VM 测试，不写 AppGraph/设置codec/声音controller/存储 |
| 独立 trellis-check | 完成后只读全面复核；发现失败交唯一文件 owner，root 统一定向复验 |

三条实施线共享冻结合同，不各造平行 DTO，不并行修改同一文件，不运行构建、formatter、设备或真实收费服务。只用合成数据/Fake/MockWebServer/已授权 Narra_Memory_V24 隔离 AVD。不 reset/revert、改凭据/全局配置/版本参数/更新 JSON/旧 PNG，不提交/推送/发布/自动归档。
