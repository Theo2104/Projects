package com.example.llama

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
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
import com.example.llama.api.createRetrofitClient
import com.example.llama.ui.theme.LlamaTheme
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var vibrator: Vibrator
    private lateinit var sharedPreferences: SharedPreferences

    private val spokenText = mutableStateOf("")
    private val assistantResponse = mutableStateOf("")
    private val explanation = mutableStateOf("")
    private val showSettingsDialog = mutableStateOf(false)
    private val isLoading = mutableStateOf(false)

    // Standardwerte für TTS-Parameter
    private var ttsPitch by mutableStateOf(1.0f)
    private var ttsSpeed by mutableStateOf(1.0f)
    private var ttsVolume by mutableStateOf(0.5f)

    // xAI-Schalter (falls Erklärungen benötigt werden)
    private var explainEnabled by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPreferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val isDarkModeEnabled = sharedPreferences.getBoolean("dark_mode", false)
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

        setContent {
            var darkMode by remember { mutableStateOf(isDarkModeEnabled) }
            LlamaTheme(darkTheme = darkMode) {
                // ResponsiveUserInterface entscheidet: Beim ersten Start wird Portrait gezeigt,
                // erst nach Drehung wechselt die UI zur Landscape-Variante.
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
                vibrateFeedback()
                processUserInput(recognizedText)
            }
            override fun onError(error: Int) {
                val errorMessage = "Es tut mir Leid. Ich habe das nicht verstanden. Bitte wiederholen."
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

    private fun vibrateFeedback(duration: Long = 100) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
        // Füge den xAI-Parameter hinzu: "explain" entspricht dem Zustand des Toggles
        val requestBody = mapOf("input" to input, "explain" to explainEnabled.toString())
        apiService.getModelResponse(requestBody).enqueue(object : Callback<Map<String, String>> {
            override fun onResponse(call: Call<Map<String, String>>, response: Response<Map<String, String>>) {
                if (response.isSuccessful) {
                    val generatedText = response.body()?.get("response") ?: "Keine Antwort erhalten"
                    // Setze die generierte Antwort
                    assistantResponse.value = generatedText
                    // Übernehme die Erklärung aus der Antwort (falls vorhanden)
                    explanation.value = response.body()?.get("explanation") ?: ""
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


    private fun speakResponse(response: String) {
        val cleanedResponse = response
            .replace("*", "")
            .replace(Regex("[^\\p{L}\\p{Nd}\\s]"), "")
        val params = android.os.Bundle().apply {
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
    var initialOrientation by remember { mutableStateOf(configuration.orientation) }
    var hasRotated by remember { mutableStateOf(false) }

    LaunchedEffect(configuration.orientation) {
        if (configuration.orientation != initialOrientation) {
            hasRotated = true
        }
    }

    if (hasRotated && configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
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
            onExplainChange = onExplainChange,
            modifier = modifier
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
            onExplainChange = onExplainChange,
            modifier = modifier
        )
    }
}

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
    onExplainChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Sprachassistent",
                style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
            )
            Button(
                onClick = onListenClick,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🎤", style = TextStyle(fontSize = 22.sp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sprechen", style = TextStyle(fontSize = 18.sp))
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            DisplayCard(
                title = "Deine Nachricht:",
                content = spokenText,
                backgroundColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
            }
            DisplayCard(
                title = "Assistenten-Antwort:",
                content = assistantResponse,
                backgroundColor = MaterialTheme.colorScheme.primaryContainer
            )
            if (explainEnabled && explanation.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
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
                AnimatedVisibility(
                    visible = explainEnabled,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    DisplayCard(
                        title = "Erklärung:",
                        content = explanation,
                        backgroundColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ActionButton(onClick = onRepeatClick, icon = "🔄", text = "Wiederholen")
                ActionButton(onClick = onSettingsClick, icon = "⚙️", text = "Einstellungen")
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
    onExplainChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxSize().padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Sprachassistent",
                style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Button(
                onClick = onListenClick,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🎤", style = TextStyle(fontSize = 22.sp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sprechen", style = TextStyle(fontSize = 18.sp))
                }
            }
            DisplayCard(
                title = "Deine Nachricht:",
                content = spokenText,
                backgroundColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ActionButton(onClick = onRepeatClick, icon = "🔄", text = "Wiederholen")
                ActionButton(onClick = onSettingsClick, icon = "⚙️", text = "Einstellungen")
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
            }
            DisplayCard(
                title = "Assistenten-Antwort:",
                content = assistantResponse,
                backgroundColor = MaterialTheme.colorScheme.primaryContainer
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
fun DisplayCard(
    title: String,
    content: String,
    backgroundColor: Color
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Text(
            text = title,
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                ),
            color = backgroundColor
        ) {
            Text(
                text = if (content.isEmpty()) "Warte auf Eingabe..." else content,
                style = TextStyle(fontSize = 16.sp),
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
fun ActionButton(
    onClick: () -> Unit,
    icon: String,
    text: String
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = icon, style = TextStyle(fontSize = 20.sp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text)
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = if (isDarkMode) Color(0xFF121212) else MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth()
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
