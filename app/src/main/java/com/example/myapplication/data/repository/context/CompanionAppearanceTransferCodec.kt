package com.example.myapplication.data.repository.context

import com.example.myapplication.data.local.CompanionVoiceCodec
import com.example.myapplication.data.local.CompanionWritingCodec
import com.example.myapplication.model.*
import com.example.myapplication.system.json.AppJson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.time.ZoneId
import java.util.Base64
import java.util.zip.CRC32
import java.util.zip.DataFormatException
import java.util.zip.Inflater

/** 显式重建白名单 DTO；未知版本/字段在预览前整包拒绝。 */
class CompanionAppearanceTransferCodec {
    fun encode(pack: CompanionAppearancePackage): String =
        AppJson.gson.toJson(pack).also { decode(it) }

    fun decode(raw: String): CompanionAppearancePackage {
        val root = try {
            MemoryTransferJson.parse(raw)
        } catch (failure: IOException) {
            throw IllegalArgumentException("装扮文件格式无效", failure)
        }
        return decodeObject(root)
    }

    internal fun decodeObject(root: JsonObject): CompanionAppearancePackage {
        root.only("version", "visuals", "images")
        require(root.number("version") == 1L) { "不支持的装扮包版本" }
        val visuals = root.obj("visuals")
        visuals.only(
            "preferredMode",
            "resources",
            "outfits",
            "selectedOutfitId",
            "selectedBackgroundId",
            "decorationIds",
            "layout"
        )
        val images = root.array("images").map { value ->
            val image = value.objectValue()
            image.only("sha256", "mimeType", "width", "height", "dataBase64")
            CompanionTransferImage(
                image.text("sha256"), image.text("mimeType"), image.number("width").intValue(),
                image.number("height").intValue(), image.text("dataBase64"),
            ).also { CompanionTransferImageValidation.bytes(it) }
        }
        require(images.size <= CompanionVisualValidation.MAX_RESOURCES && images.map { it.sha256 }
            .distinct().size == images.size) { "装扮图片内容重复或过多" }
        require(images.sumOf { CompanionTransferImageValidation.bytes(it).size.toLong() } <= MAX_TOTAL_IMAGE_BYTES) { "装扮包图片超过 12 MB 上限" }
        val resources = visuals.array("resources").map { value ->
            val resource = value.objectValue()
            resource.only("id", "name", "contentHash", "kind")
            CompanionAppearanceResource(
                resource.text("id"),
                resource.text("name"),
                resource.text("contentHash"),
                resource.enum("kind")
            )
        }
        val outfits = visuals.array("outfits").map { value ->
            val outfit = value.objectValue()
            outfit.only("id", "name", "defaultPortraitId", "expressions")
            val expressions = outfit.obj("expressions").entrySet().associate { (key, image) ->
                require(image.isJsonPrimitive && image.asJsonPrimitive.isString) { "表情图片引用无效" }
                key to image.asString
            }
            CompanionOutfit(
                outfit.text("id"),
                outfit.text("name"),
                outfit.optionalText("defaultPortraitId"),
                expressions
            )
        }
        val layout =
            visuals.get("layout")?.takeUnless { it.isJsonNull }?.objectValue()?.let { value ->
                value.only("portraitPosition", "portraitWidth", "portraitHeight")
                CompanionStageLayout(
                    value.enum("portraitPosition"),
                    value.finiteFloat("portraitWidth"),
                    value.finiteFloat("portraitHeight")
                )
            } ?: CompanionStageLayout()
        val data = CompanionAppearanceData(
            visuals.optionalEnum<CompanionDisplayMode>("preferredMode"), resources, outfits,
            visuals.optionalText("selectedOutfitId"), visuals.optionalText("selectedBackgroundId"),
            visuals.array("decorationIds").map { value ->
                require(value.isJsonPrimitive && value.asJsonPrimitive.isString) { "装饰引用无效" }
                value.asString
            },
            layout,
        )
        validate(data, images)
        return CompanionAppearancePackage(1, data, images)
    }

