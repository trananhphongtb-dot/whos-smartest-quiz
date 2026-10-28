package vn.dautruongtrithuc.ui

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import vn.dautruongtrithuc.data.Net
import vn.dautruongtrithuc.data.Prefs

/** Một ngôn ngữ chọn được trên màn hình đăng nhập, hiển thị bằng chính ngôn ngữ đó. */
data class AppLanguage(val code: String, val nativeName: String)

val APP_LANGUAGES = listOf(
    AppLanguage("en", "English"),
    AppLanguage("es", "Español"),
    AppLanguage("fr", "Français"),
    AppLanguage("de", "Deutsch"),
    AppLanguage("pt", "Português"),
    AppLanguage("ar", "العربية"),
    AppLanguage("zh", "中文 (简体)"),
    AppLanguage("ja", "日本語"),
    AppLanguage("ko", "한국어"),
    AppLanguage("ru", "Русский"),
    AppLanguage("hi", "हिन्दी"),
    AppLanguage("it", "Italiano"),
    AppLanguage("vi", "Tiếng Việt"),
)

/** Các chuỗi trên màn hình đăng nhập, theo ngôn ngữ đã chọn. */
data class LoginStrings(
    val titleLine1: String,
    val titleLine2: String,
    val tagline: String,
    val signIn: String,
    val signingIn: String,
    val note: String,
)

fun loginStrings(code: String): LoginStrings = when (code) {
    "es" -> LoginStrings(
        "¿QUIÉN ES EL MÁS INTELIGENTE?", "QUIZ",
        "Un juego de conocimientos estilo concurso con amigos y jugadores de todo el mundo.",
        "G   Iniciar sesión con Google", "Iniciando sesión…",
        "Tus puntos de clasificación, amigos y foto de perfil se guardan con tu cuenta de Google.",
    )
    "fr" -> LoginStrings(
        "QUI EST LE PLUS INTELLIGENT ?", "QUIZ",
        "Un jeu de connaissances façon jeu télévisé avec vos amis et des joueurs du monde entier.",
        "G   Se connecter avec Google", "Connexion…",
        "Vos points de classement, vos amis et votre photo de profil sont enregistrés avec votre compte Google.",
    )
    "de" -> LoginStrings(
        "WER IST AM SCHLAUSTEN?", "QUIZ",
        "Ein Quizspiel im Gameshow-Stil mit Freunden und Spielern aus aller Welt.",
        "G   Mit Google anmelden", "Anmeldung läuft…",
        "Deine Ranglistenpunkte, Freunde und dein Profilbild werden mit deinem Google-Konto gespeichert.",
    )
    "pt" -> LoginStrings(
        "QUEM É O MAIS INTELIGENTE?", "QUIZ",
        "Um jogo de conhecimentos estilo programa de TV com amigos e jogadores do mundo inteiro.",
        "G   Entrar com o Google", "Entrando…",
        "Seus pontos no ranking, amigos e foto de perfil são salvos com sua conta do Google.",
    )
    "ar" -> LoginStrings(
        "من الأذكى؟", "اختبار",
        "لعبة معلومات بأسلوب برامج المسابقات مع الأصدقاء ولاعبين من حول العالم.",
        "G   تسجيل الدخول باستخدام Google", "جارٍ تسجيل الدخول…",
        "تُحفظ نقاط تصنيفك وأصدقاؤك وصورتك الشخصية مع حساب Google الخاص بك.",
    )
    "zh" -> LoginStrings(
        "谁最聪明？", "问答",
        "一款与朋友和全球玩家一起参与的知识竞赛游戏。",
        "G   使用 Google 登录", "正在登录…",
        "您的排名积分、好友和头像将保存在您的 Google 账号中。",
    )
    "ja" -> LoginStrings(
        "誰が一番賢い？", "クイズ",
        "友達や世界中のプレイヤーと楽しむクイズ番組風の知識ゲーム。",
        "G   Google でログイン", "ログイン中…",
        "ランキングポイント、友達、プロフィール写真は Google アカウントに保存されます。",
    )
    "ko" -> LoginStrings(
        "누가 가장 똑똑할까?", "퀴즈",
        "친구 및 전 세계 플레이어와 함께하는 게임쇼 스타일의 지식 게임.",
        "G   Google로 로그인", "로그인 중…",
        "랭킹 포인트, 친구, 프로필 사진은 Google 계정에 저장됩니다.",
    )
    "ru" -> LoginStrings(
        "КТО САМЫЙ УМНЫЙ?", "ВИКТОРИНА",
        "Игра-викторина в стиле телешоу с друзьями и игроками со всего мира.",
        "G   Войти через Google", "Вход…",
        "Ваши рейтинговые очки, друзья и фото профиля сохраняются в вашем аккаунте Google.",
    )
    "hi" -> LoginStrings(
        "सबसे होशियार कौन है?", "क्विज़",
        "दोस्तों और दुनिया भर के खिलाड़ियों के साथ खेला जाने वाला गेमशो शैली का ज्ञान खेल।",
        "G   Google से साइन इन करें", "साइन इन हो रहा है…",
        "आपके रैंकिंग अंक, मित्र और प्रोफ़ाइल फ़ोटो आपके Google खाते के साथ सहेजे जाते हैं।",
    )
    "it" -> LoginStrings(
        "CHI È IL PIÙ INTELLIGENTE?", "QUIZ",
        "Un gioco di cultura generale in stile quiz televisivo con amici e giocatori di tutto il mondo.",
        "G   Accedi con Google", "Accesso in corso…",
        "I tuoi punti in classifica, gli amici e la foto del profilo vengono salvati con il tuo account Google.",
    )
    "vi" -> LoginStrings(
        "AI THÔNG MINH NHẤT", "QUIZ",
        "Thi kiến thức theo phong cách gameshow với bạn bè và người chơi khắp nơi.",
        "G   Đăng nhập bằng Google", "Đang đăng nhập…",
        "Điểm xếp hạng, bạn bè và ảnh đại diện được lưu theo tài khoản Google của bạn.",
    )
    else -> LoginStrings(
        "WHO'S SMARTEST", "QUIZ",
        "A gameshow-style knowledge game with friends and players around the world.",
        "G   Sign in with Google", "Signing in…",
        "Your ranking points, friends and profile photo are saved with your Google account.",
    )
}

