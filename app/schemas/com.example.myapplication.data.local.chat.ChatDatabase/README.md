# Room 导出 schema 说明

## 缺失的版本

- **`18.json` 未被导出**。提交 `858183b` ("feat(roleplay): add online phone interactions", 2026-04-09) 一次性把 `CURRENT_VERSION` 从 17 跳到 19，并同时实现了 `MIGRATION_17_18`（`ChatDatabase.kt:412`）与 `MIGRATION_18_19`（`ChatDatabase.kt:441`），但只提交了 `15/16/17/19.json` —— 当时应是 Room 的 export 只触发在"与最终 version 一致"时。
- v18 的 schema 已无法从 git 历史恢复，只能通过"在 17 的基础上应用 MIGRATION_17_18"重新生成。
- 目前 `ChatDatabaseMigrationRegistryTest` 会校验 `ALL_MIGRATIONS` 的完整性；MIGRATION_17_18 的 DDL 仍可信。若未来要做 v17↔v18 的 schema diff，请：
  1. 临时把 `CURRENT_VERSION` 设回 18 并运行 `./gradlew.bat :app:kspDebugKotlin`，
  2. 把生成的 `18.json` commit 进来，
  3. 再把 `CURRENT_VERSION` 恢复到 27。

## 防止再次漏导

迁移版本号跳跃式提交（n → n+2）时，Room 不会自动补导中间版本 JSON。约定：

- **每次 bump `CURRENT_VERSION` 一次只 +1**，分多次提交。
- 单独的 `ChatDatabaseMigrationRegistryTest` 已覆盖 Migration 完整性；后续 T1 会追加"schemas 目录文件数 ≥ CURRENT_VERSION - 10"的 sanity check（见 `task.md`）。
