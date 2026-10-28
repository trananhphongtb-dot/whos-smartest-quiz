package vn.dautruongtrithuc.data

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import vn.dautruongtrithuc.R

/** Hồ sơ người chơi thật trên máy chủ. */
data class NetUser(
    val uid: String,
    val name: String,
    val avatar: Int,
    val rating: Int,
    val online: Boolean = false,
    val inMatch: Boolean = false,
)

/** Lời mời / thông báo gửi đến người chơi. */
data class InboxMsg(val key: String, val type: String, val from: String, val name: String, val room: String?)

/**
 * Lớp kết nối Firebase: đăng nhập Google, hồ sơ, trạng thái online, bạn bè, hộp thư, bảng xếp hạng.
 * Mọi hàm đều an toàn khi chưa đăng nhập (không làm gì).
 */
object Net {
    private const val TAG = "Net"

    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    val db: FirebaseDatabase by lazy {
        FirebaseDatabase.getInstance().apply { try { setPersistenceEnabled(false) } catch (_: Exception) {} }
    }
    fun ref(path: String): DatabaseReference = db.getReference(path)

    var uid by mutableStateOf<String?>(null)
        private set
    val signedIn get() = uid != null

    /** Công tắc từ xa: false thì không lấp người chơi ảo (đặt ở config/fill trên Firebase). */
    var fillEnabled by mutableStateOf(true)
        private set

    /** Bạn bè là người thật: uid → hồ sơ. */
    val friends = mutableStateMapOf<String, NetUser>()
    val inbox = mutableStateListOf<InboxMsg>()

    private var serverOffset = 0L
    fun serverNow(): Long = System.currentTimeMillis() + serverOffset

    private var prefs: Prefs? = null
    private val listeners = ArrayList<Pair<DatabaseReference, Any>>()

    fun init(context: Context, p: Prefs) {
        prefs = p
        Photos.init(p)
        try {
            ref(".info/serverTimeOffset").addValueEventListener(simple { serverOffset = it.getValue(Long::class.java) ?: 0L })
            ref("config/fill").addValueEventListener(simple { fillEnabled = it.getValue(Boolean::class.java) ?: true })
            auth.currentUser?.let {
                onSignedIn(it.uid)
                if (p.photoMode == "google" && p.photo.isBlank()) {
                    scope.launch { Photos.fromGoogle()?.let { b -> Photos.setMine(p, b, "google") } }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "init", e)
        }
    }

    // ------------------------------------------------------------------ Đăng nhập

    suspend fun signInWithGoogle(activity: Activity): String? {
        return try {
            val option = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(activity.getString(R.string.default_web_client_id))
                .build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val result = CredentialManager.create(activity).getCredential(activity, request)
            val cred = result.credential
            if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val g = GoogleIdTokenCredential.createFrom(cred.data)
                val res = auth.signInWithCredential(GoogleAuthProvider.getCredential(g.idToken, null)).await()
                val user = res.user ?: return "Đăng nhập không thành công"
                val p = prefs
                if (p != null && (p.name.isBlank() || p.name == "Bạn")) {
                    g.displayName?.takeIf { it.isNotBlank() }?.let { p.name = it.trim().take(15) }
                }
                justSignedIn = true
                // Lấy ảnh Google ở phạm vi riêng: màn đăng nhập đóng lại cũng không bị ngắt
                if (p != null && p.photoMode == "google" && p.photo.isBlank()) {
                    scope.launch { Photos.fromGoogle()?.let { Photos.setMine(p, it, "google") } }
                }
                onSignedIn(user.uid)
                null
            } else {
                "Không nhận được tài khoản Google"
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: androidx.credentials.exceptions.GetCredentialCancellationException) {
            "Bạn đã hủy đăng nhập"
        } catch (e: androidx.credentials.exceptions.NoCredentialException) {
            "Máy chưa có tài khoản Google. Hãy thêm tài khoản Google trong Cài đặt của điện thoại."
        } catch (e: Exception) {
            Log.w(TAG, "signIn", e)
            "Đăng nhập không thành công, kiểm tra mạng rồi thử lại"
        }
    }

    /** Vừa đăng nhập xong: sảnh mở thẳng tab Tôi để xem hồ sơ. */
    var justSignedIn by mutableStateOf(false)

    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)

