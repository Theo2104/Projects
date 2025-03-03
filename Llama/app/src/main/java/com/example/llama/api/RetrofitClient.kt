package com.example.llama.api

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.cert.X509Certificate
import javax.net.ssl.*
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import kotlin.math.pow
import kotlin.math.minOf
import java.util.Random

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
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())

        // Unsicheres SocketFactory setzen
        val sslSocketFactory = sslContext.socketFactory

        // Komplett unsichere HostnameVerifier, die alles akzeptiert
        val hostnameVerifier = HostnameVerifier { _, _ -> true }

        // TrustManager explizit setzen
        val trustManager = trustAllCerts[0] as X509TrustManager
        
        val builder = OkHttpClient.Builder()
        builder.sslSocketFactory(sslSocketFactory, trustManager)
            .hostnameVerifier(hostnameVerifier)
            .connectTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            // Füge einen Retry-Interceptor hinzu
            .addInterceptor(RetryInterceptor())
        
        // Debug-Ausgabe für SSL-Probleme
        Log.d("SSL", "Unsicherer OkHttpClient erstellt mit vollständiger SSL-Validierung deaktiviert")
        
        builder.build()
    } catch (e: Exception) {
        Log.e("SSL", "Fehler beim Erstellen des unsicheren Clients", e)
        throw RuntimeException(e)
    }
}

/**
 * Implementiert einen Retry-Mechanismus für fehlgeschlagene API-Anfragen.
 * Anfragen werden automatisch wiederholt, wenn bestimmte Fehler auftreten.
 */
class RetryInterceptor(private val maxRetries: Int = 3) : Interceptor {
    private val random = Random()
    
    private val TAG = "RetryInterceptor"

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var response: Response? = null
        var lastException: IOException? = null
        var lastResponseCode = -1

        Log.d(TAG, "Sende Anfrage an: ${request.url}")
        
        // Versuche die Anfrage mehrmals
        for (attempt in 1..maxRetries) {
            try {
                // Stelle sicher, dass eine frühere Response geschlossen wird
                response?.close()
                
                // Führe die Anfrage aus
                response = chain.proceed(request)
                lastResponseCode = response.code
                
                // Bei Erfolg, gib die Antwort zurück
                if (response.isSuccessful) {
                    return response
                }
                
                // Bei HTTP 500 Fehlern, logge Details
                if (lastResponseCode == 500) {
                    Log.e(TAG, "Server-Fehler 500 bei Versuch $attempt/$maxRetries")
                    try {
                        val errorBody = response.peekBody(2048).string()
                        Log.e(TAG, "Error Body: $errorBody")
                    } catch (e: Exception) {
                        Log.e(TAG, "Konnte Error Body nicht lesen: ${e.message}")
                    }
                }
                
                // Schließe die Response, bevor wir es erneut versuchen
                response.close()
                
                // Bei weiteren Versuchen, warte exponentiell länger
                if (attempt < maxRetries) {
                    Log.e(TAG, "Letzter Versuch fehlgeschlagen mit Code: $lastResponseCode")
                    val waitTimeMs = calculateBackoffMillis(attempt)
                    Log.i(TAG, "Warte ${waitTimeMs}ms vor Versuch ${attempt + 1}/$maxRetries")
                    Thread.sleep(waitTimeMs)
                    Log.i(TAG, "Wiederhole Anfrage (Versuch ${attempt + 1}/$maxRetries): ${request.url}")
                }
                
            } catch (e: IOException) {
                // Schließe bei einem IO-Fehler die Response, falls vorhanden
                response?.close()
                
                // Speichere die letzte Exception
                lastException = e
                
                // Logge den Fehlertyp für bessere Diagnose
                val errorType = when (e) {
                    is SocketTimeoutException -> "Timeout"
                    is UnknownHostException -> "Unbekannter Host"
                    is ConnectException -> "Verbindungsfehler"
                    else -> "IO-Fehler"
                }
                
                Log.e(TAG, "$errorType bei Versuch $attempt/$maxRetries: ${e.message}")
                
                // Bei weiteren Versuchen, warte exponentiell länger
                if (attempt < maxRetries) {
                    val waitTimeMs = calculateBackoffMillis(attempt)
                    Log.i(TAG, "Warte ${waitTimeMs}ms vor Versuch ${attempt + 1}/$maxRetries")
                    Thread.sleep(waitTimeMs)
                    Log.i(TAG, "Wiederhole Anfrage (Versuch ${attempt + 1}/$maxRetries): ${request.url}")
                }
            } catch (e: IllegalStateException) {
                // Fange IllegalStateException, die auftreten kann, wenn eine bereits geschlossene Response verwendet wird
                Log.e(TAG, "IllegalStateException bei Versuch $attempt/$maxRetries: ${e.message}")
                
                // Bei weiteren Versuchen, warte exponentiell länger
                if (attempt < maxRetries) {
                    val waitTimeMs = calculateBackoffMillis(attempt)
                    Log.i(TAG, "Warte ${waitTimeMs}ms vor Versuch ${attempt + 1}/$maxRetries")
                    Thread.sleep(waitTimeMs)
                    Log.i(TAG, "Wiederhole Anfrage (Versuch ${attempt + 1}/$maxRetries): ${request.url}")
                }
            }
        }
        
