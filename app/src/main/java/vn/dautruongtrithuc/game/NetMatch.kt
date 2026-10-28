package vn.dautruongtrithuc.game

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.MutableData
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.tasks.await
import vn.dautruongtrithuc.data.Net

/**
 * Giao thức trận trực tuyến trên Realtime Database:
 *  rooms/{rid}:  host, kind, state (lobby|playing|done), players/{uid}: {name, avatar, rating}
 *  live/{rid}:   ảnh chụp trạng thái trận do chủ phòng ghi
 *  input/{rid}/{uid}: câu trả lời của từng người (k = mã câu, c = đáp án, t = ms, star = mã câu đặt sao, p1 = điểm phần 1)
 *  queue/{uid}:  hàng chờ thi ngẫu nhiên
 */
object Rooms {

    data class Member(val uid: String, val name: String, val avatar: Int, val rating: Int)

    fun me(name: String, avatar: Int, rating: Int) = mapOf("name" to name, "avatar" to avatar, "rating" to rating, "at" to ServerValue.TIMESTAMP)

    suspend fun create(kind: String, name: String, avatar: Int, rating: Int, extra: List<Member> = emptyList()): String? {
        val uid = Net.uid ?: return null
        val r = Net.ref("rooms").push()
        val players = HashMap<String, Any>()
        players[uid] = me(name, avatar, rating)
        for (m in extra) players[m.uid] = me(m.name, m.avatar, m.rating)
        return try {
            r.setValue(mapOf("host" to uid, "kind" to kind, "state" to "lobby", "at" to ServerValue.TIMESTAMP, "players" to players)).await()
            r.child("players/$uid").onDisconnect().removeValue()
            r.key
        } catch (e: Exception) { null }
    }

    /** Vào phòng của người khác. Trả về null nếu thành công, ngược lại là lý do. */
    suspend fun join(rid: String, name: String, avatar: Int, rating: Int): String? {
        val uid = Net.uid ?: return "Cần đăng nhập Google để vào phòng"
        return try {
            val s = Net.ref("rooms/$rid").get().await()
            if (!s.exists()) return "Không tìm thấy phòng"
            val state = s.child("state").getValue(String::class.java)
            if (state != "lobby" && !s.child("players/$uid").exists()) return "Phòng đã bắt đầu thi"
            if (s.child("players").childrenCount >= 4 && !s.child("players/$uid").exists()) return "Phòng đã đủ 4 người"
            Net.ref("rooms/$rid/players/$uid").setValue(me(name, avatar, rating)).await()
            Net.ref("rooms/$rid/players/$uid").onDisconnect().removeValue()
            null
        } catch (e: Exception) { e.message ?: "Không vào được phòng" }
    }

    fun leave(rid: String) {
        val uid = Net.uid ?: return
        Net.ref("rooms/$rid/players/$uid").removeValue()
    }

    fun setState(rid: String, state: String) {
        Net.ref("rooms/$rid/state").setValue(state)
    }

    fun members(s: DataSnapshot): List<Member> = s.child("players").children.mapNotNull { c ->
        Member(
            c.key ?: return@mapNotNull null,
            c.child("name").getValue(String::class.java) ?: "Người chơi",
            c.child("avatar").getValue(Long::class.java)?.toInt() ?: -1,
            c.child("rating").getValue(Long::class.java)?.toInt() ?: 100,
        )
    }

    // ------------------------------------------------------------------ Hàng chờ thi ngẫu nhiên

    fun enqueue(name: String, avatar: Int, rating: Int) {
        val uid = Net.uid ?: return
        val r = Net.ref("queue/$uid")
        r.setValue(mapOf("name" to name, "avatar" to avatar, "rating" to rating, "at" to ServerValue.TIMESTAMP))
        r.onDisconnect().removeValue()
    }

    fun dequeue() {
        val uid = Net.uid ?: return
        Net.ref("queue/$uid").removeValue()
    }

    /** Giữ chỗ một người trong hàng chờ cho phòng [rid]; true nếu thành công. */
    suspend fun claim(otherUid: String, rid: String): Boolean {
        val done = CompletableDeferred<Boolean>()
        Net.ref("queue/$otherUid").runTransaction(object : Transaction.Handler {
            override fun doTransaction(d: MutableData): Transaction.Result {
                if (d.value == null) return Transaction.abort()
                if (d.child("room").value != null) return Transaction.abort()
                d.child("room").value = rid
                return Transaction.success(d)
            }
            override fun onComplete(e: DatabaseError?, committed: Boolean, s: DataSnapshot?) { done.complete(committed) }
        })
        return done.await()
    }

    // ------------------------------------------------------------------ Trận

    fun publish(rid: String, live: Map<String, Any?>) {
        Net.ref("live/$rid").setValue(live)
    }

    fun answer(rid: String, data: Map<String, Any>) {
        val uid = Net.uid ?: return
        Net.ref("input/$rid/$uid").updateChildren(data)
    }

    fun listen(path: String, onData: (DataSnapshot) -> Unit): () -> Unit {
        val r = Net.ref(path)
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) = onData(s)
            override fun onCancelled(e: DatabaseError) {}
        }
        r.addValueEventListener(l)
        return { r.removeEventListener(l) }
    }

    fun cleanup(rid: String) {
        Net.ref("live/$rid").removeValue()
        Net.ref("input/$rid").removeValue()
        Net.ref("rooms/$rid").removeValue()
    }
}
