# D6 资料与装扮实施交接

2026-11-19，原生 `trellis-implement`：`/root/d6_transfer`。D5 已由主会话核实后激活现有 D6；本块功能归属 v2.4.1。以下是已落盘接口及待执行验证，不能视为工程门禁通过或发布。

## 已实现合同

- 公开 `CompanionAppearancePackage(version=1)` 只有获准的显示选择、套装/表情、布局、资源内容映射与有限 PNG。没有 Assistant、人设补充、记忆/原文、Key、声音样本、私人路径或场景运行 ID；未知字段/版本、重复 ID/字段、越界引用/尺寸、hash/CRC 错误和带文本等附属内容的 PNG 在预览前整包拒绝。
- 生产公开导出只读当前角色的本机演出素材，转换为无附属元数据的 sRGB PNG，按实际字节计算 SHA-256。图片/套装 ID 使用包内 ID，导入再按本机操作和角色映射。重复内容只保留一份图片内容及一个私有发布文件；旧资源/套装及本场覆盖保留，不删共享图片。
- 私人 `ContextDataBundle` 生产导出为 v3，尾加可空 `companions`。只扩现有角色偏好/装扮承载域，保留 v2.4 授权记忆/全部版本及来源语义快照；没有全聊天、音频、声音收藏、主回合或可执行后台任务备份。旧 v1/v2 或缺域仍保留目标现有陪伴映射，v4 及更高版本拒绝。出处 `bundleVersion` 同步支持已实现的 1–3，未来版本仍拒绝。
- DTO 重建返回、codec、normalize、section filter、isEmpty、count、preview、confirm、恢复返回都识别新域。陪伴偏好可作为 ALL 中的独立资料内容，旧角色包/世界书/记忆/预设的范围筛选保持。
- `RoomContextTransferRecoveryRepository` 冻结包、目标 Assistant/陪伴 revision、记忆目标计划、Room 对象 precondition 和资源内容。大计划仅在应用私有文件中，Room 新表保存无正文 hash/阶段/回执；不把 Base64 或第二份聊天正文放进 Room。
- Room 的资料修改及 `ROOM_COMMITTED` 回执在同一事务；记忆仍走 v2.4 导入/目标/出处/状态 CAS，用外层事务保留全部回滚与提交后通知。无 memory 包的世界书和预设也有可重开回执，提交后重试不重新覆盖 Room 用户后改。
- 文件阶段按内容 hash 发布，私有同目录硬链接只创建新入口，存在目标时校验内容后复用，不能覆盖 changed 文件。文件阶段完成后只读复验 hash 再挂接；首次阶段未完成的重复发布复用已有合法内容。临时文件只清本次所有者文件，旧/共享发布内容不清。
- 每个设置目标通过 `SettingsStore.applyContextTransferTarget` 一次 DataStore edit 执行 Assistant CAS、已带域的 companion revision CAS、仅目标字段更新与操作/计划/动作 hash 幂等回执。旧缺域不碰陪伴映射，未更新角色保留原始未知 JSON，selectedAssistantId 不被整列表覆盖。已成功但尚未确认到 Room 的目标可以免费补回执，重复操作不升 revision、不复活删除角色，也不覆写后改。
- 回执阶段为 PLANNED → ROOM_COMMITTED → FILES_COMMITTED → COMPLETE，另有取消预览 tombstone。部分目标冲突保留已完成阶段，待用户重新预览剩余目标；不声称跨 Room/DataStore/文件原子性。重复本机计划 ID 不覆盖、删除或复用旧计划。
- 既有 Tavern 图片角色的头像输入现在可放入同一冻结文件计划；准备时只规范化字节，Room 成功后才发布/挂接。进程重开不重读 mutable 原头像输入、不重复已完成头像工作。ContextDataBundle 没有因此增加头像/音频备份域。没有恢复仓储的既有测试/兼容调用仍保留原 avatar saver 默认行为。

## 最终接口与装配

源码路径均相对 `app/src/main/java/com/example/myapplication/`：

- `model/CompanionTransfer.kt`：公开/私人 DTO、阶段/摘要、设置目标及结果、文件/头像 ports。
- `model/ContextDataBundle.kt`、`ContextImportNormalization.kt`：可空新域及范围规范化。
- `data/repository/context/CompanionAppearanceTransferCodec.kt`：公开及私人新域白名单，完整 PNG/hash/尺寸/CRC 验证。
- `data/repository/context/CompanionTransferFileStore.kt`：`CompanionTransferFileStore` / `PrivateCompanionTransferFileStore(context)`。
- `data/repository/context/ContextTransferRecoveryRepository.kt`：`ContextTransferRecoveryRepository : CompanionAppearanceTransferRepository` 和同一份 Room 实现。
- `data/local/ContextTransferSettingsCodec.kt`、`AppSettingsStore.kt`：目标 CAS + 同 edit 回执；不修改凭据键。
- `viewmodel/CompanionAppearanceTransferViewModel.kt`：factory(characterId, repository)，exportPackage/previewImport/confirmImport/dismissPreview/resumeOperation/consumeMessage；pending 按当前角色过滤。
- `viewmodel/ContextTransferViewModel.kt`：构造/factory 尾加 `contextTransferRecoveryRepository`，state/preview 尾加 `companionCount`、state.pendingOperations，`resumePendingImport(operationId)`。显式继续才恢复，不在挂载时写入。
- `data/repository/context/ContextTransferCodec.kt`、`MemoryArchiveTransferValidation.kt`：v3 返回/版本与出处兼容。

