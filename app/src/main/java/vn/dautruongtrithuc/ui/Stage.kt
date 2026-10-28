package vn.dautruongtrithuc.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.dautruongtrithuc.game.PlayerState
import kotlin.math.abs

/** Sân khấu: đèn, màn LED, sàn, khán giả và các bục thí sinh xếp vòng cung. */
@Composable
fun Stage(
    players: List<PlayerState>,
    turnId: Int?,
    starId: Int? = null,
    locked: Set<Int>,
    timeFraction: Float,
    timeText: String,
    showTimer: Boolean,
    modifier: Modifier = Modifier,
    friends: Set<String> = emptySet(),
    onAddFriend: (PlayerState) -> Unit = {},
) {
    BoxWithConstraints(modifier.clip(RoundedCornerShape(18.dp))) {
        Canvas(Modifier.matchParentSize()) { drawStage() }
        if (showTimer) {
            TimerRing(
                fraction = timeFraction,
                text = timeText,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
            )
        }
        val w = maxWidth
        val h = maxHeight
        val xs = when (players.size) {
            1 -> listOf(0.5f)
            2 -> listOf(0.3f, 0.7f)
            3 -> listOf(0.2f, 0.5f, 0.8f)
            else -> listOf(0.13f, 0.38f, 0.62f, 0.87f)
        }
        val podW = 78.dp
        val podH = 125.dp
        val top = players.maxOfOrNull { it.score } ?: 0
        val leaders = if (top > 0) players.filter { it.score == top }.map { it.id }.toSet() else emptySet()
        players.forEachIndexed { i, p ->
            val x = xs.getOrElse(i) { 0.5f }
            val edge = abs(x - 0.5f) * 2f
            val baseY = h * 0.70f + (edge * 22f).dp
            Podium(
                player = p,
                isTurn = p.id == turnId,
                isLocked = p.id in locked,
                isFriend = if (p.id == 0) null else p.friendKey?.let { it in friends },
                isLeader = p.id in leaders,
                hasStar = p.id == starId,
                onAddFriend = { onAddFriend(p) },
                modifier = Modifier
                    .width(podW)
                    .offset(x = w * x - podW / 2, y = baseY - podH),
            )
        }
    }
}

@Composable
private fun Podium(
    player: PlayerState,
    isTurn: Boolean,
    isLocked: Boolean,
    isFriend: Boolean?,
    isLeader: Boolean,
    hasStar: Boolean,
    onAddFriend: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.zIndex(2f)) { ScorePopup(player.score) }
        Box {
            Avatar(
                player.name, player.palette, size = 40.dp, ring = isTurn,
                rating = when {
                    player.personaId != null || player.uid != null || player.id == 0 -> "${player.rating}"
                    else -> "BOT"
                },
                friend = isFriend,
                onAddFriend = onAddFriend,
                art = player.avatar,
                cap = true,
                uid = player.uid,
                me = player.id == 0,
            )
            if (hasStar) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 8.dp, y = (-4).dp)
                        .size(20.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(Navy1)
                        .border(1.5.dp, Gold, androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text("★", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Black) }
            }
            if (isLocked) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 30.dp, y = (-10).dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Navy1)
                        .border(1.dp, Gold, RoundedCornerShape(8.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                ) {
                    Text("✓ Đã chọn", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        val faceBrush = if (isTurn) {
            Brush.verticalGradient(listOf(GoldMid, Color(0xFFC98A12), Color(0xFF8A5A06)))
        } else {
            Brush.verticalGradient(listOf(Color(0xFF2A5BC4), Color(0xFF163A8A), Color(0xFF0E275F)))
        }
        val textColor = if (isTurn) Color(0xFF2A1A00) else TextMain
        val shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (isTurn) 66.dp else 62.dp)
                .clip(shape)
                .background(faceBrush)
                .border(if (isLeader) 2.dp else 0.dp, if (isLeader) GoldLight else Color.Transparent, shape),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.padding(horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    player.name,
                    color = textColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                LedScore(player.score)
            }
        }
    }
}