    fun signOut() {
        setOnline(false)
        detachAll()
        auth.signOut()
        uid = null
        friends.clear()
        inbox.clear()
    }

    private fun onSignedIn(id: String) {
        uid = id
        val p = prefs ?: return
        // Đồng bộ hồ sơ: lấy điểm cao hơn giữa máy và máy chủ
        ref("users/$id").get().addOnSuccessListener { s ->
            val remote = s.child("rating").getValue(Long::class.java)?.toInt()
            if (remote != null && remote > p.ladder.rating) p.ladder.rating = remote
            val w = s.child("wins").getValue(Long::class.java)?.toInt() ?: 0
            val pl = s.child("played").getValue(Long::class.java)?.toInt() ?: 0
            if (w > p.wins) p.wins = w
            if (pl > p.played) p.played = pl
            pushProfile()
        }
        startPresence(id)
        listenFriends(id)
        listenInbox(id)
    }

    /** Ghi hồ sơ hiện tại lên máy chủ (tên, ảnh, điểm, điểm theo kỳ). */
    fun pushProfile() {
        val id = uid ?: return
        val p = prefs ?: return
        val l = p.ladder
        val m = hashMapOf<String, Any>(
            "name" to p.name.ifBlank { "Người chơi" },
            "avatar" to p.avatar,
            "photo" to p.photo,
            "rating" to l.rating,
            "wins" to p.wins,
            "played" to p.played,
            "at" to ServerValue.TIMESTAMP,
        )
        for (k in 1..3) m["pp/${l.periodKey(k)}"] = l.myPeriodPoints(k)
        ref("users/$id").updateChildren(m)
    }

    // ------------------------------------------------------------------ Online

    private fun startPresence(id: String) {
        val status = ref("status/$id")
        ref(".info/connected").addValueEventListener(simple { s ->
            if (s.getValue(Boolean::class.java) == true) {
                status.onDisconnect().updateChildren(mapOf("online" to false, "inMatch" to false, "at" to ServerValue.TIMESTAMP))
                setOnline(true)
            }
        })
    }

    fun setOnline(on: Boolean) {
        val id = uid ?: return
        ref("status/$id").updateChildren(mapOf("online" to on, "at" to ServerValue.TIMESTAMP))
    }

    fun setInMatch(busy: Boolean) {
        val id = uid ?: return
        ref("status/$id").updateChildren(mapOf("inMatch" to busy, "at" to ServerValue.TIMESTAMP))
    }

    // ------------------------------------------------------------------ Bạn bè

    private fun listenFriends(id: String) {
        val r = ref("friends/$id")
        val l = object : ChildEventListener {
            override fun onChildAdded(s: DataSnapshot, prev: String?) { watchUser(s.key ?: return) }
            override fun onChildRemoved(s: DataSnapshot) { s.key?.let { friends.remove(it) } }
            override fun onChildChanged(s: DataSnapshot, prev: String?) {}
            override fun onChildMoved(s: DataSnapshot, prev: String?) {}
            override fun onCancelled(e: DatabaseError) {}
        }
        r.addChildEventListener(l)
        listeners.add(r to l)
    }

    private fun watchUser(fid: String) {
        ref("users/$fid").addValueEventListener(simple { s ->
            val old = friends[fid]
            friends[fid] = NetUser(
                fid,
                s.child("name").getValue(String::class.java) ?: "Người chơi",
                s.child("avatar").getValue(Long::class.java)?.toInt() ?: -1,
                s.child("rating").getValue(Long::class.java)?.toInt() ?: 100,
                old?.online ?: false,
                old?.inMatch ?: false,
            )
        })
        ref("status/$fid").addValueEventListener(simple { s ->
            val u = friends[fid] ?: NetUser(fid, "Người chơi", -1, 100)
            friends[fid] = u.copy(
                online = s.child("online").getValue(Boolean::class.java) == true,
                inMatch = s.child("inMatch").getValue(Boolean::class.java) == true,
            )
        })
    }

