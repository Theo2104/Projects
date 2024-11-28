package com.example.llama.api

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

// Retrofit-API-Schnittstelle für Hugging Face
interface HuggingFaceApiService {
    @Headers(
        "Authorization: Bearer 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",
        "Content-Type: application/json"
    )
    @POST("/models/meta-llama/Llama-3.2-1B") // Ersetze durch das gewünschte Modell
    fun getModelResponse(@Body requestBody: Map<String, String>): Call<Map<String, String>>
}