@Composable
private fun TimerRing(fraction: Float, text: String, modifier: Modifier) {
    Box(modifier.size(64.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val sw = 6.dp.toPx()
            drawCircle(Color(0xFF081A44), radius = size.minDimension / 2 - sw / 2)
            drawArc(
                color = Color.White.copy(alpha = 0.14f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(sw / 2, sw / 2),
                size = Size(size.width - sw, size.height - sw),
                style = Stroke(sw),
            )
            drawArc(
                color = if (fraction < 0.3f) RedBad else Gold,
                startAngle = -90f,
                sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(sw / 2, sw / 2),
                size = Size(size.width - sw, size.height - sw),
                style = Stroke(sw, cap = StrokeCap.Round),
            )
        }
        Text(text, color = GoldLight, fontSize = 22.sp, fontWeight = FontWeight.Black)
    }
}

private fun DrawScope.drawStage() {
    val w = size.width
    val h = size.height
    drawRect(
        Brush.radialGradient(
            colors = listOf(Color(0xFF1F4CA6), Color(0xFF0E2763), Color(0xFF071637)),
            center = Offset(w / 2, h * 0.3f),
            radius = w * 0.9f,
        )
    )
    // Màn LED phía sau
    val ledLeft = w * 0.12f
    val ledTop = h * 0.09f
    val ledW = w * 0.76f
    val ledH = h * 0.34f
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Color(0xFF123274), Color(0xFF0A1E4D)), startY = ledTop, endY = ledTop + ledH),
        topLeft = Offset(ledLeft, ledTop),
        size = Size(ledW, ledH),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
    )
    val grid = 12.dp.toPx()
    var gx = ledLeft
    while (gx < ledLeft + ledW) {
        drawLine(SkyLine.copy(alpha = 0.08f), Offset(gx, ledTop), Offset(gx, ledTop + ledH), 1.dp.toPx())
        gx += grid
    }
    var gy = ledTop
    while (gy < ledTop + ledH) {
        drawLine(SkyLine.copy(alpha = 0.08f), Offset(ledLeft, gy), Offset(ledLeft + ledW, gy), 1.dp.toPx())
        gy += grid
    }
    // Sàn sân khấu
    val floorTop = h * 0.60f
    val floorBottom = h * 0.88f
    val floor = Path().apply {
        moveTo(w * 0.08f, floorTop)
        lineTo(w * 0.92f, floorTop)
        lineTo(w, floorBottom)
        lineTo(0f, floorBottom)
        close()
    }
    drawPath(floor, Brush.verticalGradient(listOf(Color(0xFF1A4296), Color(0xFF0D2766)), startY = floorTop, endY = floorBottom))
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(Color(0x66AAD2FF), Color.Transparent),
            center = Offset(w / 2, h * 0.78f),
            radius = w * 0.3f,
        ),
        topLeft = Offset(w * 0.2f, h * 0.70f),
        size = Size(w * 0.6f, h * 0.16f),
    )
    // Đường viền cong ôm các bục
    drawArc(
        color = SkyLine,
        startAngle = 195f,
        sweepAngle = 150f,
        useCenter = false,
        topLeft = Offset(w * 0.04f, h * 0.58f),
        size = Size(w * 0.92f, h * 0.42f),
        style = Stroke(3.dp.toPx()),
    )
    // Mép sân khấu phát sáng
    drawLine(GoldMid, Offset(0f, floorBottom), Offset(w, floorBottom), 3.dp.toPx())
    // Chùm đèn rọi
    val beams = listOf(
        Triple(w * 0.12f, w * 0.22f, Color(0x73FFD66E)),
        Triple(w * 0.5f, w * 0.5f, Color(0x40B4D7FF)),
        Triple(w * 0.88f, w * 0.78f, Color(0x40B4D7FF)),
    )
    for ((top, bottom, c) in beams) {
        val beam = Path().apply {
            moveTo(top - 6.dp.toPx(), 0f)
            lineTo(top + 6.dp.toPx(), 0f)
            lineTo(bottom + 55.dp.toPx(), floorBottom)
            lineTo(bottom - 55.dp.toPx(), floorBottom)
            close()
        }
        drawPath(beam, Brush.verticalGradient(listOf(c, Color.Transparent), startY = 0f, endY = floorBottom))
    }
    // Dàn đèn
    val lamps = 7
    for (i in 0 until lamps) {
        val cx = w * (0.1f + 0.8f * i / (lamps - 1))
        val c = if (i % 2 == 0) Color(0xFFFFF3C4) else Color(0xFFD6EBFF)
        drawCircle(c.copy(alpha = 0.3f), radius = 9.dp.toPx(), center = Offset(cx, 9.dp.toPx()))
        drawCircle(c, radius = 4.dp.toPx(), center = Offset(cx, 9.dp.toPx()))
    }
    // Khán giả
    val back = Color(0xFF0B1B3E)
    val front = Color(0xFF02081A)
    drawRect(back, topLeft = Offset(0f, h * 0.94f), size = Size(w, h * 0.06f))
    val step = 30.dp.toPx()
    var x = step / 2
    while (x < w + step) {
        drawCircle(back, radius = 12.dp.toPx(), center = Offset(x, h * 0.935f))
        drawCircle(front, radius = 14.dp.toPx(), center = Offset(x - step / 2, h * 0.985f))
        x += step
    }
}


