# D5 声音资源、收藏与迁移实施交接

2026-11-19，v2.4.1，作者 `voice_storage`。本线产品/测试源码已冻结；Gradle、formatter、KSP、设备由 root 唯一执行。以下是实现事实和待执行范围，不是验证通过记录。没有调用真实模型/TTS、操作用户库、改凭据/全局配置/版本参数/更新 JSON、提交或归档。

## 实际装配与接口

```kotlin
RoomCompanionVoiceRepository(
    database: ChatDatabase,
    publisher: CompanionVoiceFilePublisher,
    clock: () -> Long = System::currentTimeMillis,
    beforeAttachment: suspend (CompanionVoiceAudio) -> Unit = {},
)

PrivateCompanionVoiceFilePublisher(
    directory: File,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    clock: () -> Long = System::currentTimeMillis,
    afterAudioPublished: suspend () -> Unit = {},
)
```

`PrivateCompanionVoiceFilePublisher` 位于 `data.repository.voice`，生产目录为 `File(context.filesDir, "companion_voice")`。后两个回调是故障注入入口，生产保留默认值。仓储消费 root 冻结的 `CompanionVoiceRepository`，不拥有翻译/TTS 回调，不生成音频。

## 文件与 Room 的分阶段合同

- `claim` 先写 `companion_voice_resources` 的 `PENDING`、唯一当前 `cacheKey` 和 owner/lease；相同键不会并行占用两个当前资源。
- 发布使用本次私有 `.audio.part`，完整读取校验后原子移动最终 WAV/MP3，再写并原子移动 `.json` sidecar。校验包含容器/帧完整性、SHA-256 与字节长度。Base64 解码复用旧 `normalizeBase64AudioData` 的有界规则，旧 `AudioFileStorage` 未改。
- WAV 验证 RIFF 声明长度、fmt/data 块、采样格式/对齐/采样帧；MP3 验证完整 MPEG Layer III 帧及负载长度，不把 ID3 或同步头单独当成功音频。
- sidecar 保存实际 `CompanionVoiceAudio`，附件状态先为 `PENDING`；文件和回执已经持久化后，Room 挂接失败会返回该合法回执。Controller 应停在明确存储状态并使用 `retryAttachment` 补存。
- `retryAttachment`、`recoverPending` 和重开后的 `lookup` 验证同一文件/回执并幂等挂接为 Room `READY`；这些入口不会发出网络请求。Room 已提交而 sidecar marker 更新失败时，Room 的 `ATTACHED` 回执仍成立，marker 下次幂等修复。
- `isCurrent` 在文件/回执发布和 Room 最后恢复点均检查；取消传播，真实 target 由文件层清理。取消后的 `READY` 在释放仓储 mutex 前撤销；仍被本实例 claim/pin 的 `PENDING` 不由后台 lookup/recover 提前提升成功。
- 首次文件/sidecar 未全部持久化时不承诺跨进程恢复；已发起但没有合法副本的 `PENDING` 在 owner 释放后要求 `NeedsExplicitRegeneration`。显式重生成另建身份，既有收藏不会被覆写。
- 仅拿到文件路径不等于挂接成功。没有把文件与 Room 声称为同一个事务。

## 收藏、来源、缓存与清理

- `companion_voice_favorites` 只 FK 引用音频资源，删除策略 `RESTRICT`；没有 message/turn/conversation FK。收藏保存实际原/朗读文本、语言、完整 D4 Unit/range/source、独立 voiceEmotion 和实际音色身份。
- `favorite` 保存已有合法音频，重复操作幂等；`favoriteAudio` 返回原音频/文本快照并 pin，不重新翻译/合成。`unfavorite` 只移除收藏引用，不删除 canonical 或立即删文件。
- `isSourceCurrent` 复用 `CompanionSourceStorage.read` 的真实消息/回合/场次/COMMITTED attempt 图，再核 revision、rawHash、角色、protocol/parser 与配置来源版本。`observeFavorites` 同时观察资源和真实来源表；改/删源后收藏仍在，领域状态为 `UNAVAILABLE`。
- `lookup` / claim Existing / favoriteAudio 为当前 owner pin；`releaseOwner` 只清对应 owner。LRU 稳定按 `lastUsedAt,id` 回收无收藏、无活动 pin/claim 的音频，手动清理遵守相同保护。
- 用量报告缓存/收藏的资源字节与数量、待处理数。上限由 Controller/设置调用 `trimCache`，活动 pin 与收藏作为保护例外。
- `publishedReceipts` 只读。root 追加的 `cleanupOrphans(retainedAudioIds)` 在 trim/clear 时消费完整 Room 收藏、READY、合法待补挂接和活动 pin IDs；只清本目录下超过 24 小时且无保留引用的本层文件名。坏 sidecar 不能使 Room 收藏的实际音频被误删。
- 请求键仅使用冻结领域 `SpeechRequestKey`。持久参数拒绝 key/token/authorization/base64/path 别名与 Data URI，端点快照拒绝 userInfo/query/fragment。A 已在真实 HTTP 前处理相同新冻结入口边界，旧 URL 行为未变。

