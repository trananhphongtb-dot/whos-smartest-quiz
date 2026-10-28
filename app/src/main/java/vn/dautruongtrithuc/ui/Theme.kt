package vn.dautruongtrithuc.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Navy0 = Color(0xFF050D24)
val Navy1 = Color(0xFF0B2052)
val Navy2 = Color(0xFF123873)
val Navy3 = Color(0xFF1C3F86)
val Royal = Color(0xFF1D4597)
val Gold = Color(0xFFFFD45E)
val GoldLight = Color(0xFFFFE38C)
val GoldMid = Color(0xFFF5BC36)
val GoldDark = Color(0xFFE9A31C)
val Ink = Color(0xFF3A2300)
val TextMain = Color(0xFFEAF0FF)
val TextMuted = Color(0xFFA9BCE6)
val GreenOk = Color(0xFF22C55E)
val RedBad = Color(0xFFF43F5E)
val SkyLine = Color(0xFF8CC3FF)

val ScreenBrush = Brush.radialGradient(
    colors = listOf(Color(0xFF1D4597), Color(0xFF0B2052), Navy0),
    center = Offset(540f, 0f),
    radius = 1700f,
)
val GoldBrush = Brush.verticalGradient(listOf(GoldLight, GoldMid, GoldDark))
val BlueBtnBrush = Brush.verticalGradient(listOf(Color(0xFF1E4590), Color(0xFF122C66)))

/** Bảng màu ảnh đại diện: đầu, cuối, màu chữ. */
val AvatarPalettes = listOf(
    Triple(Color(0xFFFFE7B0), Color(0xFFFBBF24), Color(0xFF4A2A00)),
    Triple(Color(0xFFC9F7E6), Color(0xFF2DD4BF), Color(0xFF04302D)),
    Triple(Color(0xFFE4DCFF), Color(0xFFA78BFA), Color(0xFF24104F)),
    Triple(Color(0xFFFFD6E8), Color(0xFFF472B6), Color(0xFF4A0A2A)),
    Triple(Color(0xFFD3ECFF), Color(0xFF60A5FA), Color(0xFF0B2A55)),
    Triple(Color(0xFFFFE0CC), Color(0xFFFB923C), Color(0xFF4A1E00)),
    Triple(Color(0xFFDCFCE7), Color(0xFF4ADE80), Color(0xFF07361A)),
    Triple(Color(0xFFFDE2E2), Color(0xFFF87171), Color(0xFF4C0A0A)),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Gold,
            onPrimary = Ink,
            background = Navy0,
            onBackground = TextMain,
            surface = Navy1,
            onSurface = TextMain,
            surfaceVariant = Navy2,
            onSurfaceVariant = TextMuted,
        ),
        content = content,
    )
}

/**
 * Ảnh đại diện. [rating] hiện ở góc dưới bên phải (đè một phần ảnh).
 * [friend]: null = không hiện nút; false = hiện dấu + (bấm để kết bạn); true = đã là bạn (dấu ✓).
 */
@Composable
fun Avatar(
    name: String,
    palette: Int,
    size: Dp,
    ring: Boolean = false,
    rating: String? = null,
    friend: Boolean? = null,
    onAddFriend: (() -> Unit)? = null,
    art: Int = -1,
    cap: Boolean = false,
    laurel: Boolean = false,
    uid: String? = null,          // người chơi thật: lấy ảnh từ máy chủ
    me: Boolean = false,          // chính mình: ảnh Google / thư viện
) {
    val photo = if (me) vn.dautruongtrithuc.data.Photos.mine else vn.dautruongtrithuc.data.Photos.of(uid)
    val p = AvatarPalettes[palette % AvatarPalettes.size]
    Box(Modifier.size(size)) {
        if (laurel) AvatarArt.Laurel(Modifier.matchParentSize())
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(p.first, p.second)))
                .border(if (ring) 3.dp else 2.dp, if (ring) GoldMid else Color.White.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (photo != null) {
                androidx.compose.foundation.Image(
                    bitmap = photo,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            } else if (art >= 0) {
                AvatarArt.Draw(art, Modifier.matchParentSize())
            } else Text(
                text = name.trim().take(1).uppercase(),
                color = p.third,
                fontWeight = FontWeight.ExtraBold,
                fontSize = (size.value * 0.42f).sp,
            )
        }
        if (cap) AvatarArt.Cap(Modifier.matchParentSize())
        if (rating != null) {
            // Ảnh nhỏ (trên bục thi): ô điểm nhỏ, nằm sát mép để không che mặt
            val small = size < 56.dp
            Pill(
                rating,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = if (small) size * 0.42f else size * 0.22f, y = if (small) size * 0.12f else 2.dp),
                fontSize = if (small) (size.value * 0.19f).coerceIn(7f, 9f) else 10f,
                padH = if (small) 4.dp else 7.dp,
            )
        }
        if (friend != null) {
            val badge = (size.value * 0.36f).coerceIn(16f, 24f).dp
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .offset(x = -badge * 0.25f, y = -badge * 0.25f)
                    .size(badge)
                    .clip(CircleShape)
                    .background(if (friend) GreenOk else Gold)
                    .border(1.5.dp, Navy0, CircleShape)
                    .clickable(enabled = !friend && onAddFriend != null) { onAddFriend?.invoke() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (friend) "✓" else "+",
                    color = if (friend) Color.White else Ink,
                    fontSize = (badge.value * 0.62f).sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, fontSize: Float = 10f, padH: Dp = 7.dp) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(GoldBrush)
            .padding(horizontal = padH, vertical = 0.dp),
    ) {
        Text(text, color = Ink, fontSize = fontSize.sp, lineHeight = (fontSize + 2).sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}

@Composable
fun GoldButton(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) GoldBrush else Brush.verticalGradient(listOf(Color(0xFF8A7A4A), Color(0xFF6B5A2A))))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
fun BlueButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(BlueBtnBrush)
            .border(BorderStroke(1.dp, Color(0x4D8CB4FF)), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(Color(0x17FFFFFF), Color(0x09FFFFFF))))
            .border(1.dp, Color(0x21FFFFFF), RoundedCornerShape(18.dp))
            .padding(12.dp),
        content = content,
    )
}

@Composable
fun OneLine(text: String, color: Color = TextMain, size: Int = 13, weight: FontWeight = FontWeight.Normal) {
    Text(text, color = color, fontSize = size.sp, fontWeight = weight, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
