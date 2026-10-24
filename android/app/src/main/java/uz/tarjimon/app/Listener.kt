package uz.tarjimon.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** Turns speech into text with the phone's speech recognition (Google). */
class Listener(
    private val ctx: Context,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onEnd: () -> Unit,
) {
    private var rec: SpeechRecognizer? = null

    fun available() = SpeechRecognizer.isRecognitionAvailable(ctx)

    fun start(code: String, offline: Boolean) {
        cancel()
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        rec = r
        var last = ""
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onPartialResults(b: Bundle?) {
                b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let {
                    if (it.isNotBlank()) { last = it; onPartial(it) }
                }
            }
            override fun onResults(b: Bundle?) {
                val text = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: last
                onEnd()
                if (text.isNotBlank()) onFinal(text)
            }
            override fun onError(error: Int) {
                onEnd()
                if (last.isNotBlank() && error != SpeechRecognizer.ERROR_CLIENT) { onFinal(last); return }
                val name = lang(code).name
                onError(when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't catch that. Tap and try again."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
                        if (offline) "$name speech recognition needs internet on this phone." else "Speech recognition needs internet. Check your connection."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Please allow the microphone for Tarjimon."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "The microphone is busy. Try again."
                    12, 13 -> "This phone can't recognise $name yet. Add it in Google voice typing → Languages."  // language not supported / unavailable
                    SpeechRecognizer.ERROR_CLIENT -> ""
                    else -> "Speech recognition error ($error). Try again."
                })
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang(code).locale)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang(code).locale)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            if (offline) putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        r.startListening(intent)
    }

    fun stop() { rec?.stopListening() }

    fun cancel() {
        rec?.let { runCatching { it.cancel(); it.destroy() } }
        rec = null
    }
}
