package vn.dautruongtrithuc.data

import android.content.Context
import org.json.JSONObject

data class Question(
    val id: String,
    val field: String,
    val level: Int,
    val text: String,
    val options: List<String>,
    val answer: Int,
)

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("dttt", Context.MODE_PRIVATE)

    /** Điểm xếp hạng và người chơi trực tuyến. */
    val ladder = Ladder(context)

    var name: String
        get() = sp.getString("name", "Bạn") ?: "Bạn"
        set(value) { sp.edit().putString("name", value).apply() }

    /** Mã ảnh đại diện của người chơi (−1 = chữ cái đầu tên). */
    var avatar: Int
        get() = sp.getInt("avatar", -1)
        set(value) { sp.edit().putInt("avatar", value).apply() }

    /** Ảnh đại diện thật dạng base64 ("" = chỉ hiện chữ cái). */
    var photo: String
        get() = sp.getString("photo", "") ?: ""
        set(value) { sp.edit().putString("photo", value).apply() }

    /** Nguồn ảnh: google | gallery | letter. */
    var photoMode: String
        get() = sp.getString("photoMode", "google") ?: "google"
        set(value) { sp.edit().putString("photoMode", value).apply() }

    /** Mã ngôn ngữ giao diện đã chọn, mặc định "en" (tiếng Anh). */
    var language: String
        get() = sp.getString("lang", "en") ?: "en"
        set(value) { sp.edit().putString("lang", value).apply() }

    var soundOn: Boolean
        get() = sp.getBoolean("sound", true)
        set(value) { sp.edit().putBoolean("sound", value).apply() }

    var voiceOn: Boolean
        get() = sp.getBoolean("voice", true)
        set(value) { sp.edit().putBoolean("voice", value).apply() }

    var played: Int
        get() = sp.getInt("played", 0)
        set(value) { sp.edit().putInt("played", value).apply() }

    var wins: Int
        get() = sp.getInt("wins", 0)
        set(value) { sp.edit().putInt("wins", value).apply() }

    var bestSolo: Int
        get() = sp.getInt("bestSolo", 0)
        set(value) { sp.edit().putInt("bestSolo", value).apply() }

    var readQuestions: Boolean
        get() = sp.getBoolean("readQ", true)
        set(value) { sp.edit().putBoolean("readQ", value).apply() }

    private fun strSet(key: String): MutableSet<String> = HashSet(sp.getStringSet(key, emptySet()) ?: emptySet())

    /** Câu đã gặp khi luyện tập / thi với máy (riêng, không ảnh hưởng thi thật). */
    fun practiceSeen(): MutableSet<String> = strSet("pseen")

    /** Câu đã trả lời sai hoặc bỏ qua khi luyện tập, dùng lại khi hết câu mới. */
    fun wrong(): MutableSet<String> = strSet("wrong")

    /** Lưu kết quả luyện tập: id → true nếu trả lời đúng. */
    fun savePractice(shown: Collection<String>, results: Map<String, Boolean>) {
        if (shown.isEmpty() && results.isEmpty()) return
        val seen = practiceSeen().apply { addAll(shown); addAll(results.keys) }
        val wr = wrong()
        for ((id, ok) in results) if (ok) wr.remove(id) else wr.add(id)
        sp.edit().putStringSet("pseen", seen).putStringSet("wrong", wr).apply()
    }

    /** Danh sách bạn bè (mã người chơi); kết bạn một chiều, không cần bên kia đồng ý. */
    fun friends(): List<String> = (sp.getStringSet("friends", emptySet()) ?: emptySet()).sorted()

    fun isFriend(id: String): Boolean = sp.getStringSet("friends", emptySet())?.contains(id) == true

    fun addFriend(name: String) {
        val f = HashSet(sp.getStringSet("friends", emptySet()) ?: emptySet())
        f.add(name)
        sp.edit().putStringSet("friends", f).apply()
    }

    fun removeFriend(name: String) {
        val f = HashSet(sp.getStringSet("friends", emptySet()) ?: emptySet())
        f.remove(name)
        sp.edit().putStringSet("friends", f).apply()
    }

    fun resetPractice() {
        sp.edit().remove("pseen").remove("wrong").remove("seen").apply()
    }
}

object QuestionBank {
    var all: List<Question> = emptyList()
        private set

    /** Ngôn ngữ của bộ câu hỏi đang nạp (null = chưa nạp). */
    var loadedLang: String? = null
        private set

    val fieldCount: Int get() = all.map { it.field }.distinct().size

    /** File ngân hàng câu hỏi theo ngôn ngữ; ngôn ngữ chưa có bộ riêng thì dùng tiếng Anh. */
    fun fileFor(lang: String): String = when (lang) {
        "vi" -> "cau_hoi_vi.json"
        "zh" -> "cau_hoi_zh.json"
        "hi" -> "cau_hoi_hi.json"
        else -> "cau_hoi.json"
    }

    fun load(context: Context, reload: Boolean = false) {
        if (!reload && all.isNotEmpty()) return
        val lang = try { Prefs(context).language } catch (_: Exception) { "en" }
        val text = context.assets.open(fileFor(lang)).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val arr = JSONObject(text).getJSONArray("cau_hoi")
        val list = ArrayList<Question>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val opts = listOf(o.getString("A"), o.getString("B"), o.getString("C"), o.getString("D"))
            val ans = "ABCD".indexOf(o.getString("dap_an"))
            if (ans < 0) continue
            list.add(
                Question(
                    id = o.getString("id"),
                    field = o.getString("linh_vuc"),
                    level = o.getInt("do_kho"),
                    text = o.getString("cau_hoi"),
                    options = opts,
                    answer = ans,
                )
            )
        }
        all = list
        loadedLang = lang
    }

    /**
     * Chọn câu hỏi trộn đều các lĩnh vực.
     * [tier] nhỏ hơn được ưu tiên trước (0 = câu mới, 1 = câu từng sai, 2 = câu đã đúng);
     * trong cùng một bậc, mức độ khó trong [levels] được ưu tiên theo thứ tự liệt kê.
     */
    fun pick(count: Int, levels: List<Int>, exclude: Set<String>, tier: (Question) -> Int): List<Question> {
        val usable = all.filter { it.level in levels && it.id !in exclude }
        val byField = usable.groupBy { it.field }
        val pools = HashMap<String, ArrayDeque<Question>>()
        for ((field, qs) in byField) {
            val ordered = ArrayList<Question>()
            for (t in 0..2) {
                for (lv in levels) {
                    ordered.addAll(qs.filter { it.level == lv && tier(it) == t }.shuffled())
                }
            }
            pools[field] = ArrayDeque(ordered)
        }
        val order = pools.keys.shuffled()
        val out = ArrayList<Question>()
        var idx = 0
        while (out.size < count && pools.values.any { it.isNotEmpty() }) {
            val deque = pools[order[idx % order.size]]
            idx++
            if (deque != null && deque.isNotEmpty()) out.add(deque.removeFirst())
        }
        return out.shuffled()
    }
}