生产构造已发给 `/root/d6_ui`，由其独占 AppGraph/Nav/Screen 接线：

```kotlin
RoomContextTransferRecoveryRepository(
    database, settingsStore, worldBookRepository, presetRepository,
    memoryTransferRepository, PrivateCompanionTransferFileStore(context),
)
```

同一实例供公开装扮 VM 与私人 ContextTransfer VM。公开 `prepareAppearanceImport` 返回 operationId/targetName/outfitCount/imageCount；Summary 为 operationId/kind/stage/title/completedSettingsTargets/settingsTargetCount，尾加 vectorsNeedingRebuild/targetAssistantIds。

## Schema 与保护

主库实际从 D5 的 56 相邻前向到 57，只新增 `context_transfer_operations` 与 `(kind,stage,createdAt,id)` 索引，注册 DAO/实体/迁移。57 JSON 由主会话 KSP 导出，本代理没有手写或改旧 schema。

- 固定 56 SHA：`d5e2dff049818be64c7ab4b3bf2b767b99f1bc0546756f10a333ca05e903eab7`，identity `37d3992a3b6f8544d71ead1e129bccef`。
- 固定 55 SHA：`5b33ea781a198962487b9324f054d2a243e0903f49c65ce00bf93b853b696033`，未变。
- 新 `s3-schema-v56.sql` 174 SQL 语句，SHA `57da481f5082135f40b13bdbc4ac722c5b217e02030f3f1e485d84b92e59af51`；`research/generate_s3_v56.py` 从固定 56 定义生成并验证，实际 exit 0。
- 旧 v55 SQL SHA `b98c6e1f893c19c5095e2e6f18b45508d600ef593ce3679a67d9d462e01721ad`，未变；旧 v54 SQL 保留。
- 更新 `BenchmarkContract`、baseline `LegacyUpgradeFixtureBuilder`、registry/FixtureSpec/S3 guard。旧 D5 fixed55To56 方法的 latest 目标断言只同步 CURRENT_VERSION，旧全表/音频/收藏断言保留。新固定 56→57 设备测试检查全部 58 旧表、来源/人工记忆/独立声音及新回执重开。

## 验证交接（尚未运行）

声明 30 个新增 JVM 方法、2 个新增原生方法。数量是源码声明，不是通过数量；没有运行 Gradle、formatter、截图或设备。主会话独占这些工具。

相关 selector：

```text
com.example.myapplication.data.repository.context.CompanionAppearanceTransferCodecTest (7)
com.example.myapplication.data.local.AppSettingsStoreContextTransferTest (7)
com.example.myapplication.data.repository.context.ContextTransferRecoveryRepositoryTest (12)
com.example.myapplication.data.local.chat.ContextTransferSchemaContractTest (1)
com.example.myapplication.viewmodel.ContextTransferRecoveryViewModelTest (3)
com.example.myapplication.data.local.chat.ChatDatabaseMigrationRegistryTest
com.example.myapplication.system.performance.LegacyUpgradeFixtureContractTest
com.example.myapplication.system.performance.FixtureSpecTest
```

原生：

```text
com.example.myapplication.data.local.chat.ContextTransferMigrationTest#fixed56To57_preservesEveryOldTableAndIndependentAudioAndNewReceipts
com.example.myapplication.data.repository.context.CompanionTransferFileInstrumentedTest#privateHashPublicationAndPlanReceipt_areDurableAndNeverOverwriteChangedFile
```

JVM 包括真实 DataStore 关闭重开、逐目标部分成功/缺 Room ack、删除目标不复活、旧包及未知字段保值、冻结计划与发布文件篡改、local ID 冲突、文件型 Room 关闭重开、无 memory 后置设置失败、全 Room 回滚/记忆出处、头像断点、内容去重和公开敏感夹具。文件 Room 的 ContextWrapper 使用独占短临时目录和短库名，保留真实持久恢复。

最终完整 JVM 会覆盖旧 ContextTransfer、MemoryArchive/vector/外层事务；新原生文件方法针对当前 Android NIO 发布实际验证，不能用 Windows/Robolectric 结果代替。

## 限制与后续

- 公开包为静态 PNG，单图 4 MiB、总图 12 MiB、包/冻结计划 16 MiB；不能自动下载旧 URL 图、带任意外来路径或大资源。过大资料明确拒绝，保留目标，不默默截断。跨角色私人偏好的累计体积也有界。
- 计划冻结首次未成功时，没有无条件跨进程恢复保证；没有业务 Room/设置写入。持久阶段后按实际回执恢复。失败操作的合法未挂接内容随待恢复计划保留；不把关闭预览伪装成跨存储回滚。
- 完整 UI/生产 AppGraph 入口由 d6_ui 接线并待主会话验证；真实模型效果、物理设备/视觉接纳未由本代理测试。
- 全部实施文件所有权现在释放给主会话与原生检查代理。只有收到主会话失败修复指派才继续写入。

版本参数、更新 JSON、旧 PNG、真实用户库和凭据未修改。没有提交、推送、发布或归档。
