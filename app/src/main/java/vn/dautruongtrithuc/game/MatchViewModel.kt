package vn.dautruongtrithuc.game

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import vn.dautruongtrithuc.audio.SoundFx
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.distinctUntilChanged
import vn.dautruongtrithuc.data.Net
import vn.dautruongtrithuc.data.Persona
import vn.dautruongtrithuc.data.Personas
import vn.dautruongtrithuc.data.Prefs
import vn.dautruongtrithuc.data.Question
import vn.dautruongtrithuc.data.QuestionBank
import kotlin.random.Random

/** bots = 0 là luyện tập một mình; level 0 dễ, 1 vừa, 2 khó; ranked = thi trực tuyến xếp hạng. */
data class MatchConfig(
    val bots: Int,
    val level: Int,
    val ranked: Boolean = false,
    val room: Boolean = false,
    val joinRoom: String? = null,     // vào phòng của người khác (qua link / lời mời)
)

data class PlayerState(
    val id: Int,
    val name: String,
    val isBot: Boolean,
    val palette: Int,
    val score: Int = 0,
    val personaId: String? = null,
    val rating: Int = 0,
    val ratingDelta: Int? = null,
    val province: String = "",
    val avatar: Int = -1,
    val uid: String? = null,          // người chơi thật trên máy chủ
) {
    /** Khóa dùng cho kết bạn: mã người chơi ảo hoặc uid người thật. */
    val friendKey: String? get() = personaId ?: uid
}

enum class Phase { LOBBY, SEARCH, INTRO, PLAY, REVEAL, END }

val PART_NAMES = listOf("", "Khởi động", "Đấu Trí", "Về Đích")

class MatchViewModel : ViewModel() {

    val players = mutableStateListOf<PlayerState>()
    var part by mutableIntStateOf(0)
        private set
    var phase by mutableStateOf(Phase.INTRO)
        private set
    var introLeft by mutableIntStateOf(0)
        private set
    var question by mutableStateOf<Question?>(null)
        private set
    var qNo by mutableIntStateOf(0)
        private set
    var qTotal by mutableIntStateOf(0)
        private set
    var timeLeftMs by mutableLongStateOf(0L)
        private set
    var timeTotalMs by mutableLongStateOf(1L)
        private set
    var humanChoice by mutableStateOf<Int?>(null)
        private set
    var reveal by mutableStateOf(false)
        private set
    var locked by mutableStateOf<Set<Int>>(emptySet())
        private set
    var turnId by mutableStateOf<Int?>(null)
        private set
    var banner by mutableStateOf("")
        private set
    var roundText by mutableStateOf("")
        private set
    var winners by mutableStateOf<Set<Int>>(emptySet())
        private set

    var solo = true
        private set
    var ranked = false
        private set
    var searchSec by mutableIntStateOf(0)
        private set
    var room = false
        private set
    var roomCode by mutableStateOf("")
        private set
    private val startSignal = kotlinx.coroutines.CompletableDeferred<Unit>()

    // ---- Trực tuyến (Firebase)
    /** Mã phòng trên máy chủ (null = chơi trên máy). */
    var netRoom by mutableStateOf<String?>(null)
        private set
    /** true = máy này là chủ phòng (điều khiển trận). */
    var isHost by mutableStateOf(true)
        private set
    private var guest = false
    var searchFailed by mutableStateOf(false)
        private set
    var netError by mutableStateOf<String?>(null)
        private set
    private val unsubs = ArrayList<() -> Unit>()
    private val remoteIn = HashMap<String, RemoteIn>()
    private var deadlineServer = 0L
    private data class RemoteIn(val k: String?, val c: Int, val t: Long, val star: String?, val p1: Int)
    private fun remotes() = players.filter { it.uid != null && it.id != 0 }

    /** Trận trực tuyến (xếp hạng hoặc phòng): người chơi trực tuyến cố gắng không thắng người thật. */
    private val online get() = ranked || room

    private var started = false
    private var job: Job? = null
    private lateinit var cfg: MatchConfig
    private lateinit var prefs: Prefs
    private lateinit var fx: SoundFx
    private val rnd = Random(System.nanoTime())
    private val personas = HashMap<Int, Persona>()
    private var matchStarted = false

    // Ngôi sao hy vọng (phần Về Đích): bấm trong câu của mình để đặt cho câu tiếp theo của mình
    var starId by mutableStateOf<Int?>(null)      // người có sao ở câu hiện tại
        private set
    var myNextStar by mutableStateOf(false)       // đã đặt sao cho câu sau chưa
        private set
    var canStarNext by mutableStateOf(false)      // đang ở câu của mình và còn câu sau
        private set
    private val pendingStar = HashSet<Int>()

    fun toggleStar() {
        if (!canStarNext) return
        myNextStar = !myNextStar
        fx.play(if (myNextStar) "chime" else "tick")
        if (guest) netRoom?.let { Rooms.answer(it, mapOf("star" to if (myNextStar) "3-$qNo" else "")) }
    }
    private var rated = false

    private fun rateKey(p: PlayerState): String = p.personaId ?: if (p.id == 0) "#me" else "#other"

    /** Có người thật khác trong trận thì cộng đủ; toàn người chơi trực tuyến ảo thì cộng ít hơn. */
    private fun gainFactor(): Float = if (players.any { it.id != 0 && it.personaId == null && !it.isBot }) 1f else 0.5f
    private val usedIds = HashSet<String>()
    private var pSeen: Set<String> = emptySet()
    private var pWrong: Set<String> = emptySet()
    private val shownNow = LinkedHashSet<String>()
    private val resultsNow = LinkedHashMap<String, Boolean>()

    // Trạng thái trong một câu hỏi
    private var questionStart = 0L
    private var humanAnswerAt = -1L
    private var skipFlag = false
    private val botAnswers = HashMap<Int, Pair<Int, Long>>()
    private val steals = ArrayList<Triple<Int, Int, Long>>()
    private var mainChoice: Int? = null
    private var mainDone = false

