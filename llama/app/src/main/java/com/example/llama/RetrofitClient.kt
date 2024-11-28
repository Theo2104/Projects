package com.example.llama.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

fun createRetrofitClient(): HuggingFaceApiService {
    val retrofit = Retrofit.Builder()
        .baseUrl("https://api-inference.huggingface.co") // Basis-URL der Hugging Face API
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    return retrofit.create(HuggingFaceApiService::class.java)
}