## 55→56 与 S3

- 当前版本从 55 单步升为 56，新增两张声音表和五个索引，`MIGRATION_55_56` 已加入 `ALL`；旧迁移和导出 schema 保留。
- 由 root KSP 生成/核验新的 `56.json`，本线没有手写或覆盖导出 schema。
- 固定 55 schema SHA-256：`5b33ea781a198962487b9324f054d2a243e0903f49c65ce00bf93b853b696033`。
- 固定 55 identity：`949d159c39b12196f82d50b2efb5748a`。
- 新 `s3-schema-v55.sql` 从固定 55 schema 原始 DDL/索引/setupQueries 生成，167 条语句；SHA-256：`b98c6e1f893c19c5095e2e6f18b45508d600ef593ce3679a67d9d462e01721ad`。旧 54 SQL 资产保留。
- S3 `BenchmarkContract`/builder/contract 与 FixtureSpec 单项更新为 source55→target56，相邻版本守卫保留，fixture 进程不提前执行迁移。
- 本线静态 SHA 检查覆盖初始清单的 45 份旧 schema，零字节变化；这不能代替 root 的真实固定55升级/当前 schema 校验。

## 最小待执行验证

新增 JVM：

- `com.example.myapplication.data.repository.voice.PrivateCompanionVoiceFilePublisherTest`：6 方法，完整 WAV/MP3、残缺/坏数据、真实 target 取消、迟到 guard/hash 和目录边界。
- `com.example.myapplication.data.repository.roleplay.RoomCompanionVoiceRepositoryTest`：6 方法，文件型 Room 关闭重开免费补挂接、实际来源观察/修订/删除、独立收藏/LRU/pin、并发 claim/未知结果、挂接后取消、坏 sidecar 收藏及无引用孤儿保护。
- `ChatDatabaseMigrationRegistryTest`：2 既有方法，维护最新迁移/连续注册。
- `LegacyUpgradeFixtureContractTest`：3 既有方法，维护固定 source55 SQL/identity/SHA 和真正相邻升级。
- `FixtureSpecTest.supportedUpgradeFixture_declaresMainProcessSourceSchema55`：只需定向这一项共享守卫，不扩大该类其它回放测试。

设备最小 selector：

`com.example.myapplication.data.local.chat.ChatDatabaseMigrationTest#fixed55To56_preservesAllLegacyRowsAndCreatesIndependentVoiceTables`

该方法从固定 55 bytes/identity 创建真实 SQLite，种入 legacy/linked 正文、已提交场次/回合/产物和记忆；对所有旧表逐字段前后比较，重开验证 Room schema/identity，再验证新 cache key 唯一/可空槽和收藏 RESTRICT/删除源不级联。

本线仅运行读取/定向静态搜索、SQL fixture 生成及旧 schema SHA 检查；没有执行 Gradle、formatter、设备或收费服务。测试数量是源码方法数，实际通过、失败和最终去重计数以 root 原始报告为准。

## 文件范围

路径相对仓库根；只列本线文件，不把父工作区的既有差异计作本线改动。

