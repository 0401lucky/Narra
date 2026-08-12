package com.example.myapplication.system.performance

import com.example.myapplication.data.local.chat.ChatDatabase
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class LegacyUpgradeFixtureContractTest {

    @Test
    fun s3UpgradeContract_measuresSchema47To48InMainProcess() {
        val sourceVersion = requireNotNull(
            BenchmarkFixtureId.S3_SUPPORTED_UPGRADE.sourceDatabaseVersion,
        )

        assertEquals(47, sourceVersion)
        assertEquals(48, ChatDatabase.CURRENT_VERSION)
        assertEquals(sourceVersion + 1, ChatDatabase.CURRENT_VERSION)
        assertTrue(
            ChatDatabase.ALL_MIGRATIONS.any { migration ->
                migration.startVersion == sourceVersion &&
                    migration.endVersion == ChatDatabase.CURRENT_VERSION
            },
        )
    }

    @Test
    fun schema47Asset_matchesExportedRoomSchemaAndDeclaredSha256() {
        val schemaFile = resolveAppFile(
            "schemas/com.example.myapplication.data.local.chat.ChatDatabase/47.json",
        )
        val assetFile = resolveAppFile("src/baseline/assets/benchmark/s3-schema-v47.sql")
        val builderSource = resolveAppFile(
            "src/baseline/java/com/example/myapplication/system/performance/" +
                "LegacyUpgradeFixtureBuilder.kt",
        ).readText()
        val databaseSchema = JsonParser.parseString(schemaFile.readText())
            .asJsonObject
            .getAsJsonObject("database")

        assertEquals(47, databaseSchema.get("version").asInt)
        assertEquals(47, builderSource.readIntConstant("MEASURED_SOURCE_SCHEMA_VERSION"))
        assertEquals(48, builderSource.readIntConstant("MEASURED_TARGET_SCHEMA_VERSION"))
        assertEquals(
            "benchmark/s3-schema-v47.sql",
            builderSource.readStringConstant("SCHEMA_ASSET_PATH"),
        )
        assertEquals(
            databaseSchema.get("identityHash").asString,
            builderSource.readStringConstant("SOURCE_SCHEMA_IDENTITY_HASH"),
        )
        assertEquals(
            exportedSchemaStatements(databaseSchema),
            assetSchemaStatements(assetFile),
        )
        assertEquals(
            assetFile.readBytes().sha256(),
            builderSource.readStringConstant("SCHEMA_ASSET_SHA256"),
        )

        val assetDocumentation = assetFile.readText()
        assertTrue(assetDocumentation.contains("fixture 进程直接创建 v47 快照，不执行 Room 迁移"))
        assertTrue(assetDocumentation.contains("被测主进程首次打开时真实执行 v47→v48"))
    }

    @Test
    fun fixtureBuilder_doesNotMigrateSnapshotBeforeMeasuredMainProcessLaunch() {
        val builderSource = resolveAppFile(
            "src/baseline/java/com/example/myapplication/system/performance/" +
                "LegacyUpgradeFixtureBuilder.kt",
        ).readText()

        assertTrue(
            builderSource.contains(
                "SupportSQLiteOpenHelper.Callback(MEASURED_SOURCE_SCHEMA_VERSION)",
            ),
        )
        assertFalse(builderSource.contains("migrateFromSourceToSnapshot"))
        assertFalse(builderSource.contains("ChatDatabase.ALL_MIGRATIONS"))
        assertFalse(builderSource.contains("migration.migrate(db)"))
    }

    private fun exportedSchemaStatements(databaseSchema: JsonObject): List<String> {
        return buildList {
            databaseSchema.getAsJsonArray("entities").forEach { element ->
                val entity = element.asJsonObject
                val tableName = entity.get("tableName").asString
                add(entity.get("createSql").asString.replace("\${TABLE_NAME}", tableName))
                entity.getAsJsonArray("indices")?.forEach { indexElement ->
                    add(
                        indexElement.asJsonObject
                            .get("createSql")
                            .asString
                            .replace("\${TABLE_NAME}", tableName),
                    )
                }
            }
            databaseSchema.getAsJsonArray("views")?.forEach { element ->
                val view = element.asJsonObject
                val viewName = view.get("viewName").asString
                add(view.get("createSql").asString.replace("\${VIEW_NAME}", viewName))
            }
            databaseSchema.getAsJsonArray("setupQueries").forEach { element ->
                add(element.asString)
            }
        }
    }

    private fun assetSchemaStatements(assetFile: File): List<String> {
        return assetFile.readLines()
            .map(String::trim)
            .filter { line -> line.isNotEmpty() && !line.startsWith("--") }
            .map { line ->
                assertTrue("schema 资产存在不完整 SQL：$line", line.endsWith(';'))
                line.removeSuffix(";")
            }
    }

    private fun String.readIntConstant(name: String): Int {
        val pattern = Regex("""const val ${Regex.escape(name)} = (\d+)""")
        return requireNotNull(pattern.find(this)) { "未找到常量 $name" }
            .groupValues[1]
            .toInt()
    }

    private fun String.readStringConstant(name: String): String {
        val pattern = Regex("""const val ${Regex.escape(name)} = "([^"]+)"""")
        return requireNotNull(pattern.find(this)) { "未找到常量 $name" }
            .groupValues[1]
    }

    private fun ByteArray.sha256(): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(this)
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun resolveAppFile(relativePath: String): File {
        return sequenceOf(
            File(relativePath),
            File("app", relativePath),
        ).firstOrNull(File::exists)
            ?: error("找不到 app 文件：$relativePath")
    }
}
