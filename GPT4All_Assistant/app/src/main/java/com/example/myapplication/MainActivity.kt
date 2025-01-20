package com.example.myapplication

import android.Manifest
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer.RESULTS_RECOGNITION
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tbruyelle.rxpermissions3.RxPermissions
import com.hitesh.texttospeech.TTS
import okhttp3.*
import java.io.IOException

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var recognizerIntent: RecognizerIntent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialisiere den SpeechRecognizer
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                Toast.makeText(applicationContext, "Fehler bei der Spracherkennung: $error", Toast.LENGTH_SHORT).show()
            }

            override fun onResults(results: Bundle?) {
                val data = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val recognizedText = data?.get(0) ?: "Keine Erkennung"
                processUserInput(recognizedText) { gptResponse ->
                    speakResponse(gptResponse)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        recognizerIntent = RecognizerIntent()
        recognizerIntent.apply {
            action = RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE") // Deutsch
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        setContent {
            val rxPermissions = RxPermissions(this)
            var responseText by remember { mutableStateOf("Drücke den Button, um zu sprechen!") }
            var isListening by remember { mutableStateOf(false) }

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Sprachassistent für Autismus") }
                    )
                },
                content = { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(responseText, modifier = Modifier.padding(16.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            rxPermissions
                                .request(Manifest.permission.RECORD_AUDIO)
                                .subscribe { granted ->
                                    if (granted) {
                                        if (!isListening) {
                                            startSpeechRecognition()
                                            isListening = true
                                        } else {
                                            stopSpeechRecognition()
                                            isListening = false
                                        }
                                    } else {
                                        responseText = "Mikrofon-Zugriff verweigert!"
                                    }
                                }
                        }) {
                            Text(if (isListening) "Stop" else "Sprechen")
                        }
                    }
                }
            )
        }
    }

    private fun startSpeechRecognition() {
        speechRecognizer.startListening(recognizerIntent)
    }

    private fun stopSpeechRecognition() {
        speechRecognizer.stopListening()
    }

    private fun processUserInput(input: String, callback: (String) -> Unit) {
        // GPT4All API-Aufruf simulieren
        val client = OkHttpClient()
        val requestBody = RequestBody.create(
            MediaType.get("application/json"), """{"input": "$input"}"""
        )
        val request = Request.Builder()
            .url("http://192.168.x.x:5000/") // Lokale GPT4All-API
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback("Fehler bei der Anfrage: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val responseText = response.body()?.string()
                callback(responseText ?: "Keine Antwort erhalten.")
            }
        })
    }

    private fun speakResponse(response: String) {
        val tts = com.hitesh.texttospeech.TTS(this)
        tts.speak(response)
    }
}
