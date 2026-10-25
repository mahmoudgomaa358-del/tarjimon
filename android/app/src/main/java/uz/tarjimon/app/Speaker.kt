package uz.tarjimon.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** Reads translations aloud: phone voices for English/Arabic/Russian, the built-in voice for Uzbek. */
class Speaker(private val ctx: Context, private val scope: CoroutineScope, private val onMessage: (String) -> Unit) {
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pending: Pair<String, String>? = null
    val uzbek = UzbekVoice(ctx)
    private var track: AudioTrack? = null
    private var uzJob: Job? = null

    init {
        tts = TextToSpeech(ctx) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            pending?.let { (t, l) -> pending = null; speak(t, l) }
        }
        var shown = -1
        uzbek.onProgress = { pct ->
            val step = pct / 20 * 20
            if (step != shown) { shown = step; onMessage("Downloading the Uzbek voice (57 MB, one time only): $step%") }
        }
        // Prepare the Uzbek voice in the background so the first sentence is quick.
        scope.launch(Dispatchers.Default) { runCatching { uzbek.ensure() } }
    }

    fun stop() {
        uzJob?.cancel()
        runCatching { tts?.stop() }
        runCatching { track?.stop(); track?.release() }
        track = null
    }

    fun speak(text: String, code: String) {
        if (text.isBlank()) return
        stop()
        if (code == "uz") {
            if (!uzbek.isDownloaded() && Translator.isOnline(ctx)) onMessage("Getting the Uzbek voice ready…")
            speakUzbek(text); return
        }
        val engine = tts
        if (engine == null || !ttsReady) { pending = text to code; return }
        val r = engine.setLanguage(Locale.forLanguageTag(lang(code).locale))
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            onMessage(lang(code).noVoice + ". Settings → Text-to-speech → Install voice data.")
            return
        }
        engine.setSpeechRate(0.95f)
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tarjimon")
    }

    private fun speakUzbek(text: String) {
        uzJob = scope.launch {
            val pcm = try {
                withContext(Dispatchers.Default) { uzbek.synth(text) }
            } catch (e: Throwable) {
                onMessage(if (!uzbek.isDownloaded() && !Translator.isOnline(ctx))
                    "Connect to the internet once so the Uzbek voice can download (57 MB)."
                else "Uzbek voice error: ${e.message ?: e.javaClass.simpleName}")
                return@launch
            }
            if (pcm.isEmpty()) return@launch
            play(pcm, uzbek.sampleRate)
        }
    }

    private fun play(pcm: FloatArray, rate: Int) {
        val shorts = ShortArray(pcm.size) { i -> (pcm[i].coerceIn(-1f, 1f) * 32767).toInt().toShort() }
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(shorts.size * 2)
            .build()
        t.write(shorts, 0, shorts.size)
        t.play()
        track = t
    }

    fun shutdown() { stop(); tts?.shutdown() }
}
