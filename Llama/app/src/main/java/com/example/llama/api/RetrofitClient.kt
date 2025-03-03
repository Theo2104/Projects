package com.example.llama.api

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.cert.X509Certificate
import javax.net.ssl.*

/**
 * Erstellt einen OkHttpClient, der SSL-Zertifikate ignoriert.
 * Hinweis: Dies sollte in Produktionsumgebungen vermieden werden.
 */
fun createUnsafeOkHttpClient(): OkHttpClient {
    return try {
        // Vertrauenswürdiger Manager, der alle Zertifikate akzeptiert
        val trustAllCerts = arrayOf<TrustManager>(
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }
        )

        // SSL-Kontext mit dem vertrauenswürdigen Manager initialisieren
        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())

        // Unsicheres SocketFactory setzen
        val sslSocketFactory = sslContext.socketFactory

        OkHttpClient.Builder()
            .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true } // Hostnamenprüfung überspringen
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    } catch (e: Exception) {
        throw RuntimeException(e)
    }
}

/**
 * Erstellt einen Retrofit-Client für die Kommunikation mit dem Flask-Backend.
 * Unterstützt die erweiterten Anwendungsfunktionen wie Session-Management und xAI.
 */
fun createRetrofitClient(): FlaskApiService {
    val okHttpClient = createUnsafeOkHttpClient()
    val retrofit = Retrofit.Builder()
        .baseUrl("https://192.168.2.106:5000/")
        .addConverterFactory(GsonConverterFactory.create())
        .client(okHttpClient)
        .build()

    return retrofit.create(FlaskApiService::class.java)
}
