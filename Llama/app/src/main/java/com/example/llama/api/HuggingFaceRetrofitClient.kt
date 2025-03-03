package com.example.llama.api

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Erstellt einen OkHttpClient für die Kommunikation mit der HuggingFace API.
 */
fun createHuggingFaceOkHttpClient(): OkHttpClient {
    return OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
}

/**
 * Erstellt einen Retrofit-Client für die Kommunikation mit der HuggingFace-API.
 */
fun createHuggingFaceRetrofitClient(): HuggingFaceApiService {
    val okHttpClient = createHuggingFaceOkHttpClient()
    val retrofit = Retrofit.Builder()
        .baseUrl("https://api-inference.huggingface.co/models/")
        .addConverterFactory(GsonConverterFactory.create())
        .client(okHttpClient)
        .build()

    return retrofit.create(HuggingFaceApiService::class.java)
}