    fun start(config: MatchConfig, p: Prefs, f: SoundFx) {
        if (started) return
        started = true
        cfg = config
        prefs = p
        fx = f
        ranked = config.ranked
        room = config.room
        solo = config.bots == 0 && !ranked && !room
        pSeen = p.practiceSeen()
        pWrong = p.wrong()
        players.clear()
        players.add(PlayerState(0, p.name.ifBlank { "Bạn" }, isBot = false, palette = 0, rating = p.ladder.rating, avatar = p.avatar))
        if (!ranked && !room) {
            val names = listOf("An", "Bình", "Chi", "Dũng", "Hà", "Khoa", "Linh", "Nam").shuffled(rnd)
            repeat(config.bots.coerceIn(0, 3)) { i ->
                players.add(PlayerState(i + 1, names[i], isBot = true, palette = i + 1))
            }
        }
        if (config.joinRoom != null) {
            guest = true
            room = true
            solo = false
            isHost = false
            job = viewModelScope.launch { runGuest(config.joinRoom, alreadyMember = false) }
            return
        }
        job = viewModelScope.launch {
            if (ranked) search()
            if (guest) return@launch       // đã được ghép vào phòng người khác
            if (room) lobby()
            matchStarted = true
            Net.setInMatch(true)
            if (netRoom != null) hostNet()
            intro(1)
            part1()
            intro(2)
            part2()
            intro(3)
            part3()
            finish()
        }
    }

    /** Dừng trận khi người chơi thoát giữa chừng. */
    fun stop() {
        // Bỏ trận trực tuyến giữa chừng: tính thua, xếp cuối
        if (online && matchStarted && !rated && ::prefs.isInitialized) {
            val low = (players.minOfOrNull { it.score } ?: 0) - 1
            val entries = players.map { Triple(rateKey(it), it.rating, if (it.id == 0) low else it.score) }
            prefs.ladder.applyMatch(entries, reduced = gainFactor() < 1f)
            prefs.played = prefs.played + 1
            rated = true
        }
        job?.cancel()
        flush()
        if (::fx.isInitialized) fx.stopVoice()
        unsubs.forEach { it() }
        unsubs.clear()
        if (ranked) Rooms.dequeue()
        netRoom?.let { rid ->
            if (isHost && remotes().isEmpty()) Rooms.cleanup(rid)
            else if (isHost && phase == Phase.END) Rooms.cleanup(rid)
            else Rooms.leave(rid)
        }
        Net.setInMatch(false)
        Net.pushProfile()
    }

    // ------------------------------------------------------------------ Người chơi bấm

    fun choose(i: Int) {
        if (phase != Phase.PLAY || reveal || timeLeftMs <= 0L) return
        if (guest && part >= 2) {
            if (humanChoice != null) return
            humanChoice = i
            locked = locked + 0
            fx.play("lock")
            val t = (Net.serverNow() - (deadlineServer - timeTotalMs)).coerceAtLeast(0L)
            netRoom?.let { Rooms.answer(it, mapOf("k" to "$part-$qNo", "c" to i, "t" to t)) }
            return
        }
        val now = SystemClock.elapsedRealtime() - questionStart
        when (part) {
            1 -> if (humanChoice == null) humanChoice = i
            2 -> if (humanChoice == null) {
                humanChoice = i
                humanAnswerAt = now
                locked = locked + 0
                fx.play("lock")
            }
            3 -> {
                if (mainDone || humanChoice != null) return
                if (turnId == 0) {
                    humanChoice = i
                    mainChoice = i
                    mainDone = true
                } else if (!solo) {
                    humanChoice = i
                    steals.add(Triple(0, i, now))
                    locked = locked + 0
                    fx.play("lock")
                }
            }
        }
    }

    fun skip() {
        if (part == 1 && phase == Phase.PLAY && humanChoice == null && !reveal) skipFlag = true
    }

    fun canAnswer(): Boolean {
        if (phase != Phase.PLAY || reveal || question == null || timeLeftMs <= 0L) return false
        if (guest && part >= 2) return humanChoice == null
        return when (part) {
            3 -> !mainDone && humanChoice == null && (turnId == 0 || !solo)
            else -> humanChoice == null
        }
    }

    // ------------------------------------------------------------------ Các phần thi

    /** Chủ phòng bấm Bắt đầu. */
    fun startRoom() {
        if (phase == Phase.LOBBY && isHost && players.size >= 2) {
            netRoom?.let { Rooms.setState(it, "playing") }
            startSignal.complete(Unit)
        }
    }

    /** Phòng chờ: 1–2 người chơi trực tuyến vào làm mồi; khi có người thật thì họ tự rời. */
    private suspend fun lobby() = coroutineScope {
        phase = Phase.LOBBY
        roomCode = (100_000 + rnd.nextInt(900_000)).toString()
        if (Net.signedIn) {
            val rid = Rooms.create("room", prefs.name.ifBlank { "Bạn" }, prefs.avatar, prefs.ladder.rating)
            if (rid != null) {
                netRoom = rid
                roomCode = rid
                val myUid = Net.uid
                unsubs += Rooms.listen("rooms/$rid") { snap ->
                    val ms = Rooms.members(snap).filter { it.uid != myUid }
                    // Người thật mới vào
                    for (m in ms) {
                        if (players.none { it.uid == m.uid } && players.count { it.personaId == null } < 4) {
                            val id = (1..3).firstOrNull { k -> players.none { it.id == k && it.personaId == null } } ?: continue
                            players.removeAll { it.id == id }   // nhường chỗ nếu người ảo đang giữ số này
                            personas.remove(id)
                            onRealPlayerJoined(
                                PlayerState(id, m.name, isBot = false, palette = id, rating = m.rating, avatar = m.avatar, uid = m.uid)
                            )
                            fx.play("lock")
                        }
                    }
                    // Người thật rời phòng chờ
                    players.removeAll { it.uid != null && it.id != 0 && ms.none { m -> m.uid == it.uid } }
                }
            }
        }
        val want = if (rnd.nextFloat() < 0.5f) 1 else 2
        val picks = pickPersonas(want)
        val joiner = launch {
            for ((k, pr) in picks.withIndex()) {
                delay(rnd.nextLong(2_000L, 9_000L))
                if (realCount() >= 2 || !Net.fillEnabled) break
                val free = (1..3).firstOrNull { id -> players.none { it.id == id } } ?: break
                addPersona(free, pr.first, pr.second)
            }
        }
        startSignal.await()
        joiner.cancel()
    }

