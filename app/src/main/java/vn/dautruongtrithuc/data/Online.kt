package vn.dautruongtrithuc.data

import android.content.Context
import org.json.JSONObject
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Người chơi trực tuyến lấp chỗ trống khi chưa đủ người thật.
 * Khi có máy chủ: chỉ ghép thêm khi tìm người thật quá thời gian chờ; tắt hẳn bằng [FILL_ENABLED].
 */
object OnlineConfig {
    @Volatile var FILL_ENABLED = true
}

data class Persona(
    val id: String,
    val name: String,
    val palette: Int,
    val province: String,
    val skill: Float,            // 0..1
    val strong: Set<String>,     // lĩnh vực mạnh
    val weak: Set<String>,       // lĩnh vực yếu
    val risk: Float,             // 0..1: hay giành quyền
    val baseRating: Int,
    val nightOwl: Boolean,
    val avatar: Int = -1,        // mã ảnh đại diện (xem AvatarArt)
)

object Personas {
    private val given = listOf(
        "Minh Anh", "Đức Minh", "Ngọc Hân", "Gia Huy", "Bảo Ngọc", "Quang Huy", "Thu Trang", "Hoàng Long",
        "Khánh Linh", "Tuấn Kiệt", "Phương Thảo", "Minh Khôi", "Thanh Hà", "Đăng Khoa", "Mai Chi", "Hải Đăng",
        "Lan Anh", "Quốc Bảo", "Thảo Vy", "Việt Hoàng", "Hà My", "Trung Kiên", "Yến Nhi", "Nhật Minh",
        "Diệu Linh", "Anh Tuấn", "Hồng Nhung", "Văn Thắng", "Kim Ngân", "Đình Phúc", "Thùy Dương", "Công Danh",
        "Ngọc Ánh", "Hữu Nghĩa", "Bích Phương", "Thành Đạt", "Hoài An", "Tiến Dũng", "Cẩm Tú", "Duy Mạnh",
        "Tường Vi", "Phúc Lâm", "Minh Thư", "Xuân Trường", "Như Quỳnh", "Văn Hậu", "Hương Giang", "Quang Vinh",
        "Thanh Tâm", "Bảo Châu", "Khắc Việt", "Tú Anh", "Đức Anh", "Mỹ Linh", "Hoàng Nam", "Gia Bảo",
    )
    private val nick = listOf(
        "Mèo Ú", "Gà Con", "Sói Xám", "Cáo Nhỏ", "Học Bá", "Thánh Đoán", "Tí Nị", "Bánh Bao", "Su Su", "Bơ",
        "Mít Ướt", "Cún Con", "Gấu Trúc", "Chuột Lắt", "Cá Voi", "Ốc Sên", "Kẹo Ngọt", "Mây Trắng", "Nắng Sớm",
        "Trà Sữa", "Sữa Chua", "Tôm Hùm", "Khoai Lang", "Bắp Rang", "Đậu Đỏ", "Hạt Tiêu", "Rùa Con", "Báo Đen",
        "Đại Bàng", "Thỏ Ngọc", "Vịt Bầu", "Nhím Xù", "Ong Vàng", "Sóc Nâu", "Cú Mèo", "Mắm Tôm", "Bé Na",
        "Ếch Xanh", "Thánh Toán", "Mọt Sách",
    )
    private val provinces = listOf(
        "Hà Nội", "TP. Hồ Chí Minh", "Hải Phòng", "Đà Nẵng", "Cần Thơ", "Huế", "Hưng Yên", "Ninh Bình",
        "Bắc Ninh", "Thanh Hóa", "Nghệ An", "Hà Tĩnh", "Quảng Ninh", "Phú Thọ", "Thái Nguyên", "Lào Cai",
        "Tuyên Quang", "Lạng Sơn", "Cao Bằng", "Sơn La", "Điện Biên", "Lai Châu", "Quảng Trị", "Quảng Ngãi",
        "Gia Lai", "Khánh Hòa", "Đắk Lắk", "Lâm Đồng", "Đồng Nai", "Tây Ninh", "Vĩnh Long", "Đồng Tháp",
        "An Giang", "Cà Mau",
    )
    private val fields = listOf("TOAN", "LY", "HOA", "SINH", "VAN", "SU", "DIA", "ANH", "TIN", "DN", "SK", "TT")

    private fun ascii(s: String): String {
        val src = "àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ"
        val dst = "aaaaaaaaaaaaaaaaaeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyyyd"
        val sb = StringBuilder()
        for (c in s.lowercase()) {
            val i = src.indexOf(c)
            when {
                i >= 0 -> sb.append(dst[i])
                c.isLetterOrDigit() -> sb.append(c)
            }
        }
        return sb.toString()
    }

