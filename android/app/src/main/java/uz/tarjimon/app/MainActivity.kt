package uz.tarjimon.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

private const val APK_LINK = "https://github.com/mahmoudgomaa358-del/tarjimon/releases/latest/download/Tarjimon.apk"
private const val WEB_LINK = "https://mahmoudgomaa358-del.github.io/tarjimon/"

class Side(code: String) {
    var lang by mutableStateOf(code)
    var main by mutableStateOf("")
    var sub by mutableStateOf("")
}

data class Pal(
    val bg: Color, val panel: Color, val ink: Color, val muted: Color, val line: Color,
    val a: Color, val aSoft: Color, val b: Color, val bSoft: Color,
)

private val Light = Pal(Color(0xFFF3F5FA), Color.White, Color(0xFF14203A), Color(0xFF5D6782), Color(0xFFDDE2EE),
    Color(0xFF1F4FB8), Color(0xFFE6EDFB), Color(0xFFC2324A), Color(0xFFFBE8EB))
private val Dark = Pal(Color(0xFF0D1220), Color(0xFF161D31), Color(0xFFEEF1F8), Color(0xFF9AA4C0), Color(0xFF28314A),
    Color(0xFF5B8CF0), Color(0xFF1A2747), Color(0xFFEC6479), Color(0xFF3A1C25))