    /** Kết bạn ngay (không cần bên kia đồng ý) và báo cho họ biết. */
    fun addFriend(fid: String, myName: String) {
        val id = uid ?: return
        if (fid == id) return
        ref("friends/$id/$fid").setValue(true)
        ref("inbox/$fid").push().setValue(mapOf("type" to "friend", "from" to id, "name" to myName, "at" to ServerValue.TIMESTAMP))
    }

    fun removeFriend(fid: String) {
        val id = uid ?: return
        ref("friends/$id/$fid").removeValue()
    }

    fun isFriend(fid: String) = friends.containsKey(fid)

    // ------------------------------------------------------------------ Hộp thư

    private fun listenInbox(id: String) {
        val r = ref("inbox/$id")
        val l = object : ChildEventListener {
            override fun onChildAdded(s: DataSnapshot, prev: String?) {
                val at = s.child("at").getValue(Long::class.java) ?: 0L
                // Bỏ lời mời quá cũ (> 2 phút)
                if (serverNow() - at > 120_000L) { s.ref.removeValue(); return }
                inbox.add(
                    InboxMsg(
                        s.key ?: return,
                        s.child("type").getValue(String::class.java) ?: "",
                        s.child("from").getValue(String::class.java) ?: "",
                        s.child("name").getValue(String::class.java) ?: "Người chơi",
                        s.child("room").getValue(String::class.java),
                    )
                )
            }
            override fun onChildRemoved(s: DataSnapshot) { inbox.removeAll { it.key == s.key } }
            override fun onChildChanged(s: DataSnapshot, prev: String?) {}
            override fun onChildMoved(s: DataSnapshot, prev: String?) {}
            override fun onCancelled(e: DatabaseError) {}
        }
        r.addChildEventListener(l)
        listeners.add(r to l)
    }

    fun consume(msg: InboxMsg) {
        val id = uid ?: return
        ref("inbox/$id/${msg.key}").removeValue()
        inbox.removeAll { it.key == msg.key }
    }

    fun invite(fid: String, myName: String, roomId: String) {
        if (uid == null) return
        ref("inbox/$fid").push().setValue(
            mapOf("type" to "invite", "from" to uid, "name" to myName, "room" to roomId, "at" to ServerValue.TIMESTAMP)
        )
    }

    // ------------------------------------------------------------------ Bảng xếp hạng

    /** Người chơi thật trên bảng: kind 0 theo điểm xếp hạng, 1–3 theo điểm trong kỳ. */
    suspend fun topUsers(kind: Int): List<Pair<NetUser, Int>> {
        if (uid == null) return emptyList()
        val p = prefs ?: return emptyList()
        return try {
            val snap = ref("users").orderByChild("rating").limitToLast(150).get().await()
            val key = if (kind == 0) null else p.ladder.periodKey(kind)
            snap.children.mapNotNull { s ->
                val u = NetUser(
                    s.key ?: return@mapNotNull null,
                    s.child("name").getValue(String::class.java) ?: "Người chơi",
                    s.child("avatar").getValue(Long::class.java)?.toInt() ?: -1,
                    s.child("rating").getValue(Long::class.java)?.toInt() ?: 100,
                )
                val v = if (key == null) u.rating else s.child("pp/$key").getValue(Long::class.java)?.toInt() ?: 0
                u to v
            }
        } catch (e: Exception) {
            Log.w(TAG, "top", e)
            emptyList()
        }
    }

    suspend fun user(fid: String): NetUser? = try {
        val s = ref("users/$fid").get().await()
        if (!s.exists()) null else NetUser(
            fid,
            s.child("name").getValue(String::class.java) ?: "Người chơi",
            s.child("avatar").getValue(Long::class.java)?.toInt() ?: -1,
            s.child("rating").getValue(Long::class.java)?.toInt() ?: 100,
        )
    } catch (e: Exception) { null }

    // ------------------------------------------------------------------ Tiện ích

    fun simple(onData: (DataSnapshot) -> Unit) = object : ValueEventListener {
        override fun onDataChange(s: DataSnapshot) = onData(s)
        override fun onCancelled(e: DatabaseError) { Log.w(TAG, "cancelled ${e.message}") }
    }

    private fun detachAll() {
        for ((r, l) in listeners) {
            when (l) {
                is ChildEventListener -> r.removeEventListener(l)
                is ValueEventListener -> r.removeEventListener(l)
            }
        }
        listeners.clear()
    }
}
