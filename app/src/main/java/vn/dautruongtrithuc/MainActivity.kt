package vn.dautruongtrithuc

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import vn.dautruongtrithuc.audio.SoundFx
import vn.dautruongtrithuc.data.Net
import vn.dautruongtrithuc.data.Prefs
import vn.dautruongtrithuc.data.QuestionBank
import vn.dautruongtrithuc.game.MatchConfig
import vn.dautruongtrithuc.ui.AppTheme
import vn.dautruongtrithuc.ui.Gold
import vn.dautruongtrithuc.ui.HomeScreen
import vn.dautruongtrithuc.ui.LoginScreen
import vn.dautruongtrithuc.ui.MatchScreen
import vn.dautruongtrithuc.ui.Navy1
import vn.dautruongtrithuc.ui.TextMuted

/** Link mời vào phòng: mở trang web, trang này chuyển sang app (hoặc Google Play nếu chưa cài). */
fun inviteLink(code: String) = "https://trananhphongtb-dot.github.io/dau-truong-tri-thuc/join/?r=$code"

class MainActivity : ComponentActivity() {

    private var joinCode by mutableStateOf<String?>(null)

    private fun readJoin(i: Intent?) {
        val d = i?.data ?: return
        val code = d.getQueryParameter("r") ?: return
        if (Regex("[A-Za-z0-9_-]{4,40}").matches(code)) joinCode = code
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readJoin(intent)
    }

    private lateinit var prefs: Prefs
    private lateinit var fx: SoundFx

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        QuestionBank.load(this)
        fx = SoundFx(this, prefs)
        Net.init(this, prefs)
        readJoin(intent)

        setContent {
            AppTheme {
                var config by remember { mutableStateOf<MatchConfig?>(null) }
                var matchKey by remember { mutableIntStateOf(0) }
                val scope = rememberCoroutineScope()
                val current = config

                // Bấm link mời: vào thẳng phòng (cần đăng nhập Google)
                val jc = joinCode
                if (jc != null && current == null) {
                    if (Net.signedIn) {
                        LaunchedEffect(jc) {
                            joinCode = null
                            matchKey++
                            config = MatchConfig(0, 1, room = true, joinRoom = jc)
                        }
                    } else {
                        AlertDialog(
                            onDismissRequest = { joinCode = null },
                            containerColor = Navy1,
                            title = { Text("Vào phòng thi", color = Gold, fontWeight = FontWeight.Black) },
                            text = { Text("Bạn cần đăng nhập Google để vào phòng của bạn bè.", color = TextMuted) },
                            confirmButton = {
                                TextButton(onClick = {
                                    scope.launch {
                                        val err = Net.signInWithGoogle(this@MainActivity)
                                        if (err != null) {
                                            Toast.makeText(this@MainActivity, err, Toast.LENGTH_LONG).show()
                                            joinCode = null
                                        }
                                    }
                                }) { Text("Đăng nhập Google", color = Gold, fontWeight = FontWeight.Black) }
                            },
                            dismissButton = { TextButton(onClick = { joinCode = null }) { Text("Để sau", color = TextMuted) } },
                        )
                    }
                }

                // Thông báo từ người khác: kết bạn, mời vào phòng
                val first = Net.inbox.firstOrNull()
                LaunchedEffect(first?.key, current == null) {
                    val m = first ?: return@LaunchedEffect
                    when (m.type) {
                        "friend" -> {
                            Toast.makeText(this@MainActivity, "${m.name} đã kết bạn với bạn", Toast.LENGTH_SHORT).show()
                            Net.consume(m)
                        }
                        "invite" -> {
                            Net.consume(m)
                            val rid = m.room
                            if (rid != null && config == null) {
                                Toast.makeText(this@MainActivity, "Đang vào phòng của ${m.name}…", Toast.LENGTH_SHORT).show()
                                matchKey++
                                config = MatchConfig(0, 1, room = true, joinRoom = rid)
                            }
                        }
                        else -> Net.consume(m)
                    }
                }

                if (!Net.signedIn) {
                    LoginScreen()
                } else if (current == null) {
                    HomeScreen(prefs, fx) {
                        matchKey++
                        config = it
                    }
                } else {
                    BackHandler { config = null }
                    MatchScreen(
                        config = current,
                        key = matchKey,
                        prefs = prefs,
                        fx = fx,
                        onReplay = { matchKey++; if (current.joinRoom != null) config = MatchConfig(0, 1, room = true) },
                        onHome = { config = null },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Net.setOnline(true)
    }

    override fun onStop() {
        super.onStop()
        Net.setOnline(false)
    }

    override fun onResume() {
        super.onResume()
        if (::fx.isInitialized) fx.recheck()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isChangingConfigurations) fx.release()
    }
}
