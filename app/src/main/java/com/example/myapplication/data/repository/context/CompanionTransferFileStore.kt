package com.example.myapplication.data.repository.context

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import com.example.myapplication.data.repository.normalizeImagePath
import com.example.myapplication.model.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.util.Base64
import java.util.UUID

interface CompanionTransferFileStore {
    suspend fun readImage(resource: CompanionImageResource): CompanionTransferImage
    suspend fun prepareImage(bytes: ByteArray): CompanionTransferImage
    suspend fun freezePlan(operationId: String, json: String): ContextTransferPlanFile
    suspend fun readPlan(operationId: String, expectedHash: String): String
    suspend fun publishImages(images: List<CompanionTransferImage>): List<CompanionPublishedTransferImage>
    suspend fun validatePublishedImages(images: List<CompanionPublishedTransferImage>)
    suspend fun discardPlan(operationId: String, expectedHash: String)
}

/** 只读已授权图片、生成无附属元数据 PNG；不访问网络、音色样本或外来路径。 */
class PrivateCompanionTransferFileStore(
    context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CompanionTransferFileStore {
    private val root = File(context.filesDir, "companion_transfers").canonicalFile
    private val privateRoot = context.filesDir.canonicalFile

    override suspend fun readImage(resource: CompanionImageResource): CompanionTransferImage =
        withContext(dispatcher) {
            val path = normalizeImagePath(resource.path) ?: error("装扮图片不是本机素材")
            val source = File(path).canonicalFile
            require(
                source.toPath().startsWith(privateRoot.toPath()) && source.isFile
            ) { "装扮图片已不可用" }
            require(source.length() in 1..32L * 1024 * 1024) { "装扮图片过大" }
            val bytes = boundedRead(source, 32 * 1024 * 1024)
            normalizedImage(bytes)
        }

    override suspend fun prepareImage(bytes: ByteArray): CompanionTransferImage =
        withContext(dispatcher) {
            require(bytes.size in 1..32 * 1024 * 1024) { "导入头像内容过大或为空" }
            normalizedImage(bytes)
        }

    private fun normalizedImage(bytes: ByteArray): CompanionTransferImage {
        val bounds = bounds(bytes)
        val decoded =
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("装扮图片无法解码")
        val bitmap = try {
            Bitmap.createBitmap(bounds.first, bounds.second, Bitmap.Config.ARGB_8888)
                .also { Canvas(it).drawBitmap(decoded, 0f, 0f, null) }
        } finally {
            decoded.recycle()
        }
        val encoded = try {
            ByteArrayOutputStream().use { output ->
                check(
                    bitmap.compress(
                        Bitmap.CompressFormat.PNG,
                        100,
                        output
                    )
                ) { "装扮图片编码失败" }
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
        return CompanionTransferImage(
            CompanionTransferImageValidation.sha256(encoded), "image/png", bounds.first,
            bounds.second, Base64.getEncoder().encodeToString(encoded),
        ).also { CompanionTransferImageValidation.bytes(it) }
    }

    override suspend fun freezePlan(operationId: String, json: String): ContextTransferPlanFile =
        withContext(dispatcher) {
            val bytes = json.toByteArray(Charsets.UTF_8)
            require(bytes.size <= MemoryTransferJson.MAX_BYTES) { "冻结资料计划超过 16 MB 上限" }
            val hash = CompanionTransferImageValidation.sha256(bytes)
            ContextTransferPlanFile(hash, publish(planFile(operationId), bytes, hash))
        }

    override suspend fun readPlan(operationId: String, expectedHash: String): String =
        withContext(dispatcher) {
            val file = planFile(operationId)
            require(file.isFile && file.length() in 1..MemoryTransferJson.MAX_BYTES.toLong()) { "冻结导入计划暂不可读取" }
            val bytes = boundedRead(file, MemoryTransferJson.MAX_BYTES)
            require(CompanionTransferImageValidation.sha256(bytes) == expectedHash) { "冻结导入计划已改变，未恢复写入" }
            bytes.toString(Charsets.UTF_8)
        }

    override suspend fun publishImages(images: List<CompanionTransferImage>): List<CompanionPublishedTransferImage> =
        withContext(dispatcher) {
            images.distinctBy { it.sha256 }.map { image ->
                val bytes = CompanionTransferImageValidation.bytes(image)
                val size = bounds(bytes)
                require(size.first == image.width && size.second == image.height) { "装扮图片尺寸不匹配" }
                val probe = BitmapFactory.Options().apply {
                    inSampleSize = generateSequence(1) { it * 2 }.first {
                        maxOf(
                            size.first,
                            size.second
                        ) / it <= 128
                    }
                }
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, probe)
                    ?: error("装扮图片像素内容不可读取")
                bitmap.recycle()
                val assets = File(root, "assets")
                val legacy = File(assets, "${image.sha256}.png")
                val path = (if (legacy.exists()) legacy else File(
                    File(assets, image.sha256),
                    "image.png"
                )).canonicalFile
                require(path.toPath().startsWith(root.toPath())) { "图片发布路径无效" }
                publish(path, bytes, image.sha256)
                CompanionPublishedTransferImage(image.sha256, path.path, size.first, size.second)
            }
        }

    override suspend fun discardPlan(operationId: String, expectedHash: String) =
        withContext(dispatcher) {
            val file = planFile(operationId)
            if (file.isFile && file.length() <= MemoryTransferJson.MAX_BYTES && CompanionTransferImageValidation.sha256(
                    boundedRead(file, MemoryTransferJson.MAX_BYTES)
                ) == expectedHash
            ) {
                check(file.delete() || !file.exists()) { "未接受的导入计划暂无法清理" }
                if (file.name == "plan.json") file.parentFile?.delete()
            }
        }

    override suspend fun validatePublishedImages(images: List<CompanionPublishedTransferImage>) =
        withContext(dispatcher) {
            images.forEach { image ->
                val file = File(image.path).canonicalFile
                require(
                    file.toPath().startsWith(
                        File(
                            root,
                            "assets"
                        ).canonicalFile.toPath()
                    ) && file.isFile && file.length() in 1..CompanionTransferImageValidation.MAX_IMAGE_BYTES.toLong()
                ) { "已发布素材暂不可读取" }
                val bytes = boundedRead(file, CompanionTransferImageValidation.MAX_IMAGE_BYTES)
                require(
                    CompanionTransferImageValidation.sha256(bytes) == image.sha256 && bounds(
                        bytes
                    ) == (image.width to image.height)
                ) { "已发布素材在挂接前已改变，未覆盖文件或设置" }
            }
        }

    private fun planFile(id: String): File {
        require(Regex("[A-Za-z0-9_-]{1,80}").matches(id)) { "本机导入计划标识无效" }
        val plans = File(root, "plans")
        val legacy = File(plans, "$id.json")
        return (if (legacy.exists()) legacy else File(
            File(plans, id),
            "plan.json"
        )).canonicalFile.also {
            require(
                it.toPath().startsWith(root.toPath())
            ) { "导入计划路径无效" }
        }
    }

    private fun bounds(bytes: ByteArray): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        require(options.outWidth in 1..4096 && options.outHeight in 1..4096 && options.outWidth.toLong() * options.outHeight <= 16_000_000L) { "装扮图片尺寸过大或内容无效" }
        return options.outWidth to options.outHeight
    }

    private fun publish(file: File, bytes: ByteArray, hash: String): Boolean {
        if (file.exists()) {
            requirePublishedBytes(file, bytes.size, hash)
            return false
        }
        // 旧单文件若在选择后消失，不能把其共享父目录当作新发布目标。
        require(file.name == "plan.json" || file.name == "image.png") { "旧导入文件状态已改变，请重试" }
        val destination = requireNotNull(file.parentFile)
        val parent = requireNotNull(destination.parentFile)
        check(parent.mkdirs() || parent.isDirectory) { "私有导入目录暂不可写" }
        if (destination.exists()) {
            requirePublishedBytes(file, bytes.size, hash)
            return false
        }
        val temporaryDirectory = File(parent, ".${destination.name}.${UUID.randomUUID()}.tmp")
        check(temporaryDirectory.mkdir()) { "私有导入临时目录暂不可写" }
        val temporary = File(temporaryDirectory, file.name)
        try {
            FileOutputStream(temporary).use { output -> output.write(bytes); output.flush(); output.fd.sync() }
            requirePublishedBytes(temporary, bytes.size, hash)
            return try {
                // Android 禁止 app_data_file 硬链接；非空目录改名不会覆盖并发发布的非空目标。
                Files.move(temporaryDirectory.toPath(), destination.toPath())
                requirePublishedBytes(file, bytes.size, hash)
                true
            } catch (failure: IOException) {
                if (!destination.exists()) throw failure
                requirePublishedBytes(file, bytes.size, hash)
                false
            }
        } finally {
            if (temporary.exists()) temporary.delete()
            if (temporaryDirectory.exists()) temporaryDirectory.delete()
        }
    }

    private fun requirePublishedBytes(file: File, size: Int, hash: String) {
        require(
            file.isFile && file.length() == size.toLong() &&
                    CompanionTransferImageValidation.sha256(boundedRead(file, size)) == hash
        ) { "已发布导入文件发生变化，未覆盖" }
    }

    private fun boundedRead(file: File, maximum: Int): ByteArray = file.inputStream().use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size().toLong() + count <= maximum) { "导入文件在读取中超过大小上限" }
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    }
}
