package com.example.llama.api

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface FlaskApiService {
    @POST("/")
    fun getModelResponse(@Body requestBody: Map<String, String>): Call<Map<String, String>>
}
