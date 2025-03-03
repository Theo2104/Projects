package com.example.llama

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.*
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.llama.api.createRetrofitClient
import com.example.llama.ui.UserInterface
import com.example.llama.ui.theme.LlamaTheme
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.*
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException

class MainActivity : ComponentActivity() {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var vibrator: Vibrator
    private lateinit var sharedPreferences: SharedPreferences
    
    // Eindeutige Session-ID für Gesprächskontext-Erhaltung
    private var sessionId = ""

    // State-Variablen
    private val spokenText = mutableStateOf("")
    private val assistantResponse = mutableStateOf("")
    private val explanationText = mutableStateOf("")
    private val isLoading = mutableStateOf(false)
    private val isListening = mutableStateOf(false)
    private val isBusy = mutableStateOf(false)
    private val showSettingsDialog = mutableStateOf(false)
    
    // TTS-Parameter mit Standardwerten
    private var ttsPitch by mutableStateOf(1.0f)
    private var ttsSpeed by mutableStateOf(1.0f)
    private var ttsVolume by mutableStateOf(0.5f)
    
    // Letzte erkannte Anfrage für Wiederholungen
    private var lastRecognizedInput = ""
    // Speichere die aktuelle Benutzereingabe für Fallback-Antworten
    private var currentUserInput: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Laden der gespeicherten Einstellungen
        sharedPreferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val isDarkModeEnabled = sharedPreferences.getBoolean("dark_mode", false)
        ttsPitch = sharedPreferences.getFloat("tts_pitch", 1.0f)
        ttsSpeed = sharedPreferences.getFloat("tts_speed", 1.0f)
        ttsVolume = sharedPreferences.getFloat("tts_volume", 0.5f)
        
        // Generiere eine eindeutige Session-ID für den Server (oder lade sie aus SharedPreferences)
        sessionId = sharedPreferences.getString("session_id", "") ?: ""
        if (sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString()
            sharedPreferences.edit().putString("session_id", sessionId).apply()
        }
        