    val all: List<Persona> by lazy {
        val r = Random(20261025)
        val used = HashSet<String>()
        val out = ArrayList<Persona>()
        var n = 0
        while (out.size < 200) {
            n++
            val name = when (r.nextInt(10)) {
                in 0..4 -> given.random(r)
                5, 6 -> ascii(given.random(r)) + listOf("2k7", "2k8", "2k9", "2k6", "99", "_vn", "123", "05", "hp", "").random(r)
                7 -> ascii(given.random(r)).take(r.nextInt(4, 8)) + "_" + r.nextInt(1, 99)
                else -> nick.random(r)
            }
            if (!used.add(name.lowercase())) continue
            val skill = (r.nextFloat().pow(1.35f)).coerceIn(0.05f, 0.98f)
            val f = fields.shuffled(r)
            // Điểm xếp hạng khởi đầu: khoảng 100–700, nhiều người ở mức thấp
            val base = (100 + skill.pow(2.0f) * 560 + r.nextInt(-40, 60) * 0.5f).roundToInt().coerceAtLeast(60)
            out.add(
                Persona(
                    id = "p%03d".format(out.size + 1),
                    name = name,
                    palette = r.nextInt(8),
                    province = provinces.random(r),
                    skill = skill,
                    strong = f.take(2).toSet(),
                    weak = f.drop(2).take(2).toSet(),
                    risk = r.nextFloat(),
                    baseRating = base,
                    nightOwl = r.nextBoolean(),
                    avatar = Random(out.size * 977 + 13).let { ar ->
                        val k = ar.nextInt(100)
                        // Như người thật: nhiều người không để ảnh, số còn lại để ảnh con vật/cảnh vật
                        if (k < 55) -1 else 10000 + ar.nextInt(15)
                    },
                )
            )
        }
        out
    }

    private val byId by lazy { all.associateBy { it.id } }
    fun get(id: String): Persona? = byId[id]

    private fun h(vararg xs: Any): Int {
        var x = 17
        for (o in xs) x = x * 31 + o.hashCode()
        x = x xor (x ushr 16)
        x *= 0x45d9f3b
        x = x xor (x ushr 16)
        return x
    }

    /** Biến động theo ngày để bảng xếp hạng luôn "sống". */
    fun drift(p: Persona, day: Int): Int {
        var total = 0
        val start = day - 60
        for (d in start..day) {
            val v = h(p.id, d) % 100
            if (abs(v) < 45) total += (p.skill * 6 + abs(v) % 5).roundToInt()
        }
        return total
    }

    /** Có đang trực tuyến không: phần lớn online buổi tối, một số "cú đêm". */
    fun isOnline(p: Persona, cal: Calendar = Calendar.getInstance()): Boolean {
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val slot = cal.get(Calendar.DAY_OF_YEAR) * 24 + hour
        val base = when (hour) {
            in 19..22 -> 55
            in 12..13 -> 30
            in 23..23, in 0..1 -> if (p.nightOwl) 45 else 8
            in 2..5 -> if (p.nightOwl) 12 else 2
            else -> 18
        }
        return abs(h(p.id, slot)) % 100 < base
    }

    /** Đang thi trận khác (đổi mỗi 10 phút). */
    fun isBusy(p: Persona): Boolean = abs(h(p.id, "busy", System.currentTimeMillis() / 600_000L)) % 100 < 30

    /** Điểm kiếm được trong một kỳ (tuần/tháng/năm) – xác định từ mã kỳ. */
    fun periodPoints(p: Persona, periodKey: String, fraction: Float): Int {
        val v = abs(h(p.id, periodKey)) % 100
        if (v < 25) return 0
        val scale = if (periodKey.startsWith("w")) 1f else 4f
        return ((p.skill * 25 + v * 0.2f) * scale * fraction).roundToInt()
    }
}

/** Lưu điểm xếp hạng của người chơi và người chơi trực tuyến trên máy. */
class Ladder(context: Context) {
    private val sp = context.getSharedPreferences("dttt_online", Context.MODE_PRIVATE)

    var rating: Int
        get() = sp.getInt("rating", 100)
        set(v) { sp.edit().putInt("rating", v.coerceAtLeast(0)).apply() }

    private fun deltas(): JSONObject = try { JSONObject(sp.getString("pdelta", "{}") ?: "{}") } catch (e: Exception) { JSONObject() }

    // Ba người đứng đầu luôn giữ khoảng cách phía trên người chơi
    private val topIds by lazy { Personas.all.sortedByDescending { it.baseRating }.take(3).map { it.id } }

    fun ratingOf(p: Persona): Int {
        val raw = (p.baseRating + Personas.drift(p, today()) + deltas().optInt(p.id, 0)).coerceAtLeast(0)
        val k = topIds.indexOf(p.id)
        return if (k < 0) raw else maxOf(raw, rating + 25 - k * 8)
    }

    fun addPersonaDelta(id: String, d: Int) {
        val j = deltas()
        j.put(id, j.optInt(id, 0) + d)
        sp.edit().putString("pdelta", j.toString()).apply()
    }