    private fun realCount() = players.count { !it.isBot }

    /** Mời bạn (người chơi trực tuyến) đang rảnh vào phòng. */
    fun inviteFriend(id: String): Boolean {
        if (!isHost || phase != Phase.LOBBY || players.size >= 4 || players.any { it.personaId == id }) return false
        val pp = Personas.get(id) ?: return false
        viewModelScope.launch {
            delay(rnd.nextLong(1_000L, 3_000L))
            if (phase == Phase.LOBBY && players.size < 4 && players.none { it.personaId == id }) {
                val nextId = (1..3).first { k -> players.none { it.id == k } }
                addPersona(nextId, pp, prefs.ladder.ratingOf(pp))
            }
        }
        return true
    }

    /** Gọi khi một người thật vào phòng (khi có máy chủ). */
    fun onRealPlayerJoined(p: PlayerState) {
        players.add(p)
        if (realCount() >= 2) {
            players.removeAll { it.personaId != null }
        } else {
            players.lastOrNull { it.personaId != null }?.let { players.remove(it) }
        }
        personas.keys.retainAll(players.map { it.id }.toSet())
    }

    private fun pickPersonas(n: Int): List<Pair<Persona, Int>> {
        val my = prefs.ladder.rating
        val taken = players.mapNotNull { it.personaId }.toSet()
        return Personas.all
            .filter { it.id !in taken }
            .map { it to prefs.ladder.ratingOf(it) }
            .sortedBy { (_, r) -> kotlin.math.abs(r - my) + rnd.nextInt(0, 120) }
            .take(24)
            .shuffled(rnd)
            .take(n)
    }

    private fun addPersona(id: Int, pp: Persona, r: Int) {
        personas[id] = pp
        players.add(
            PlayerState(
                id, pp.name, isBot = true, palette = pp.palette,
                personaId = pp.id, rating = r, province = pp.province, avatar = pp.avatar,
            )
        )
        fx.play("lock")
    }

    /** Tìm đối thủ: người chơi lần lượt vào phòng. */
    private suspend fun search() {
        phase = Phase.SEARCH
        if (Net.signedIn && netSearch()) return
        if (!Net.fillEnabled) {
            searchFailed = true
            awaitCancellation()
        }
        val pool = pickPersonas(3)
        val totalMs = rnd.nextLong(3_000L, 12_000L)
        val joins = List(3) { rnd.nextLong(600L, totalMs) }.sorted()
        val t0 = SystemClock.elapsedRealtime()
        var next = 0
        while (next < 3) {
            val el = SystemClock.elapsedRealtime() - t0
            searchSec = (el / 1000).toInt()
            if (el >= joins[next]) {
                val (pp, r) = pool[next]
                addPersona(next + 1, pp, r)
                next++
            }
            delay(100)
        }
        delay(1_200)
    }

    private suspend fun intro(n: Int) {
        part = n
        phase = Phase.INTRO
        question = null
        reveal = false
        humanChoice = null
        banner = ""
        roundText = ""
        turnId = null
        locked = emptySet()
        timeLeftMs = 0L
        fx.play("chime")
        delay(700)
        fx.say("Chào mừng đến với phần thi ${PART_NAMES[n]}")
        introLeft = 6
        while (introLeft > 0) {
            delay(1000)
            introLeft -= 1
            if (introLeft in 1..3) fx.play("tick")
        }
        phase = Phase.PLAY
    }

    /** Đồng hồ đếm ngược chạy song song, kêu tích tắc mỗi giây. */
    private fun CoroutineScope.timer(totalMs: Long, isDone: () -> Boolean): Job {
        timeTotalMs = totalMs
        timeLeftMs = totalMs
        deadlineServer = Net.serverNow() + totalMs
        val start = SystemClock.elapsedRealtime()
        return launch {
            var lastSec = ((totalMs + 999) / 1000).toInt()
            while (timeLeftMs > 0L && !isDone()) {
                delay(50)
                timeLeftMs = (totalMs - (SystemClock.elapsedRealtime() - start)).coerceAtLeast(0L)
                val sec = ((timeLeftMs + 999) / 1000).toInt()
                if (sec != lastSec) {
                    lastSec = sec
                    if (sec in 1..3) fx.play("tick_hi") else if (sec > 0) fx.play("tick")
                }
            }
        }
    }

    private fun tier(q: Question): Int = when {
        q.id !in pSeen -> 0
        q.id in pWrong -> 1
        else -> 2
    }

    private fun pick(count: Int, levels: List<Int>): List<Question> {
        var qs = QuestionBank.pick(count, levels, usedIds) { tier(it) }
        if (qs.size < count) {
            qs = qs + QuestionBank.pick(count - qs.size, listOf(1, 2, 3), usedIds + qs.map { it.id }) { tier(it) }
        }
        usedIds.addAll(qs.map { it.id })
        return qs
    }