        // Vibrator initialisieren
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        // Mikrofonberechtigung anfordern
        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (!isGranted) {
                Toast.makeText(this, "Mikrofon-Berechtigung wird benötigt", Toast.LENGTH_SHORT).show()
            }
        }
        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)

        // Prüfe Serververbindung
        checkServerConnection()

        // Sprach- und TTS-Initialisierung 
        initializeTextToSpeech()
        initializeSpeechRecognizer()

        setContent {
            // UI-Status-Variablen
            var darkMode by remember { mutableStateOf(isDarkModeEnabled) }
            var explainEnabled by remember { mutableStateOf(sharedPreferences.getBoolean("explain_enabled", false)) }
            
            LlamaTheme(darkTheme = darkMode) {
                UserInterface(
                    spokenText = spokenText.value,
                    assistantResponse = assistantResponse.value,
                    explanation = explanationText.value,
                    isLoading = isLoading.value,
                    onListenClick = { startListening() },
                    onRepeatClick = { repeatResponse() },
                    onSettingsClick = { showSettingsDialog.value = true },
                    showSettingsDialog = showSettingsDialog.value,
                    onDismissSettings = { showSettingsDialog.value = false },
                    ttsPitch = ttsPitch,
                    ttsSpeed = ttsSpeed,
                    ttsVolume = ttsVolume,
                    onTtsPitchChange = { newPitch ->
                        ttsPitch = newPitch
                        textToSpeech.setPitch(ttsPitch)
                        saveSettings("tts_pitch", newPitch)
                    },
                    onTtsSpeedChange = { newSpeed ->
                        ttsSpeed = newSpeed
                        textToSpeech.setSpeechRate(ttsSpeed)
                        saveSettings("tts_speed", newSpeed)
                    },
                    onTtsVolumeChange = { newVolume: Float ->
                        ttsVolume = newVolume
                        saveSettings("tts_volume", newVolume)
                    },
                    isDarkMode = darkMode,
                    onDarkModeChange = { isEnabled ->
                        darkMode = isEnabled
                        saveSettings("dark_mode", isEnabled)
                    },
                    explainEnabled = explainEnabled,
                    onExplainChange = { newValue ->
                        explainEnabled = newValue
                        saveSettings("explain_enabled", newValue)
                    }
                )
            }
        }
    }
    
    private fun saveSettings(key: String, value: Any) {
        when (value) {
            is Boolean -> sharedPreferences.edit().putBoolean(key, value).apply()
            is Float -> sharedPreferences.edit().putFloat(key, value).apply()
            is String -> sharedPreferences.edit().putString(key, value).apply()
            else -> throw IllegalArgumentException("Unsupported type for settings: ${value::class.java.name}")
        }
    }
    
    private fun initializeTextToSpeech() {
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Explizit deutsches Locale mit Ländercode setzen
                val germanLocale = Locale("de", "DE")
                val result = textToSpeech.setLanguage(germanLocale)
                
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TextToSpeech", "Deutsche Sprache wird nicht unterstützt! Verwende Standardsprache.")
                    Toast.makeText(this, "Die deutsche Sprache wird nicht unterstützt. Verwende Standardsprache.", Toast.LENGTH_LONG).show()
                } else {
                    Log.d("TextToSpeech", "TextToSpeech auf Deutsch initialisiert: ${germanLocale.displayLanguage}")
                }
                
                textToSpeech.setPitch(ttsPitch)
                textToSpeech.setSpeechRate(ttsSpeed)
                
                // Füge einen Listener hinzu, um den Fortschritt der Sprachausgabe zu verfolgen
                textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        // Sprachausgabe beginnt
                        Log.d("TextToSpeech", "Sprachausgabe beginnt")
                    }
                    
                    override fun onDone(utteranceId: String?) {
                        // Sprachausgabe abgeschlossen
                        Log.d("TextToSpeech", "Sprachausgabe abgeschlossen")
                    }
                    
                    override fun onError(utteranceId: String?) {
                        // Fehler bei der Sprachausgabe
                        Log.e("TextToSpeech", "Fehler bei der Sprachausgabe")
                    }
                })
            } else {
                Log.e("TextToSpeech", "TextToSpeech konnte nicht initialisiert werden: $status")
                Toast.makeText(this, "Sprachausgabe konnte nicht initialisiert werden.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun initializeSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Spracherkennung ist auf diesem Gerät nicht verfügbar", Toast.LENGTH_LONG).show()
            return
        }
        
        // Aktuelle deutsche Sprache im Log dokumentieren
        val deviceLocale = Locale.getDefault()
        Log.d("SpeechRecognition", "Aktuelle Gerätesprache: ${deviceLocale.language}-${deviceLocale.country}, Display: ${deviceLocale.displayLanguage}")
        
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                if (results == null) {
                    Log.e("SpeechRecognition", "Leeres Bundle erhalten")
                    assistantResponse.value = "Ich konnte deine Sprache nicht erkennen. Bitte versuche es noch einmal."
                    speakResponse(assistantResponse.value)
                    isLoading.value = false
                    return
                }
                
                // Debug-Ausgabe aller Keys im Bundle
                for (key in results.keySet()) {
                    Log.d("SpeechRecognition", "Bundle enthält Key: $key")
                }
                
                val resultsList = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                
                // Alle verfügbaren Spracherkennungsergebnisse ausgeben
                if (resultsList != null) {
                    for (i in resultsList.indices) {
                        Log.d("SpeechRecognition", "Ergebnis $i: ${resultsList[i]}")
                    }
                }
                
                // Prüfen, ob Ergebnisse vorhanden sind
                if (resultsList != null && resultsList.isNotEmpty()) {
                    val recognizedText = resultsList[0]
                    Log.d("SpeechRecognition", "Erkannter Text: $recognizedText")
                    
                    // Auch bei leerem Text als erkannt behandeln
                    if (recognizedText.isBlank()) {
                        Log.w("SpeechRecognition", "Erkannter Text ist leer")
                        assistantResponse.value = "Ich habe nichts gehört. Bitte versuche es noch einmal."
                        speakResponse(assistantResponse.value)
                        isLoading.value = false
                        return
                    }
                    
                    Toast.makeText(applicationContext, "Erkannt: $recognizedText", Toast.LENGTH_SHORT).show()
                    
                    spokenText.value = recognizedText
                    lastRecognizedInput = recognizedText

                    // Taktile Rückmeldung (Vibration)
                    vibrateFeedback(100) // Kurze, sanfte Vibration

                    // Trotz möglicher englischer Erkennung die Eingabe akzeptieren
                    processUserInput(recognizedText)
                } else {
                    Log.e("SpeechRecognition", "Leere Ergebnisliste erhalten")
                    assistantResponse.value = "Ich konnte deine Sprache nicht erkennen. Bitte versuche es noch einmal."
                    speakResponse(assistantResponse.value)
                    isLoading.value = false
                }
            }

            override fun onError(error: Int) {
                Log.e("SpeechRecognition", "Fehler bei der Spracherkennung: $error")
                
                val errorText = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio-Problem aufgetreten. Bitte überprüfe dein Mikrofon."
                    SpeechRecognizer.ERROR_CLIENT -> "Interner Fehler. Bitte versuche es noch einmal."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mikrofon-Berechtigung fehlt."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Netzwerkprobleme aufgetreten. Bitte überprüfe deine Internetverbindung."
                    SpeechRecognizer.ERROR_NO_MATCH -> "Ich konnte deine Sprache nicht verstehen. Bitte sprich deutlicher."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Der Sprachassistent ist beschäftigt. Einen Moment bitte."
                    SpeechRecognizer.ERROR_SERVER -> "Serverproblem aufgetreten. Bitte versuche es später noch einmal."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Ich habe keine Sprache erkannt. Bitte sprich nach dem Antippen des Mikrofons."
                    else -> "Es ist ein Fehler aufgetreten (Code: $error). Bitte versuche es noch einmal."
                }
                
                Toast.makeText(applicationContext, "Fehler: $errorText", Toast.LENGTH_LONG).show()
                
                assistantResponse.value = errorText
                speakResponse(errorText)
                isLoading.value = false
            }

            override fun onReadyForSpeech(params: Bundle?) {
                Log.d("SpeechRecognition", "Bereit für Spracherkennung")
                spokenText.value = ""
            }

            override fun onBeginningOfSpeech() {
                Log.d("SpeechRecognition", "Spracherkennung beginnt")
                isLoading.value = true
            }
            
            override fun onEndOfSpeech() {
                Log.d("SpeechRecognition", "Spracherkennung endet")
            }
            
            override fun onRmsChanged(rmsdB: Float) {
                // Lautstärkeänderungen - keine Aktion notwendig
            }
            
            override fun onBufferReceived(buffer: ByteArray?) {
                // Buffer erhalten - keine Aktion notwendig
            }
            
            override fun onPartialResults(partialResults: Bundle?) {
                val resultsList = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (resultsList != null && resultsList.isNotEmpty()) {
                    val recognizedText = resultsList[0]
                    spokenText.value = recognizedText + "..."
                    Log.d("SpeechRecognition", "Teilweises Ergebnis: $recognizedText")
                }
            }
            
            override fun onEvent(eventType: Int, params: Bundle?) {
                Log.d("SpeechRecognition", "Event: $eventType")
            }
        })
    }

    private fun startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Mikrofon-Berechtigung fehlt. Bitte erteilen Sie die Berechtigung in den Einstellungen.", Toast.LENGTH_LONG).show()
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            return
        }
        
        // Test mit RecognizerIntent direkt
        val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
        speechIntent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Sprechen Sie jetzt...")
        
        try {
            startActivityForResult(speechIntent, 100)
            return
        } catch (e: Exception) {
            Log.e("SpeechRecognition", "Fehler beim Starten der Intent-basierten Erkennung: ${e.message}")
            // Fallback auf SpeechRecognizer
        }
        
        isLoading.value = true
        spokenText.value = "Ich höre zu..."
        
        try {
            speechRecognizer.cancel()
        } catch (e: Exception) {
            Log.e("SpeechRecognition", "Fehler beim Abbrechen der letzten Erkennung: ${e.message}")
        }
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            // Standard-Konfiguration
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            
            // Verschiedene Möglichkeiten, um Deutsch zu setzen (einer davon sollte funktionieren)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "de-DE")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true)
            
            // Weitere Parameter
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)  // Mehr Ergebnisse für bessere Chancen
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }

        try {
            speechRecognizer.startListening(intent)
            Log.d("SpeechRecognition", "Spracherkennung gestartet mit Sprache: de-DE")
            vibrateFeedback(50)
            Toast.makeText(this, "Deutsche Spracherkennung gestartet...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("SpeechRecognition", "Fehler beim Starten der Spracherkennung: ${e.message}")
            Toast.makeText(this, "Fehler beim Starten der Spracherkennung: ${e.message}", Toast.LENGTH_SHORT).show()
            isLoading.value = false
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == 100 && resultCode == RESULT_OK) {
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (results != null && results.isNotEmpty()) {
                val recognizedText = results[0]
                Log.d("SpeechRecognition", "Erkannter Text (via Intent): $recognizedText")
                
                Toast.makeText(applicationContext, "Erkannt (via Intent): $recognizedText", Toast.LENGTH_SHORT).show()
                
                spokenText.value = recognizedText
                lastRecognizedInput = recognizedText
                
                // Verarbeite die Eingabe
                processUserInput(recognizedText)
            } else {
                Log.e("SpeechRecognition", "Keine Ergebnisse vom Intent erhalten")
                assistantResponse.value = "Ich konnte deine Sprache nicht erkennen. Bitte versuche es noch einmal."
                speakResponse(assistantResponse.value)
            }
        } else if (requestCode == 100) {
            Log.e("SpeechRecognition", "Intent-Erkennung fehlgeschlagen: resultCode=$resultCode")
            assistantResponse.value = "Die Spracherkennung hat nicht funktioniert. Bitte versuche es noch einmal."
            speakResponse(assistantResponse.value)
        }
    }

    private fun processUserInput(input: String) {
        if (input.isBlank()) {
            assistantResponse.value = "Ich habe dich nicht verstanden. Bitte sprich noch einmal."
            speakResponse(assistantResponse.value)
            return
        }
        
        // Speichere die aktuelle Eingabe für Fallback-Antworten
        currentUserInput = input
        
        // API-Anfrage vorbereiten
        isLoading.value = true
        explanationText.value = ""
        
        // Debug-Information
        Log.d("ApiRequest", "Verarbeite Benutzeranfrage: '$input'")
        
        val apiService = createRetrofitClient()
        
        val explainEnabled = sharedPreferences.getBoolean("explain_enabled", false)
        val requestBody = mapOf(
            "input" to input,
            "explain" to explainEnabled.toString(),
            "session_id" to sessionId
        )
        
        // Debug-Ausgabe der Anfrage
        Log.d("ApiRequest", "Sende Anfrage mit: input='$input', explain='${explainEnabled.toString()}', session_id='$sessionId'")
        
        val call = apiService.getModelResponse(requestBody)
        call.enqueue(object : Callback<Map<String, String>> {
            override fun onResponse(call: Call<Map<String, String>>, response: Response<Map<String, String>>) {
                Log.d("ApiRequest", "Antwort erhalten: isSuccessful=${response.isSuccessful}, code=${response.code()}")
                
                if (response.isSuccessful) {
                    val responseData = response.body()
                    Log.d("ApiRequest", "Antwortdaten: $responseData")
                    
                    if (responseData != null) {
                        // Füge mehr Debug-Informationen über die empfangenen Schlüssel hinzu
                        Log.d("ApiRequest", "Erhaltene Schlüssel: ${responseData.keys.joinToString()}")
                        
                        val generatedText = responseData["response"]
                        
                        if (generatedText != null) {
                            assistantResponse.value = generatedText
                            
                            if (explainEnabled) {
                                explanationText.value = responseData["explanation"] ?: ""
                            }
                            
                            Log.d("ApiRequest", "Erfolgreiche Antwort: '$generatedText'")
                            speakResponse(generatedText)
                        } else {
                            // Versuche alternative Schlüssel, falls der Server ein anderes Format verwendet
                            val alternativeText = responseData.entries.firstOrNull()?.value
                            
                            if (alternativeText != null) {
                                Log.w("ApiRequest", "Verwende alternativen Schlüssel: ${responseData.entries.first().key}")
                                assistantResponse.value = alternativeText
                                speakResponse(alternativeText)
                            } else {
                                Log.e("ApiRequest", "Antwort enthält keinen 'response'-Schlüssel und keine alternativen Werte: $responseData")
                                assistantResponse.value = "Fehler: Unerwartetes Antwortformat. Bitte versuche es noch einmal."
                                speakResponse(assistantResponse.value)
                            }
                        }
                    } else {
                        Log.e("ApiRequest", "Antwortdaten sind null")
                        assistantResponse.value = "Fehler: Leere Antwort vom Server. Bitte versuche es noch einmal."
                        speakResponse(assistantResponse.value)
                    }
                } else {
                    try {
                        val errorBody = response.errorBody()?.string()
                        Log.e("ApiRequest", "API-Fehler: ${response.code()} - ${response.message()}, Error Body: $errorBody")
                        
                        // Versuche einen detaillierteren Fehler zu extrahieren
                        val errorMessage = when (response.code()) {
                            401 -> "Authentifizierungsfehler beim Zugriff auf den Server."
                            404 -> "Der API-Endpunkt wurde nicht gefunden."
                            429 -> "Zu viele Anfragen an den Server. Bitte warte einen Moment."
                            500, 502, 503, 504 -> "Serverfehler. Bitte versuche es später noch einmal."
                            else -> "Verbindung zum Server fehlgeschlagen (Code: ${response.code()})."
                        }
                        
                        assistantResponse.value = "Fehler: $errorMessage"
                    } catch (e: Exception) {
                        Log.e("ApiRequest", "Fehler beim Lesen des Fehlerkörpers: ${e.message}")
                        assistantResponse.value = "Fehler: Ich konnte keine Antwort erhalten. Bitte versuche es noch einmal."
                    }
                    
                    speakResponse(assistantResponse.value)
                }
                
                isLoading.value = false
            }

            override fun onFailure(call: Call<Map<String, String>>, t: Throwable) {
                isLoading.value = false
                
                // Spezifische Fehlermeldungen je nach Ausnahmetyp
                val errorMessage = when {
                    t is SocketTimeoutException -> "Zeitüberschreitung bei der Serveranfrage. Versuche es noch einmal oder prüfe die Serverauslastung."
                    t is ConnectException -> "Verbindung zum Server fehlgeschlagen. Prüfe, ob der Server läuft und du im richtigen Netzwerk bist."
                    t.message?.contains("reset") == true -> "Verbindung zurückgesetzt: Der Server hat die Verbindung beendet. Mögliche Ursachen: 1) Server-Neustart, 2) Zu große Anfrage, 3) Netzwerkfirewall"
                    t is SSLHandshakeException -> "SSL-Fehler bei der Verbindung. Bitte prüfe die Sicherheitskonfiguration."
                    t.message?.contains("certificate") == true -> "SSL-Zertifikatsprobleme: ${t.message}"
                    else -> "Verbindungsfehler: ${t.message ?: "Unbekannter Fehler"}"
                }
                
                // Ausführliche Fehlermeldungen und Protokollierung für Diagnosezwecke
                Log.e("ApiRequest", "Fehler bei der API-Anfrage: $errorMessage", t)
                Log.d("ApiRequest", "Server-URL: ${call.request().url}")
                Log.d("ApiRequest", "Request-Headers: ${call.request().headers}")
                Log.d("ApiRequest", "Exception Typ: ${t.javaClass.simpleName}")
                Log.d("ApiRequest", "Stacktrace: ${t.stackTraceToString()}")
                
                // Die UI muss auf dem Hauptthread aktualisiert werden
                runOnUiThread {
                    try {
                        isLoading.value = false
                        isListening.value = false
                        isBusy.value = false
                        
                        // Toast mit Fehlerinformation anzeigen
                        Toast.makeText(applicationContext, errorMessage, Toast.LENGTH_LONG).show()
                        
                        // Generiere und zeige die Fallback-Antwort
                        val fallbackMessage = getFallbackResponse(t, response.code())
                        Log.i("ApiRequest", "Setze Fallback-Antwort: '$fallbackMessage' für Eingabe: '$currentUserInput'")
                        assistantResponse.value = fallbackMessage
                        speakResponse(fallbackMessage)
                    } catch (e: Exception) {
                        Log.e("ApiRequest", "Fehler beim Anzeigen der Fallback-Antwort", e)
                    }
                }
            }
        })
    }

    private fun vibrateFeedback(duration: Long = 100, amplitude: Int = VibrationEffect.DEFAULT_AMPLITUDE) {
        val adjustedAmplitude = (amplitude * 0.7).toInt()
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                vibrator.vibrate(VibrationEffect.createOneShot(duration, adjustedAmplitude))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, adjustedAmplitude))
            } else {
                vibrator.vibrate(duration)
            }
        } catch (e: Exception) {
            // Ignoriere Fehler bei der Vibration
        }
    }

    private fun speakResponse(response: String) {
        if (response.isBlank()) return
        
        val cleanedResponse = response
            .replace("*", "") 
            .replace(Regex("[^\\p{L}\\p{Nd}\\s.,?!]"), "") // Entfernt Sonderzeichen außer Satzzeichen
        
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, ttsVolume)
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "msg")
        }
        
        textToSpeech.speak(cleanedResponse, TextToSpeech.QUEUE_FLUSH, params, "msg")
    }

    private fun repeatResponse() {
        if (assistantResponse.value.isNotEmpty()) {
            speakResponse(assistantResponse.value)
            vibrateFeedback(50)
        } else if (lastRecognizedInput.isNotEmpty()) {
            processUserInput(lastRecognizedInput)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
        textToSpeech.shutdown()
    }

    private fun checkServerConnection() {
        // Erstelle einen API-Client
        val apiService = createRetrofitClient()
        
        // Einfache Test-Anfrage
        val testRequest = mapOf(
            "input" to "test",
            "explain" to "false",
            "session_id" to sessionId
        )
        
        // Sende eine Testanfrage an den Server
        Log.d("ServerCheck", "Überprüfe HTTP-Serververbindung mit sessionId: $sessionId")
        
        // Verwende einen Hintergrund-Thread, um die Netzwerkanfrage nicht auf dem Hauptthread auszuführen
        Thread {
            try {
                val response = apiService.getModelResponse(testRequest).execute()
                if (response.isSuccessful) {
                    Log.d("ServerCheck", "HTTP-Serververbindung erfolgreich! Antwort: ${response.body()}")
                    runOnUiThread {
                        Toast.makeText(this, "HTTP-Serververbindung erfolgreich", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val errorCode = response.code()
                    val errorBody = response.errorBody()?.string() ?: "Unbekannter Fehler"
                    Log.e("ServerCheck", "HTTP-Serverfehler: $errorCode - ${response.message()}, Body: $errorBody")
                    runOnUiThread {
                        Toast.makeText(this, "HTTP-Serverfehler: $errorCode - Prüfe Logs für Details", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                val errorMessage = when {
                    e is ConnectException -> "HTTP-Verbindungsfehler: Server nicht erreichbar oder offline."
                    e is SocketTimeoutException -> "HTTP-Zeitüberschreitung: Server reagiert zu langsam."
                    e.message?.contains("404") == true -> "HTTP 404: Endpunkt nicht gefunden. API-Route prüfen."
                    e.message?.contains("reset") == true -> "Verbindung zurückgesetzt: Der Server hat die HTTP-Verbindung abgelehnt."
                    else -> "HTTP-Verbindungsfehler: ${e.message}"
                }
                
                Log.e("ServerCheck", "HTTP-$errorMessage", e)
                Log.e("ServerCheck", "Exception Typ: ${e.javaClass.simpleName}")
                Log.e("ServerCheck", "Stacktrace: ${e.stackTraceToString()}")
                
                runOnUiThread {
                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    /**
     * Liefert eine sinnvolle Fallback-Antwort, wenn der Server nicht verfügbar ist.
     * Bietet je nach Fehlerart unterschiedliche, unterstützende Antworten.
     */
    private fun getFallbackResponse(error: Throwable?, errorCode: Int? = null): String {
        Log.e("API", "Fehler beim Abruf der Antwort: ${error?.message}", error)
        
        // Wenn ein HTTP-Fehlercode vorliegt, geben wir eine spezifische Meldung aus
        if (errorCode != null) {
            return when (errorCode) {
                500 -> "Der Server hat ein Problem festgestellt. Ich helfe dir trotzdem. Was möchtest du wissen?"
                503 -> "Der Server ist gerade überlastet. Kannst du die Frage später noch einmal stellen?"
                in 400..499 -> "Deine Anfrage konnte nicht richtig verstanden werden. Kannst du sie anders formulieren?"
                else -> "Es gab ein Problem mit der Serververbindung. Ich bin trotzdem für dich da."
            }
        }
        
        // Spezifische Meldungen je nach Fehlertyp
        if (error != null) {
            return when (error) {
                is SocketTimeoutException -> "Die Anfrage hat zu lange gedauert. Kannst du deine Frage kürzer fassen?"
                
                is UnknownHostException -> "Ich konnte keine Verbindung zum Server herstellen. Bitte prüfe deine Internetverbindung."
                
                is ConnectException -> "Der Server ist nicht erreichbar. Bitte versuche es später noch einmal."
                
                is SSLException -> "Es gab ein Problem mit der sicheren Verbindung. Das wird bald behoben."
                
                else -> {
                    // Analyse der Fehlermeldung für bessere Diagnose
                    val errorMessage = error.message?.lowercase() ?: ""
                    
                    when {
                        errorMessage.contains("closed") -> 
                            "Die Verbindung wurde geschlossen. Ich bin trotzdem für dich da."
                            
                        errorMessage.contains("reset") -> 
                            "Die Verbindung wurde zurückgesetzt. Bitte versuche es noch einmal."
                            
                        errorMessage.contains("timeout") -> 
                            "Die Anfrage hat zu lange gedauert. Kannst du deine Frage kürzer fassen?"
                            
                        errorMessage.contains("certificate") -> 
                            "Es gibt ein Problem mit der Sicherheitszertifizierung. Ich helfe dir trotzdem."
                            
                        else -> "Es gab ein technisches Problem. Aber ich bin trotzdem für dich da."
                    }
                }
            }
        }
        
        // Standardmeldung, wenn keine spezifischen Informationen vorliegen
        return "Ich konnte keine Verbindung zum Server herstellen. Ich bin aber trotzdem für dich da."
    }
}
