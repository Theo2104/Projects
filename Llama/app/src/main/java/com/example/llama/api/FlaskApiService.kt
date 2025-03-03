package com.example.llama.api

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Interface für die Kommunikation mit dem Flask-Backend.
 * Sendet Benutzereingaben und erhält Antworten des Sprachmodells.
 * Unterstützt Session-ID für Kontexterhaltung und Erklärungsfunktion für xAI.
 */
interface FlaskApiService {
    @POST("/chat")
    fun getModelResponse(@Body request: Map<String, String>): Call<Map<String, String>>
}
