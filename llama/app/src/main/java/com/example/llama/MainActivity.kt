package com.example.llama

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.llama.api.createRetrofitClient
import com.example.llama.ui.theme.LlamaTheme
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Locale
import androidx.compose.ui.unit.TextUnit
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue


import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var vibrator: Vibrator
    private lateinit var sharedPreferences: SharedPreferences

    // UI-Zustände (über Compose-State)
    private val spokenText = mutableStateOf("")
    private val assistantResponse = mutableStateOf("")
    private val explanation = mutableStateOf("")
    private val showSettingsDialog = mutableStateOf(false)
    private val isLoading = mutableStateOf(false)

    // Standardwerte für TTS-Parameter
    private var ttsPitch by mutableStateOf(1.0f)
    private var ttsSpeed by mutableStateOf(1.0f)
    private var ttsVolume by mutableStateOf(0.5f)

    // Schalter für xAI-Erklärungen
    private var explainEnabled by mutableStateOf(false)

    // ActivityResultLauncher für Google Speech Recognition
    private val googleSpeechLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val results = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                val recognizedText = results?.get(0) ?: ""
                spokenText.value = recognizedText
                vibrateFeedback(50)
                processUserInput(recognizedText)
            } else {
                // Fallback: Starte den nativen SpeechRecognizer
                startNativeSpeechRecognition()
            }
        }

    // Berechtigung für das Mikrofon anfordern
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                Toast.makeText(this, "Mikrofon-Berechtigung wird benötigt", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPreferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val isDarkModeEnabled = sharedPreferences.getBoolean("dark_mode", false)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        // Berechtigung prüfen und ggf. anfordern
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        // Initialisiere TextToSpeech und SpeechRecognizer
        initializeTextToSpeech()
        initializeSpeechRecognizer()

        setContent {
            var darkMode by remember { mutableStateOf(isDarkModeEnabled) }
            LlamaTheme(darkTheme = darkMode) {
                ResponsiveUserInterface(
                    spokenText = spokenText.value,
                    assistantResponse = assistantResponse.value,
                    explanation = explanation.value,
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
                    },
                    explainEnabled = explainEnabled,
                    onExplainChange = { explainEnabled = it }
                )
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d("MainActivity", "Configuration changed: Orientation = ${newConfig.orientation}")

    }
    private fun saveDarkModeSetting(isEnabled: Boolean) {
        sharedPreferences.edit().putBoolean("dark_mode", isEnabled).apply()
    }

    private fun initializeTextToSpeech() {
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val germanLocale = Locale("de", "DE")
                val result = textToSpeech.setLanguage(germanLocale)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TextToSpeech", "Deutsche Sprache wird nicht unterstützt!")
                    Toast.makeText(this, "Deutsche Sprache wird nicht unterstützt.", Toast.LENGTH_SHORT).show()
                }
                textToSpeech.setPitch(ttsPitch)
                textToSpeech.setSpeechRate(ttsSpeed)
            } else {
                Log.e("TextToSpeech", "TTS konnte nicht initialisiert werden: $status")
                Toast.makeText(this, "Sprachausgabe konnte nicht initialisiert werden.", Toast.LENGTH_SHORT).show()
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
                val recognizedText = resultsList?.get(0) ?: ""
                spokenText.value = recognizedText
                vibrateFeedback(50)
                processUserInput(recognizedText)
            }
            override fun onError(error: Int) {
                val errorMessage = "Es tut mir Leid. Ich habe das nicht verstanden. Bitte wiederholen."
                assistantResponse.value = errorMessage
                speakResponse(errorMessage)
                isLoading.value = false
            }
            override fun onBeginningOfSpeech() { isLoading.value = true }
            override fun onEndOfSpeech() { }
            override fun onReadyForSpeech(params: Bundle?) { }
            override fun onRmsChanged(rmsdB: Float) { }
            override fun onBufferReceived(buffer: ByteArray?) { }
            override fun onPartialResults(partialResults: Bundle?) {
                val resultsList = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!resultsList.isNullOrEmpty()) {
                    spokenText.value = resultsList[0] + "..."
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) { }
        })
    }

    /**
     * Versucht zunächst die Google Speech Recognition via ActivityResultLauncher.
     * Schlägt diese fehl, wird der native SpeechRecognizer als Fallback gestartet.
     */
    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Bitte sprechen Sie jetzt...")
        }
        try {
            googleSpeechLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e("SpeechRecognition", "Fehler bei Google Speech Recognition: ${e.message}")
            startNativeSpeechRecognition()
        }
    }

    /**
     * Fallback-Methode: Startet die native Spracherkennung über den SpeechRecognizer.
     */
    private fun startNativeSpeechRecognition() {
        isLoading.value = true
        spokenText.value = "Ich höre zu..."
        try {
            speechRecognizer.cancel()
        } catch (e: Exception) {
            Log.e("SpeechRecognition", "Fehler beim Abbrechen der vorherigen Erkennung: ${e.message}")
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        try {
            speechRecognizer.startListening(intent)
            vibrateFeedback(50)
            Toast.makeText(this, "Native Spracherkennung gestartet...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("SpeechRecognition", "Fehler beim Starten der nativen Spracherkennung: ${e.message}")
            Toast.makeText(this, "Fehler: ${e.message}", Toast.LENGTH_SHORT).show()
            isLoading.value = false
        }
    }

    private fun vibrateFeedback(duration: Long = 100) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
            val defaultVibrator = vibratorManager.defaultVibrator
            defaultVibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
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
        isLoading.value = true
        val apiService = createRetrofitClient()
        // xAI-Parameter: "explain" entspricht dem Zustand des Toggles
        val requestBody = mapOf("input" to input, "explain" to explainEnabled.toString())
        apiService.getModelResponse(requestBody).enqueue(object : Callback<Map<String, String>> {
            override fun onResponse(call: Call<Map<String, String>>, response: Response<Map<String, String>>) {
                isLoading.value = false
                if (response.isSuccessful) {
                    val generatedText = response.body()?.get("response") ?: "Keine Antwort erhalten"
                    assistantResponse.value = generatedText
                    explanation.value = response.body()?.get("explanation") ?: ""
                    speakResponse(generatedText)
                } else {
                    assistantResponse.value = "API-Fehler: ${response.code()}"
                }
            }
            override fun onFailure(call: Call<Map<String, String>>, t: Throwable) {
                isLoading.value = false
                assistantResponse.value = "Fehler bei der API-Anfrage: ${t.message}"
            }
        })
    }

    private fun speakResponse(response: String) {
        val cleanedResponse = response
            .replace("*", "")
            .replace(Regex("[^\\p{L}\\p{Nd}\\s]"), "")
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, ttsVolume)
        }
        textToSpeech.speak(cleanedResponse, TextToSpeech.QUEUE_FLUSH, params, null)
    }

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