    private fun validate(data: CompanionAppearanceData, images: List<CompanionTransferImage>) {
        require(data.resources.size <= CompanionVisualValidation.MAX_RESOURCES && data.outfits.size <= CompanionVisualValidation.MAX_OUTFITS) { "装扮资源数量超出上限" }
        require(data.resources.map { it.id }
            .distinct().size == data.resources.size && data.outfits.map { it.id }
            .distinct().size == data.outfits.size) { "装扮标识不能重复" }
        (data.resources.map { it.id } + data.outfits.map { it.id }).forEach { require(ID.matches(it)) { "装扮标识无效" } }
        (data.resources.map { it.name } + data.outfits.map { it.name }).forEach { name ->
            require(name.isNotBlank() && name.length <= CompanionVisualValidation.MAX_NAME && name.none { it.isISOControl() } && !PATH_LABEL.containsMatchIn(
                name
            )) { "装扮名称无效" }
        }
        val content = images.map { it.sha256 }.toSet()
        require(data.resources.all { HASH.matches(it.contentHash) && it.contentHash in content } && content.all { hash -> data.resources.any { it.contentHash == hash } }) { "图片内容映射不完整" }
        val kinds = data.resources.associate { it.id to it.kind }
        require(data.outfits.all { outfit ->
            outfit.expressions.size <= CompanionVisualValidation.MAX_EXPRESSIONS &&
                    outfit.expressions.keys.all { CompanionExpressionKeys.normalize(it) == it } &&
                    (outfit.expressions.values + listOfNotNull(outfit.defaultPortraitId)).all { kinds[it] == CompanionImageKind.PORTRAIT }
        }) { "套装表情映射无效" }
        require(data.selectedOutfitId == null || data.outfits.any { it.id == data.selectedOutfitId }) { "套装选择无效" }
        require(data.selectedBackgroundId == null || kinds[data.selectedBackgroundId] == CompanionImageKind.BACKGROUND) { "背景选择无效" }
        require(data.decorationIds.distinct().size == data.decorationIds.size && data.decorationIds.all { kinds[it] == CompanionImageKind.DECOR }) { "装饰选择无效" }
        require(data.layout == data.layout.normalized()) { "装扮布局超出范围" }
    }

    internal fun privateEntries(value: JsonElement?): List<CompanionContextTransferEntry>? {
        if (value == null || value.isJsonNull) return null
        require(value.isJsonArray && value.asJsonArray.size() <= CONTEXT_IMPORT_MAX_ASSISTANTS) { "陪伴偏好列表无效或过多" }
        val entries = value.asJsonArray.map { item ->
            val root = item.objectValue()
            root.only("assistantId", "schemaVersion", "zoneId", "writing", "voice", "appearance")
            require(root.number("schemaVersion") == 1L) { "不支持的陪伴资料版本" }
            val id = root.text("assistantId")
            require(id.isNotBlank() && id.length <= 200) { "陪伴目标角色标识无效" }
            val zone = root.optionalText("zoneId")?.also { if (it.isNotEmpty()) ZoneId.of(it) }
            val writing = root.get("writing")?.takeUnless { it.isJsonNull }?.let { value ->
                validateWritingShape(value.objectValue())
                CompanionWritingCodec.decode(value)
                    .also { require(CompanionWritingValidation.error(it) == null) { "陪伴文风配置无效" } }
            }
            val voice = root.get("voice")?.takeUnless { it.isJsonNull }?.let { value ->
                val obj = value.objectValue()
                obj.only(
                    "schemaVersion",
                    "enabled",
                    "autoPlay",
                    "readMode",
                    "sourceLanguage",
                    "targetLanguage",
                    "cacheLimitBytes"
                )
                require(obj.number("schemaVersion") == 1L) { "不支持的声音偏好版本" }
                obj.booleans("enabled", "autoPlay")
                obj.optionalEnum<CompanionVoiceReadMode>("readMode")
                listOf(
                    "sourceLanguage",
                    "targetLanguage"
                ).forEach {
                    require(
                        (obj.optionalText(it)?.length
                            ?: 0) <= CompanionVoicePreferences.MAX_LANGUAGE_LENGTH
                    ) { "声音语言名称过长" }
                }
                require(obj.number("cacheLimitBytes") in 1024L * 1024..512L * 1024 * 1024) { "声音缓存上限无效" }
                CompanionVoiceCodec.decode(value).also(CompanionVoiceCodec::validate)
            }
            CompanionContextTransferEntry(
                id,
                1,
                zone,
                writing,
                voice,
                root.get("appearance")?.takeUnless { it.isJsonNull }
                    ?.let { decodeObject(it.objectValue()) })
        }
        require(entries.map { it.assistantId }
            .distinct().size == entries.size) { "陪伴资料目标角色重复" }
        return entries
    }