    /** Phần 1 – Khởi động: 2 phút, trả lời liên tục, đúng +10, được bỏ qua. */
    private suspend fun part1() = coroutineScope {
        val qs = pick(60, listOf(1, 2))  // mức 1; hết câu mới mức 1 thì lấy câu mới mức 2
        qTotal = qs.size
        roundText = "2 phút"
        val clock = timer(120_000L) { false }
        if (remotes().isNotEmpty()) launch {
            while (timeLeftMs > 0L || remotes().any { (remoteIn[it.uid]?.p1 ?: 0) > it.score }) {
                for (rp in remotes()) {
                    val v = remoteIn[rp.uid]?.p1 ?: 0
                    if (v > rp.score) addScore(rp.id, v - rp.score)
                }
                if (timeLeftMs <= 0L) break
                delay(150)
            }
        }
        players.filter { it.isBot }.forEach { bot ->
            launch {
                while (timeLeftMs > 0L) {
                    delay(botThinkPart1(bot.id))
                    if (timeLeftMs <= 0L) break
                    val f = qs.getOrNull(rnd.nextInt(qs.size.coerceAtLeast(1)))?.field ?: ""
                    if (rnd.nextFloat() < accuracy(1, bot.id, f)) addScore(bot.id, 10)
                }
            }
        }
        var i = 0
        while (timeLeftMs > 0L && i < qs.size) {
            val q = qs[i]
            show(q)
            qNo = i + 1
            humanChoice = null
            reveal = false
            skipFlag = false
            questionStart = SystemClock.elapsedRealtime()
            while (timeLeftMs > 0L && humanChoice == null && !skipFlag) delay(40)
            val c = humanChoice
            if (skipFlag && c == null) result(q, false)
            if (c != null) {
                result(q, c == q.answer)
                reveal = true
                if (c == q.answer) {
                    addScore(0, 10)
                    fx.play("correct")
                } else {
                    fx.play("wrong")
                }
                delay(500)
            }
            i++
        }
        clock.join()
        delay(if (remotes().isNotEmpty()) 1_200L else 0L)   // chờ điểm phần 1 của người khác gửi lên
        for (rp in remotes()) {
            val v = remoteIn[rp.uid]?.p1 ?: 0
            if (v > rp.score) addScore(rp.id, v - rp.score)
        }
        coroutineContext.cancelChildren()
        reveal = false
        question = null
        fx.stopVoice()
        fx.play("buzz")
        flush()
        banner = "Hết giờ phần Khởi động!"
        delay(1800)
    }

    /** Phần 2 – Đấu Trí: 10 câu, 15 giây; đúng nhanh nhất +30, nhì +20, ba +10. */
    private suspend fun part2() {
        val qs = pick(10, listOf(2))
        qTotal = qs.size
        for ((i, q) in qs.withIndex()) {
            qNo = i + 1
            roundText = "Câu ${i + 1}/${qs.size}"
            humanChoice = null
            humanAnswerAt = -1L
            reveal = false
            locked = emptySet()
            botAnswers.clear()
            banner = "Trả lời đúng càng nhanh, điểm càng cao"
            show(q)
            coroutineScope {
                val start = SystemClock.elapsedRealtime()
                questionStart = start
                val others = players.count { it.id != 0 }
                val clock = timer(15_000L) { humanChoice != null && botAnswers.size >= others }
                val key = "2-$qNo"
                if (remotes().isNotEmpty()) launch {
                    while (timeLeftMs > 0L) {
                        for (rp in remotes()) {
                            val inp = remoteIn[rp.uid] ?: continue
                            if (inp.k == key && rp.id !in botAnswers) {
                                botAnswers[rp.id] = inp.c to inp.t
                                locked = locked + rp.id
                                fx.play("lock")
                            }
                        }
                        delay(60)
                    }
                }
                players.filter { it.isBot }.forEach { bot ->
                    if (ranked && rnd.nextFloat() < 0.05f) return@forEach   // thỉnh thoảng không kịp trả lời
                    var at = botAnswerTime(2, bot.id)
                    var acc = accuracy(q.level, bot.id, q.field)
                    if (ranked && rnd.nextFloat() < 0.07f) { at = rnd.nextLong(900L, 1_900L); acc *= 0.6f }  // bấm vội
                    val ok = rnd.nextFloat() < acc
                    val ch = if (ok) q.answer else wrongChoice(q)
                    launch {
                        delay(at)
                        if (timeLeftMs > 0L) {
                            botAnswers[bot.id] = ch to (SystemClock.elapsedRealtime() - start)
                            locked = locked + bot.id
                            fx.play("lock")
                        }
                    }
                }
                clock.join()
                coroutineContext.cancelChildren()
            }
            reveal = true
            fx.stopVoice()
            result(q, humanChoice == q.answer)
            val answers = ArrayList<Triple<Int, Int, Long>>()
            humanChoice?.let { answers.add(Triple(0, it, humanAnswerAt)) }
            botAnswers.forEach { (id, a) -> answers.add(Triple(id, a.first, a.second)) }
            val correct = answers.filter { it.second == q.answer }.sortedBy { it.third }
            val msgs = ArrayList<String>()
            if (solo) {
                correct.firstOrNull()?.let {
                    val pts = when {
                        it.third <= 5_000L -> 30
                        it.third <= 10_000L -> 20
                        else -> 10
                    }
                    addScore(0, pts)
                    msgs.add("Bạn +$pts")
                }
            } else {
                val pts = listOf(30, 20, 10)
                correct.take(3).forEachIndexed { k, a ->
                    addScore(a.first, pts[k])
                    msgs.add("${nameOf(a.first)} +${pts[k]}")
                }
            }
            fx.play(if (humanChoice == q.answer) "correct" else "wrong")
            banner = if (msgs.isEmpty()) "Không ai trả lời đúng" else msgs.joinToString("   ·   ")
            delay(2600)
        }
        question = null
        reveal = false
        flush()
    }

