package vn.dautruongtrithuc.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.URL

/**
 * Ảnh đại diện thật (ảnh Google hoặc ảnh chọn từ thư viện).
 * Ảnh được cắt vuông 128×128, nén JPEG và lưu dạng chuỗi base64 trong users/{uid}/photo.
 */
object Photos {
    private const val SIZE = 128

    /** Ảnh của chính mình (null = hiện chữ cái). */
    var mine by mutableStateOf<ImageBitmap?>(null)
        private set

    private val cache = mutableStateMapOf<String, ImageBitmap>()
    private val missing = HashSet<String>()
    private val loading = HashSet<String>()

    fun init(prefs: Prefs) {
        mine = decode(prefs.photo)
    }

    /** Ảnh của người chơi thật khác (tải từ máy chủ một lần, lưu tạm). */
    fun of(uid: String?): ImageBitmap? {
        if (uid == null) return null
        cache[uid]?.let { return it }
        if (uid in missing || !loading.add(uid)) return null
        try {
            Net.ref("users/$uid/photo").get().addOnSuccessListener { s ->
                val bmp = decode(s.getValue(String::class.java))
                if (bmp != null) cache[uid] = bmp else missing.add(uid)
                loading.remove(uid)
            }.addOnFailureListener { loading.remove(uid) }
        } catch (_: Exception) {
            loading.remove(uid)
        }
        return null
    }

    fun forget(uid: String) {
        cache.remove(uid)
        missing.remove(uid)
    }

    fun decode(b64: String?): ImageBitmap? {
        if (b64.isNullOrBlank()) return null
        return try {
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } catch (_: Exception) { null }
    }

    private fun squareEncode(src: Bitmap): String {
        val side = minOf(src.width, src.height)
        val x = (src.width - side) / 2
        val y = (src.height - side) / 2
        val crop = Bitmap.createBitmap(src, x, y, side, side)
        val small = Bitmap.createScaledBitmap(crop, SIZE, SIZE, true)
        val out = ByteArrayOutputStream()
        small.compress(Bitmap.CompressFormat.JPEG, 78, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    /** Đặt ảnh của mình (chuỗi rỗng = chỉ hiện chữ cái) và gửi lên máy chủ. */
    fun setMine(prefs: Prefs, b64: String, mode: String, art: Int = -1) {
        prefs.photo = b64
        prefs.photoMode = mode
        prefs.avatar = art
        mine = decode(b64)
        Net.pushProfile()
    }

    /** Ảnh chọn từ thư viện. */
    suspend fun fromGallery(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            var sample = 1
            while (minOf(opts.outWidth, opts.outHeight) / (sample * 2) >= SIZE * 2) sample *= 2
            val bmp = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@withContext null
            squareEncode(bmp)
        } catch (_: Exception) { null }
    }

    /** Ảnh hồ sơ Google (nếu tài khoản có ảnh). */
    suspend fun fromGoogle(): String? = withContext(Dispatchers.IO) {
        try {
            val url = Net.auth.currentUser?.photoUrl?.toString() ?: return@withContext null
            val big = url.replace(Regex("=s\\d+-c"), "=s256-c").replace(Regex("/s\\d+-c/"), "/s256-c/")
            val bmp = URL(big).openStream().use { BitmapFactory.decodeStream(it) } ?: return@withContext null
            squareEncode(bmp)
        } catch (_: Exception) { null }
    }
}
