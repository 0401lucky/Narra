package com.example.myapplication.testutil

import com.example.myapplication.data.repository.context.MemoryArchiveRepository
import com.example.myapplication.model.MemoryRuntimeContext
import java.lang.reflect.Proxy

/** 仅供摘要组装器的只读运行查询；误入提取、写入或模型路径会立即失败。 */
fun fakeSummaryRuntimeArchive(contexts: () -> List<MemoryRuntimeContext>): MemoryArchiveRepository =
    Proxy.newProxyInstance(
        MemoryArchiveRepository::class.java.classLoader,
        arrayOf(MemoryArchiveRepository::class.java),
    ) { _, method, arguments ->
        when (method.name) {
            "getRuntimeContext" -> contexts().firstOrNull {
                it.conversationId == arguments!![0] && it.characterId == arguments[1]
            }

            "toString" -> "FakeSummaryRuntimeArchive"
            "getEngineVersion" -> com.example.myapplication.model.MEMORY_ENGINE_COMPATIBILITY
            else -> error("摘要只读测试不应调用 ${method.name}")
        }
    } as MemoryArchiveRepository