/* --- UI-Komponenten --- */
@Composable
fun PortraitUserInterface(
    spokenText: String,
    assistantResponse: String,
    explanation: String,
    isLoading: Boolean,
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
    explainEnabled: Boolean,
    onExplainChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Titel und Spracheingabe-Button
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Sprachassistent",
                style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onListenClick,
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(64.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("🎤", style = TextStyle(fontSize = 28.sp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sprechen", style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold))
                }
            }
        }
        // Nutzereingabe
        DisplayCard(
            title = "Deine Nachricht:",
            content = spokenText,
            backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            scrollable = true
        )
        // Ladeindikator
        AnimatedVisibility(
            visible = isLoading,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                LoadingSnail(
                    modifier = Modifier.size(80.dp),
                    shellColor = MaterialTheme.colorScheme.primary,
                    bodyColor = MaterialTheme.colorScheme.secondary
                )
            }
        }
        // Assistenten-Antwort
        DisplayCard(
            title = "Assistenten-Antwort:",
            content = assistantResponse,
            backgroundColor = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            scrollable = true,
            fontSize = 18.sp,
            fontWeight = FontWeight.Normal
        )
        // Erklärung (falls aktiviert)
        if (explainEnabled && explanation.isNotEmpty()) {
            DisplayCard(
                title = "Erklärung:",
                content = explanation,
                backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                scrollable = true
            )
        }
        // Umschalten der Erklärung
        if (explanation.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = { onExplainChange(!explainEnabled) }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("ℹ️", style = TextStyle(fontSize = 20.sp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (explainEnabled) "Erklärung ausblenden" else "Erklärung anzeigen",
                            style = TextStyle(fontSize = 14.sp)
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ActionButton(
                onClick = onRepeatClick,
                icon = "🔄",
                text = "Wiederholen",
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp)
            )
            ActionButton(
                onClick = onSettingsClick,
                icon = "⚙️",
                text = "Einstellungen",
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            )
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
            explainEnabled = explainEnabled,
            onExplainChange = onExplainChange,
            onDismiss = onDismissSettings
        )
    }
}
@Composable
fun ResponsiveUserInterface(
    spokenText: String,
    assistantResponse: String,
    explanation: String,
    isLoading: Boolean,
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
    explainEnabled: Boolean,
    onExplainChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (isLandscape) {

            LandscapeUserInterface(
                spokenText = spokenText,
                assistantResponse = assistantResponse,
                explanation = explanation,
                isLoading = isLoading,
                onListenClick = onListenClick,
                onRepeatClick = onRepeatClick,
                onSettingsClick = onSettingsClick,
                showSettingsDialog = showSettingsDialog,
                onDismissSettings = onDismissSettings,
                ttsPitch = ttsPitch,
                ttsSpeed = ttsSpeed,
                ttsVolume = ttsVolume,
                onTtsPitchChange = onTtsPitchChange,
                onTtsSpeedChange = onTtsSpeedChange,
                onTtsVolumeChange = onTtsVolumeChange,
                isDarkMode = isDarkMode,
                onDarkModeChange = onDarkModeChange,
                explainEnabled = explainEnabled,
                onExplainChange = onExplainChange
            )
        } else {

            PortraitUserInterface(
                spokenText = spokenText,
                assistantResponse = assistantResponse,
                explanation = explanation,
                isLoading = isLoading,
                onListenClick = onListenClick,
                onRepeatClick = onRepeatClick,
                onSettingsClick = onSettingsClick,
                showSettingsDialog = showSettingsDialog,
                onDismissSettings = onDismissSettings,
                ttsPitch = ttsPitch,
                ttsSpeed = ttsSpeed,
                ttsVolume = ttsVolume,
                onTtsPitchChange = onTtsPitchChange,
                onTtsSpeedChange = onTtsSpeedChange,
                onTtsVolumeChange = onTtsVolumeChange,
                isDarkMode = isDarkMode,
                onDarkModeChange = onDarkModeChange,
                explainEnabled = explainEnabled,
                onExplainChange = onExplainChange
            )
        }
    }
}

