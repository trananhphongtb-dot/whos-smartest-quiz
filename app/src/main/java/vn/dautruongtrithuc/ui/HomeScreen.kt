package vn.dautruongtrithuc.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.dautruongtrithuc.audio.SoundFx
import vn.dautruongtrithuc.data.BoardRow
import vn.dautruongtrithuc.data.Leaderboard
import vn.dautruongtrithuc.data.OnlineConfig
import vn.dautruongtrithuc.data.Personas
import vn.dautruongtrithuc.data.Net
import vn.dautruongtrithuc.data.Prefs
import vn.dautruongtrithuc.data.QuestionBank
import vn.dautruongtrithuc.game.MatchConfig

@Composable
fun HomeScreen(prefs: Prefs, fx: SoundFx, onStart: (MatchConfig) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(if (Net.justSignedIn) 2 else 0) }
    LaunchedEffect(Unit) { Net.justSignedIn = false }
    var settings by rememberSaveable { mutableStateOf(false) }
    // Tăng mỗi khi đổi tên để thanh trên cùng vẽ lại
    var nameVersion by remember { mutableIntStateOf(0) }

    if (settings) {
        BackHandler { settings = false }
        SettingsScreen(prefs, fx, onBack = { settings = false })
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(ScreenBrush)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        TopBar(prefs, nameVersion)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> PlayTab(onStart)
                1 -> RankTab(prefs)
                else -> MeTab(prefs, onOpenSettings = { settings = true }, onNameChanged = { nameVersion++ })
            }
        }
        BottomTabs(tab) { tab = it }
    }
}

@Composable
private fun TopBar(prefs: Prefs, nameVersion: Int) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 26.dp, top = 14.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("ĐẤU TRƯỜNG", color = GoldLight, fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
            Text("TRÍ THỨC", color = Gold, fontSize = 36.sp, lineHeight = 38.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        }
        // nameVersion chỉ để buộc vẽ lại khi đổi tên
        Avatar(
            if (nameVersion >= 0) prefs.name else "", 0, size = 50.dp, ring = true, rating = "${prefs.ladder.rating}",
            me = true, art = if (prefs.photo.isBlank()) prefs.avatar else -1,
        )
    }
}

