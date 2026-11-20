package com.example.myapplication.data.remote

import com.example.myapplication.model.MemoryEmbeddingRequest
import com.example.myapplication.model.MemoryEmbeddingResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface MemoryEmbeddingApi {
    @POST("embeddings")
    suspend fun createEmbeddings(@Body request: MemoryEmbeddingRequest): Response<MemoryEmbeddingResponse>
}