private val LedOn = Color(0xFF39FF6A)
private val LedOff = Color(0xFF0B2E14)

// Các đoạn của chữ số 7 đoạn trong khung 20×36
private val SEGS = mapOf(
    'a' to listOf(2f to 0f, 18f to 0f, 15f to 3.6f, 5f to 3.6f),
    'b' to listOf(20f to 1.5f, 20f to 17f, 16.4f to 15f, 16.4f to 4.5f),
    'c' to listOf(20f to 19f, 20f to 34.5f, 16.4f to 31.5f, 16.4f to 21f),
    'd' to listOf(2f to 36f, 18f to 36f, 15f to 32.4f, 5f to 32.4f),
    'e' to listOf(0f to 19f, 0f to 34.5f, 3.6f to 31.5f, 3.6f to 21f),
    'f' to listOf(0f to 1.5f, 0f to 17f, 3.6f to 15f, 3.6f to 4.5f),
    'g' to listOf(2f to 18f, 5f to 16.2f, 15f to 16.2f, 18f to 18f, 15f to 19.8f, 5f to 19.8f),
)
private val DIGITS = mapOf(
    '0' to "abcdef", '1' to "bc", '2' to "abged", '3' to "abgcd", '4' to "fgbc",
    '5' to "afgcd", '6' to "afgedc", '7' to "abc", '8' to "abcdefg", '9' to "abcdfg", '-' to "g",
)

/** Điểm hiển thị kiểu đồng hồ điện tử, số chạy dần tới điểm mới. */
@Composable
fun LedScore(score: Int, modifier: Modifier = Modifier) {
    val shown by animateIntAsState(score, animationSpec = tween(550), label = "led")
    val text = shown.coerceIn(-99, 999).toString().padStart(3, ' ')
    Box(
        modifier
            .clip(RoundedCornerShape(5.dp))
            .background(Color(0xFF040805))
            .border(1.dp, Color(0xFF1F3A26), RoundedCornerShape(5.dp))
            .padding(horizontal = 5.dp, vertical = 4.dp),
    ) {
        Canvas(Modifier.width(52.dp).height(24.dp)) {
            val cell = size.width / 3f
            val sc = minOf((cell - 3.dp.toPx()) / 20f, size.height / 36f)
            val dw = 20f * sc
            for ((i, ch) in text.withIndex()) {
                val lit = DIGITS[ch] ?: ""
                val ox = i * cell + (cell - dw) / 2f
                val oy = (size.height - 36f * sc) / 2f
                for ((name, pts) in SEGS) {
                    val path = Path().apply {
                        pts.forEachIndexed { k, (px, py) ->
                            val x = ox + px * sc
                            val y = oy + py * sc
                            if (k == 0) moveTo(x, y) else lineTo(x, y)
                        }
                        close()
                    }
                    drawPath(path, if (name in lit) LedOn else LedOff)
                }
            }
        }
    }
}

/** "+10" / "−1" bay lên khi điểm thay đổi. */
@Composable
private fun ScorePopup(score: Int) {
    var prev by remember { mutableIntStateOf(score) }
    var delta by remember { mutableIntStateOf(0) }
    val anim = remember { Animatable(1f) }
    LaunchedEffect(score) {
        val d = score - prev
        prev = score
        if (d != 0) {
            delta = d
            anim.snapTo(0f)
            anim.animateTo(1f, tween(1200))
        }
    }
    Box(Modifier.height(18.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
        if (delta != 0 && anim.value < 1f) {
            Text(
                if (delta > 0) "+$delta" else "−${-delta}",
                color = (if (delta > 0) LedOn else RedBad).copy(alpha = (1f - anim.value).coerceIn(0f, 1f)),
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.offset(y = (6f - 14f * anim.value).dp),
            )
        }
    }
}