@Composable
private fun BottomTabs(selected: Int, onSelect: (Int) -> Unit) {
    val items = listOf("⚔" to "Thi đấu", "♛" to "Xếp hạng", "☺" to "Tôi")
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xF0071435))
            .border(1.dp, Color(0x1AFFFFFF))
            .padding(vertical = 6.dp),
    ) {
        items.forEachIndexed { i, (icon, label) ->
            val on = i == selected
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(i) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(icon, color = if (on) Gold else TextMuted, fontSize = 20.sp)
                Text(label, color = if (on) Gold else TextMuted, fontSize = 12.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun TabColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

// ============================================================ Tab Thi đấu

@Composable
private fun PlayTab(onStart: (MatchConfig) -> Unit) {
    val ctx = LocalContext.current
    var botDialog by remember { mutableStateOf(false) }
    val soon = { Toast.makeText(ctx, "Cần đăng nhập Google · tính năng sắp có", Toast.LENGTH_SHORT).show() }

    TabColumn {
        GoldButton(
            modifier = Modifier.fillMaxWidth().height(84.dp),
            onClick = { if (OnlineConfig.FILL_ENABLED) onStart(MatchConfig(3, 1, ranked = true)) else soon() },
        ) {
            Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("⚡", fontSize = 30.sp, color = Ink)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Thi ngẫu nhiên", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Text("Ghép với người chơi cùng trình độ", color = Ink.copy(alpha = 0.75f), fontSize = 12.sp)
                }
                Text("»", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Black)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MenuTile("＋", "Tạo phòng", "Mời bạn bè", Modifier.weight(1f)) {
                if (OnlineConfig.FILL_ENABLED) onStart(MatchConfig(0, 1, room = true)) else soon()
            }
            MenuTile("✎", "Luyện tập", "Thi một mình", Modifier.weight(1f)) { onStart(MatchConfig(0, 1)) }
        }
        MenuTile("◎", "Luyện với máy", "1–3 đối thủ máy · chọn độ khó", Modifier.fillMaxWidth()) { botDialog = true }

        val online = remember { onlineCount() }
        GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(GreenOk))
                Spacer(Modifier.width(10.dp))
                Text("$online người đang trực tuyến", color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (botDialog) {
        BotDialog(onDismiss = { botDialog = false }) { bots, level ->
            botDialog = false
            onStart(MatchConfig(bots, level))
        }
    }
}

@Composable
private fun MenuTile(icon: String, title: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
    BlueButton(modifier = modifier.height(84.dp), onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Color(0x26FFD45E)),
                contentAlignment = Alignment.Center,
            ) { Text(icon, color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(10.dp))
            Column {
                OneLine(title, size = 16, weight = FontWeight.Bold)
                OneLine(sub, color = TextMuted, size = 12)
            }
        }
    }
}

@Composable
private fun BotDialog(onDismiss: () -> Unit, onGo: (Int, Int) -> Unit) {
    var bots by remember { mutableIntStateOf(3) }
    var level by remember { mutableIntStateOf(1) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Navy1,
        title = { Text("Luyện với máy", color = Gold, fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Số đối thủ", color = TextMuted, fontSize = 13.sp)
                ChoiceRow(listOf("1", "2", "3"), bots - 1) { bots = it + 1 }
                Text("Độ khó", color = TextMuted, fontSize = 13.sp)
                ChoiceRow(listOf("Dễ", "Vừa", "Khó"), level) { level = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onGo(bots, level) }) { Text("Bắt đầu", color = Gold, fontWeight = FontWeight.Black) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy", color = TextMuted) }
        },
    )
}

@Composable
private fun ChoiceRow(items: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { i, s ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) GoldBrush else BlueBtnBrush)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(s, color = if (on) Ink else TextMain, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ============================================================ Tab Xếp hạng

@Composable
private fun RankTab(prefs: Prefs) {
    val ctx = LocalContext.current
    var period by rememberSaveable { mutableIntStateOf(0) }
    var local by remember { mutableStateOf(prefs.friends().toSet()) }
    val friends = local + Net.friends.keys
    var real by remember { mutableStateOf<List<Pair<vn.dautruongtrithuc.data.NetUser, Int>>>(emptyList()) }
    LaunchedEffect(period, Net.uid) { real = Net.topUsers(period) }
    val rows = remember(period, real) { Leaderboard.rows(prefs, period, real) }
    val myIdx = rows.indexOfFirst { it.isMe }
    val shown = rows.take(100)
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Tất cả", "Tuần", "Tháng", "Năm").forEachIndexed { i, s ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (i == period) GoldBrush else BlueBtnBrush)
                        .clickable { period = i },
                    contentAlignment = Alignment.Center,
                ) { Text(s, color = if (i == period) Ink else TextMuted, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            }
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(shown) { i, r ->
                BoardRowView(i + 1, r, friends, period) { id, name ->
                    if (r.uid != null) Net.addFriend(id, prefs.name) else {
                        prefs.addFriend(id)
                        local = prefs.friends().toSet()
                    }
                    Toast.makeText(ctx, "Đã thêm $name vào bạn bè", Toast.LENGTH_SHORT).show()
                }
            }
        }
        if (myIdx >= 100) {
            Spacer(Modifier.height(6.dp))
            BoardRowView(myIdx + 1, rows[myIdx], friends, period) { _, _ -> }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun BoardRowView(rank: Int, r: BoardRow, friends: Set<String>, period: Int, onAdd: (String, String) -> Unit) {
    val me = r.isMe
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (me) Color(0x33FFD45E) else Color(0x14FFFFFF))
            .border(1.dp, if (me) GoldMid else Color.Transparent, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            when (rank) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "$rank" },
            color = if (rank <= 3) Gold else TextMuted,
            fontSize = if (rank <= 3) 20.sp else 15.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(40.dp),
        )
        Spacer(Modifier.width(6.dp))
        val id = r.key
        Avatar(
            r.name, r.palette, size = 38.dp,
            friend = id?.let { it in friends },
            onAddFriend = { if (id != null) onAdd(id, r.name) },
            art = r.avatar,
            uid = r.uid,
            me = r.isMe,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            OneLine(if (me) "${r.name} (bạn)" else r.name, size = 15, weight = FontWeight.Bold)
            if (r.province.isNotEmpty()) Text(r.province, color = TextMuted, fontSize = 11.sp, maxLines = 1)
        }
        Text("${r.value}", color = Gold, fontSize = 17.sp, fontWeight = FontWeight.Black)
        if (period != 0) Text(" đ", color = TextMuted, fontSize = 12.sp)
    }
}

private fun onlineCount(): Int {
    val n = Personas.all.count { Personas.isOnline(it) }
    // Mỗi người chơi hiện trong danh sách đại diện cho nhiều người khác
    return n * 7 + (System.currentTimeMillis() / 60_000L % 13).toInt()
}

// ============================================================ Tab Tôi

@Composable
private fun MeTab(prefs: Prefs, onOpenSettings: () -> Unit, onNameChanged: () -> Unit) {
    var name by remember { mutableStateOf(prefs.name) }
    var editing by remember { mutableStateOf(false) }
    var local by remember { mutableStateOf(prefs.friends()) }
    val friends = local + Net.friends.keys.filter { it !in local }
    var removing by remember { mutableStateOf<String?>(null) }
    val activity = LocalContext.current as? android.app.Activity
    val scope = rememberCoroutineScope()
    var signing by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    var picking by remember { mutableStateOf(false) }

    TabColumn {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Hồ sơ", color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0x22FFFFFF))
                    .clickable(onClick = onOpenSettings),
                contentAlignment = Alignment.Center,
            ) { Text("⚙", color = Gold, fontSize = 22.sp) }
        }
        GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clickable { picking = true }) {
                    Avatar(
                        name.ifBlank { "B" }, 0, size = 64.dp, ring = true, rating = "${prefs.ladder.rating}",
                        me = true, art = if (prefs.photo.isBlank()) prefs.avatar else -1,
                    )
                    // Nút bút chì để đổi ảnh
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .offset(x = (-6).dp, y = (-6).dp)
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Gold)
                            .border(1.5.dp, Navy0, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text("✎", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Black) }
                }
                Spacer(Modifier.width(22.dp))
                if (editing) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(15) },
                        label = { Text("Tên hiển thị") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Gold,
                            unfocusedBorderColor = Color(0x558CB4FF),
                            focusedLabelColor = Gold,
                            unfocusedLabelColor = TextMuted,
                            focusedTextColor = TextMain,
                            unfocusedTextColor = TextMain,
                            cursorColor = Gold,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        name = name.trim().ifBlank { "Bạn" }
                        prefs.name = name; Net.pushProfile()
                        editing = false
                        onNameChanged()
                    }) { Text("Lưu", color = Gold, fontWeight = FontWeight.Black) }
                } else {
                    Column(Modifier.weight(1f)) {
                        OneLine(name, size = 20, weight = FontWeight.Black)
                        Text(
                            if (Net.signedIn) "Đã đăng nhập Google" else "Khách · chưa đăng nhập",
                            color = if (Net.signedIn) GreenOk else TextMuted,
                            fontSize = 12.sp,
                        )
                    }
                    TextButton(onClick = { editing = true }) { Text("✎ Sửa", color = Gold, fontWeight = FontWeight.Bold) }
                }
            }
        }
        if (!Net.signedIn) {
            GoldButton(
                modifier = Modifier.fillMaxWidth().height(52.dp),
                onClick = {
                    if (signing || activity == null) return@GoldButton
                    signing = true
                    scope.launch {
                        val err = Net.signInWithGoogle(activity)
                        signing = false
                        if (err == null) {
                            name = prefs.name
                            onNameChanged()
                            Toast.makeText(ctx, "Đã đăng nhập", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(ctx, err, Toast.LENGTH_LONG).show()
                        }
                    }
                },
            ) {
                Text(
                    if (signing) "Đang đăng nhập…" else "G  Đăng nhập Google để thi trực tuyến",
                    color = Ink,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            var real by remember { mutableStateOf<List<Pair<vn.dautruongtrithuc.data.NetUser, Int>>>(emptyList()) }
            LaunchedEffect(Net.uid) { real = Net.topUsers(0) }
            val rank = remember(real) { Leaderboard.myRank(prefs, 0, real) }
            Stat("Điểm xếp hạng", "${prefs.ladder.rating}", Modifier.weight(1f))
            Stat("Hạng", "#$rank", Modifier.weight(1f))
            Stat("Thắng / Trận", "${prefs.wins}/${prefs.played}", Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("BẠN BÈ", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
                modifier = Modifier.weight(1f))
            Text("${friends.size}", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        if (friends.isEmpty()) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(
                    "Chưa có bạn bè.\nBấm dấu + ở góc ảnh người chơi (trong trận hoặc bảng xếp hạng) là thêm bạn ngay.",
                    color = TextMuted,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
            }
        } else {
            fun onlineOf(id: String): Boolean = Net.friends[id]?.online ?: (Personas.get(id)?.let { Personas.isOnline(it) } == true)
            val sorted = friends.sortedByDescending { onlineOf(it) }
            sorted.forEachIndexed { i, f ->
                val pp = Personas.get(f)
                val nu = Net.friends[f]
                val fname = nu?.name ?: pp?.name ?: f
                val on = onlineOf(f)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x14FFFFFF))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(fname, pp?.palette ?: (i + 1), size = 40.dp, friend = true,
                        rating = nu?.let { "${it.rating}" } ?: pp?.let { "${prefs.ladder.ratingOf(it)}" },
                        art = nu?.avatar ?: pp?.avatar ?: -1, uid = nu?.uid)
                    Spacer(Modifier.width(22.dp))
                    Column(Modifier.weight(1f)) {
                        OneLine(fname, size = 15, weight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(if (on) GreenOk else Color(0x55FFFFFF)))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (on && nu?.inMatch == true) "Đang trong trận" else if (on) "Đang trực tuyến" else "Ngoại tuyến",
                                color = if (on) GreenOk else TextMuted,
                                fontSize = 12.sp,
                            )
                        }
                    }
                    TextButton(onClick = { removing = f }) { Text("Bỏ", color = TextMuted) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (picking) {
        AvatarPicker(prefs, onDismiss = { picking = false }) {
            picking = false
            onNameChanged()
        }
    }

    val r = removing
    if (r != null) {
        AlertDialog(
            onDismissRequest = { removing = null },
            containerColor = Navy1,
            title = { Text("Bỏ kết bạn?", color = TextMain, fontWeight = FontWeight.Bold) },
            text = { Text("Xóa ${Net.friends[r]?.name ?: Personas.get(r)?.name ?: r} khỏi danh sách bạn bè.", color = TextMuted) },
            confirmButton = {
                TextButton(onClick = {
                    if (Net.friends.containsKey(r)) Net.removeFriend(r) else prefs.removeFriend(r)
                    local = prefs.friends()
                    removing = null
                }) { Text("Bỏ", color = RedBad, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Thôi", color = Gold) } },
        )
    }
}

@Composable
private fun AvatarPicker(prefs: Prefs, onDismiss: () -> Unit, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val b64 = vn.dautruongtrithuc.data.Photos.fromGallery(ctx, uri)
            busy = false
            if (b64 != null) {
                vn.dautruongtrithuc.data.Photos.setMine(prefs, b64, "gallery")
                onDone()
            } else Toast.makeText(ctx, "Không đọc được ảnh này", Toast.LENGTH_SHORT).show()
        }
    }
    // Ảnh hoạt cảnh có sẵn: con vật, cảnh vật và một số khuôn mặt vẽ
    val arts = (0 until AvatarArt.ICON_COUNT).map { AvatarArt.ICON_BASE + it } + listOf(7, 23, 58, 91, 140, 233, 377, 610)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Navy1,
        title = { Text("Đổi ảnh đại diện", color = Gold, fontWeight = FontWeight.Black) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BlueButton(modifier = Modifier.fillMaxWidth().height(48.dp), onClick = {
                    if (!busy) pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("🖼  Chọn ảnh từ thư viện", color = TextMain, fontWeight = FontWeight.Bold) }
                BlueButton(modifier = Modifier.fillMaxWidth().height(48.dp), onClick = {
                    if (busy) return@BlueButton
                    busy = true
                    scope.launch {
                        val b64 = vn.dautruongtrithuc.data.Photos.fromGoogle()
                        busy = false
                        if (b64 != null) {
                            vn.dautruongtrithuc.data.Photos.setMine(prefs, b64, "google")
                        } else {
                            // Tài khoản không có ảnh: hiện chữ cái đầu tên
                            vn.dautruongtrithuc.data.Photos.setMine(prefs, "", "google")
                            Toast.makeText(ctx, "Tài khoản Google chưa có ảnh, sẽ hiện chữ cái đầu tên", Toast.LENGTH_SHORT).show()
                        }
                        onDone()
                    }
                }) { Text("G  Dùng ảnh Google", color = TextMain, fontWeight = FontWeight.Bold) }
                if (busy) Text("Đang xử lý ảnh…", color = TextMuted, fontSize = 13.sp)
                Text("Hoặc chọn ảnh hoạt cảnh", color = TextMuted, fontSize = 13.sp)
                arts.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { code ->
                            val on = prefs.photo.isBlank() && prefs.avatar == code
                            Box(
                                Modifier
                                    .clip(CircleShape)
                                    .border(3.dp, if (on) Gold else Color.Transparent, CircleShape)
                                    .clickable {
                                        vn.dautruongtrithuc.data.Photos.setMine(prefs, "", "art", code)
                                        onDone()
                                    }
                                    .padding(3.dp),
                            ) { Avatar(prefs.name.ifBlank { "B" }, 0, size = 50.dp, art = code) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Đóng", color = TextMuted) } },
    )
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    GlassCard(modifier) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = Gold, fontSize = 22.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text(label, color = TextMuted, fontSize = 11.sp, maxLines = 1)
        }
    }
}

// ============================================================ Màn Cài đặt

@Composable
private fun SettingsScreen(prefs: Prefs, fx: SoundFx, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var sound by remember { mutableStateOf(prefs.soundOn) }
    var voice by remember { mutableStateOf(prefs.voiceOn) }
    var readQ by remember { mutableStateOf(prefs.readQuestions) }
    var seen by remember { mutableIntStateOf(prefs.practiceSeen().size) }
    var wrongN by remember { mutableIntStateOf(prefs.wrong().size) }

    Column(
        Modifier
            .fillMaxSize()
            .background(ScreenBrush)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0x22FFFFFF))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Text("‹", color = TextMain, fontSize = 26.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(12.dp))
            Text("Cài đặt", color = Gold, fontSize = 24.sp, fontWeight = FontWeight.Black)
        }

        Text("ÂM THANH", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        GlassCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SettingRow("Âm thanh hiệu ứng", sound) { sound = it; prefs.soundOn = it }
                SettingRow("Giọng đọc chào mừng", voice) { voice = it; prefs.voiceOn = it }
                SettingRow("Đọc câu hỏi và đáp án", readQ) { readQ = it; prefs.readQuestions = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        (if (fx.voiceOk) "✓ " else "⚠ ") + fx.voiceStatus,
                        color = if (fx.voiceOk) GreenOk else GoldLight,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f),
                    )
                    if (fx.voiceOk) {
                        TextButton(onClick = { fx.say("Chào mừng bạn đến với Đấu Trường Trí Thức", force = true) }) {
                            Text("Nghe thử", color = Gold, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        TextButton(onClick = { fx.openVoiceSetup(ctx) }) {
                            Text("Cài giọng", color = Gold, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (!fx.voiceOk) {
                    Text(
                        "Cài \"Dịch vụ lời nói của Google\" và tải gói tiếng Việt, rồi chọn nó làm bộ đọc mặc định. Quay lại app sẽ tự nhận.",
                        color = TextMuted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                }
            }
        }

        Text("CÂU HỎI", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Câu đã gặp khi luyện tập", color = TextMain, fontWeight = FontWeight.SemiBold)
                    Text("$seen / ${QuestionBank.all.size} · cần ôn lại: $wrongN", color = TextMuted, fontSize = 13.sp)
                }
                TextButton(onClick = {
                    prefs.resetPractice()
                    seen = 0
                    wrongN = 0
                    Toast.makeText(ctx, "Đã làm mới danh sách câu hỏi", Toast.LENGTH_SHORT).show()
                }) { Text("Làm mới", color = Gold, fontWeight = FontWeight.Bold) }
            }
        }

        if (Net.signedIn) {
            Text("TÀI KHOẢN", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Đã đăng nhập Google", color = TextMain, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        Net.signOut()
                        Toast.makeText(ctx, "Đã đăng xuất", Toast.LENGTH_SHORT).show()
                    }) { Text("Đăng xuất", color = RedBad, fontWeight = FontWeight.Bold) }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Phiên bản ${appVersion(ctx)}",
            color = TextMuted.copy(alpha = 0.6f),
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

private fun appVersion(ctx: android.content.Context): String = try {
    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
} catch (e: Exception) {
    ""
}

@Composable
private fun SettingRow(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextMain, modifier = Modifier.weight(1f))
        Switch(
            checked = on,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Ink,
                checkedTrackColor = Gold,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = Navy2,
            ),
        )
    }
}
