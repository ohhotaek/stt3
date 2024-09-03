
package com.example.stt_test

import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.content.Context
import android.content.Intent
import android.app.Activity
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.stt_test.ui.theme.Stt_testTheme
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.delay
import androidx.compose.material3.Text as Text

class MainActivity : ComponentActivity() {
    private val apiKey = ""
    private val model = GenerativeModel(
        modelName = "gemini-pro",
        apiKey = apiKey,
        generationConfig = generationConfig {
            temperature = 0.9f
            topK = 1
            topP = 1f
            maxOutputTokens = 2048
        }
    )

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var speechRecognizerIntent: Intent

    var onSpeechResult: ((String?) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initializeSpeechRecognizer() // 음성 인식 초기화

        setContent {
            Stt_testTheme {
                SpeechToTextScreen { text ->
                    analyzeText(text)
                }
            }
        }
    }

    private fun initializeSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR") // 한국어 설정
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Toast.makeText(this@MainActivity, "음성 인식을 시작하세요.", Toast.LENGTH_SHORT).show()
            }

            override fun onBeginningOfSpeech() { }
            override fun onRmsChanged(rmsdB: Float) { }
            override fun onBufferReceived(buffer: ByteArray?) { }
            override fun onEndOfSpeech() { }

            override fun onError(error: Int) {
                Toast.makeText(this@MainActivity, "오류 발생: $error", Toast.LENGTH_SHORT).show()
                onSpeechResult?.invoke(null)
            }

            override fun onResults(results: Bundle?) {
                val spokenText = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0)
                onSpeechResult?.invoke(spokenText)
            }

            override fun onPartialResults(partialResults: Bundle?) { }
            override fun onEvent(eventType: Int, params: Bundle?) { }
        })
    }

    fun startSpeechRecognition() {
        speechRecognizer.startListening(speechRecognizerIntent)
    }

    fun stopSpeechRecognition() {
        speechRecognizer.stopListening()
    }

    private suspend fun analyzeText(text: String): String {
        val prompt = """
        다음 통화 스크립트를 분석하여 보이스피싱 여부를 판단해주세요:
        [$text]
        """
        return withContext(Dispatchers.IO) {
            try {
                val response = model.generateContent(prompt)
                response.text ?: "분석 결과를 얻지 못했습니다."
            } catch (e: Exception) {
                "오류가 발생했습니다: ${e.message}"
            }
        }
    }

    fun showNotification(context: Context, message: String) {
        val channelId = "VoicePhishingChannel"
        val notificationId = 1

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Voice Phishing Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("보이스피싱 분석 결과")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        notificationManager.notify(notificationId, builder.build())
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy() // SpeechRecognizer 리소스 해제
    }
}

@Composable
fun SpeechToTextScreen(analyzeText: suspend (String) -> String) {
    val context = LocalContext.current as MainActivity
    var speechResult by remember { mutableStateOf("") }
    var analysisResult by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (speechResult.isEmpty()) "음성인식 결과가 여기에 표시됩니다." else speechResult,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Button(
            onClick = {
                if (isListening) {
                    isListening = false
                    context.stopSpeechRecognition()
                } else {
                    isListening = true
                    context.startSpeechRecognition()
                }
            }
        ) {
            Text(text = if (isListening) "음성인식 중지" else "음성인식 시작")
        }

        Text(
            text = analysisResult,
            modifier = Modifier.padding(top = 24.dp)
        )
    }

    LaunchedEffect(isListening) {
        context.onSpeechResult = { result ->
            if (result != null) {
                speechResult = result
                scope.launch {
                    delay(2000L)
                    val analyzedText = analyzeText(speechResult)
                    analysisResult += "\n" + analyzedText
                    context.showNotification(context, analyzedText)

                    if (isListening) {
                        context.startSpeechRecognition()
                    }
                }
            } else {
                isListening = false
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SpeechToTextScreenPreview() {
    Stt_testTheme {
        SpeechToTextScreen(
            analyzeText = { "분석 결과 미리보기" }
        )
    }
}
