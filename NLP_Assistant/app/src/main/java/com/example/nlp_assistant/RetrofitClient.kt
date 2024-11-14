package com.example.nlp_assistant.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

// Retrofit-Client erstellen
fun createRetrofitClient(): HuggingFaceApiService {
    val retrofit = Retrofit.Builder()
        .baseUrl("https://api-inference.huggingface.co/") // Basis-URL für Hugging Face API
        .addConverterFactory(GsonConverterFactory.create()) // Converter für JSON zu Objekten
        .build()

    return retrofit.create(HuggingFaceApiService::class.java)
}
