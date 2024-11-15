package com.example.nlp_assistant

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
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.nlp_assistant.ui.theme.NLP_AssistantTheme
import com.example.nlp_assistant.api.createRetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var textToSpeech: TextToSpeech

    private val spokenText = mutableStateOf("")
    private val assistantResponse = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mikrofon-Berechtigung anfordern
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

        setContent {
            NLP_AssistantTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        spokenText = spokenText.value,
                        assistantResponse = assistantResponse.value,
                        modifier = Modifier.padding(innerPadding),
                        onButtonClick = { startListening() }
                    )
                }
            }
        }
    }

    // Startet die Spracherkennung
    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.GERMAN)
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val resultsList = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                spokenText.value = resultsList?.get(0) ?: ""
                processUserInput(spokenText.value)
            }

            override fun onError(error: Int) {
                Toast.makeText(this@MainActivity, "Fehler bei der Spracherkennung", Toast.LENGTH_SHORT).show()
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

    // Sendet die Benutzereingabe an die Hugging Face API
    private fun processUserInput(input: String) {
        val apiService = createRetrofitClient()

        // Request-Body mit Benutzer-Eingabe
        val requestBody = mapOf("inputs" to "Benutzer: $input\nAssistent:")

        val call = apiService.getModelResponse(requestBody)
        call.enqueue(object : Callback<List<Map<String, Any>>> {
            override fun onResponse(call: Call<List<Map<String, Any>>>, response: Response<List<Map<String, Any>>>) {
                if (response.isSuccessful) {
                    val modelResponse = response.body()
                    if (modelResponse != null) {
                        val generatedText = modelResponse.firstOrNull()?.get("generated_text") as? String
                            ?: "Keine Antwort erhalten"
                        assistantResponse.value = generatedText
                        textToSpeech.speak(generatedText, TextToSpeech.QUEUE_FLUSH, null, null)
                    } else {
                        assistantResponse.value = "Antwort war leer."
                        textToSpeech.speak("Antwort war leer.", TextToSpeech.QUEUE_FLUSH, null, null)
                    }
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Keine Fehlerbeschreibung"
                    assistantResponse.value = "API-Fehler: ${response.code()} - $errorBody"
                    textToSpeech.speak("Fehler bei der Verarbeitung der Anfrage.", TextToSpeech.QUEUE_FLUSH, null, null)
                }
            }

            override fun onFailure(call: Call<List<Map<String, Any>>>, t: Throwable) {
                assistantResponse.value = "Fehler bei der API-Anfrage: ${t.message}"
                textToSpeech.speak("Fehler bei der API-Anfrage.", TextToSpeech.QUEUE_FLUSH, null, null)
            }
        })
    }



    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
        textToSpeech.shutdown()
    }
}

@Composable
fun Greeting(
    spokenText: String,
    assistantResponse: String,
    modifier: Modifier = Modifier,
    onButtonClick: () -> Unit
) {
    Column(modifier = modifier.padding(16.dp)) {
        Button(
            onClick = { onButtonClick() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Sprich mit mir!")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Du hast gesagt: $spokenText", modifier = Modifier.padding(top = 8.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Assistent Antwort: $assistantResponse", modifier = Modifier.padding(top = 8.dp))
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    NLP_AssistantTheme {
        Greeting(
            spokenText = "Hallo",
            assistantResponse = "Wie kann ich helfen?",
            onButtonClick = {}
        )
    }
}
