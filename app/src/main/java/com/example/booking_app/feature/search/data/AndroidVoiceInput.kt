package com.example.booking_app.feature.search.data

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.example.booking_app.feature.search.domain.VoiceError
import com.example.booking_app.feature.search.domain.VoiceEvent
import com.example.booking_app.feature.search.domain.VoiceInput
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

/** [SpeechRecognizer] với `vi-VN` và kết quả tạm (spec 022 T082). Phải chạy trên main thread. */
class AndroidVoiceInput @Inject constructor(
    @ApplicationContext private val context: Context,
) : VoiceInput {

    override fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    override fun listen(): Flow<VoiceEvent> = callbackFlow {
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                trySend(VoiceEvent.Ready)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                firstResult(partialResults)?.let { trySend(VoiceEvent.Partial(it)) }
            }

            override fun onResults(results: Bundle?) {
                val text = firstResult(results)
                trySend(if (text.isNullOrBlank()) VoiceEvent.Error(VoiceError.NO_MATCH) else VoiceEvent.Final(text))
                close()
            }

            override fun onError(error: Int) {
                trySend(VoiceEvent.Error(errorKind(error)))
                close()
            }

            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        })
        awaitClose { recognizer.destroy() }
    }.flowOn(Dispatchers.Main.immediate)

    private fun firstResult(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()

    private fun errorKind(error: Int): VoiceError = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceError.NO_MATCH
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> VoiceError.NETWORK
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> VoiceError.BUSY
        else -> VoiceError.OTHER
    }
}
