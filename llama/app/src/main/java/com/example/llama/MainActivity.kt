package com.example.llama

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.llama.api.FlaskApiService
import com.example.llama.api.createRetrofitClient
import com.example.llama.ui.theme.LlamaTheme
import com.example.llama.ui.UserInterface
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Locale
import java.util.UUID
import android.content.SharedPreferences
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

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
    private val showSettingsDialog = mutableStateOf(false)
    
    // TTS-Parameter mit Standardwerten
    private var ttsPitch by mutableStateOf(1.0f)
    private var ttsSpeed by mutableStateOf(1.0f)
    private var ttsVolume by mutableStateOf(0.5f)
    
    // Letzte erkannte Anfrage für Wiederholungen
    private var lastRecognizedInput = ""

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
                    onTtsVolumeChange = { newVolume ->
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
                textToSpeech.language = Locale.GERMAN
                textToSpeech.setPitch(ttsPitch)
                textToSpeech.setSpeechRate(ttsSpeed)
                
                // Füge einen Listener hinzu, um den Fortschritt der Sprachausgabe zu verfolgen
                textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        // Sprachausgabe beginnt
                    }
                    
                    override fun onDone(utteranceId: String?) {
                        // Sprachausgabe abgeschlossen
                    }
                    
                    override fun onError(utteranceId: String?) {
                        // Fehler bei der Sprachausgabe
                    }
                })
            }
        }
    }
    
    private fun initializeSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Spracherkennung ist auf diesem Gerät nicht verfügbar", Toast.LENGTH_LONG).show()
            return
        }
        
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val resultsList = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (resultsList != null && resultsList.isNotEmpty()) {
                    val recognizedText = resultsList[0]
                    Log.d("SpeechRecognition", "Erkannter Text: $recognizedText")
                    Toast.makeText(applicationContext, "Erkannt: $recognizedText", Toast.LENGTH_SHORT).show()
                    
                    spokenText.value = recognizedText
                    lastRecognizedInput = recognizedText

                    // Taktile Rückmeldung (Vibration)
                    vibrateFeedback(100) // Kurze, sanfte Vibration

                    processUserInput(recognizedText)
                } else {
                    Log.e("SpeechRecognition", "Leere Ergebnisliste erhalten")
                    assistantResponse.value = "Ich konnte deine Sprache nicht erkennen. Bitte versuche es noch einmal."
                    speakResponse(assistantResponse.value)
                }
            }

            override fun onError(error: Int) {
                Log.e("SpeechRecognition", "Fehler bei der Spracherkennung: $error")
                
                val errorText = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio-Problem aufgetreten."
                    SpeechRecognizer.ERROR_CLIENT -> "Bitte versuche es noch einmal."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mikrofon-Berechtigung fehlt."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Netzwerkprobleme aufgetreten."
                    SpeechRecognizer.ERROR_NO_MATCH -> "Ich habe nichts verstanden. Bitte noch einmal."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Der Sprachassistent ist beschäftigt."
                    SpeechRecognizer.ERROR_SERVER -> "Serverproblem aufgetreten."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Keine Sprache erkannt. Bitte noch einmal."
                    else -> "Fehler aufgetreten. Bitte noch einmal versuchen."
                }
                
                Toast.makeText(applicationContext, "Fehler: $errorText", Toast.LENGTH_SHORT).show()
                
                assistantResponse.value = errorText
                speakResponse(errorText)
                isLoading.value = false
            }

            override fun onBeginningOfSpeech() {
                Log.d("SpeechRecognition", "Spracherkennung beginnt")
                isLoading.value = true
            }
            
            override fun onEndOfSpeech() {
                Log.d("SpeechRecognition", "Spracherkennung endet")
            }
            
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d("SpeechRecognition", "Bereit für Spracherkennung")
                spokenText.value = ""
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
        
        isLoading.value = true
        spokenText.value = "Ich höre zu..."
        
        try {
            speechRecognizer.cancel()
        } catch (e: Exception) {
            Log.e("SpeechRecognition", "Fehler beim Abbrechen der letzten Erkennung: ${e.message}")
        }
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.GERMAN.toString())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Locale.GERMAN.toString())
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        try {
            speechRecognizer.startListening(intent)
            Log.d("SpeechRecognition", "Spracherkennung gestartet")
            vibrateFeedback(50)
            Toast.makeText(this, "Spracherkennung gestartet...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("SpeechRecognition", "Fehler beim Starten der Spracherkennung: ${e.message}")
            Toast.makeText(this, "Fehler beim Starten der Spracherkennung: ${e.message}", Toast.LENGTH_SHORT).show()
            isLoading.value = false
        }
    }

    // Vibrationsmethode für alle Android-Versionen
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

    private fun processUserInput(input: String) {
        if (input.isBlank()) {
            assistantResponse.value = "Ich habe dich nicht verstanden. Bitte sprich noch einmal."
            speakResponse(assistantResponse.value)
            return
        }
        
        isLoading.value = true
        explanationText.value = ""
        
        val apiService = createRetrofitClient()
        
        val explainEnabled = sharedPreferences.getBoolean("explain_enabled", false)
        val requestBody = mapOf(
            "input" to input,
            "explain" to explainEnabled.toString(),
            "session_id" to sessionId
        )
        
        val call = apiService.getModelResponse(requestBody)
        call.enqueue(object : Callback<Map<String, String>> {
            override fun onResponse(call: Call<Map<String, String>>, response: Response<Map<String, String>>) {
                if (response.isSuccessful) {
                    val responseData = response.body()
                    val generatedText = responseData?.get("response") ?: "Keine Antwort erhalten"
                    
                    assistantResponse.value = generatedText
                    
                    if (explainEnabled) {
                        explanationText.value = responseData?.get("explanation") ?: ""
                    }
                    
                    speakResponse(generatedText)
                } else {
                    assistantResponse.value = "Fehler: Ich konnte keine Antwort erhalten. Bitte versuche es noch einmal."
                    speakResponse(assistantResponse.value)
                }
                
                isLoading.value = false
            }

            override fun onFailure(call: Call<Map<String, String>>, t: Throwable) {
                assistantResponse.value = "Verbindungsfehler: Bitte prüfe deine Internetverbindung."
                speakResponse(assistantResponse.value)
                isLoading.value = false
            }
        })
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
}
