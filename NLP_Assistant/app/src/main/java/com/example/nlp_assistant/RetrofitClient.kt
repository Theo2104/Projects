package com.example.nlp_assistant.api

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

// Retrofit-Client erstellen
fun createRetrofitClient(): HuggingFaceApiService {
    val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)   // Timeout für die Verbindung
        .writeTimeout(30, TimeUnit.SECONDS)     // Timeout für das Senden von Daten
        .readTimeout(30, TimeUnit.SECONDS)      // Timeout für das Empfangen von Daten
        .build()

    val retrofit = Retrofit.Builder()
        .baseUrl("https://api-inference.huggingface.co/") // Basis-URL für Hugging Face API
        .addConverterFactory(GsonConverterFactory.create()) // Converter für JSON zu Objekten
        .client(okHttpClient) // Verwende den OkHttpClient mit den geänderten Timeout-Einstellungen
        .build()

    return retrofit.create(HuggingFaceApiService::class.java)
}
