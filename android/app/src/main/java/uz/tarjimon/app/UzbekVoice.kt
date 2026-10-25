package uz.tarjimon.app

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.LongBuffer

/**
 * Uzbek speech made on the phone itself with Meta's MMS voice (no internet needed).
 * The voice model reads Cyrillic, so Latin Uzbek is converted first.
 */
class UzbekVoice(private val ctx: Context) {
    private val env = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null
    private var vocab: Map<Char, Long> = emptyMap()
    val sampleRate = 16000
    /** Called with 0..100 while the voice downloads. */
    var onProgress: ((Int) -> Unit)? = null
    val modelFile get() = File(ctx.filesDir, "uz_voice_q8.onnx")
    fun isDownloaded() = modelFile.length() == MODEL_SIZE

    @Synchronized
    fun ensure() {
        if (session != null) return
        File(ctx.filesDir, "uz_voice_v1.onnx").delete()   // from older versions
        File(ctx.filesDir, "uz_voice_fp16.onnx").delete()
        val file = modelFile
        if (!isDownloaded()) download(file)
        val opts = OrtSession.SessionOptions().apply { setIntraOpNumThreads(2) }
        session = env.createSession(file.absolutePath, opts)
        val js = JSONObject(ctx.assets.open("uz/vocab.json").bufferedReader().use { it.readText() })
        val map = HashMap<Char, Long>()
        js.keys().forEach { k -> if (k.length == 1) map[k[0]] = js.getLong(k) }
        vocab = map
    }

    private fun download(target: File) {
        val part = File(target.path + ".part")
        var attempt = 0
        while (true) {
            try {
                if (part.length() >= MODEL_SIZE) part.delete()
                val have = if (part.exists()) part.length() else 0L
                val c = URL(MODEL_URL).openConnection() as HttpURLConnection
                c.connectTimeout = 15000; c.readTimeout = 30000
                if (have > 0) c.setRequestProperty("Range", "bytes=$have-")
                val code = c.responseCode
                if (code != 200 && code != 206) throw IOException("HTTP $code")
                val append = code == 206
                var done = if (append) have else 0L
                var lastPct = -1
                FileOutputStream(part, append).use { out ->
                    c.inputStream.use { input ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf); if (n < 0) break
                            out.write(buf, 0, n); done += n
                            val pct = (done * 100 / MODEL_SIZE).toInt()
                            if (pct != lastPct) { lastPct = pct; onProgress?.invoke(pct) }
                        }
                    }
                }
                c.disconnect()
                if (part.length() != MODEL_SIZE) throw IOException("download incomplete")
                if (!part.renameTo(target)) throw IOException("could not save the voice")
                return
            } catch (e: Exception) {
                attempt++
                if (attempt >= 6) throw IOException("Uzbek voice download failed. Check your internet and try again.", e)
                Thread.sleep(2000L * attempt)
            }
        }
    }

    /** Returns 16 kHz mono audio samples. */
    fun synth(text: String): FloatArray {
        ensure()
        val sentences = toCyrillic(text).split(Regex("[.!?;:\\n]+")).map { clean(it) }.filter { it.isNotBlank() }
        val parts = sentences.map { run(tokens(it)) }.filter { it.isNotEmpty() }
        val gap = (sampleRate * 0.25).toInt()
        val total = parts.sumOf { it.size } + gap * maxOf(0, parts.size - 1)
        val out = FloatArray(total)
        var o = 0
        parts.forEachIndexed { i, p -> p.copyInto(out, o); o += p.size + if (i < parts.size - 1) gap else 0 }
        return out
    }

    private fun tokens(s: String): LongArray {
        val ids = s.mapNotNull { vocab[it] }
        val out = LongArray(ids.size * 2 + 1)   // a blank (0) between every letter, like the original tokenizer
        ids.forEachIndexed { i, id -> out[i * 2 + 1] = id }
        return out
    }

    private fun run(ids: LongArray): FloatArray {
        if (ids.size < 3) return FloatArray(0)
        val s = session!!
        val shape = longArrayOf(1, ids.size.toLong())
        val inputs = HashMap<String, OnnxTensor>()
        inputs["input_ids"] = OnnxTensor.createTensor(env, LongBuffer.wrap(ids), shape)
        if ("attention_mask" in s.inputNames) {
            inputs["attention_mask"] = OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(ids.size) { 1L }), shape)
        }
        try {
            s.run(inputs).use { r ->
                val t = r.get(0) as OnnxTensor
                val fb = t.floatBuffer
                val out = FloatArray(fb.remaining()); fb.get(out)
                return out
            }
        } finally { inputs.values.forEach { it.close() } }
    }

    companion object {
        const val MODEL_URL = "https://mahmoudgomaa358-del.github.io/tarjimon/models/uz/onnx/model_quantized.onnx"
        const val MODEL_SIZE = 37555282L
        private val ONES = listOf("nol", "bir", "ikki", "uch", "to'rt", "besh", "olti", "yetti", "sakkiz", "to'qqiz")
        private val TENS = listOf("", "o'n", "yigirma", "o'ttiz", "qirq", "ellik", "oltmish", "yetmish", "sakson", "to'qson")
        private fun num(n: Long): String = when {
            n < 10 -> ONES[n.toInt()]
            n < 100 -> TENS[(n / 10).toInt()] + if (n % 10 != 0L) " " + ONES[(n % 10).toInt()] else ""
            n < 1000 -> { val h = n / 100; (if (h > 1) ONES[h.toInt()] + " " else "") + "yuz" + if (n % 100 != 0L) " " + num(n % 100) else "" }
            n < 1_000_000 -> num(n / 1000) + " ming" + if (n % 1000 != 0L) " " + num(n % 1000) else ""
            n < 1_000_000_000 -> num(n / 1_000_000) + " million" + if (n % 1_000_000 != 0L) " " + num(n % 1_000_000) else ""
            else -> n.toString().map { ONES[it - '0'] }.joinToString(" ")
        }
        private val SINGLE = mapOf(
            'a' to "а", 'b' to "б", 'c' to "с", 'd' to "д", 'e' to "е", 'f' to "ф", 'g' to "г", 'h' to "ҳ", 'i' to "и",
            'j' to "ж", 'k' to "к", 'l' to "л", 'm' to "м", 'n' to "н", 'o' to "о", 'p' to "п", 'q' to "қ", 'r' to "р",
            's' to "с", 't' to "т", 'u' to "у", 'v' to "в", 'w' to "в", 'x' to "х", 'y' to "й", 'z' to "з",
        )
        private const val LETTER = "a-zа-яёўғқҳ"

        fun toCyrillic(text: String): String {
            var s = text.lowercase()
            s = Regex("\\d+").replace(s) { " " + (it.value.toLongOrNull()?.let { n -> num(n) } ?: it.value) + " " }
            s = s.replace(Regex("[‘’ʻʼ`´']"), "'")
            s = s.replace("o'", "ў").replace("g'", "ғ").replace("sh", "ш").replace("ch", "ч")
            s = Regex("(^|[^$LETTER])e").replace(s) { it.groupValues[1] + "э" }
            s = s.replace("yo", "ё").replace("yu", "ю").replace("ya", "я").replace("ye", "е")
            s = s.replace("'", "ъ")
            val sb = StringBuilder()
            for (ch in s) sb.append(if (ch in 'a'..'z') SINGLE[ch] ?: "" else ch.toString())
            return sb.toString()
        }

        fun clean(cyr: String): String =
            cyr.replace(Regex("[^абвгдежзийклмнопрстуфхчшъьэюяёўғқҳ ]"), " ").replace(Regex("\\s+"), " ").trim()
    }
}
