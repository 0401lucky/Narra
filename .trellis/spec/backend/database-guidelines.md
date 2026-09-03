# 持久化规范

## 存储选择

- Room：会话、消息、世界书、记忆、角色扮演、手机快照、信箱、朋友圈、
  经济系统等结构化业务数据。
- DataStore Preferences：`AppSettings` 等小型配置和用户偏好，入口为
  `data/local/AppSettingsStore.kt`。
- Android Keystore + 私有 SharedPreferences：API Key、搜索 Key、语音 Key，
  入口为 `SecureValueStore.kt`。
- 文件存储：图片、音频、导入导出等大对象。数据库只保存路径、URI 或元数据。

不要把密钥写回普通 DataStore JSON，也不要用 Room 保存大型 Base64 内容。

## Room 结构

唯一数据库为 `ChatDatabase`：

- `version = ChatDatabase.CURRENT_VERSION`，当前为 48。
- `exportSchema = true`，KSP 输出到 `app/schemas/`。
- 迁移通过 `ChatDatabase.ALL_MIGRATIONS` 暴露，并由 `AppGraph` 注册。
- 不使用 `fallbackToDestructiveMigration()`。

命名沿用现有模式：

- 表名：`snake_case` 复数，如 `conversations`、
  `roleplay_group_participants`。
- Kotlin 存储类型：`XxxEntity`；DAO：`XxxDao`。
- 列名沿用 Kotlin `camelCase` 属性。
- 主键多数为 UUID 字符串 `id`；时间戳为 `Long` 的
  `createdAt`、`updatedAt`、`lastUsedAt`。
- 外键、过滤条件和排序热路径建立索引；依赖级联删除时显式声明
  `ForeignKey(..., onDelete = CASCADE)`。

参考 `ConversationEntity.kt`、`MessageEntity.kt`、
`RoleplayScenarioEntity.kt` 和 `MomentEntities.kt`。

## DAO 与查询

- 观察数据：`fun observeXxx(...): Flow<T>`。
- 一次性读取：`suspend fun getXxx(...)`、`listXxx(...)`。
- 写入优先 `@Upsert`；明确需要覆盖快照时可用
  `@Insert(onConflict = REPLACE)`。
- 删除命名为 `deleteXxx`、`clearXxx`。
- 查询排序必须稳定；消息使用 `createdAt, id`，列表通常带第二排序键，避免
  同时间戳顺序漂移。

多语句原子操作：

- 简单 DAO 组合使用 `@Transaction` 默认方法，例如
  `ConversationDao.replaceConversationSnapshot()`。
- 需要“读取领域对象 → 变换 → 差量写入”的操作使用
  `database.withTransaction`，例如
  `RoomConversationStore.updateConversationMessages()` 和经济仓储操作。
- 新增跨表逻辑关联时，要么声明 FK/CASCADE，要么在 Repository 中提供原子
  清理并补测试。经济表当前存在字符串逻辑关联，不能假设数据库会自动清理。

## Entity 与领域模型

- Entity/DAO 只在 `data/local` 和 Room 实现中使用；UI、ViewModel、领域逻辑
  只接触 `model` 类型。
- `RoomConversationStore`、`RoomXxxRepository` 负责 `toEntity()`、
  `toDomain()`、枚举存储值和 JSON 字段转换。
- 写入边界继续执行 `trim`、`coerceIn`、`normalized` 和默认值处理，不能绕过
  Repository 直接写 DAO。
- 可选实现沿用“接口 + Room 实现 + Empty 实现”，例如
  `MemoryRepository`、`RoomMemoryRepository`、`EmptyMemoryRepository`。

## JSON 与枚举字段

- 集合或复合值使用 TEXT JSON 时，字段以 `Json` 结尾并提供安全默认值，
  例如 `attachmentsJson = "[]"`、`extrasJson = "{}"`。
- Gson 默认使用 `AppJson.gson`，禁止每个 Store 自建 `Gson()`。
- 解析兼容路径可以 `runCatching` 后回退空集合/默认值，但重要失败应使用
  `logFailure(TAG) { "... raw.len=${raw.length}" }`，只记录长度，不记录原文。
- 枚举持久化使用 `storageValue` / `fromStorageValue` 或显式字符串兼容逻辑，
  读取旧值时提供稳定默认值。

## 迁移流程

每次 schema 变更必须同时完成：

1. 更新 Entity/DAO。
2. `CURRENT_VERSION` 只增加 1，禁止一次跳多个版本。
3. 在 `ChatDbMigrations.kt` 新增 `MIGRATION_N_N+1`。
4. 将迁移按顺序加入 `ChatDbMigrations.ALL`。
5. 运行 KSP/构建并提交新的 `app/schemas/.../<version>.json`。
6. 更新 `ChatDatabaseMigrationRegistryTest` 的最新迁移断言。
7. 对数据保留、默认值、索引或复杂 DDL 补充
   `ChatDatabaseMigrationTest` 设备测试。

迁移 DDL 集中在 `data/local/chat/migrations/ChatDbMigrations.kt`。新增列时可
沿用 `hasColumn` 做幂等防护，但不能用 destructive fallback 掩盖迁移缺失。

## DataStore 与敏感值

- `AppSettingsStore.settingsFlow` 负责从 Preferences、旧字段和安全存储组装
  完整 `AppSettings`。
- Provider、搜索和语音 API Key 由 `SecureValueStore` 保存；写普通 JSON 前
  清空 `apiKey` 字段。
- `SecureValueStore` 使用 Android Keystore AES/GCM/NoPadding 和随机 IV，
  SharedPreferences 只保存 `iv:ciphertext`。
- 读取/解密失败当前会回退为空值，因此调用方不能把“空值”当作保存成功的
  证明；错误日志也不得包含明文密钥。

## 验证

- 连续迁移注册：`ChatDatabaseMigrationRegistryTest.kt`。
- 真实旧库升级：`app/src/androidTest/.../ChatDatabaseMigrationTest.kt`。
- Store/Repository：使用手写 Fake 或内存状态验证映射、事务结果和边界。
- 安全迁移：`AppSettingsStoreSensitiveMigrationTest.kt`。

## 禁止模式

- 在 ViewModel 或 Screen 中引用 Entity/DAO。
- 跳过 Repository 的规范化直接写表。
- schema 变更后遗漏迁移、注册、schema JSON 或迁移测试。
- 使用 destructive migration 让测试“通过”。
- 把 API Key、完整用户 JSON、Base64 媒体写入日志或普通设置 JSON。
