package vn.dautruongtrithuc.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import vn.dautruongtrithuc.audio.SoundFx
import vn.dautruongtrithuc.data.Net
import vn.dautruongtrithuc.data.Prefs
import vn.dautruongtrithuc.game.MatchConfig
import vn.dautruongtrithuc.game.MatchViewModel
import vn.dautruongtrithuc.game.PART_NAMES
import vn.dautruongtrithuc.game.Phase

val FIELD_NAMES = mapOf(
    "TOAN" to "Toán", "LY" to "Vật lý", "HOA" to "Hóa học", "SINH" to "Sinh học",
    "VAN" to "Văn học", "SU" to "Lịch sử", "DIA" to "Địa lý", "ANH" to "Tiếng Anh",
    "TIN" to "Tin học", "DN" to "Danh nhân", "SK" to "Sự kiện", "TT" to "Thường thức",
)

@Composable
fun MatchScreen(
    config: MatchConfig,
    key: Int,
    prefs: Prefs,
    fx: SoundFx,
    onReplay: () -> Unit,
    onHome: () -> Unit,
) {
    val vm: MatchViewModel = viewModel(key = "match$key")
    val activity = LocalContext.current as? ComponentActivity
    LaunchedEffect(vm) { vm.start(config, prefs, fx) }
    DisposableEffect(vm) {
        onDispose { if (activity?.isChangingConfigurations != true) vm.stop() }
    }

    var askExit by remember { mutableStateOf(false) }
    BackHandler(enabled = vm.phase != Phase.END) { askExit = true }
    val ctx = LocalContext.current
    var localFriends by remember { mutableStateOf(prefs.friends().toSet()) }
    val friends = localFriends + Net.friends.keys
    val addFriend: (vn.dautruongtrithuc.game.PlayerState) -> Unit = { p ->
        val pid = p.personaId
        val uid = p.uid
        if (pid != null && pid !in friends) {
            prefs.addFriend(pid)
            localFriends = prefs.friends().toSet()
            android.widget.Toast.makeText(ctx, "Đã thêm ${p.name} vào bạn bè", android.widget.Toast.LENGTH_SHORT).show()
        } else if (pid == null && uid != null && uid !in friends) {
            Net.addFriend(uid, prefs.name)
            android.widget.Toast.makeText(ctx, "Đã thêm ${p.name} vào bạn bè", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val err = vm.netError
    if (err != null) {
        AlertDialog(
            onDismissRequest = onHome,
            containerColor = Navy1,
            title = { Text("Không vào được phòng", color = TextMain, fontWeight = FontWeight.Bold) },
            text = { Text(err, color = TextMuted) },
            confirmButton = { TextButton(onClick = onHome) { Text("Về sảnh", color = Gold, fontWeight = FontWeight.Bold) } },
        )
        return
    }

    if (vm.phase == Phase.END) {
        ResultScreen(
            players = vm.players.toList(),
            winners = vm.winners,
            solo = vm.solo,
            ranked = vm.ranked || vm.room,
            room = vm.room,
            friends = friends,
            onAddFriend = addFriend,
            onReplay = onReplay,
            onHome = onHome,
        )
        return
    }

    if (vm.phase == Phase.LOBBY) {
        Lobby(
            code = vm.roomCode,
            players = vm.players.toList(),
            friends = friends,
            onAddFriend = addFriend,
            onLeave = { vm.stop(); onHome() },
            onStart = { vm.startRoom() },
            prefs = prefs,
            onInvite = { vm.inviteFriend(it) },
            isHost = vm.isHost,
            netRoom = vm.netRoom,
        )
        return
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(ScreenBrush)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            Header(vm, onClose = { askExit = true })
            Spacer(Modifier.height(6.dp))
            val secs = ((vm.timeLeftMs + 999) / 1000).toInt()
            Stage(
                players = vm.players.toList(),
                turnId = vm.turnId,
                starId = vm.starId,
                locked = vm.locked,
                timeFraction = vm.timeLeftMs.toFloat() / vm.timeTotalMs.coerceAtLeast(1L),
                timeText = if (vm.part == 1 && secs >= 60) "%d:%02d".format(secs / 60, secs % 60) else "$secs",
                showTimer = vm.phase == Phase.PLAY && vm.timeTotalMs > 1L,
                modifier = Modifier.fillMaxWidth().height(270.dp),
                friends = friends,
                onAddFriend = addFriend,
            )
            Box(Modifier.fillMaxWidth().heightIn(min = 34.dp).padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                if (vm.banner.isNotEmpty()) {
                    Text(
                        vm.banner,
                        color = GoldLight,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                }
            }
            val q = vm.question
            if (q != null) {
                QuestionCard(
                    no = vm.qNo,
                    total = vm.qTotal,
                    field = FIELD_NAMES[q.field] ?: q.field,
                    text = q.text,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    star = if (vm.canStarNext || (vm.part == 3 && vm.turnId == 0 && vm.myNextStar)) vm.myNextStar else null,
                    onStar = { vm.toggleStar() },
                )
                Spacer(Modifier.height(10.dp))
                val enabled = vm.canAnswer()
                AnswerGrid(
                    options = q.options,
                    answer = q.answer,
                    chosen = vm.humanChoice,
                    reveal = vm.reveal,
                    enabled = enabled,
                    onChoose = { vm.choose(it) },
                )
                if (vm.part == 1) {
                    Spacer(Modifier.height(8.dp))
                    BlueButton(
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        onClick = { vm.skip() },
                    ) {
                        Text("Bỏ qua  »", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
            } else {
                Spacer(Modifier.weight(1f))
            }
        }

        AnimatedVisibility(
            visible = vm.phase == Phase.INTRO && vm.part > 0,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            IntroOverlay(part = vm.part, left = vm.introLeft, solo = vm.solo)
        }
        if (vm.phase == Phase.SEARCH) {
            SearchOverlay(found = vm.players.size - 1, secs = vm.searchSec, failed = vm.searchFailed, onCancel = { vm.stop(); onHome() })
        }
    }

    if (askExit) {
        AlertDialog(
            onDismissRequest = { askExit = false },
            containerColor = Navy1,
            title = { Text("Rời trận?", color = TextMain, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if ((vm.ranked || vm.room) && vm.phase != Phase.SEARCH) "Rời trận giữa chừng sẽ bị tính thua (xếp cuối)."
                    else if (vm.phase == Phase.SEARCH) "Dừng tìm đối thủ?"
                    else "Kết quả trận này sẽ không được lưu.",
                    color = TextMuted,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    askExit = false
                    vm.stop()
                    onHome()
                }) { Text("Rời trận", color = RedBad, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { askExit = false }) { Text("Thi tiếp", color = Gold, fontWeight = FontWeight.Bold) }
            },
        )
    }
}

@Composable
private fun Header(vm: MatchViewModel, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0x22FFFFFF))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Text("✕", color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        if (vm.part > 0) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(GoldBrush)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text("PHẦN ${vm.part}", color = Ink, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                PART_NAMES[vm.part].uppercase(),
                color = Gold,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (vm.roundText.isNotEmpty()) {
            Text(vm.roundText, color = TextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun QuestionCard(
    no: Int,
    total: Int,
    field: String,
    text: String,
    modifier: Modifier,
    star: Boolean? = null,
    onStar: () -> Unit = {},
) {
    Box(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF16357A), Color(0xFF0C2257))))
            .border(1.5.dp, Color(0x80FFD45E), RoundedCornerShape(18.dp))
            .padding(14.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(GoldBrush)
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                ) {
                    Text("Câu $no / $total", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, Color(0x558CC3FF), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                ) {
                    Text(field, color = SkyLine, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                if (star != null) {
                    Spacer(Modifier.width(8.dp))
                    // Ngôi sao hy vọng cho câu sau: xám = chưa đặt, vàng = đã đặt
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (star) Color(0x40FFD45E) else Color(0x1AFFFFFF))
                            .border(1.5.dp, if (star) Gold else Color(0x55FFFFFF), CircleShape)
                            .clickable(onClick = onStar),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("★", color = if (star) Gold else Color(0xFF8A94A8), fontSize = 20.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text,
                    color = TextMain,
                    fontSize = if (text.length > 160) 15.sp else if (text.length > 90) 17.sp else 19.sp,
                    lineHeight = if (text.length > 160) 21.sp else 25.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun AnswerGrid(
    options: List<String>,
    answer: Int,
    chosen: Int?,
    reveal: Boolean,
    enabled: Boolean,
    onChoose: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in 0..1) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (col in 0..1) {
                    val i = row * 2 + col
                    AnswerButton(
                        letter = "ABCD"[i].toString(),
                        text = options[i],
                        state = when {
                            reveal && i == answer -> AnsState.RIGHT
                            reveal && i == chosen -> AnsState.WRONG
                            i == chosen -> AnsState.PICKED
                            reveal -> AnsState.DIM
                            else -> AnsState.NORMAL
                        },
                        enabled = enabled,
                        onClick = { onChoose(i) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private enum class AnsState { NORMAL, PICKED, RIGHT, WRONG, DIM }

@Composable
private fun AnswerButton(
    letter: String,
    text: String,
    state: AnsState,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val bg: Brush = when (state) {
        AnsState.RIGHT -> Brush.verticalGradient(listOf(Color(0xFF34D399), Color(0xFF15803D)))
        AnsState.WRONG -> Brush.verticalGradient(listOf(Color(0xFFFB7185), Color(0xFFBE123C)))
        AnsState.PICKED -> GoldBrush
        AnsState.DIM -> Brush.verticalGradient(listOf(Color(0xFF15295A), Color(0xFF0E1E46)))
        AnsState.NORMAL -> Brush.verticalGradient(listOf(Color(0xFF1E4590), Color(0xFF122C66)))
    }
    val fg = when (state) {
        AnsState.PICKED -> Ink
        AnsState.DIM -> TextMuted.copy(alpha = 0.6f)
        else -> TextMain
    }
    val border = when (state) {
        AnsState.NORMAL -> Color(0x668CB4FF)
        AnsState.DIM -> Color(0x22FFFFFF)
        else -> Color(0x99FFFFFF)
    }
    Row(
        modifier
            .heightIn(min = 58.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (state == AnsState.PICKED) Color(0x33000000) else Color(0x33FFFFFF)),
            contentAlignment = Alignment.Center,
        ) {
            Text(letter, color = if (state == AnsState.PICKED) Ink else GoldLight, fontWeight = FontWeight.Black, fontSize = 13.sp)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            color = fg,
            fontSize = if (text.length > 40) 12.sp else 14.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 3,
        )
    }
}

@Composable
private fun IntroOverlay(part: Int, left: Int, solo: Boolean) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xE6040A1E))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (n in 1..3) {
                    Box(
                        Modifier
                            .width(46.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (n <= part) Gold else Color(0x33FFFFFF)),
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
            Text("PHẦN THI THỨ ${part}", color = TextMuted, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                PART_NAMES[part].uppercase(),
                color = Gold,
                fontSize = 44.sp,
                lineHeight = 48.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            Text(
                rulesFor(part, solo),
                color = TextMain,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            Box(
                Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(GoldBrush),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (left > 0) "$left" else "▶", color = Ink, fontSize = 36.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(8.dp))
            Text("Bắt đầu sau", color = TextMuted, fontSize = 13.sp)
        }
    }
}

private fun rulesFor(part: Int, solo: Boolean): String = when (part) {
    1 -> "2 phút trả lời liên tục.\nĐúng +10 điểm · được bỏ qua · sai không bị trừ."
    2 -> if (solo) {
        "10 câu, mỗi câu 15 giây.\nĐúng trong 5 giây +30 · trong 10 giây +20 · sau đó +10."
    } else {
        "10 câu, mỗi câu 15 giây.\nĐúng nhanh nhất +30 · nhì +20 · ba +10.\nSai không bị trừ."
    }
    else -> if (solo) {
        "5 câu, mỗi câu 10 giây.\nĐúng +10 · sai −10.\nBấm ★ góc trên câu hỏi để đặt ngôi sao hy vọng cho câu sau của mình: đúng +20 · sai −20."
    } else {
        "Mỗi người lần lượt 1 câu, 5 vòng, 10 giây/câu.\nĐúng +10 · sai −10.\nBấm ★ góc trên câu hỏi để đặt ngôi sao hy vọng cho câu sau của mình: đúng +20 · sai −20.\nNgười khác được giành quyền khi người chính sai: đúng +10 · sai −10."
    }
}


@Composable
private fun SearchOverlay(found: Int, secs: Int, failed: Boolean, onCancel: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xF2040A1E))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("⚡", color = Gold, fontSize = 44.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                if (failed) "CHƯA TÌM ĐƯỢC ĐỐI THỦ" else if (found >= 3) "ĐÃ ĐỦ NGƯỜI!" else "ĐANG TÌM ĐỐI THỦ…",
                color = Gold,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text("Thi đấu xếp hạng · 4 người", color = TextMuted, fontSize = 14.sp)
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (i in 0 until 4) {
                    val on = i == 0 || i <= found
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(if (on) Gold else Color(0x22FFFFFF))
                            .border(2.dp, if (on) GoldMid else Color(0x33FFFFFF), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(if (on) "✓" else "?", color = if (on) Ink else TextMuted, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("${found + 1}/4 người · ${secs}s", color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(26.dp))
            if (found < 3 || failed) {
                TextButton(onClick = onCancel) { Text(if (failed) "Về sảnh" else "Hủy tìm", color = TextMuted, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
            }
        }
    }
}


@Composable
private fun Lobby(
    code: String,
    players: List<vn.dautruongtrithuc.game.PlayerState>,
    friends: Set<String>,
    onAddFriend: (vn.dautruongtrithuc.game.PlayerState) -> Unit,
    onLeave: () -> Unit,
    onStart: () -> Unit,
    prefs: Prefs,
    onInvite: (String) -> Boolean,
    isHost: Boolean = true,
    netRoom: String? = null,
) {
    val ctx = LocalContext.current
    var askLeave by remember { mutableStateOf(false) }
    var inviting by remember { mutableStateOf(false) }
    val invited = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    BackHandler { askLeave = true }
    Column(
        Modifier
            .fillMaxSize()
            .background(ScreenBrush)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("PHÒNG CHỜ", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text(if (isHost) "Phòng của bạn" else "Phòng của ${players.getOrNull(1)?.name ?: "bạn bè"}", color = Gold, fontSize = 22.sp, fontWeight = FontWeight.Black, maxLines = 1)
            }
            TextButton(onClick = {
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(
                        android.content.Intent.EXTRA_TEXT,
                        if (netRoom != null) "Vào Đấu Trường Trí Thức thi với mình nhé! Bấm vào đây để vào phòng: ${vn.dautruongtrithuc.inviteLink(netRoom)}"
                        else "Tải Đấu Trường Trí Thức để thi đấu kiến thức cùng mình nhé!",
                    )
                }
                ctx.startActivity(android.content.Intent.createChooser(send, "Mời bạn vào phòng"))
            }) { Text("Gửi link ↗", color = Gold, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
        }
        BlueButton(modifier = Modifier.fillMaxWidth().height(46.dp), onClick = { inviting = true }) {
            Text("＋ Mời bạn bè trong app", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        for (i in 0 until 4) {
            val p = players.getOrNull(i)
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(82.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (p != null) Color(0x22FFFFFF) else Color(0x0DFFFFFF))
                    .border(1.dp, if (p?.id == 0) GoldMid else Color(0x22FFFFFF), RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (p == null) {
                    Box(
                        Modifier.size(46.dp).clip(CircleShape).background(Color(0x14FFFFFF)),
                        contentAlignment = Alignment.Center,
                    ) { Text("?", color = TextMuted, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(22.dp))
                    Text("Đang chờ người chơi…", color = TextMuted, fontSize = 15.sp)
                } else {
                    Spacer(Modifier.width(4.dp))
                    Avatar(
                        p.name, p.palette, size = 42.dp, ring = p.id == 0, rating = "${p.rating}",
                        friend = if (p.id == 0) null else p.friendKey?.let { it in friends },
                        onAddFriend = { onAddFriend(p) },
                        art = p.avatar,
                        cap = true,
                        uid = p.uid,
                        me = p.id == 0,
                    )
                    Spacer(Modifier.width(26.dp))
                    Column(Modifier.weight(1f)) {
                        OneLine(p.name, size = 16, weight = FontWeight.Bold)
                        Text(if (p.id == 0) "Chủ phòng" else p.province.ifBlank { "Sẵn sàng" }, color = TextMuted, fontSize = 12.sp)
                    }
                    Text("✓", color = GreenOk, fontSize = 20.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            when {
                !isHost -> "Đang chờ chủ phòng bắt đầu…"
                players.size < 2 -> "Cần ít nhất 2 người để bắt đầu"
                else -> "Trận tính điểm xếp hạng"
            },
            color = TextMuted,
            fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BlueButton(modifier = Modifier.weight(1f).height(54.dp), onClick = { askLeave = true }) {
                Text("Rời phòng", color = RedBad, fontWeight = FontWeight.Black, fontSize = 16.sp)
            }
            if (isHost) GoldButton(
                modifier = Modifier.weight(1f).height(54.dp),
                onClick = { if (players.size >= 2) onStart() },
            ) {
                Text(
                    "Bắt đầu",
                    color = if (players.size >= 2) Ink else Ink.copy(alpha = 0.4f),
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                )
            }
        }
    }
    if (inviting) {
        val list = prefs.friends().mapNotNull { vn.dautruongtrithuc.data.Personas.get(it) }
            .sortedByDescending { vn.dautruongtrithuc.data.Personas.isOnline(it) }
        val real = Net.friends.values.sortedByDescending { it.online && !it.inMatch }
        AlertDialog(
            onDismissRequest = { inviting = false },
            containerColor = Navy1,
            title = { Text("Mời bạn bè", color = Gold, fontWeight = FontWeight.Black) },
            text = {
                if (list.isEmpty() && real.isEmpty()) {
                    Text("Chưa có bạn bè. Bấm dấu + trên ảnh người chơi để kết bạn.", color = TextMuted)
                } else Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    real.forEach { u ->
                        val free = u.online && !u.inMatch
                        val inRoom = players.any { it.uid == u.uid }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(u.name, 1, size = 38.dp, art = u.avatar, uid = u.uid)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                OneLine(u.name, size = 15, weight = FontWeight.Bold)
                                Text(
                                    when {
                                        inRoom -> "Đã vào phòng"
                                        !u.online -> "Ngoại tuyến"
                                        u.inMatch -> "Đang trong trận"
                                        u.uid in invited -> "Đã mời"
                                        else -> "Đang trực tuyến"
                                    },
                                    color = if (free) GreenOk else TextMuted,
                                    fontSize = 12.sp,
                                )
                            }
                            if (free && !inRoom && u.uid !in invited && netRoom != null) {
                                TextButton(onClick = {
                                    Net.invite(u.uid, prefs.name, netRoom)
                                    invited.add(u.uid)
                                }) { Text("Mời", color = Gold, fontWeight = FontWeight.Black) }
                            }
                        }
                    }
                    list.forEach { pp ->
                        val on = vn.dautruongtrithuc.data.Personas.isOnline(pp)
                        val busy = on && vn.dautruongtrithuc.data.Personas.isBusy(pp)
                        val inRoom = players.any { it.personaId == pp.id }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(pp.name, pp.palette, size = 38.dp, art = pp.avatar)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                OneLine(pp.name, size = 15, weight = FontWeight.Bold)
                                Text(
                                    when {
                                        inRoom -> "Đã vào phòng"
                                        !on -> "Ngoại tuyến"
                                        busy -> "Đang trong trận"
                                        pp.id in invited -> "Đang vào…"
                                        else -> "Đang trực tuyến"
                                    },
                                    color = if (on && !busy) GreenOk else TextMuted,
                                    fontSize = 12.sp,
                                )
                            }
                            if (on && !busy && !inRoom && pp.id !in invited) {
                                TextButton(onClick = {
                                    if (players.size >= 4) {
                                        android.widget.Toast.makeText(ctx, "Phòng đã đủ 4 người", android.widget.Toast.LENGTH_SHORT).show()
                                    } else if (onInvite(pp.id)) invited.add(pp.id)
                                }) { Text("Mời", color = Gold, fontWeight = FontWeight.Black) }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { inviting = false }) { Text("Xong", color = Gold, fontWeight = FontWeight.Bold) } },
        )
    }

    if (askLeave) {
        AlertDialog(
            onDismissRequest = { askLeave = false },
            containerColor = Navy1,
            title = { Text("Rời phòng?", color = TextMain, fontWeight = FontWeight.Bold) },
            text = { Text("Bạn sẽ rời khỏi phòng chờ này.", color = TextMuted) },
            confirmButton = {
                TextButton(onClick = { askLeave = false; onLeave() }) { Text("Rời phòng", color = RedBad, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { askLeave = false }) { Text("Ở lại", color = Gold, fontWeight = FontWeight.Bold) } },
        )
    }
}