@Composable
fun LandscapeUserInterface(
    spokenText: String,
    assistantResponse: String,
    explanation: String,
    isLoading: Boolean,
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
    explainEnabled: Boolean,
    onExplainChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Linke Spalte: Nutzereingabe, Titel & Aktionstasten
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Titel und Spracheingabe-Button
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Sprachassistent",
                    style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onListenClick,
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(64.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("🎤", style = TextStyle(fontSize = 28.sp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sprechen", style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold))
                    }
                }
            }
            // Nutzereingabe ("Deine Nachricht")
            DisplayCard(
                title = "Deine Nachricht:",
                content = spokenText,
                backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                scrollable = false
            )
            // Aktionstasten
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ActionButton(
                    onClick = onRepeatClick,
                    icon = "🔄",
                    text = "Wiederholen",
                    modifier = Modifier.weight(1f)
                )
                ActionButton(
                    onClick = onSettingsClick,
                    icon = "⚙️",
                    text = "Einstellungen",
                    modifier = Modifier.weight(1f)
                )
            }
        }
        // Rechte Spalte: Assistenten-Antwort und Erklärung
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Top
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingSnail(
                        modifier = Modifier.size(80.dp),
                        shellColor = MaterialTheme.colorScheme.primary,
                        bodyColor = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            // Assistenten-Antwort
            DisplayCard(
                title = "Assistenten-Antwort:",
                content = assistantResponse,
                backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                scrollable = false,
                fontSize = 20.sp,
                fontWeight = FontWeight.Normal
            )
            // Erklärung direkt unter der Antwort
            if (explainEnabled && explanation.isNotEmpty()) {
                DisplayCard(
                    title = "Erklärung:",
                    content = explanation,
                    backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    scrollable = true
                )
            }
            // Toggle zur Anzeige der Erklärung (optional)
            if (explanation.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { onExplainChange(!explainEnabled) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("ℹ️", style = TextStyle(fontSize = 20.sp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (explainEnabled) "Erklärung ausblenden" else "Erklärung anzeigen",
                                style = TextStyle(fontSize = 14.sp)
                            )
                        }
                    }
                }
            }
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
            explainEnabled = explainEnabled,
            onExplainChange = onExplainChange,
            onDismiss = onDismissSettings
        )
    }
}



