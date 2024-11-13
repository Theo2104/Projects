package com.example.sprachassistent

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.sprachassistent.ui.theme.SprachassistentTheme
import org.json.JSONObject
import java.io.InputStream
import java.util.Locale
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var textToSpeech: TextToSpeech

    private var dialogState = "GREETING"
    private var previousQuestion: String? = null
    private lateinit var dialogConfig: Map<String, Dialog>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (!isGranted) {
                Toast.makeText(this, "Mikrofon-Berechtigung benötigt", Toast.LENGTH_SHORT).show()
            }
        }
        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.language = Locale.GERMAN
            }
        }

        dialogConfig = loadDialogConfig() // Lade die Dialogkonfiguration

        setContent {
            SprachassistentTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        modifier = Modifier.padding(innerPadding),
                        onButtonClick = { startListening() }
                    )
                }
            }
        }
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.GERMAN)
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val spokenText = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0) ?: ""
                handleDialog(spokenText)
            }

            override fun onError(error: Int) {
                Toast.makeText(this@MainActivity, "Fehler bei Spracherkennung", Toast.LENGTH_SHORT).show()
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
    private var name: String? = null
    private fun handleDialog(spokenText: String) {
        val dialog = dialogConfig[dialogState] ?: return
        var response = dialog.text

        when (dialogState) {
            "ASKING_NAME" -> {
                // Speichern des Namens, wenn nach dem Namen gefragt wird
                name = spokenText
                response = response.replace("{name}", spokenText)
            }
            "ASKING_MOOD" -> {
                response = if (spokenText.contains("gut", ignoreCase = true)) {
                    dialog.text
                } else {
                    dialog.fallback ?: dialog.text
                }
            }
            "GOODBYE" -> {
                // Ersetze {name} in der Antwort mit dem gespeicherten Namen
                response = response.replace("{name}", name ?: "Freund")
            }
        }

        // Sprechen der Antwort
        textToSpeech.speak(response, TextToSpeech.QUEUE_FLUSH, null, null)
        dialogState = dialog.nextState // Nächsten Zustand setzen
    }

    private fun loadDialogConfig(): Map<String, Dialog> {
        val inputStream: InputStream = assets.open("dialog_config.json")
        val json = inputStream.bufferedReader().use { it.readText() }
        val jsonObject = JSONObject(json)

        val dialogMap = mutableMapOf<String, Dialog>()
        jsonObject.keys().forEach { key ->
            val dialogJson = jsonObject.getJSONObject(key)
            dialogMap[key] = Dialog(
                text = dialogJson.getString("text"),
                nextState = dialogJson.getString("nextState"),
                fallback = dialogJson.optString("fallback", null)
            )
        }
        return dialogMap
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
        textToSpeech.shutdown()
    }
}

// Dialogdatenklasse
data class Dialog(val text: String, val nextState: String, val fallback: String?)

@Composable
fun Greeting(modifier: Modifier = Modifier, onButtonClick: () -> Unit) {
    Button(
        onClick = { onButtonClick() },
        modifier = modifier.padding(16.dp)
    ) {
        Text(text = "Sprich mit mir!")
    }
}


@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    SprachassistentTheme {
        Greeting {}
    }
}
