# D1 54→55 迁移合同

2026-11-09，生产DDL已加入ChatDbMigrations.MIGRATION_54_55，实体在data/local/companion/CompanionEntities.kt，CURRENT_VERSION=55。旧schema54及更早文件不改；schema55已由KSP生成并经过作者构建校验，正式独立/设备验证结果以最新检查报告为准。

- messages仅新增companionTurnId TEXT NOT NULL DEFAULT ''、companionMemoryState TEXT NOT NULL DEFAULT 'legacy'。旧行保持原ID、所有原文/时间/模式/来源版本。未知新状态不能回退legacy。
- companion_encounters：FK conversationId→conversations(id) DELETE CASCADE；普通索引conversationId/createdAt/id、characterId/personaKey/createdAt/id；activeKey独立可空唯一索引，ENDED/DRAFT不占活动键。
- companion_turns：FK encounterId→encounters(id) DELETE CASCADE；encounterId/ordinal唯一，inputMessageId和replyMessageId各自可空唯一；消息引用由事务校验，避免双向级联。
- companion_attempts：FK turnId→turns(id) DELETE CASCADE；索引turnId、state/leaseUntil。生成产物与尝试同一行，artifactJson/hash可空；请求描述无Key。
- 三表迁移初始为空，不为旧消息推断场次或新来源，旧2.4表/数据保留。
- 新字段与表的精确列名、NULL类型和索引名以已落盘Entity及DDL为准，任何后续DDL变更会先消息通知迁移代理。
- 纯领域ChatMessage另有@Transient companionSourceProof，由Room读取时关联图核验后填写，含资格与完整消息指纹；既不新增DB列，也不从JSON导入可信proof。copy改正文/状态/link后旧proof失效。

迁移测试应覆盖旧完整消息字段/来源保留、新列缺省、空新表、唯一索引（NULL不互斥）和三层cascade；S3使用固定54源SQL/identity/SHA，不提前迁移。