    /** Phần 3 – Về Đích: xoay vòng 5 vòng, 10 giây/câu, người khác được giành quyền. */
    private suspend fun part3() {
        val rounds = 5
        val order = players.map { it.id }
        val qs = pick(rounds * order.size, listOf(3))
        qTotal = qs.size
        var k = 0
        for (r in 1..rounds) {
            for (pid in order) {
                if (k >= qs.size) break
                val q = qs[k]
                k++
                qNo = k
                roundText = "Vòng $r/$rounds"
                turnId = pid
                humanChoice = null
                reveal = false
                locked = emptySet()
                steals.clear()
                mainChoice = null
                mainDone = false
                val isBotTurn = players.firstOrNull { it.id == pid }?.isBot == true
                val turnUid = players.firstOrNull { it.id == pid }?.uid?.takeIf { pid != 0 }
                // Ngôi sao hy vọng đã đặt từ lượt trước
                starId = if (pendingStar.remove(pid)) pid else null
                if (pid == 0) {
                    myNextStar = false
                    canStarNext = r < rounds
                }
                val star = starId == pid
                banner = when {
                    star -> "⭐ ${if (pid == 0) "Bạn" else nameOf(pid)} có ngôi sao hy vọng: đúng +20 · sai −20"
                    pid == 0 -> if (solo) "Lượt của bạn" else "Lượt của bạn · người khác có thể giành quyền"
                    else -> "Lượt của ${nameOf(pid)} · bạn có thể chọn trước để giành quyền"
                }
                show(q)
                coroutineScope {
                    val start = SystemClock.elapsedRealtime()
                    questionStart = start
                    val clock = timer(10_000L) { mainDone }
                    if (isBotTurn && !(ranked && rnd.nextFloat() < 0.04f)) {
                        val at = botAnswerTime(3, pid)
                        val ok = rnd.nextFloat() < accuracy(q.level, pid, q.field)
                        val ch = if (ok) q.answer else wrongChoice(q)
                        launch {
                            delay(at)
                            if (timeLeftMs > 0L && !mainDone) {
                                mainChoice = ch
                                mainDone = true
                            }
                        }
                    }
                    val key = "3-$qNo"
                    if (remotes().isNotEmpty()) launch {
                        val stolen = HashSet<Int>()
                        while (timeLeftMs > 0L && !mainDone) {
                            for (rp in remotes()) {
                                val inp = remoteIn[rp.uid] ?: continue
                                if (inp.k != key) continue
                                if (rp.id == pid) {
                                    mainChoice = inp.c
                                    mainDone = true
                                } else if (rp.id !in stolen) {
                                    stolen.add(rp.id)
                                    steals.add(Triple(rp.id, inp.c, inp.t))
                                    locked = locked + rp.id
                                    fx.play("lock")
                                }
                            }
                            delay(60)
                        }
                    }
                    players.filter { it.isBot && it.id != pid }.forEach { bot ->
                        if (rnd.nextFloat() < stealChance(bot.id)) {
                            val at = 1_500L + rnd.nextLong(6_500L)
                            val ok = rnd.nextFloat() < accuracy(q.level, bot.id, q.field)
                            val ch = if (ok) q.answer else wrongChoice(q)
                            launch {
                                delay(at)
                                if (timeLeftMs > 0L && !mainDone) {
                                    steals.add(Triple(bot.id, ch, SystemClock.elapsedRealtime() - start))
                                    locked = locked + bot.id
                                    fx.play("lock")
                                }
                            }
                        }
                    }
                    clock.join()
                    coroutineContext.cancelChildren()
                }
                reveal = true
                fx.stopVoice()
                // Chốt ngôi sao cho câu sau
                if (pid == 0) {
                    if (myNextStar) pendingStar.add(0)
                    canStarNext = false
                } else if (turnUid != null) {
                    if (r < rounds && remoteIn[turnUid]?.star == "3-$qNo") pendingStar.add(pid)
                } else if (r < rounds) {
                    val risk = personas[pid]?.risk ?: 0.5f
                    val chance = 0.10f + risk * 0.25f + (if (r >= rounds - 2) 0.15f else 0f)
                    if (rnd.nextFloat() < chance) pendingStar.add(pid)
                }
                val hc = humanChoice
                if (pid == 0) result(q, hc == q.answer) else if (hc != null) result(q, hc == q.answer)
                val msgs = ArrayList<String>()
                val mc = mainChoice
                var someoneRight = false
                val pts = if (star) 20 else 10
                if (mc != null && mc == q.answer) {
                    addScore(pid, pts)
                    msgs.add("${nameOf(pid)} đúng +$pts" + if (star) " ⭐" else "")
                    someoneRight = true
                } else {
                    if (mc != null) {
                        addScore(pid, -pts)
                        msgs.add("${nameOf(pid)} sai −$pts")
                    } else if (star) {
                        addScore(pid, -pts)
                        msgs.add("${nameOf(pid)} hết giờ −$pts")
                    } else {
                        msgs.add("${nameOf(pid)} hết giờ")
                    }
                    for (s in steals.sortedBy { it.third }) {
                        if (s.second == q.answer) {
                            addScore(s.first, 10)
                            msgs.add("${nameOf(s.first)} giành quyền đúng +10")
                            someoneRight = true
                            break
                        } else {
                            addScore(s.first, -10)
                            msgs.add("${nameOf(s.first)} giành quyền sai −10")
                        }
                    }
                }
                fx.play(if (someoneRight) "correct" else "wrong")
                banner = msgs.joinToString("   ·   ")
                delay(2800)
            }
        }
        question = null
        reveal = false
        turnId = null
    }

    private fun finish() {
        flush()
        val max = players.maxOf { it.score }
        winners = players.filter { it.score == max }.map { it.id }.toSet()
        prefs.played = prefs.played + 1
        if (online && !rated) {
            val entries = players.map { Triple(rateKey(it), it.rating, it.score) }
            val d = prefs.ladder.applyMatch(entries, reduced = gainFactor() < 1f)
            for (i in players.indices) players[i] = players[i].copy(ratingDelta = d[i])
            rated = true
        }
        val me = players.first { it.id == 0 }
        if (solo) {
            if (me.score > prefs.bestSolo) prefs.bestSolo = me.score
            fx.play("win")
            fx.say("Xin chúc mừng! Bạn đã hoàn thành phần thi với ${me.score} điểm")
        } else if (0 in winners) {
            prefs.wins = prefs.wins + 1
            fx.play("win")
            fx.say("Xin chúc mừng ${me.name}, bạn đã chiến thắng!")
        } else {
            fx.play("lose")
            fx.say("Chúc bạn may mắn lần sau!")
        }
        phase = Phase.END
        Net.setInMatch(false)
        Net.pushProfile()
        netRoom?.let { if (isHost) Rooms.setState(it, "done") }
    }

    // ------------------------------------------------------------------ Trực tuyến: chủ phòng

    /** Chủ phòng: nghe câu trả lời của người khác và phát trạng thái trận lên máy chủ. */
    private fun hostNet() {
        val rid = netRoom ?: return
        unsubs += Rooms.listen("input/$rid") { snap ->
            for (c in snap.children) {
                val uid = c.key ?: continue
                remoteIn[uid] = RemoteIn(
                    c.child("k").getValue(String::class.java),
                    c.child("c").getValue(Long::class.java)?.toInt() ?: -1,
                    c.child("t").getValue(Long::class.java) ?: 0L,
                    c.child("star").getValue(String::class.java),
                    c.child("p1").getValue(Long::class.java)?.toInt() ?: 0,
                )
            }
        }
        viewModelScope.launch {
            snapshotFlow { buildLive() }.distinctUntilChanged().collect { Rooms.publish(rid, it) }
        }
    }