    private fun validateWritingShape(root: JsonObject) {
        root.only(
            "style",
            "pointOfView",
            "extraStyle",
            "templates",
            "detailHintsEnabled",
            "observation"
        )
        root.optionalEnum<CompanionWritingStyle>("style")
        root.optionalEnum<CompanionPointOfView>("pointOfView")
        root.optionalText("extraStyle")
        root.booleans("detailHintsEnabled")
        val templates = root.array("templates")
        require(templates.size <= CompanionWritingValidation.MAX_TEMPLATES) { "文风模板过多" }
        val ids = templates.map { item ->
            item.objectValue().also { it.only("id", "name", "text") }.let { value ->
                require(ID.matches(value.text("id"))) { "文风模板标识无效" }
                value.text("name"); value.text("text"); value.text("id")
            }
        }
        require(ids.distinct().size == ids.size) { "文风模板标识重复" }
        root.get("observation")?.takeUnless { it.isJsonNull }?.objectValue()?.let { observation ->
            observation.only("enabled", "style", "fields", "customFields")
            observation.booleans("enabled")
            observation.optionalEnum<CompanionObservationStyle>("style")
            listOf("fields", "customFields").forEach { key ->
                val fields = observation.array(key)
                require(fields.size <= if (key == "fields") 4 else CompanionWritingValidation.MAX_CUSTOM_FIELDS) { "观测字段过多" }
                val fieldIds = fields.map { item ->
                    item.objectValue().let { field ->
                        field.only(
                            "id",
                            "label",
                            "enabled"
                        ); field.text("label"); field.booleans("enabled")
                        field.text("id").also { id ->
                            require(if (key == "fields") companionBuiltinObservationFields().any { it.id == id } else Regex(
                                "obs_[A-Za-z0-9_-]{1,64}"
                            ).matches(id)) { "观测字段标识无效" }
                        }
                    }
                }
                require(fieldIds.distinct().size == fieldIds.size) { "观测字段重复" }
            }
        }
    }

    private fun JsonObject.only(vararg names: String) {
        require(keySet().all { it in names }) { "资料包含不支持的字段" }
    }

    private fun JsonElement.objectValue(): JsonObject {
        require(isJsonObject) { "装扮资料必须为对象" }; return asJsonObject
    }

    private fun JsonObject.obj(name: String): JsonObject =
        requireNotNull(get(name)) { "装扮资料缺少必要内容" }.objectValue()

    private fun JsonObject.array(name: String): List<JsonElement> =
        get(name)?.takeUnless { it.isJsonNull }
            ?.let { require(it.isJsonArray) { "装扮列表格式无效" }; it.asJsonArray.toList() }
            .orEmpty()

    private fun JsonObject.text(name: String): String {
        val value =
            get(name); require(value?.isJsonPrimitive == true && value.asJsonPrimitive.isString) { "装扮文字字段无效" }; return value.asString
    }

    private fun JsonObject.optionalText(name: String): String? =
        get(name)?.takeUnless { it.isJsonNull }?.let { text(name) }

    private fun JsonObject.number(name: String): Long =
        MemoryTransferJson.integer(get(name), "装扮数值")

    private fun Long.intValue(): Int {
        require(this in 1..Int.MAX_VALUE) { "图片尺寸无效" }; return toInt()
    }

    private fun JsonObject.finiteFloat(name: String): Float {
        val value =
            get(name); require(value?.isJsonPrimitive == true && value.asJsonPrimitive.isNumber) { "布局数值无效" }; return value.asFloat.also {
            require(
                it.isFinite()
            ) { "布局数值无效" }
        }
    }

    private inline fun <reified T : Enum<T>> JsonObject.enum(name: String): T =
        enumValues<T>().firstOrNull { it.name == text(name) }
            ?: throw IllegalArgumentException("装扮枚举无效")

    private inline fun <reified T : Enum<T>> JsonObject.optionalEnum(name: String): T? =
        get(name)?.takeUnless { it.isJsonNull }?.let { enum<T>(name) }

