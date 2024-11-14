package com.example.nlp_assistant.api

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

// Retrofit-API-Schnittstelle
interface HuggingFaceApiService {
    @Headers("Authorization: Bearer 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")
    @POST("https://api-inference.huggingface.co/models/distilbert-base-uncased")
    fun getModelResponse(@Body requestBody: Map<String, String>): Call<Map<String, Any>>
}
