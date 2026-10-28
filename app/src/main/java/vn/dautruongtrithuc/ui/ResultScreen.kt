package vn.dautruongtrithuc.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.dautruongtrithuc.game.PlayerState
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun ResultScreen(
    players: List<PlayerState>,
    winners: Set<Int>,
    solo: Boolean,
    ranked: Boolean = false,
    room: Boolean = false,
    friends: Set<String> = emptySet(),
    onAddFriend: (PlayerState) -> Unit = {},
    onReplay: () -> Unit,
    onHome: () -> Unit,
) {
    val me = players.firstOrNull { it.id == 0 }
    Box(Modifier.fillMaxSize().background(ScreenBrush)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(10.dp))
            Text(
                if (solo) "HOÀN THÀNH" else "KẾT QUẢ",
                color = Gold,
                fontSize = 40.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            if (solo && me != null) {
                Spacer(Modifier.height(8.dp))
                Text("Bạn đạt ${me.score} điểm", color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(30.dp))
                Avatar(me.name, me.palette, size = 96.dp, art = me.avatar, laurel = true, me = true)
                Spacer(Modifier.height(30.dp))
            } else {
                Spacer(Modifier.height(18.dp))
                PodiumRow(players, winners, ranked, friends, onAddFriend)
            }

            Spacer(Modifier.height(18.dp))
            Text(
                when {
                    ranked && me != null -> {
                        val d = me.ratingDelta ?: 0
                        "Điểm xếp hạng: ${me.rating} → ${me.rating + d} (+$d)"
                    }
                    else -> "Trận luyện tập không tính điểm xếp hạng."
                },
                color = TextMain,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BlueButton(modifier = Modifier.weight(1f).height(52.dp), onClick = onHome) {
                    Text("Về sảnh", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                GoldButton(modifier = Modifier.weight(1f).height(52.dp), onClick = onReplay) {
                    Text(
                        if (room) "Phòng mới" else if (ranked) "Trận mới" else "Chơi lại",
                        color = Ink,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        Confetti(Modifier.fillMaxSize())
    }
}

/** Bục xếp hạng: nhì – nhất – ba – tư (chỉ hiện đúng số người). */
@Composable
private fun PodiumRow(
    players: List<PlayerState>,
    winners: Set<Int>,
    ranked: Boolean,
    friends: Set<String>,
    onAddFriend: (PlayerState) -> Unit,
) {
    val sorted = players.sortedByDescending { it.score }
    val order = when (sorted.size) {
        1 -> listOf(0)
        2 -> listOf(1, 0)
        3 -> listOf(1, 0, 2)
        else -> listOf(1, 0, 2, 3)
    }.filter { it < sorted.size }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Bottom,
    ) {
        for (i in order) {
            val p = sorted[i]
            val rank = sorted.count { it.score > p.score } + 1
            val win = p.id in winners
            Column(
                Modifier.width(if (win) 96.dp else 78.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Label(if (win) "CHIẾN THẮNG" else "CHÚC MỪNG", win)
                Spacer(Modifier.height(if (win) 30.dp else 26.dp))
                Avatar(
                    p.name, p.palette,
                    size = if (win) 64.dp else 50.dp,
                    rating = if (p.personaId != null || p.uid != null || p.id == 0) "${p.rating + (p.ratingDelta ?: 0)}" else null,
                    friend = if (p.id == 0) null else p.friendKey?.let { it in friends },
                    onAddFriend = { onAddFriend(p) },
                    art = p.avatar,
                    cap = !win,
                    laurel = win,
                    uid = p.uid,
                    me = p.id == 0,
                    ring = p.id == 0,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    if (p.id == 0) "${p.name} (bạn)" else p.name,
                    color = TextMain,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${p.score} điểm" + (p.ratingDelta?.takeIf { ranked }?.let { " · +$it" } ?: ""),
                    color = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                )
                Spacer(Modifier.height(6.dp))
                PodiumBlock(rank, height = when (rank) { 1 -> 118.dp; 2 -> 92.dp; 3 -> 72.dp; else -> 56.dp })
            }
        }
    }
}

@Composable
private fun Label(text: String, win: Boolean) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .shadow(if (win) 10.dp else 6.dp, shape, ambientColor = if (win) Gold else SkyLine, spotColor = if (win) Gold else SkyLine)
            .clip(shape)
            .background(
                if (win) Brush.verticalGradient(listOf(Color(0xFFFFE48A), Color(0xFFF2B52B)))
                else Brush.verticalGradient(listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8)))
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (win) Color(0xFF2A1A00) else Color.White,
            fontSize = if (win) 13.sp else 11.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun PodiumBlock(rank: Int, height: Dp) {
    val top = when (rank) {
        1 -> Color(0xFFFFD45E)
        2 -> Color(0xFFC9D0DD)
        3 -> Color(0xFFD9925B)
        else -> Color(0xFF5B6E99)
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
            .background(Brush.verticalGradient(listOf(top, top.copy(alpha = 0.55f), Color(0x33000000))))
            .border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)),
        contentAlignment = Alignment.TopCenter,
    ) {
        Text("$rank", color = Color(0xFF1B1B3A), fontSize = 30.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 6.dp))
    }
}

private class Piece(val x: Float, val delay: Float, val speed: Float, val sway: Float, val size: Float, val color: Color, val spin: Float)

/** Pháo giấy rơi một lượt khi vào màn kết quả. */
@Composable
private fun Confetti(modifier: Modifier) {
    val progress = remember { Animatable(0f) }
    val pieces = remember {
        val r = Random(System.nanoTime())
        val colors = listOf(0xFFFFD45E, 0xFFF472B6, 0xFF60A5FA, 0xFF4ADE80, 0xFFFB923C, 0xFFFFFFFF).map { Color(it) }
        List(110) {
            Piece(
                x = r.nextFloat(),
                delay = r.nextFloat() * 0.35f,
                speed = 0.75f + r.nextFloat() * 0.6f,
                sway = r.nextFloat() * 6.28f,
                size = 5f + r.nextFloat() * 6f,
                color = colors.random(r),
                spin = r.nextFloat() * 720f - 360f,
            )
        }
    }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(4200, easing = LinearEasing)) }
    if (progress.value >= 1f) return
    Canvas(modifier) {
        val t = progress.value
        for (p in pieces) {
            val local = ((t - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
            if (local <= 0f || local >= 1f) continue
            val y = -20f + local * p.speed * (size.height + 60f)
            if (y > size.height + 20f) continue
            val x = p.x * size.width + sin(p.sway + local * 9f) * 24f
            val w = p.size.dp.toPx()
            rotate(p.spin * local, Offset(x, y)) {
                drawRect(p.color.copy(alpha = 0.95f), Offset(x - w / 2, y - w), Size(w, w * 1.7f))
            }
        }
    }
}