    private fun buildLive(): Map<String, Any?> {
        val q = question
        val m = HashMap<String, Any?>()
        m["part"] = part
        m["phase"] = phase.name
        m["intro"] = introLeft
        m["qNo"] = qNo
        m["qTotal"] = qTotal
        m["deadline"] = deadlineServer
        m["total"] = timeTotalMs
        m["reveal"] = reveal
        m["banner"] = banner
        m["round"] = roundText
        m["turn"] = turnId ?: -1
        m["star"] = starId ?: -1
        m["locked"] = locked.joinToString(",")
        m["winners"] = winners.joinToString(",")
        if (q != null && part >= 2) {
            m["q"] = mapOf(
                "id" to q.id, "f" to q.field, "l" to q.level, "t" to q.text,
                "o" to q.options, "a" to if (reveal) q.answer else -1,
            )
        }
        m["players"] = players.map {
            mapOf(
                "id" to it.id, "uid" to (if (it.id == 0) Net.uid else it.uid), "name" to it.name,
                "avatar" to it.avatar, "palette" to it.palette, "rating" to it.rating,
                "score" to it.score, "delta" to (it.ratingDelta ?: -999), "persona" to it.personaId,
            )
        }
        return m
    }

    // ------------------------------------------------------------------ Trực tuyến: ghép trận

    /** Tìm người thật trong hàng chờ. true nếu đã ghép được (làm chủ hoặc vào phòng người khác). */
    private suspend fun netSearch(): Boolean {
        val myUid = Net.uid ?: return false
        val myRating = prefs.ladder.rating
        Rooms.enqueue(prefs.name.ifBlank { "Bạn" }, prefs.avatar, myRating)
        data class Q(val uid: String, val name: String, val avatar: Int, val rating: Int, val at: Long, val room: String?)
        var queue: List<Q> = emptyList()
        val stopListen = Rooms.listen("queue") { snap ->
            queue = snap.children.mapNotNull { c ->
                Q(
                    c.key ?: return@mapNotNull null,
                    c.child("name").getValue(String::class.java) ?: "Người chơi",
                    c.child("avatar").getValue(Long::class.java)?.toInt() ?: -1,
                    c.child("rating").getValue(Long::class.java)?.toInt() ?: 100,
                    c.child("at").getValue(Long::class.java) ?: 0L,
                    c.child("room").getValue(String::class.java),
                )
            }
        }
        val limit = if (Net.fillEnabled) 12_000L else 90_000L
        val t0 = SystemClock.elapsedRealtime()
        try {
            while (true) {
                val el = SystemClock.elapsedRealtime() - t0
                searchSec = (el / 1000).toInt()
                if (el > limit) break
                val mine = queue.firstOrNull { it.uid == myUid }
                // Đã được chủ phòng khác giữ chỗ
                if (mine?.room != null) {
                    stopListen()
                    Rooms.dequeue()
                    guest = true
                    isHost = false
                    runGuest(mine.room, alreadyMember = true)
                    return true
                }
                val range = if (el < 6_000L) 50 else 150
                val now = Net.serverNow()
                val cands = queue.filter {
                    it.room == null && now - it.at < 30_000L && kotlin.math.abs(it.rating - myRating) <= range
                }
                if (cands.size >= 2 && cands.any { it.uid == myUid }) {
                    val hostQ = cands.minWith(compareBy<Q>({ it.at }, { it.uid }))
                    if (hostQ.uid == myUid) {
                        val rid = Rooms.create("ranked", prefs.name.ifBlank { "Bạn" }, prefs.avatar, myRating)
                        if (rid != null) {
                            val got = ArrayList<Q>()
                            for (o in cands.filter { it.uid != myUid }.take(3)) {
                                if (Rooms.claim(o.uid, rid)) got.add(o)
                            }
                            if (got.isNotEmpty()) {
                                for ((i, o) in got.withIndex()) {
                                    Net.ref("rooms/$rid/players/${o.uid}").setValue(Rooms.me(o.name, o.avatar, o.rating))
                                    players.add(PlayerState(i + 1, o.name, isBot = false, palette = i + 1, rating = o.rating, avatar = o.avatar, uid = o.uid))
                                    fx.play("lock")
                                }
                                Rooms.setState(rid, "playing")
                                netRoom = rid
                                stopListen()
                                Rooms.dequeue()
                                delay(1_200)
                                return true
                            } else {
                                Rooms.cleanup(rid)
                            }
                        }
                    }
                }
                delay(700)
            }
        } finally {
            stopListen()
        }
        Rooms.dequeue()
        return false
    }

    // ------------------------------------------------------------------ Trực tuyến: người tham gia

    /** Máy người tham gia: hiện phòng chờ, rồi theo dõi trạng thái trận do chủ phòng phát. */
    private suspend fun runGuest(rid: String, alreadyMember: Boolean) = coroutineScope {
        netRoom = rid
        roomCode = rid
        isHost = false
        guest = true
        ranked = false
        phase = Phase.LOBBY
        val myUid = Net.uid
        if (!alreadyMember) {
            val err = Rooms.join(rid, prefs.name.ifBlank { "Bạn" }, prefs.avatar, prefs.ladder.rating)
            if (err != null) {
                netError = err
                return@coroutineScope
            }
        }
        Net.setInMatch(true)
        var started = false
        var lastLive = SystemClock.elapsedRealtime()
        unsubs += Rooms.listen("rooms/$rid") { snap ->
            if (!snap.exists()) {
                if (!started) netError = "Chủ phòng đã đóng phòng"
                return@listen
            }
            if (snap.child("kind").getValue(String::class.java) == "ranked") ranked = true
            if (!started) {
                val ms = Rooms.members(snap)
                val hostUid = snap.child("host").getValue(String::class.java)
                val sorted = ms.sortedBy { if (it.uid == myUid) 0 else if (it.uid == hostUid) 1 else 2 }
                players.clear()
                sorted.forEachIndexed { i, m ->
                    players.add(PlayerState(i, m.name, isBot = false, palette = i, rating = m.rating, avatar = m.avatar, uid = m.uid))
                }
            }
            if (snap.child("state").getValue(String::class.java) == "playing") started = true
        }
        unsubs += Rooms.listen("live/$rid") { snap ->
            if (!snap.exists()) return@listen
            lastLive = SystemClock.elapsedRealtime()
            matchStarted = true
            applyLive(snap)
        }
        // Đồng hồ phía người tham gia
        launch {
            var lastSec = -1
            while (true) {
                delay(50)
                if (phase == Phase.PLAY && deadlineServer > 0L && !reveal) {
                    timeLeftMs = (deadlineServer - Net.serverNow()).coerceIn(0L, timeTotalMs)
                    val sec = ((timeLeftMs + 999) / 1000).toInt()
                    if (sec != lastSec) {
                        lastSec = sec
                        if (sec in 1..3) fx.play("tick_hi") else if (sec > 0) fx.play("tick")
                    }
                } else if (reveal) {
                    timeLeftMs = 0L
                }
                // Mất kết nối với chủ phòng
                if (matchStarted && phase != Phase.END && SystemClock.elapsedRealtime() - lastLive > 20_000L) {
                    banner = "Mất kết nối với chủ phòng"
                    winners = emptySet()
                    phase = Phase.END
                }
            }
        }
        awaitCancellation()
    }