        // Wenn wir hier ankommen, waren alle Versuche erfolglos
        Log.e(TAG, "Alle $maxRetries Versuche fehlgeschlagen")
        
        // Wenn wir eine Response haben, geben wir diese zurück, auch wenn sie einen Fehlercode enthält
        if (response != null) {
            return response
        }
        
        // Wenn wir keine Response haben, aber eine Exception, werfen wir diese
        if (lastException != null) {
            throw lastException
        }
        
        // Fallback für den unwahrscheinlichen Fall, dass wir weder Response noch Exception haben
        throw IOException("Anfrage fehlgeschlagen nach $maxRetries Versuchen, letzter Statuscode war $lastResponseCode")
    }
    
    /**
     * Berechnet die Wartezeit für den nächsten Versuch basierend auf der exponentiellen Backoff-Strategie mit Jitter.
     */
    private fun calculateBackoffMillis(attempt: Int): Long {
        // Basis-Wartezeit: 1000ms * 2^(Versuchsnummer)
        val baseWaitTimeMs = 1000 * (1 shl (attempt - 1))
        
        // Füge Zufälligkeit (Jitter) hinzu, um "Thundering Herd"-Probleme zu vermeiden
        // Zufälliger Wert zwischen 50% und 100% der Basis-Wartezeit
        val jitterPct = 0.5 + random.nextDouble() * 0.5
        
        // Begrenze die maximale Wartezeit auf 30 Sekunden
        return minOf(baseWaitTimeMs * jitterPct, 30000.0).toLong()
    }
}

/**
 * Erstellt einen Retrofit-Client für die Kommunikation mit dem Flask-Backend.
 * Unterstützt die erweiterten Anwendungsfunktionen wie Session-Management und xAI.
 */
fun createRetrofitClient(): FlaskApiService {
    // Erstelle den OkHttpClient mit RetryInterceptor und unsicheren SSL-Einstellungen für Entwicklung
    val okHttpClient = createUnsafeOkHttpClient().newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS) // Längere Verbindungs-Timeout
        .readTimeout(30, TimeUnit.SECONDS)    // Längere Lese-Timeout
        .writeTimeout(30, TimeUnit.SECONDS)   // Längere Schreib-Timeout
        .addInterceptor(RetryInterceptor(3))  // Neuer RetryInterceptor mit 3 Versuchen
        .build()
    
    // Verschiedene Basis-URLs für verschiedene Umgebungen
    val serverOptions = listOf(
        "http://10.0.2.2:5000",         // Option 1: Android Emulator -> Host mit HTTP
        "http://192.168.2.106:5000",    // Option 2: Lokales WLAN (Host-PC IP) mit HTTP
        "http://192.168.2.1:5000",      // Option 3: Lokales WLAN (Router) mit HTTP
        "http://127.0.0.1:5000"         // Option 4: Localhost (funktioniert evtl. nicht auf Android) mit HTTP
    )
    
    // Versuche, die Umgebung zu erkennen (Emulator oder echtes Gerät)
    val isEmulator = isEmulator()
    
    // Wähle die passende Server-URL basierend auf der Umgebung
    val serverIndex = if (isEmulator) {
        Log.d("RetrofitClient", "Emulator erkannt, verwende localhost (10.0.2.2)")
        0 // Verwende die Emulator-Option (10.0.2.2:5000)
    } else {
        Log.d("RetrofitClient", "Physisches Gerät erkannt, verwende lokale IP")
        1 // Verwende die lokale IP-Option (192.168.2.106:5000)
    }
    
    val baseUrl = serverOptions[serverIndex]
    Log.d("RetrofitClient", "Verwende HTTP Server-Option $serverIndex: $baseUrl")
    
    val retrofit = Retrofit.Builder()
        .baseUrl("$baseUrl/") // Stelle sicher, dass die Base-URL mit einem Slash endet
        .addConverterFactory(GsonConverterFactory.create())
        .client(okHttpClient)
        .build()

    return retrofit.create(FlaskApiService::class.java)
}

/**
 * Hilfsmethode, um zu erkennen, ob die App in einem Emulator läuft.
 */
fun isEmulator(): Boolean {
    return android.os.Build.MODEL.contains("sdk", ignoreCase = true) ||
           android.os.Build.MODEL.contains("emulator", ignoreCase = true) ||
           android.os.Build.MODEL.contains("google_sdk", ignoreCase = true) ||
           android.os.Build.PRODUCT.contains("sdk", ignoreCase = true) ||
           android.os.Build.FINGERPRINT.contains("generic", ignoreCase = true)
}
