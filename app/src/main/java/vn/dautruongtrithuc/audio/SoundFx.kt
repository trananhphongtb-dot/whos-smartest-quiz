package vn.dautruongtrithuc.audio

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.SoundPool
import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import vn.dautruongtrithuc.R
import vn.dautruongtrithuc.data.Prefs
import java.util.Locale

/** Hiệu ứng âm thanh (tiếng đồng hồ, chuông…) và giọng đọc tiếng Việt. */
class SoundFx(context: Context, private val prefs: Prefs) {

    private val app = context.applicationContext
    private val vi = Locale("vi", "VN")

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val ids: Map<String, Int> = mapOf(
        "tick" to pool.load(context, R.raw.tick, 1),
        "tick_hi" to pool.load(context, R.raw.tick_hi, 1),
        "chime" to pool.load(context, R.raw.chime, 1),
        "correct" to pool.load(context, R.raw.correct, 1),
        "wrong" to pool.load(context, R.raw.wrong, 1),
        "lock" to pool.load(context, R.raw.lock, 1),
        "buzz" to pool.load(context, R.raw.buzz, 1),
        "win" to pool.load(context, R.raw.win, 1),
        "lose" to pool.load(context, R.raw.lose, 1),
    )

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val triedEngines = HashSet<String>()
    private var checking = false

    /** Trạng thái giọng đọc để hiện trong tab Tôi. */
    var voiceStatus by mutableStateOf("Đang kiểm tra giọng đọc…")
        private set
    var voiceOk by mutableStateOf(false)
        private set

    init {
        connect(GOOGLE_TTS)
    }

    /** Thử lần lượt: Google TTS → mặc định của máy → các bộ đọc khác có tiếng Việt. */
    private fun connect(engine: String?) {
        checking = true
        if (engine != null) triedEngines.add(engine) else triedEngines.add("")
        var created: TextToSpeech? = null
        var earlyStatus: Int? = null
        val listener = TextToSpeech.OnInitListener { status ->
            val t = created
            if (t == null) earlyStatus = status else onEngineInit(t, status, engine)
        }
        created = try {
            if (engine != null) TextToSpeech(app, listener, engine) else TextToSpeech(app, listener)
        } catch (e: Exception) {
            null
        }
        tts = created
        val made = created
        val early = earlyStatus
        if (made == null) tryNext() else if (early != null) onEngineInit(made, early, engine)
    }

    private fun onEngineInit(t: TextToSpeech, status: Int, engine: String?) {
        if (status == TextToSpeech.SUCCESS) {
            val r = try { t.setLanguage(vi) } catch (e: Exception) { TextToSpeech.LANG_NOT_SUPPORTED }
            if (r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED) {
                t.setSpeechRate(0.95f)
                ttsReady = true
                voiceOk = true
                checking = false
                val name = t.defaultEngine ?: engine ?: ""
                voiceStatus = if (name.contains("google")) "Giọng Google tiếng Việt · sẵn sàng" else "Giọng tiếng Việt · sẵn sàng"
                return
            }
            // Bộ đọc này không có tiếng Việt: tìm bộ khác
            val others = try { t.engines.map { it.name } } catch (e: Exception) { emptyList() }
            t.shutdown()
            pendingEngines = others.filter { it !in triedEngines }
            tryNext()
        } else {
            t.shutdown()
            tryNext()
        }
    }

    private var pendingEngines: List<String>? = null

    private fun tryNext() {
        ttsReady = false
        if ("" !in triedEngines) {
            connect(null)
            return
        }
        val next = pendingEngines?.firstOrNull { it !in triedEngines }
        if (next != null) {
            connect(next)
        } else {
            tts = null
            voiceOk = false
            checking = false
            voiceStatus = "Máy chưa có giọng tiếng Việt"
        }
    }

    /** Kiểm tra lại sau khi người dùng cài giọng. */
    fun recheck() {
        if (voiceOk || checking) return
        tts?.shutdown()
        tts = null
        triedEngines.clear()
        pendingEngines = null
        voiceStatus = "Đang kiểm tra giọng đọc…"
        connect(GOOGLE_TTS)
    }

    fun play(name: String) {
        if (!prefs.soundOn) return
        val id = ids[name] ?: return
        pool.play(id, 1f, 1f, 1, 0, 1f)
    }

    fun say(text: String, force: Boolean = false) {
        if ((!prefs.voiceOn && !force) || !ttsReady) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "dttt")
    }

    fun stopVoice() {
        tts?.stop()
    }

    /** Mở trang cài giọng tiếng Việt (Google) hoặc cài đặt giọng nói của máy. */
    fun openVoiceSetup(context: Context) {
        val attempts = listOf(
            Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).setPackage(GOOGLE_TTS),
            Intent("com.android.settings.TTS_SETTINGS"),
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$GOOGLE_TTS")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$GOOGLE_TTS")),
        )
        for (i in attempts) {
            try {
                context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: ActivityNotFoundException) {
                // thử cách tiếp theo
            } catch (e: SecurityException) {
                // thử cách tiếp theo
            }
        }
    }

    fun release() {
        pool.release()
        tts?.shutdown()
        tts = null
    }

    companion object {
        const val GOOGLE_TTS = "com.google.android.tts"
    }
}