class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("tarjimon", MODE_PRIVATE) }
    private lateinit var sides: Map<String, Side>
    private var listening by mutableStateOf<String?>(null)
    private var busy by mutableStateOf<String?>(null)
    private var face by mutableStateOf(false)
    private var current: String? = null
    private var pendingSide: String? = null
    private data class Said(val side: String, val text: String, val lang: String)
    private var last: Said? = null

    private lateinit var speaker: Speaker
    private lateinit var listener: Listener

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val s = pendingSide; pendingSide = null
        if (ok && s != null) startListening(s)
        else if (!ok) toast("Tarjimon needs the microphone to hear you.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sides = mapOf("A" to Side(prefs.getString("A", "uz")!!), "B" to Side(prefs.getString("B", "en")!!))
        face = prefs.getBoolean("face", false)
        speaker = Speaker(this, lifecycleScope) { toast(it) }
        listener = Listener(this,
            onPartial = { t -> current?.let { sides[it]!!.main = t } },
            onFinal = { t -> current?.let { handleHeard(it, t.trim()) } },
            onError = { m -> if (m.isNotBlank()) toast(m) },
            onEnd = { listening = null },
        )
        setContent { App() }
        lifecycleScope.launch { Translator.prepareOffline() }
    }

    override fun onDestroy() {
        listener.cancel(); speaker.shutdown()
        super.onDestroy()
    }

    private fun other(s: String) = if (s == "A") "B" else "A"
    private fun toast(m: String) = runOnUiThread { Toast.makeText(this, m, Toast.LENGTH_LONG).show() }

    // ---------- actions ----------
    private fun micTap(side: String) {
        if (listening == side) { listener.stop(); return }
        speaker.stop()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingSide = side; micPermission.launch(Manifest.permission.RECORD_AUDIO); return
        }
        startListening(side)
    }

    private fun startListening(side: String) {
        if (!listener.available()) { toast("Speech recognition isn't available. Please install or update the Google app."); return }
        current = side; listening = side
        sides[side]!!.main = ""; sides[side]!!.sub = ""
        listener.start(sides[side]!!.lang, offline = !Translator.isOnline(this))
    }

    private fun failMessage(from: String, to: String) = when {
        Translator.isOnline(this) -> "Translation failed. Please try again."
        Translator.canWorkOffline(from, to) -> "Offline translation isn't downloaded yet. Open the app once on Wi-Fi."
        else -> "Translating Uzbek needs internet for now."
    }

    private fun handleHeard(side: String, text: String) {
        if (text.isBlank()) return
        val to = other(side); val from = sides[side]!!.lang; val toL = sides[to]!!.lang
        val said = Said(side, text, from); last = said
        sides[side]!!.main = text; sides[side]!!.sub = ""
        busy = side
        lifecycleScope.launch {
            try {
                val out = Translator.translate(this@MainActivity, text, from, toL)
                if (last !== said) return@launch
                sides[to]!!.main = out; sides[to]!!.sub = text
                sides[side]!!.sub = "→ $out"
                busy = null
                speaker.speak(out, toL)
            } catch (e: Exception) { busy = null; toast(failMessage(from, toL)) }
        }
    }

    /** Shows the last thing said again in the newly chosen language(s). */
    private fun retranslate(changed: List<String>, speakSide: String) {
        val job = last ?: return
        lifecycleScope.launch {
            for (sd in changed) {
                val target = sides[sd]!!.lang
                if (target == job.lang) { sides[sd]!!.main = job.text; sides[sd]!!.sub = ""; continue }
                busy = sd
                try {
                    val out = Translator.translate(this@MainActivity, job.text, job.lang, target)
                    if (last !== job) { busy = null; return@launch }
                    sides[sd]!!.main = out
                    sides[sd]!!.sub = if (sd == job.side) "" else job.text
                    val o = other(sd)
                    if (sides[o]!!.lang == job.lang) sides[o]!!.sub = "→ $out"
                    busy = null
                    if (sd == speakSide) speaker.speak(out, target)
                } catch (e: Exception) { busy = null; toast(failMessage(job.lang, target)); return@launch }
            }
        }
    }

    private fun setLang(side: String, code: String) {
        if (sides[side]!!.lang == code) return
        val changed = mutableListOf(side)
        val o = other(side)
        if (sides[o]!!.lang == code) { sides[o]!!.lang = sides[side]!!.lang; changed.add(o) }
        sides[side]!!.lang = code
        prefs.edit().putString("A", sides["A"]!!.lang).putString("B", sides["B"]!!.lang).apply()
        retranslate(changed, side)
    }

    private fun swap() {
        val a = sides["A"]!!; val b = sides["B"]!!
        val l = a.lang; a.lang = b.lang; b.lang = l
        val m = a.main; a.main = b.main; b.main = m
        val s = a.sub; a.sub = b.sub; b.sub = s
        last = last?.let { it.copy(side = other(it.side)) }
        prefs.edit().putString("A", a.lang).putString("B", b.lang).apply()
    }

    private fun share() {
        val text = "Tarjimon – voice translator for English, Arabic, Uzbek and Russian.\n" +
            "Android app: $APK_LINK\nWeb version: $WEB_LINK"
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share Tarjimon"))
    }

    private fun copy(text: String) {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Tarjimon", text))
        toast("Copied")
    }

    // ---------- screen ----------
    @Composable
    private fun App() {
        val dark = isSystemInDarkTheme()
        val c = if (dark) Dark else Light
        MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
            Column(Modifier.fillMaxSize().background(c.bg).systemBarsPadding().padding(horizontal = 12.dp)) {
                Header(c)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    val angle by animateFloatAsState(if (face) 180f else 0f, tween(350), label = "flip")
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Panel("B", c, Modifier.weight(1f).rotate(angle))
                        Panel("A", c, Modifier.weight(1f))
                    }
                    IconButton(
                        onClick = { swap() },
                        modifier = Modifier.align(Alignment.Center).size(44.dp)
                            .background(c.panel, CircleShape).border(1.dp, c.line, CircleShape),
                    ) { Icon(painterResource(R.drawable.ic_swap), "Swap languages", tint = c.ink, modifier = Modifier.size(20.dp)) }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }

    @Composable
    private fun Header(c: Pal) {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Tarjimon", color = c.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("English · العربية · O‘zbekcha · Русский", color = c.muted, fontSize = 11.sp)
            }
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(if (face) c.ink else c.panel)
                    .border(1.dp, if (face) c.ink else c.line, RoundedCornerShape(50))
                    .clickable { face = !face; prefs.edit().putBoolean("face", face).apply() }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val fg = if (face) c.bg else c.ink
                Icon(painterResource(R.drawable.ic_rotate), null, tint = fg, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Face-to-face", color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.width(6.dp))
            IconButton(onClick = { share() }, modifier = Modifier.size(40.dp).background(c.panel, CircleShape).border(1.dp, c.line, CircleShape)) {
                Icon(painterResource(R.drawable.ic_share), "Share Tarjimon", tint = c.ink, modifier = Modifier.size(18.dp))
            }
        }
    }

    @Composable
    private fun Panel(id: String, c: Pal, modifier: Modifier) {
        val s = sides[id]!!
        val L = lang(s.lang)
        val accent = if (id == "A") c.a else c.b
        val soft = if (id == "A") c.aSoft else c.bSoft
        var typing by remember { mutableStateOf(false) }
        var typed by remember { mutableStateOf("") }
        val shape = RoundedCornerShape(22.dp)

        Column(
            modifier.fillMaxWidth().clip(shape).background(c.panel).border(1.dp, c.line, shape).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LangPicker(id, L, accent, soft)
                Spacer(Modifier.weight(1f))
                SmallIcon(R.drawable.ic_keyboard, "Type instead", c.muted) { typing = !typing }
                SmallIcon(R.drawable.ic_copy, "Copy", c.muted, enabled = s.main.isNotBlank()) { copy(s.main) }
                SmallIcon(R.drawable.ic_volume, "Play again", c.muted, enabled = s.main.isNotBlank()) { speaker.speak(s.main, s.lang) }
            }
            if (typing) {
                OutlinedTextField(
                    value = typed, onValueChange = { typed = it }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(L.name) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (typed.isNotBlank()) { handleHeard(id, typed.trim()); typed = "" }
                    }),
                )
            }
            val dir = if (L.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val scroll = rememberScrollState()
                LaunchedEffect(s.main, s.sub) { if (listening == id) scroll.scrollTo(scroll.maxValue) else scroll.scrollTo(0) }
                val minH = maxHeight
                CompositionLocalProvider(LocalLayoutDirection provides dir) {
                    Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                        Column(Modifier.fillMaxWidth().heightIn(min = minH), verticalArrangement = Arrangement.Center) {
                            if (s.main.isBlank()) Text(L.hint, color = c.muted, fontSize = 16.sp, modifier = Modifier.fillMaxWidth())
                            else Text(s.main, color = c.ink, fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium, modifier = Modifier.fillMaxWidth())
                            if (s.sub.isNotBlank()) Text(s.sub, color = c.muted, fontSize = 15.sp, lineHeight = 20.sp, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                        }
                    }
                }
            }
            MicButton(id, L, accent, Modifier.align(Alignment.CenterHorizontally))
        }
    }

    @Composable
    private fun SmallIcon(res: Int, label: String, tint: Color, enabled: Boolean = true, onClick: () -> Unit) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(40.dp)) {
            Icon(painterResource(res), label, tint = if (enabled) tint else tint.copy(alpha = 0.3f), modifier = Modifier.size(20.dp))
        }
    }

    @Composable
    private fun LangPicker(id: String, L: Lang, accent: Color, soft: Color) {
        var open by remember { mutableStateOf(false) }
        Box {
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(soft).clickable { open = true }
                    .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(L.name, color = accent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Icon(painterResource(R.drawable.ic_down), null, tint = accent, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                LANGS.forEach { l ->
                    DropdownMenuItem(text = { Text(l.name, fontSize = 17.sp) }, onClick = { open = false; setLang(id, l.code) })
                }
            }
        }
    }

    @Composable
    private fun MicButton(id: String, L: Lang, accent: Color, modifier: Modifier) {
        val isListening = listening == id
        val isBusy = busy == id
        val pulse = rememberInfiniteTransition(label = "pulse")
        val grow by pulse.animateFloat(1f, 1.12f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "grow")
        Box(
            modifier.size(124.dp).scale(if (isListening) grow else 1f).clip(CircleShape)
                .background(if (isBusy) accent.copy(alpha = 0.7f) else accent)
                .clickable { micTap(id) },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(10.dp)) {
                Icon(painterResource(if (isListening) R.drawable.ic_stop else R.drawable.ic_mic), null, tint = Color.White, modifier = Modifier.size(44.dp))
                Text(
                    when { isListening -> L.listen; isBusy -> L.busy; else -> L.tap },
                    color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 2, lineHeight = 16.sp,
                )
            }
        }
    }
}
