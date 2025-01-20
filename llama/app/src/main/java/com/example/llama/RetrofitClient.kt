package com.example.llama.api

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

fun createRetrofitClient(): HuggingFaceApiService {
    val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS) // Erhöhe die Verbindungs-Timeouts auf 60 Sekunden
        .writeTimeout(60, TimeUnit.SECONDS)   // Erhöhe die Schreib-Timeouts auf 60 Sekunden
        .readTimeout(60, TimeUnit.SECONDS)    // Erhöhe die Lese-Timeouts auf 60 Sekunden
        .build()
    val retrofit = Retrofit.Builder()
        .baseUrl("https://api-inference.huggingface.co") // Basis-URL der Hugging Face API
        .addConverterFactory(GsonConverterFactory.create())
        .client(okHttpClient)
        .build()

    return retrofit.create(HuggingFaceApiService::class.java)
}