    private var guestKey = ""
    private var guestTurnKey = ""
    private var guestP1: Job? = null
    private var guestP1Score = 0
    private var guestRated = false

    private fun applyLive(s: com.google.firebase.database.DataSnapshot) {
        val myUid = Net.uid
        val list = s.child("players").children.map { c ->
            PlayerState(
                id = c.child("id").getValue(Long::class.java)?.toInt() ?: 0,
                name = c.child("name").getValue(String::class.java) ?: "?",
                isBot = c.child("persona").getValue(String::class.java) != null,
                palette = c.child("palette").getValue(Long::class.java)?.toInt() ?: 0,
                score = c.child("score").getValue(Long::class.java)?.toInt() ?: 0,
                rating = c.child("rating").getValue(Long::class.java)?.toInt() ?: 0,
                ratingDelta = c.child("delta").getValue(Long::class.java)?.toInt()?.takeIf { it != -999 },
                avatar = c.child("avatar").getValue(Long::class.java)?.toInt() ?: -1,
                uid = c.child("uid").getValue(String::class.java),
            )
        }
        val myHostId = list.firstOrNull { it.uid == myUid }?.id ?: return
        fun map(id: Int) = when (id) { myHostId -> 0; 0 -> myHostId; else -> id }
        val newPart = s.child("part").getValue(Long::class.java)?.toInt() ?: 0
        val newPhase = runCatching { Phase.valueOf(s.child("phase").getValue(String::class.java) ?: "INTRO") }.getOrDefault(Phase.INTRO)
        if (newPart != part && newPhase == Phase.INTRO) {
            fx.play("chime")
            fx.say("Chào mừng đến với phần thi ${PART_NAMES.getOrElse(newPart) { "" }}")
        }
        part = newPart
        introLeft = s.child("intro").getValue(Long::class.java)?.toInt() ?: 0
        qTotal = s.child("qTotal").getValue(Long::class.java)?.toInt() ?: 0
        deadlineServer = s.child("deadline").getValue(Long::class.java) ?: 0L
        timeTotalMs = (s.child("total").getValue(Long::class.java) ?: 1L).coerceAtLeast(1L)
        val newReveal = s.child("reveal").getValue(Boolean::class.java) == true
        banner = (s.child("banner").getValue(String::class.java) ?: "").replace("Bạn", "Chủ phòng")
        roundText = s.child("round").getValue(String::class.java) ?: ""
        turnId = s.child("turn").getValue(Long::class.java)?.toInt()?.takeIf { it >= 0 }?.let(::map)
        starId = s.child("star").getValue(Long::class.java)?.toInt()?.takeIf { it >= 0 }?.let(::map)
        locked = (s.child("locked").getValue(String::class.java) ?: "").split(",").mapNotNull { it.toIntOrNull()?.let(::map) }.toSet() +
            (if (humanChoice != null && part >= 2) setOf(0) else emptySet())

        // Danh sách người chơi theo góc nhìn của máy này (mình luôn là số 0)
        val mapped = list.map { it.copy(id = map(it.id)) }.sortedBy { it.id }.map {
            if (it.id == 0 && part == 1 && guestP1Score > it.score) it.copy(score = guestP1Score) else it
        }
        players.clear()
        players.addAll(mapped)

        // Phần 1: mỗi người tự trả lời câu của mình trên máy mình
        if (part == 1 && newPhase == Phase.PLAY && guestP1 == null && deadlineServer > 0L) {
            guestP1 = viewModelScope.launch { guestPart1() }
        }
        if (part >= 2) {
            val qs = s.child("q")
            val key = "$part-${s.child("qNo").getValue(Long::class.java) ?: 0}"
            if (qs.exists()) {
                val opts = qs.child("o").children.map { it.getValue(String::class.java) ?: "" }
                val ans = qs.child("a").getValue(Long::class.java)?.toInt() ?: -1
                val qq = Question(
                    qs.child("id").getValue(String::class.java) ?: "",
                    qs.child("f").getValue(String::class.java) ?: "",
                    qs.child("l").getValue(Long::class.java)?.toInt() ?: 1,
                    qs.child("t").getValue(String::class.java) ?: "",
                    if (opts.size == 4) opts else listOf("", "", "", ""),
                    ans,
                )
                if (key != guestKey) {
                    guestKey = key
                    humanChoice = null
                    qNo = s.child("qNo").getValue(Long::class.java)?.toInt() ?: 0
                    show(qq)
                } else {
                    question = qq
                }
                if (newReveal && !reveal) {
                    fx.stopVoice()
                    val hc = humanChoice
                    if (hc != null || turnId == 0) {
                        result(qq, hc == ans)
                        fx.play(if (hc == ans) "correct" else "wrong")
                    }
                }
            } else {
                question = null
            }
            // Ngôi sao cho câu sau của mình
            val rNow = roundText.substringAfter("Vòng ").substringBefore("/").toIntOrNull() ?: 0
            val rMax = roundText.substringAfter("/").toIntOrNull() ?: 5
            val myTurnNow = part == 3 && turnId == 0 && !newReveal
            if (myTurnNow && key != guestTurnKey) {
                guestTurnKey = key
                myNextStar = false
                canStarNext = rNow < rMax
            } else if (!myTurnNow && canStarNext) {
                canStarNext = false
            }
            reveal = newReveal
        }

        if (newPhase == Phase.END && phase != Phase.END) {
            winners = (s.child("winners").getValue(String::class.java) ?: "").split(",").mapNotNull { it.toIntOrNull()?.let(::map) }.toSet()
            flush()
            if (!guestRated) {
                guestRated = true
                rated = true
                val d = players.firstOrNull { it.id == 0 }?.ratingDelta ?: 0
                if (d > 0) prefs.ladder.addMine(d)
                prefs.played = prefs.played + 1
                if (0 in winners) prefs.wins = prefs.wins + 1
                Net.setInMatch(false)
                Net.pushProfile()
            }
            if (0 in winners) { fx.play("win"); fx.say("Xin chúc mừng, bạn đã chiến thắng!") } else { fx.play("lose"); fx.say("Chúc bạn may mắn lần sau!") }
        }
        phase = if (newPhase == Phase.REVEAL) Phase.PLAY else newPhase
    }

