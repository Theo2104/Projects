package com.example.llama.api

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Interface für die Kommunikation mit der HuggingFace-API.
 * Sendet Benutzereingaben und erhält Antworten des Sprachmodells.
 */
interface HuggingFaceApiService {
    @POST("/")
    fun getModelResponse(@Body requestBody: Map<String, String>): Call<Map<String, String>>
}
