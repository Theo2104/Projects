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
    private lateinit var dialogConfig: Map<String, Dialog>
    private val userData = mutableMapOf<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Berechtigung anfordern
        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (!isGranted) {
                Toast.makeText(this, "Mikrofon-Berechtigung benötigt", Toast.LENGTH_SHORT).show()
            }
        }
        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)

        // Initialisiere Spracherkennung und Text-to-Speech
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.language = Locale.GERMAN
            }
        }

        // Lade Dialogkonfiguration aus JSON
        dialogConfig = loadDialogConfig()

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

    // Intent-Erkennung anhand von Schlüsselwörtern
    private fun detectIntent(spokenText: String): String {
        return when {
            spokenText.contains("wie geht", ignoreCase = true) -> "ASKING_MOOD"
            spokenText.contains("Hilfe", ignoreCase = true) -> "HELP"
            spokenText.contains("danke", ignoreCase = true) -> "GOODBYE"
            else -> dialogState // Standardmäßig aktuellen Zustand beibehalten
        }
    }

    private fun handleDialog(spokenText: String) {
        // Erkennen des Intents und Einstellen des neuen Dialogzustands
        dialogState = detectIntent(spokenText)

        val dialog = dialogConfig[dialogState] ?: return
        var response = dialog.text

        // Platzhalter und Benutzereingaben dynamisch verwalten
        when (dialogState) {
            "ASKING_NAME" -> {
                saveUserData("name", spokenText)
                response = response.replace("{name}", spokenText)
            }
            "ASKING_MOOD" -> {
                response = if (spokenText.contains("gut", ignoreCase = true)) {
                    dialog.text
                } else {
                    dialog.fallback ?: dialog.text
                }
            }
            "HELP" -> {
                response = "Ich kann dir bei verschiedenen Dingen helfen. Frage einfach los!"
                dialogState = "GREETING" // Zurück zur Begrüßung
            }
            "GOODBYE" -> {
                response = response.replace("{name}", getUserData("name") ?: "Freund")
            }
        }

        // Antwort ausgeben
        textToSpeech.speak(response, TextToSpeech.QUEUE_FLUSH, null, null)

        // Nächsten Zustand festlegen
        dialogState = dialog.nextState
    }

    // Benutzerdaten in einer Map speichern und abrufen
    private fun saveUserData(key: String, value: String) {
        userData[key] = value
    }

    private fun getUserData(key: String): String? {
        return userData[key]
    }

    // Dialogkonfiguration aus JSON laden
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
