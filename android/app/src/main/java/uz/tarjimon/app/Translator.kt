package uz.tarjimon.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Free translation:
 *  online  -> Google Translate (free web connection), then MyMemory as a backup
 *  offline -> Google ML Kit on-device models (English / Arabic / Russian only)
 */
object Translator {
    private val OFFLINE_LANGS = setOf("en", "ar", "ru")
    private val cache = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 300
    }

    fun isOnline(ctx: Context): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun canWorkOffline(from: String, to: String) = from in OFFLINE_LANGS && to in OFFLINE_LANGS

    suspend fun translate(ctx: Context, text: String, from: String, to: String): String {
        if (from == to) return text
        val key = "$from>$to:$text"
        synchronized(cache) { cache[key] }?.let { return it }
        val online = isOnline(ctx)
        val out = withContext(Dispatchers.IO) {
            var r: String? = null
            if (online) {
                r = runCatching { google(text, from, to) }.getOrNull()
                if (r.isNullOrBlank()) r = runCatching { myMemory(text, from, to) }.getOrNull()
            }
            if (r.isNullOrBlank() && canWorkOffline(from, to)) r = runCatching { mlkit(text, from, to) }.getOrNull()
            r
        }
        if (out.isNullOrBlank()) throw IOException(if (online) "translation failed" else "offline")
        synchronized(cache) { cache[key] = out }
        return out
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 10000
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Tarjimon")
        try {
            if (c.responseCode != 200) throw IOException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun google(text: String, from: String, to: String): String {
        val body = get("https://translate.googleapis.com/translate_a/single?client=gtx&sl=$from&tl=$to&dt=t&q=${enc(text)}")
        val parts = JSONArray(body).getJSONArray(0)
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val p = parts.optJSONArray(i) ?: continue
            if (!p.isNull(0)) sb.append(p.optString(0))
        }
        return sb.toString()
    }

    private fun myMemory(text: String, from: String, to: String): String {
        val body = get("https://api.mymemory.translated.net/get?q=${enc(text)}&langpair=$from|$to")
        return JSONObject(body).getJSONObject("responseData").getString("translatedText")
    }

    private fun options(from: String, to: String) = TranslatorOptions.Builder()
        .setSourceLanguage(TranslateLanguage.fromLanguageTag(from)!!)
        .setTargetLanguage(TranslateLanguage.fromLanguageTag(to)!!)
        .build()

    private suspend fun mlkit(text: String, from: String, to: String): String {
        val t = Translation.getClient(options(from, to))
        try {
            t.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            return t.translate(text).await()
        } finally { t.close() }
    }

    /** Downloads the offline English/Arabic/Russian packs in the background (Wi-Fi only). */
    suspend fun prepareOffline() {
        for ((a, b) in listOf("en" to "ar", "en" to "ru", "ar" to "ru")) {
            runCatching {
                val t = Translation.getClient(options(a, b))
                t.downloadModelIfNeeded(DownloadConditions.Builder().requireWifi().build()).await()
                t.close()
            }
        }
    }
}