    /** Phần 1 trên máy người tham gia: câu hỏi riêng, gửi điểm lên cho chủ phòng. */
    private suspend fun guestPart1() {
        val rid = netRoom ?: return
        val qs = pick(60, listOf(1, 2))
        var i = 0
        while (Net.serverNow() < deadlineServer && i < qs.size && part == 1) {
            val q = qs[i]
            show(q)
            qNo = i + 1
            humanChoice = null
            reveal = false
            skipFlag = false
            while (Net.serverNow() < deadlineServer && humanChoice == null && !skipFlag && part == 1) delay(40)
            val c = humanChoice
            if (skipFlag && c == null) result(q, false)
            if (c != null) {
                result(q, c == q.answer)
                reveal = true
                if (c == q.answer) {
                    guestP1Score += 10
                    addScore(0, 10)
                    Rooms.answer(rid, mapOf("p1" to guestP1Score))
                    fx.play("correct")
                } else {
                    fx.play("wrong")
                }
                delay(500)
                reveal = false
            }
            i++
        }
        Rooms.answer(rid, mapOf("p1" to guestP1Score))
        question = null
        flush()
    }

    // ------------------------------------------------------------------ Tiện ích

    /** Hiện câu hỏi mới: ghi nhận đã gặp và đọc to câu hỏi + đáp án. */
    private fun show(q: Question) {
        question = q
        shownNow.add(q.id)
        if (prefs.readQuestions) {
            val o = q.options
            fx.say("${q.text}. A: ${o[0]}. B: ${o[1]}. C: ${o[2]}. D: ${o[3]}.")
        }
    }

    private fun result(q: Question, ok: Boolean) {
        resultsNow[q.id] = ok
    }

    private fun flush() {
        if (!::prefs.isInitialized) return
        prefs.savePractice(shownNow, resultsNow)
        shownNow.clear()
        resultsNow.clear()
    }

    private fun addScore(id: Int, delta: Int) {
        val i = players.indexOfFirst { it.id == id }
        if (i >= 0) players[i] = players[i].copy(score = players[i].score + delta)
    }

    fun nameOf(id: Int): String = if (id == 0) "Bạn" else players.firstOrNull { it.id == id }?.name ?: "?"

    private fun accuracy(level: Int, botId: Int, field: String): Float {
        val p = personas[botId]
        if (p != null) {
            var a = 0.34f + p.skill * 0.56f - 0.07f * (level - 2)
            if (field in p.strong) a += 0.12f
            if (field in p.weak) a -= 0.12f
            a = a.coerceIn(0.12f, 0.96f)
            if (online) {
                // Cố gắng không vượt người thật: càng sát hoặc vượt thì càng hay sai
                val me = players.firstOrNull { it.id == 0 }?.score ?: 0
                val mine = players.firstOrNull { it.id == botId }?.score ?: 0
                val gap = me - mine
                a *= when {
                    gap <= 0 -> 0.3f
                    gap < 15 -> 0.65f
                    gap < 30 -> 0.85f
                    else -> 1f
                }
            }
            return a
        }
        val base = when (cfg.level) {
            0 -> 0.50f
            1 -> 0.68f
            else -> 0.84f
        }
        return (base - 0.08f * (level - 2)).coerceIn(0.2f, 0.95f)
    }

    private fun botThinkPart1(botId: Int): Long = personas[botId]?.let {
        val lo = (2_000 + (1f - it.skill) * 4_500).toLong()
        rnd.nextLong(lo, lo + 3_500L)
    } ?: when (cfg.level) {
        0 -> rnd.nextLong(4_500L, 9_000L)
        1 -> rnd.nextLong(3_200L, 6_500L)
        else -> rnd.nextLong(2_200L, 4_500L)
    }

    private fun botAnswerTime(p: Int, botId: Int): Long = personas[botId]?.let {
        val lo = (1_600 + (1f - it.skill) * (if (p == 2) 6_000 else 4_500)).toLong()
        rnd.nextLong(lo, lo + if (p == 2) 5_500L else 3_500L).coerceAtMost(if (p == 2) 14_500L else 9_700L)
    } ?: if (p == 2) {
        when (cfg.level) {
            0 -> rnd.nextLong(5_000L, 13_000L)
            1 -> rnd.nextLong(3_000L, 10_000L)
            else -> rnd.nextLong(2_000L, 7_000L)
        }
    } else {
        when (cfg.level) {
            0 -> rnd.nextLong(4_000L, 9_800L)
            1 -> rnd.nextLong(3_000L, 8_000L)
            else -> rnd.nextLong(1_800L, 6_000L)
        }
    }

    private fun stealChance(botId: Int): Float = personas[botId]?.let { 0.12f + it.risk * 0.38f } ?: when (cfg.level) {
        0 -> 0.20f
        1 -> 0.30f
        else -> 0.40f
    }

    private fun wrongChoice(q: Question): Int = (0..3).filter { it != q.answer }.random(rnd)
}