@Composable
fun DisplayCard(
    title: String,
    content: String,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    scrollable: Boolean = false,
    fontSize: TextUnit = 16.sp,
    fontWeight: FontWeight = FontWeight.Normal
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 80.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                ),
            color = backgroundColor
        ) {
            if (scrollable) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 80.dp)
                ) {
                    val scrollState = rememberScrollState()
                    Text(
                        text = if (content.isEmpty()) "Warte auf Eingabe..." else content,
                        style = TextStyle(fontSize = fontSize, fontWeight = fontWeight),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .verticalScroll(scrollState)
                    )
                }
            } else {
                Text(
                    text = if (content.isEmpty()) "Warte auf Eingabe..." else content,
                    style = TextStyle(fontSize = fontSize, fontWeight = fontWeight),
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}


@Composable
fun ActionButton(
    onClick: () -> Unit,
    icon: String,
    text: String,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(text = icon, style = TextStyle(fontSize = 20.sp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = text, style = TextStyle(fontSize = 14.sp))
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
    explainEnabled: Boolean,
    onExplainChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = if (isDarkMode) Color(0xFF121212) else MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState()).fillMaxWidth()
            ) {
                Text(
                    "Einstellungen",
                    style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                SettingsSection(title = "Sprachwiedergabe") {
                    SettingsSlider(
                        title = "Stimmlage",
                        value = ttsPitch,
                        onValueChange = onTtsPitchChange,
                        valueRange = 0.5f..1.5f,
                        steps = 4,
                        lowValueLabel = "Tiefer",
                        highValueLabel = "Höher"
                    )
                    SettingsSlider(
                        title = "Geschwindigkeit",
                        value = ttsSpeed,
                        onValueChange = onTtsSpeedChange,
                        valueRange = 0.7f..1.3f,
                        steps = 3,
                        lowValueLabel = "Langsamer",
                        highValueLabel = "Schneller"
                    )
                    SettingsSlider(
                        title = "Lautstärke",
                        value = ttsVolume,
                        onValueChange = onTtsVolumeChange,
                        valueRange = 0.0f..1.0f,
                        steps = 4,
                        lowValueLabel = "Leiser",
                        highValueLabel = "Lauter"
                    )
                }
                Divider(modifier = Modifier.padding(vertical = 16.dp))
                SettingsSection(title = "App-Einstellungen") {
                    SettingsToggle(
                        title = "Dunkler Modus",
                        description = "Dunkle Farben für das Erscheinungsbild",
                        checked = isDarkMode,
                        onCheckedChange = onDarkModeChange
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsToggle(
                        title = "Erklärungen anzeigen",
                        description = "Zusätzliche Informationen zu Antworten anzeigen",
                        checked = explainEnabled,
                        onCheckedChange = onExplainChange
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Schließen")
                }
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
            modifier = Modifier.padding(bottom = 12.dp)
        )
        content()
    }
}

@Composable
fun SettingsSlider(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    lowValueLabel: String,
    highValueLabel: String
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(text = title, style = TextStyle(fontSize = 14.sp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = lowValueLabel, style = TextStyle(fontSize = 12.sp))
            Text(text = highValueLabel, style = TextStyle(fontSize = 12.sp))
        }
    }
}

@Composable
fun SettingsToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = TextStyle(fontSize = 14.sp))
            Text(
                text = description,
                style = TextStyle(fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors()
        )
    }
}