    // ---------------------------------------------------------- Kỳ xếp hạng
    fun periodKey(kind: Int, cal: Calendar = Calendar.getInstance()): String = when (kind) {
        1 -> "w${cal.get(Calendar.YEAR)}-${cal.get(Calendar.WEEK_OF_YEAR)}"
        2 -> "m${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH)}"
        else -> "y${cal.get(Calendar.YEAR)}"
    }

    /** Tỉ lệ thời gian đã trôi trong kỳ, để điểm người ảo tăng dần theo kỳ. */
    fun periodFraction(kind: Int, cal: Calendar = Calendar.getInstance()): Float = when (kind) {
        1 -> (cal.get(Calendar.DAY_OF_WEEK) + cal.get(Calendar.HOUR_OF_DAY) / 24f) / 7f
        2 -> (cal.get(Calendar.DAY_OF_MONTH) + cal.get(Calendar.HOUR_OF_DAY) / 24f) / cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        else -> (cal.get(Calendar.DAY_OF_YEAR) / cal.getActualMaximum(Calendar.DAY_OF_YEAR).toFloat()) * 12f
    }.coerceIn(0.05f, 12f)

    fun myPeriodPoints(kind: Int): Int = sp.getInt("pp_" + periodKey(kind), 0)

    private fun addPeriodPoints(d: Int) {
        if (d <= 0) return
        val e = sp.edit()
        for (k in 1..3) {
            val key = "pp_" + periodKey(k)
            e.putInt(key, sp.getInt(key, 0) + d)
        }
        e.apply()
    }

    /**
     * Cập nhật điểm sau trận theo thứ hạng: nhất +3, nhì +2, ba +1, người bét 0 (thua không trừ).
     * Hòa điểm thì cùng hạng cao hơn. [reduced] = trận chỉ có người chơi trực tuyến ảo: +2/+1/+1/0.
     * [entries] gồm (mã persona hoặc null cho bạn, điểm xếp hạng trước trận, điểm trong trận).
     */
    fun applyMatch(entries: List<Triple<String?, Int, Int>>, reduced: Boolean = false): List<Int> {
        val low = entries.minOfOrNull { it.third } ?: 0
        val out = entries.map { (_, _, s) ->
            val rank = entries.count { it.third > s } + 1
            val base = when {
                rank == 1 -> 3
                s == low -> 0
                rank == 2 -> 2
                rank == 3 -> 1
                else -> 0
            }
            if (reduced) when (base) { 3 -> 2; 2 -> 1; 1 -> 1; else -> 0 } else base
        }
        entries.forEachIndexed { i, e ->
            val id = e.first
            when {
                id == null || id == "#me" -> addMine(out[i])
                id.startsWith("#") -> {}                 // người thật khác: máy họ tự cộng
                else -> addPersonaDelta(id, out[i])
            }
        }
        return out
    }

    /** Cộng điểm xếp hạng cho chính mình (và điểm theo kỳ). */
    fun addMine(d: Int) {
        rating = rating + d
        addPeriodPoints(d)
    }

    companion object {
        fun today(): Int = (System.currentTimeMillis() / 86_400_000L).toInt()
    }
}

data class BoardRow(
    val personaId: String?,
    val name: String,
    val palette: Int,
    val province: String,
    val value: Int,
    val avatar: Int = -1,
    val uid: String? = null,
) {
    val isMe get() = personaId == null && uid == null
    val key get() = personaId ?: uid
}

object Leaderboard {
    /** kind: 0 tất cả (điểm xếp hạng), 1 tuần, 2 tháng, 3 năm (điểm kiếm được trong kỳ). */
    fun rows(prefs: Prefs, kind: Int, real: List<Pair<NetUser, Int>> = emptyList()): List<BoardRow> {
        val l = prefs.ladder
        val list = ArrayList<BoardRow>()
        for (p in Personas.all) {
            val v = if (kind == 0) l.ratingOf(p) else Personas.periodPoints(p, l.periodKey(kind), l.periodFraction(kind))
            if (kind != 0 && v <= 0) continue
            list.add(BoardRow(p.id, p.name, p.palette, p.province, v, p.avatar))
        }
        for ((u, v) in real) {
            if (u.uid == Net.uid) continue
            if (kind != 0 && v <= 0) continue
            list.add(BoardRow(null, u.name, 1, "", v, u.avatar, u.uid))
        }
        val mine = if (kind == 0) l.rating else l.myPeriodPoints(kind)
        list.add(BoardRow(null, prefs.name.ifBlank { "Bạn" }, 0, "", mine, prefs.avatar))
        return list.sortedWith(compareByDescending<BoardRow> { it.value }.thenBy { !it.isMe })
    }

    fun myRank(prefs: Prefs, kind: Int = 0, real: List<Pair<NetUser, Int>> = emptyList()): Int =
        rows(prefs, kind, real).indexOfFirst { it.isMe } + 1
}
