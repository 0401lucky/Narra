package com.example.myapplication.context

import com.example.myapplication.model.MemoryRecord
import com.example.myapplication.model.MemoryRuntimeContext
import com.example.myapplication.model.MemoryVectorHit

/** 调用方统一处理软时限、排名融合与预算；协程取消继续向上传播。 */
fun interface MemoryVectorSearch {
    suspend fun search(
        context: MemoryRuntimeContext,
        records: List<MemoryRecord>,
        queries: List<String>,
    ): List<MemoryVectorHit>
}

object EmptyMemoryVectorSearch : MemoryVectorSearch {
    override suspend fun search(
        context: MemoryRuntimeContext,
        records: List<MemoryRecord>,
        queries: List<String>,
    ): List<MemoryVectorHit> = emptyList()
}