/**
 * Màn hình đầu khi chưa đăng nhập: chọn ngôn ngữ và đăng nhập Google
 * nằm cùng một trang, không tách thành hai màn hình.
 */
@Composable
fun LoginScreen() {
    val ctx = LocalContext.current
    val activity = ctx as? Activity
    val scope = rememberCoroutineScope()
    val prefs = remember { Prefs(ctx) }
    var lang by remember { mutableStateOf(prefs.language.takeIf { code -> APP_LANGUAGES.any { it.code == code } } ?: "en") }
    var menuExpanded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val strings = loginStrings(lang)
    val selectedLanguage = APP_LANGUAGES.firstOrNull { it.code == lang } ?: APP_LANGUAGES.first()
    Column(
        Modifier
            .fillMaxSize()
            .background(ScreenBrush)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Dropdown chọn ngôn ngữ, ở trên cùng trang đăng nhập.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box {
                TextButton(onClick = { menuExpanded = true }) {
                    Text("🌐 ${selectedLanguage.nativeName} ▾", color = GoldLight, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    APP_LANGUAGES.forEach { language ->
                        DropdownMenuItem(
                            text = { Text(language.nativeName) },
                            onClick = {
                                lang = language.code
                                prefs.language = language.code
                                menuExpanded = false
                            },
                        )
                    }
                }
            }
        }
        Text("⚔", color = Gold, fontSize = 64.sp)
        Spacer(Modifier.height(10.dp))
        Text(strings.titleLine1, color = GoldLight, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
        // Tiêu đề dịch sang một số ngôn ngữ dài hơn tiếng Anh nên thu nhỏ để không tràn màn hình.
        val titleLine2Size = if (strings.titleLine2.length > 10) 32.sp else 44.sp
        Text(strings.titleLine2, color = Gold, fontSize = titleLine2Size, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        Text(
            strings.tagline,
            color = TextMuted,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(40.dp))
        GoldButton(
            modifier = Modifier.fillMaxWidth().height(58.dp),
            onClick = {
                if (busy || activity == null) return@GoldButton
                busy = true
                scope.launch {
                    val err = Net.signInWithGoogle(activity)
                    busy = false
                    if (err != null) Toast.makeText(ctx, err, Toast.LENGTH_LONG).show()
                }
            },
        ) {
            Text(if (busy) strings.signingIn else strings.signIn, color = Ink, fontWeight = FontWeight.Black, fontSize = 17.sp)
        }
        Spacer(Modifier.height(14.dp))
        Text(
            strings.note,
            color = TextMuted.copy(alpha = 0.75f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}