- `app/src/main/java/com/example/myapplication/data/local/companion/CompanionVoiceEntities.kt`
- `app/src/main/java/com/example/myapplication/data/local/companion/CompanionVoiceDao.kt`
- `app/src/main/java/com/example/myapplication/data/repository/voice/CompanionAudioIntegrity.kt`
- `app/src/main/java/com/example/myapplication/data/repository/voice/PrivateCompanionVoiceFilePublisher.kt`
- `app/src/main/java/com/example/myapplication/data/repository/roleplay/RoomCompanionVoiceRepository.kt`
- `app/src/main/java/com/example/myapplication/data/local/chat/ChatDatabase.kt`
- `app/src/main/java/com/example/myapplication/data/local/chat/migrations/ChatDbMigrations.kt`
- `app/src/main/java/com/example/myapplication/system/performance/BenchmarkContract.kt`
- `app/src/baseline/java/com/example/myapplication/system/performance/LegacyUpgradeFixtureBuilder.kt`
- `app/src/baseline/assets/benchmark/s3-schema-v55.sql`
- `app/src/test/java/com/example/myapplication/data/repository/voice/PrivateCompanionVoiceFilePublisherTest.kt`
- `app/src/test/java/com/example/myapplication/data/repository/roleplay/RoomCompanionVoiceRepositoryTest.kt`
- `app/src/test/java/com/example/myapplication/data/local/chat/ChatDatabaseMigrationRegistryTest.kt`
- `app/src/test/java/com/example/myapplication/system/performance/LegacyUpgradeFixtureContractTest.kt`
- `app/src/test/java/com/example/myapplication/system/performance/FixtureSpecTest.kt`
- `app/src/androidTest/java/com/example/myapplication/data/local/chat/ChatDatabaseMigrationTest.kt`

真实 MiMo 波形/语言/音色情绪质量和物理设备声音仍未测；结构性 MP3、合成 PCM 和 Fake 验证不能说成真实供应商效果。D6 接手导出/恢复时应保持收藏独立快照、来源失效状态、有效键/音频版本与完整引用保护，不从阅读窗口反推模型授权或删除资格。

## 独立检查 F3 的定向修正

root 反馈第05轮首次105 JVM/双APK通过后，独立 check 找到确定取消缺口：旧 `cancelPublicationLocked` 先删除文件再写 `PENDING`，删除 IOException 会留下 `READY`；合法 sidecar 也会被恢复入口重新挂接。

本次只修改 `RoomCompanionVoiceRepository.kt` 与对应 Test，已再次冻结。没有改 publisher/领域 port、Entity、56 schema、旧迁移或其它线文件，没有自行运行 Gradle/formatter/设备。

- 取消先用现有 `state` TEXT 列持久化 `CANCELLED` tombstone，并清该资源 claim/lease；已缺失资源行时也写入同 audioId 的 marker。成功写入后才尽力删除音频/sidecar。
- `lookup`、`recoverPending`、`retryAttachment`、旧 claim publication 及晚到 `favorite` 均拒绝取消 marker，不由合法旧 sidecar 重新写 `READY`。正常合法 `PENDING` 的免费补挂接不变。
- 取消不删除仍被既有收藏/其它有效 owner pin 引用的实际文件。已存收藏是独立挂接回执，可以使用原音频快照；这不改变缓存资源的取消状态，也不允许新增晚到收藏。
- 清理取消资源时仍先保留 marker，实际文件删除成功后才删除 Room 行；文件删除失败保留取消证据，不把清理报成成功。待清理数量包含取消 marker。
- 清理异常附入原 `CancellationException.suppressed` 并留下脱敏诊断，原协程取消继续传播。
- 数据库取消记录失败时，当前仓储实例的内存 guard 仍拒绝旧音频。如果取消记录和文件清理都不能成功落盘，**不能保证进程死亡后保留取消状态**；日志/异常保留这段失败证据，没有声称跨介质原子保证。正常已落盘 tombstone 可跨重开拒绝旧音频。

仅新增一个真实文件型 Room 方法：

`com.example.myapplication.data.repository.roleplay.RoomCompanionVoiceRepositoryTest.cancelDelete_reopenRejectsAudioAndKeepsFavorite`

同一方法分别覆盖发布后的 `PENDING` 和刚挂接的 `READY` 两个断点。真实 private publisher 先写完整 WAV/sidecar，Fake 只注入实际删除 IOException；验证仍存在的合法文件不会在重开后 lookup/recover/补挂接/晚到收藏/旧 publish 成功，手清失败仍保 marker，原 canonical 行和既有收藏逐值保持。原取消方法的状态断言同步为 `CANCELLED`，本类现7方法；方法名保持短，避免 Windows 文件型 SQLite 路径过长。

执行与实际数量由 root 定向 Controller 22 / 受影响 Room 7 的原始报告记录，本线不把新增源码方法当作已经通过，也不重跑此前已过105项。
