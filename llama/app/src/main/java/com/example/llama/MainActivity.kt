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
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.llama.api.createRetrofitClient
import com.example.llama.ui.theme.LlamaTheme
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Locale
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import android.content.SharedPreferences

class MainActivity : ComponentActivity() {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var vibrator: Vibrator
    private lateinit var sharedPreferences: SharedPreferences

    private val spokenText = mutableStateOf("")
    private val assistantResponse = mutableStateOf("")
    private val showSettingsDialog = mutableStateOf(false)

    // Standardwerte für TTS-Parameter
    private var ttsPitch by mutableStateOf(1.0f)
    private var ttsSpeed by mutableStateOf(1.0f)
    private var ttsVolume by mutableStateOf(0.5f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPreferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val isDarkModeEnabled = sharedPreferences.getBoolean("dark_mode", false)
        // Vibrator initialisieren
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        // Mikrofonberechtigung anfordern
        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (!isGranted) {
                Toast.makeText(this, "Mikrofon-Berechtigung benötigt", Toast.LENGTH_SHORT).show()
            }
        }
        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)

        // Sprach- und TTS-Initialisierung
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.language = Locale.GERMAN
                textToSpeech.setPitch(ttsPitch)
                textToSpeech.setSpeechRate(ttsSpeed)
            }
        }

        // Dark Mode-Zustand global innerhalb des setContent verwalten
        setContent {
            // Der darkMode-Zustand wird hier mit remember gehalten
            var darkMode by remember { mutableStateOf(isDarkModeEnabled) }
            LlamaTheme(darkTheme = darkMode) {
                MainScreen(
                    spokenText = spokenText.value,
                    assistantResponse = assistantResponse.value,
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
                    },
                    onTtsSpeedChange = { newSpeed ->
                        ttsSpeed = newSpeed
                        textToSpeech.setSpeechRate(ttsSpeed)
                    },
                    onTtsVolumeChange = { newVolume ->
                        ttsVolume = newVolume
                    },
                    isDarkMode = darkMode,
                    onDarkModeChange = { isEnabled ->
                        darkMode = isEnabled
                        saveDarkModeSetting(isEnabled)
                    }
                )
            }
        }
    }

    private fun saveDarkModeSetting(isEnabled: Boolean) {
        sharedPreferences.edit().putBoolean("dark_mode", isEnabled).apply()
    }
    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.GERMAN)
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val resultsList = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val recognizedText = resultsList?.get(0) ?: ""
                spokenText.value = recognizedText

                // Taktile Rückmeldung (Vibration)
                vibrateFeedback()

                processUserInput(recognizedText)
            }

            override fun onError(error: Int) {
                val errorMessage = "Es tut mir Leid. Ich habe das nicht verstanden. Kannst du das bitte noch einmal wiederholen?"
                assistantResponse.value = errorMessage
                speakResponse(errorMessage)
            }

            override fun onBeginningOfSpeech() {}
            override fun onEndOfSpeech() {}
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer.startListening(intent)
    }

    // Moderne Vibrationsmethode für alle Android-Versionen
    private fun vibrateFeedback(duration: Long = 100) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12 und neuer: VibratorManager nutzen
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
            val vibrator = vibratorManager.defaultVibrator
            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(duration)
            }
        }
    }

    private fun processUserInput(input: String) {
        val apiService = createRetrofitClient()
        val requestBody = mapOf("input" to input)
        val call = apiService.getModelResponse(requestBody)
        call.enqueue(object : Callback<Map<String, String>> {
            override fun onResponse(call: Call<Map<String, String>>, response: Response<Map<String, String>>) {
                if (response.isSuccessful) {
                    val generatedText = response.body()?.get("response") ?: "Keine Antwort erhalten"
                    assistantResponse.value = generatedText
                    speakResponse(generatedText)
                } else {
                    assistantResponse.value = "API-Fehler: ${response.code()}"
                }
            }

            override fun onFailure(call: Call<Map<String, String>>, t: Throwable) {
                assistantResponse.value = "Fehler bei der API-Anfrage: ${t.message}"
            }
        })
    }

    // Bereinigt den Text: Entfernt * und andere Sonderzeichen,
    // sodass diese nicht von TTS ausgesprochen werden.
    private fun speakResponse(response: String) {
        val cleanedResponse = response
            .replace("*", "") // Entferne explizit *
            .replace(Regex("[^\\p{L}\\p{Nd}\\s]"), "") // Entfernt alle übrigen Sonderzeichen
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, ttsVolume)
        }
        textToSpeech.speak(cleanedResponse, TextToSpeech.QUEUE_FLUSH, params, null)
    }

    // Wiederholt die letzte Antwort
    private fun repeatResponse() {
        if (assistantResponse.value.isNotEmpty()) {
            speakResponse(assistantResponse.value)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
        textToSpeech.shutdown()
    }
}


@Composable
fun MainScreen(
    spokenText: String,
    assistantResponse: String,
    onListenClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onSettingsClick: () -> Unit,
    showSettingsDialog: Boolean,
    onDismissSettings: () -> Unit,
    ttsPitch: Float,
    ttsSpeed: Float,
    ttsVolume: Float,
    onTtsPitchChange: (Float) -> Unit,
    onTtsSpeedChange: (Float) -> Unit,
    onTtsVolumeChange: (Float) -> Unit,
    isDarkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    // Verwende eine Surface, die den Hintergrund aus dem Theme bezieht
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)) {
            Button(
                onClick = onListenClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Sprich mit mir!")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Du hast gesagt: $spokenText")
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Assistent Antwort: $assistantResponse")
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onRepeatClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Antwort wiederholen")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onSettingsClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Einstellungen")
            }
        }

        if (showSettingsDialog) {
            SettingsDialog(
                ttsPitch = ttsPitch,
                ttsSpeed = ttsSpeed,
                ttsVolume = ttsVolume,
                isDarkMode = isDarkMode,
                onTtsPitchChange = onTtsPitchChange,
                onTtsSpeedChange = onTtsSpeedChange,
                onTtsVolumeChange = onTtsVolumeChange,
                onDarkModeChange = onDarkModeChange,
                onDismiss = onDismissSettings
            )
        }
    }
}

@Composable
fun SettingsDialog(
    ttsPitch: Float,
    ttsSpeed: Float,
    ttsVolume: Float,
    isDarkMode: Boolean,
    onTtsPitchChange: (Float) -> Unit,
    onTtsSpeedChange: (Float) -> Unit,
    onTtsVolumeChange: (Float) -> Unit,
    onDarkModeChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Einstellungen") },
        text = {
            Column {
                Text("Stimmlage (Pitch)")
                Slider(
                    value = ttsPitch,
                    onValueChange = onTtsPitchChange,
                    valueRange = 0.5f..2.0f,
                    steps = 5
                )
                Text("Sprechgeschwindigkeit")
                Slider(
                    value = ttsSpeed,
                    onValueChange = onTtsSpeedChange,
                    valueRange = 0.5f..2.0f,
                    steps = 5
                )
                Text("Stimmlautstärke")
                Slider(
                    value = ttsVolume,
                    onValueChange = onTtsVolumeChange,
                    valueRange = 0.0f..1.0f,
                    steps = 5
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Dark Mode")
                    Switch(checked = isDarkMode,
                           onCheckedChange = { onDarkModeChange(it) })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

