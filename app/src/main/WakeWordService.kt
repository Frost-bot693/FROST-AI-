package com.frostai.jarvis

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import java.util.Locale

class WakeWordService : Service() {

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var listening = false

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNotification()
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                configureVoice()
            }
        }
        startListening()
    }

    private fun configureVoice() {
        tts?.let { engine ->
            engine.language = Locale.US
            engine.setPitch(0.7f)
            engine.setSpeechRate(0.95f)
            val voiceList = engine.voices
            if (voiceList != null) {
                for (voice in voiceList) {
                    val name = voice.name.lowercase()
                    if (name.contains("male") || name.contains("david") ||
                        name.contains("george") || name.contains("mark") ||
                        name.contains("daniel") || name.contains("james")
                    ) {
                        engine.voice = voice
                        break
                    }
                }
            }
        }
    }

    private fun startListening() {
        if (listening) return
        listening = true

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                restartListening()
            }

            override fun onResults(results: android.os.Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.lowercase() ?: ""
                handleTranscript(text)
                restartListening()
            }

            override fun onPartialResults(partialResults: android.os.Bundle?) {}
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        })

        speechRecognizer?.startListening(intent)
    }

    private fun restartListening() {
        listening = false
        speechRecognizer?.destroy()
        speechRecognizer = null
        startListening()
    }

    private fun handleTranscript(text: String) {
        val wakeWords = listOf("hey frosty", "hey frost", "ok frosty", "ok frost")
        val matched = wakeWords.any { text.contains(it) }
        if (!matched) return

        val command = text
        val appMap = mapOf(
            "whatsapp" to "com.whatsapp",
            "instagram" to "com.instagram.android",
            "youtube" to "com.google.android.youtube",
            "gmail" to "com.google.android.gm"
        )

        val matchedApp = appMap.entries.firstOrNull { command.contains(it.key) }
        if (matchedApp != null) {
            val launchIntent = packageManager.getLaunchIntentForPackage(matchedApp.value)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
                return
            }
        }

        val mainIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("voice_command", command)
        }
        startActivity(mainIntent)
    }

    private fun startForegroundServiceNotification() {
        val channelId = "frost_ai_wake_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Frost AI Background Listening",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Frost AI")
            .setContentText("Listening for \"Hey Frosty\"")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
