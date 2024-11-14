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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.nlp_assistant.ui.theme.NLP_AssistantTheme
import java.util.Locale
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import com.example.nlp_assistant.api.createRetrofitClient
import androidx.compose.runtime.Composable
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


class MainActivity : ComponentActivity() {
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var textToSpeech: TextToSpeech

    // State to hold the spoken text and the assistant's response
    private val spokenText = mutableStateOf("")
    private val assistantResponse = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request permission to use the microphone
        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (!isGranted) {
                Toast.makeText(this, "Mikrofon-Berechtigung benötigt", Toast.LENGTH_SHORT).show()
            }
        }
        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)

        // Initialize Speech Recognition and Text-to-Speech
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

    // Start speech recognition and listen to the user's speech
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

    // Process user input and send it to Hugging Face API for NLP processing
    private fun processUserInput(input: String) {
        val apiService = createRetrofitClient()

        // Request-Body mit Benutzereingabe
        val requestBody = mapOf("inputs" to input)

        val call = apiService.getModelResponse(requestBody)
        call.enqueue(object : Callback<Map<String, Any>> {
            override fun onResponse(call: Call<Map<String, Any>>, response: Response<Map<String, Any>>) {
                if (response.isSuccessful) {
                    val modelResponse = response.body()
                    val generatedText = modelResponse?.get("generated_text") as? String ?: "Keine Antwort erhalten"
                    assistantResponse.value = generatedText
                    textToSpeech.speak(generatedText, TextToSpeech.QUEUE_FLUSH, null, null)
                } else {
                    assistantResponse.value = "Fehler bei der Verarbeitung der Anfrage."
                    textToSpeech.speak("Fehler bei der Verarbeitung der Anfrage.", TextToSpeech.QUEUE_FLUSH, null, null)
                }
            }

            override fun onFailure(call: Call<Map<String, Any>>, t: Throwable) {
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
    // Column für vertikale Anordnung
    Column(
        modifier = modifier.padding(16.dp) // Padding für den gesamten Inhalt
    ) {
        // Button, der beim Klicken die Methode `onButtonClick` ausführt
        Button(
            onClick = { onButtonClick() },
            modifier = Modifier.fillMaxWidth() // Button auf volle Breite
        ) {
            Text(text = "Sprich mit mir!")
        }

        // Abstand zwischen Button und Text
        Spacer(modifier = Modifier.height(16.dp)) // Abstand zwischen Button und den Texten

        // Text für die gesprochene Eingabe
        Text(
            text = "Du hast gesagt: $spokenText",
            modifier = Modifier.padding(top = 8.dp) // Zusätzlicher Abstand zum nächsten Text
        )

        // Abstand zwischen den Texten
        Spacer(modifier = Modifier.height(8.dp))

        // Text für die Antwort des Assistenten
        Text(
            text = "Assistent Antwort: $assistantResponse",
            modifier = Modifier.padding(top = 8.dp) // Zusätzlicher Abstand zum vorherigen Text
        )
    }
}


@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    NLP_AssistantTheme {
        Greeting(
            spokenText = "Hallo",
            assistantResponse = "Wie kann ich helfen?",
            onButtonClick = { /* Leere Lambda oder eine Aktion */ }
        )
    }
}