    private fun JsonObject.booleans(vararg names: String) = names.forEach { name ->
        get(name)?.takeUnless { it.isJsonNull }
            ?.let { require(it.isJsonPrimitive && it.asJsonPrimitive.isBoolean) { "陪伴开关格式无效" } }
    }

    private companion object {
        val ID = Regex("[A-Za-z0-9_-]{1,80}")
        val HASH = Regex("[a-f0-9]{64}")
        val PATH_LABEL =
            Regex("(?:[A-Za-z]:[\\\\/]|(?:file|content|https?)://|^/)", RegexOption.IGNORE_CASE)
        const val MAX_TOTAL_IMAGE_BYTES = 12L * 1024 * 1024
    }
}

internal object CompanionTransferImageValidation {
    const val MAX_IMAGE_BYTES = 4 * 1024 * 1024
    private val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 13, 10, 26, 10)
    private val chunks =
        setOf("IHDR", "PLTE", "IDAT", "IEND", "tRNS", "sRGB", "gAMA", "cHRM", "sBIT", "pHYs")

    fun bytes(image: CompanionTransferImage): ByteArray {
        require(image.mimeType == "image/png" && image.width in 1..4096 && image.height in 1..4096 && image.width.toLong() * image.height <= 16_000_000L) { "装扮图片类型或尺寸无效" }
        require(image.dataBase64.length <= (MAX_IMAGE_BYTES + 2) / 3 * 4) { "装扮单张图片超过 4 MB 上限" }
        val bytes = try {
            Base64.getDecoder().decode(image.dataBase64)
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("图片内容编码无效")
        }
        require(bytes.size in 1..MAX_IMAGE_BYTES && sha256(bytes) == image.sha256) { "图片内容 hash 不匹配" }
        require(
            bytes.size >= 33 && bytes.copyOfRange(0, 8).contentEquals(signature)
        ) { "装扮图片不是 PNG" }
        var offset = 8
        var ended = false
        var dataSeen = false
        var dataEnded = false
        var bitDepth = 0
        var colorType = 0
        var interlace = 0
        var paletteSize = 0
        val singleChunks = mutableSetOf<String>()
        val compressed = ByteArrayOutputStream()
        while (offset + 12 <= bytes.size) {
            val size = uint(bytes, offset)
            require(size <= MAX_IMAGE_BYTES && size <= bytes.size - offset - 12L) { "PNG 内容不完整" }
            val length = size.toInt()
            val type = String(bytes, offset + 4, 4, Charsets.US_ASCII)
            require(type in chunks && (!ended) && (offset != 8 || type == "IHDR")) { "PNG 包含不允许的附属内容" }
            val crc = CRC32().apply { update(bytes, offset + 4, length + 4) }.value
            require(crc == uint(bytes, offset + 8 + length)) { "PNG 内容校验失败" }
            val start = offset + 8
            if (type != "IDAT") require(singleChunks.add(type)) { "PNG 内容块重复" }
            when (type) {
                "IHDR" -> {
                    require(
                        offset == 8 && length == 13 && uint(
                            bytes,
                            start
                        ) == image.width.toLong() && uint(bytes, start + 4) == image.height.toLong()
                    ) { "PNG 尺寸声明不一致" }
                    bitDepth = bytes[start + 8].toInt() and 255
                    colorType = bytes[start + 9].toInt() and 255
                    interlace = bytes[start + 12].toInt() and 255
                    val allowedDepths = when (colorType) {
                        0 -> setOf(1, 2, 4, 8, 16)
                        2, 4, 6 -> setOf(8, 16)
                        3 -> setOf(1, 2, 4, 8)
                        else -> emptySet()
                    }
                    require(bitDepth in allowedDepths && bytes[start + 10] == 0.toByte() && bytes[start + 11] == 0.toByte() && interlace in 0..1) { "PNG 像素格式无效" }
                }

                "IDAT" -> {
                    require(!dataEnded && (colorType != 3 || paletteSize > 0)) { "PNG 像素块顺序或调色板无效" }
                    dataSeen = true
                    compressed.write(bytes, start, length)
                }

                "IEND" -> {
                    require(length == 0 && dataSeen) { "PNG 缺少像素内容" }
                    ended = true
                }

                else -> {
                    require(!dataSeen) { "PNG 附属内容顺序无效" }
                    when (type) {
                        "PLTE" -> {
                            require(
                                colorType in setOf(
                                    2,
                                    3,
                                    6
                                ) && length in 3..768 && length % 3 == 0
                            ) { "PNG 调色板无效" }
                            paletteSize = length / 3
                            require(colorType != 3 || paletteSize <= (1 shl bitDepth)) { "PNG 调色板超出位深" }
                        }

                        "tRNS" -> require(
                            when (colorType) {
                                0 -> length == 2
                                2 -> length == 6
                                3 -> length in 1..paletteSize
                                else -> false
                            }
                        ) { "PNG 透明度内容无效" }

                        "sRGB" -> require(length == 1 && (bytes[start].toInt() and 255) in 0..3) { "PNG 色彩内容无效" }
                        "gAMA" -> require(
                            length == 4 && uint(
                                bytes,
                                start
                            ) > 0
                        ) { "PNG 色彩内容无效" }

                        "cHRM" -> require(length == 32) { "PNG 色彩内容无效" }
                        "pHYs" -> require(length == 9 && (bytes[start + 8].toInt() and 255) in 0..1) { "PNG 尺寸内容无效" }
                        "sBIT" -> {
                            val channels = if (colorType == 3) 3 else channels(colorType)
                            require(length == channels && (0 until length).all { (bytes[start + it].toInt() and 255) in 1..(if (colorType == 3) 8 else bitDepth) }) { "PNG 位深内容无效" }
                        }
                    }
                }
            }
            if (dataSeen && type != "IDAT") dataEnded = true
            offset += length + 12
            if (ended) break
        }
        require(ended && offset == bytes.size) { "PNG 内容未完整结束" }
        validatePixels(
            compressed.toByteArray(),
            image.width,
            image.height,
            bitDepth,
            colorType,
            interlace
        )
        return bytes
    }

    private fun channels(colorType: Int): Int = when (colorType) {
        0, 3 -> 1
        2 -> 3
        4 -> 2
        6 -> 4
        else -> error("PNG 像素格式无效")
    }

    /** 按行验证有界像素流，不分配整张 Bitmap，也不接受合法 CRC 下的损坏/夹带内容。 */
    private fun validatePixels(
        compressed: ByteArray,
        width: Int,
        height: Int,
        bitDepth: Int,
        colorType: Int,
        interlace: Int
    ) {
        val inflater = Inflater()
        try {
            inflater.setInput(compressed)
            val passes = if (interlace == 0) listOf(intArrayOf(0, 0, 1, 1)) else listOf(
                intArrayOf(0, 0, 8, 8), intArrayOf(4, 0, 8, 8), intArrayOf(0, 4, 4, 8),
                intArrayOf(2, 0, 4, 4), intArrayOf(0, 2, 2, 4), intArrayOf(1, 0, 2, 2),
                intArrayOf(0, 1, 1, 2),
            )
            passes.forEach { pass ->
                val columns = if (width <= pass[0]) 0 else (width - pass[0] + pass[2] - 1) / pass[2]
                val rows = if (height <= pass[1]) 0 else (height - pass[1] + pass[3] - 1) / pass[3]
                if (columns > 0 && rows > 0) {
                    val row = ByteArray((columns * channels(colorType) * bitDepth + 7) / 8 + 1)
                    repeat(rows) {
                        var written = 0
                        while (written < row.size) {
                            val count = inflater.inflate(row, written, row.size - written)
                            require(count > 0) { "PNG 像素内容不完整" }
                            written += count
                        }
                        require((row[0].toInt() and 255) in 0..4) { "PNG 像素过滤方式无效" }
                    }
                }
            }
            require(inflater.inflate(ByteArray(1)) == 0 && inflater.finished() && inflater.remaining == 0) { "PNG 像素内容超出声明或未完整结束" }
        } catch (_: DataFormatException) {
            throw IllegalArgumentException("PNG 像素压缩内容无效")
        } finally {
            inflater.end()
        }
    }

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun uint(bytes: ByteArray, offset: Int): Long =
        (0..3).fold(0L) { value, index -> (value shl 8) or (bytes[offset + index].toLong() and 255L) }
}