@Composable
fun LoadingSnail(
    modifier: Modifier = Modifier,
    shellColor: Color = MaterialTheme.colorScheme.primary,
    bodyColor: Color = MaterialTheme.colorScheme.secondary
) {
    // Animation für die Rotation
    val infiniteTransition = rememberInfiniteTransition(label = "snailRotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "snailRotation"
    )

    // Animation für Fühler-Bewegung
    val antennaWiggle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 15f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "antennaWiggle"
    )

    Box(
        modifier = modifier.size(80.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(80.dp)) {
            // Rotiere die gesamte Schnecke
            rotate(rotation) {
                // Schneckengehäuse (Spirale)
                val shellPath = Path().apply {
                    val centerX = size.width / 2
                    val centerY = size.height / 2

                    // Spiralförmiges Gehäuse
                    moveTo(centerX, centerY)
                    var currentRadius = 5f
                    var angle = 0f
                    val radiusIncrement = 2f
                    val angleIncrement = 15f

                    repeat(24) {
                        angle += angleIncrement
                        currentRadius += radiusIncrement
                        val x = centerX + currentRadius * kotlin.math.cos(Math.toRadians(angle.toDouble())).toFloat()
                        val y = centerY + currentRadius * kotlin.math.sin(Math.toRadians(angle.toDouble())).toFloat()
                        lineTo(x, y)
                    }
                }

                // Schneckenkörper
                val bodyPath = Path().apply {
                    val startX = size.width / 2 - 30f
                    val startY = size.height / 2 + 5f

                    moveTo(startX, startY)
                    // Unterteil des Körpers
                    quadraticBezierTo(
                        startX - 15f, startY + 10f,
                        startX - 30f, startY
                    )

                    // Unterseite des Körpers
                    quadraticBezierTo(
                        startX - 40f, startY - 5f,
                        startX - 35f, startY - 15f
                    )

                    // Oberseite des Körpers zurück zur Spirale
                    quadraticBezierTo(
                        startX - 25f, startY - 25f,
                        startX - 5f, startY - 10f
                    )

                    // Verbindung zurück zum Startpunkt
                    close()
                }

                // Fühler mit Animation
                val antennaPath1 = Path().apply {
                    val startX = size.width / 2 - 35f
                    val startY = size.height / 2 - 10f

                    moveTo(startX, startY)
                    quadraticBezierTo(
                        startX - 10f, startY - 15f - antennaWiggle,
                        startX - 15f, startY - 25f - antennaWiggle
                    )
                }

                val antennaPath2 = Path().apply {
                    val startX = size.width / 2 - 30f
                    val startY = size.height / 2 - 13f

                    moveTo(startX, startY)
                    quadraticBezierTo(
                        startX - 5f, startY - 15f - antennaWiggle,
                        startX - 8f, startY - 30f - antennaWiggle
                    )
                }

                // Augen
                val eyeX1 = size.width / 2 - 32f
                val eyeX2 = size.width / 2 - 24f
                val eyeY = size.height / 2 - 8f

                // Zeichnen der Elemente
                drawPath(
                    path = shellPath,
                    color = shellColor,
                    style = Stroke(width = 6f, cap = StrokeCap.Round)
                )

                drawPath(
                    path = bodyPath,
                    color = bodyColor,
                    style = Stroke(width = 4f, cap = StrokeCap.Round)
                )

                drawPath(
                    path = antennaPath1,
                    color = bodyColor,
                    style = Stroke(width = 2f, cap = StrokeCap.Round)
                )

                drawPath(
                    path = antennaPath2,
                    color = bodyColor,
                    style = Stroke(width = 2f, cap = StrokeCap.Round)
                )


                drawCircle(
                    color = shellColor,
                    radius = 3f,
                    center = Offset(
                        x = size.width / 2 - 15f,
                        y = size.height / 2 - 25f - antennaWiggle
                    )
                )

                drawCircle(
                    color = shellColor,
                    radius = 3f,
                    center = Offset(
                        x = size.width / 2 - 8f,
                        y = size.height / 2 - 30f - antennaWiggle
                    )
                )

                // Augen
                drawCircle(
                    color = Color.Black,
                    radius = 3f,
                    center = Offset(eyeX1, eyeY)
                )

                drawCircle(
                    color = Color.Black,
                    radius = 3f,
                    center = Offset(eyeX2, eyeY)
                )

                // Schleimspur
                val trailWidth = 15f
                val trailHeight = 3f
                drawOval(
                    color = Color.LightGray.copy(alpha = 0.5f),
                    size = Size(trailWidth, trailHeight),
                    topLeft = Offset(
                        x = size.width / 2 - 50f,
                        y = size.height / 2 + 5f
                    )
                )
            }
        }
    }
}