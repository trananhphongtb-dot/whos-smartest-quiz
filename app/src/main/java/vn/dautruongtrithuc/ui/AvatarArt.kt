package vn.dautruongtrithuc.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import kotlin.random.Random

/**
 * Ảnh đại diện vẽ bằng code (khung 100×100).
 * Mã ảnh: < 0 = chữ cái đầu tên; 0..9999 = mặt hoạt hình (seed); 10000 + k = con vật/cảnh vật thứ k.
 */
object AvatarArt {
    const val ICON_BASE = 10000
    val ICON_COUNT get() = icons.size

    private sealed interface Op
    private class C(val x: Float, val y: Float, val r: Float, val fill: Color?, val stroke: Color? = null, val sw: Float = 0f) : Op
    private class E(val x: Float, val y: Float, val rx: Float, val ry: Float, val fill: Color, val rot: Float = 0f) : Op
    private class P(val d: String, val fill: Color?, val stroke: Color? = null, val sw: Float = 0f, val rot: Float = 0f, val rcx: Float = 50f, val rcy: Float = 50f) : Op

    private val pathCache = HashMap<String, Path>()
    private fun path(d: String): Path = pathCache.getOrPut(d) { PathParser().parsePathString(d).toPath() }

    private fun c(hex: Long) = Color(hex)

    private val SK = listOf(0xFFFFE0C4, 0xFFF8D0A8, 0xFFEDB98A, 0xFFD49A6A, 0xFFB87A50).map(::c)
    private val HAIR = listOf(0xFF2B1B12, 0xFF3B2416, 0xFF121212, 0xFF6B3E1F, 0xFFA0522D, 0xFFC9A46A).map(::c)
    private val BG = listOf(0xFF60A5FA, 0xFFF472B6, 0xFF34D399, 0xFFFBBF24, 0xFFA78BFA, 0xFFFB923C, 0xFF38BDF8, 0xFFF87171).map(::c)
    private val INK = c(0xFF222222)
    private val LIP = c(0xFF7A2E2E)

    private val faceCache = HashMap<Int, List<Op>>()

    private fun face(seed: Int): List<Op> = faceCache.getOrPut(seed) {
        val r = Random(seed * 31 + 5)
        val bg = BG.random(r); val sk = SK.random(r); val hc = HAIR.random(r)
        val st = r.nextInt(6)
        val o = ArrayList<Op>()
        o += C(50f, 50f, 50f, bg)
        if (st == 1 || st == 4) o += P("M22 48 Q20 88 34 92 L66 92 Q80 88 78 48 Z", hc)
        o += P("M43 68 L57 68 L57 82 L43 82 Z", sk)
        o += P("M20 100 Q22 80 50 80 Q78 80 80 100 Z", BG.random(r))
        o += E(50f, 50f, 25f, 27f, sk)
        o += E(25f, 52f, 4f, 6f, sk); o += E(75f, 52f, 4f, 6f, sk)
        o += when (st) {
            0 -> P("M24 46 Q26 20 50 20 Q74 20 76 46 Q66 32 50 34 Q36 34 24 46Z", hc)
            1 -> P("M24 50 Q24 20 50 20 Q76 20 76 50 Q70 30 56 32 Q50 40 38 34 Q30 38 24 50Z", hc)
            2 -> P("M24 44 L28 24 L36 32 L42 18 L50 30 L58 17 L64 31 L72 22 L76 44 Q50 30 24 44Z", hc)
            3 -> P("M24 46 Q26 20 50 20 Q74 20 76 46 Q60 36 30 40Z", hc)
            4 -> P("M24 52 Q22 20 50 20 Q78 20 76 52 Q72 34 50 32 Q28 34 24 52Z", hc)
            else -> P("M25 42 Q30 22 50 22 Q70 22 75 42 Q50 34 25 42Z", hc)
        }
        if (st == 3) o += C(50f, 16f, 9f, hc)
        val e = r.nextInt(3); val m = r.nextInt(3)
        val happyL = "M36 53 Q40 48 44 53"; val happyR = "M56 53 Q60 48 64 53"
        when (e) {
            0 -> { o += C(40f, 52f, 3.2f, INK); o += C(60f, 52f, 3.2f, INK) }
            1 -> { o += P(happyL, null, INK, 2.6f); o += P(happyR, null, INK, 2.6f) }
            else -> { o += C(40f, 52f, 3.2f, INK); o += P("M56 53 Q60 49 64 53", null, INK, 2.6f) }
        }
        o += P("M35 45 Q40 42 45 45", null, hc, 2f); o += P("M55 45 Q60 42 65 45", null, hc, 2f)
        when (m) {
            0 -> o += P("M42 63 Q50 70 58 63", null, LIP, 2.6f)
            1 -> { o += P("M41 62 Q50 74 59 62 Z", LIP); o += P("M44 63 L56 63", null, Color.White, 2f) }
            else -> o += E(50f, 64f, 4f, 3f, LIP)
        }
        val blush = Color(0x73FF8A8A)
        o += C(34f, 60f, 4f, blush); o += C(66f, 60f, 4f, blush)
        if (r.nextFloat() < 0.3f) {
            o += C(40f, 52f, 7f, null, INK, 2f); o += C(60f, 52f, 7f, null, INK, 2f)
            o += P("M47 52 L53 52", null, INK, 2f)
        } else if ((st == 1 || st == 3 || st == 4) && r.nextFloat() < 0.5f) {
            o += P("M66 26 L76 20 L76 32 Z M66 26 L56 20 L56 32Z", c(0xFFFF4D8D))
            o += C(66f, 26f, 3f, c(0xFFFF86B0))
        } else if (st == 5 && r.nextFloat() < 0.5f) {
            // mũ lưỡi trai
            val cap = BG.random(r)
            o += P("M24 40 Q26 16 50 16 Q74 16 76 40 Z", cap)
            o += P("M50 38 Q76 34 88 42 Q74 44 50 42 Z", cap)
        }
        o
    }

    private val icons: List<List<Op>> = listOf(
        // mèo
        listOf(
            C(50f, 50f, 50f, c(0xFFFDE68A)),
            P("M26 40 L30 16 L46 32 Z M74 40 L70 16 L54 32Z", c(0xFFF59E0B)),
            E(50f, 55f, 28f, 25f, c(0xFFF59E0B)),
            E(40f, 52f, 4f, 5f, INK), E(60f, 52f, 4f, 5f, INK),
            P("M47 61 L53 61 L50 64Z", c(0xFFC2410C)),
            P("M50 64 Q46 69 42 66 M50 64 Q54 69 58 66", null, INK, 1.6f),
            P("M30 60 L18 58 M30 64 L18 66 M70 60 L82 58 M70 64 L82 66", null, INK, 1.4f),
        ),
        // gấu trúc
        listOf(
            C(50f, 50f, 50f, c(0xFFBBF7D0)),
            C(28f, 30f, 10f, INK), C(72f, 30f, 10f, INK),
            C(50f, 54f, 28f, Color.White),
            E(39f, 52f, 7f, 9f, INK, -20f), E(61f, 52f, 7f, 9f, INK, 20f),
            C(40f, 51f, 2.2f, Color.White), C(60f, 51f, 2.2f, Color.White),
            E(50f, 63f, 4f, 3f, INK), P("M45 68 Q50 72 55 68", null, INK, 2f),
        ),
        // chó
        listOf(
            C(50f, 50f, 50f, c(0xFFBFDBFE)),
            E(26f, 46f, 9f, 18f, c(0xFF7C4A2D)), E(74f, 46f, 9f, 18f, c(0xFF7C4A2D)),
            E(50f, 52f, 24f, 26f, c(0xFFD6A26E)),
            E(50f, 64f, 13f, 10f, c(0xFFF3D9B8)),
            C(41f, 48f, 3.5f, INK), C(59f, 48f, 3.5f, INK),
            E(50f, 60f, 5f, 3.5f, INK), P("M50 63 L50 67 M45 68 Q50 72 55 68", null, INK, 2f),
            P("M47 70 Q50 78 53 70Z", c(0xFFF472B6)),
        ),
        // cáo
        listOf(
            C(50f, 50f, 50f, c(0xFFFEF3C7)),
            P("M24 22 L40 38 L28 46Z M76 22 L60 38 L72 46Z", c(0xFFEA580C)),
            P("M22 40 Q50 30 78 40 L50 82Z", c(0xFFF97316)),
            P("M34 54 L50 82 L66 54 Q50 62 34 54Z", Color.White),
            C(40f, 50f, 3f, INK), C(60f, 50f, 3f, INK), C(50f, 78f, 3.5f, INK),
        ),
        // thỏ
        listOf(
            C(50f, 50f, 50f, c(0xFFE9D5FF)),
            E(38f, 22f, 7f, 20f, Color.White), E(62f, 22f, 7f, 20f, Color.White),
            E(38f, 22f, 3.5f, 14f, c(0xFFF9A8D4)), E(62f, 22f, 3.5f, 14f, c(0xFFF9A8D4)),
            E(50f, 60f, 24f, 22f, Color.White),
            C(41f, 56f, 3f, INK), C(59f, 56f, 3f, INK),
            P("M47 64 L53 64 L50 67Z", c(0xFFF472B6)), P("M50 67 Q46 72 43 69 M50 67 Q54 72 57 69", null, INK, 1.6f),
        ),
        // gấu nâu
        listOf(
            C(50f, 50f, 50f, c(0xFFFED7AA)),
            C(28f, 30f, 10f, c(0xFF92400E)), C(72f, 30f, 10f, c(0xFF92400E)),
            C(28f, 30f, 5f, c(0xFFD97706)), C(72f, 30f, 5f, c(0xFFD97706)),
            C(50f, 54f, 27f, c(0xFFB45309)),
            E(50f, 64f, 12f, 9f, c(0xFFFDE68A)),
            C(40f, 50f, 3.2f, INK), C(60f, 50f, 3.2f, INK), E(50f, 60f, 4.5f, 3.2f, INK),
        ),
        // núi
        listOf(
            C(50f, 50f, 50f, c(0xFF93C5FD)),
            C(68f, 32f, 9f, c(0xFFFDE047)),
            P("M0 80 L30 40 L48 62 L62 46 L100 82 L100 100 L0 100Z", c(0xFF1E3A8A)),
            P("M30 40 L38 51 L33 49 L28 53 L24 48Z", Color.White),
            P("M0 86 Q50 74 100 88 L100 100 L0 100Z", c(0xFF16A34A)),
        ),
        // biển hoàng hôn
        listOf(
            C(50f, 50f, 50f, c(0xFFFDBA74)),
            C(50f, 56f, 16f, c(0xFFFDE047)),
            P("M0 60 L100 60 L100 100 L0 100Z", c(0xFF0EA5E9)),
            P("M10 70 Q18 66 26 70 T42 70 M54 78 Q62 74 70 78 T86 78 M20 86 Q28 82 36 86 T52 86", null, Color.White, 2.4f),
        ),
        // hoa
        listOf(C(50f, 50f, 50f, c(0xFFFBCFE8)), P("M50 60 L50 92", null, c(0xFF16A34A), 4f), E(60f, 80f, 9f, 4f, c(0xFF22C55E), -30f)) +
            (0 until 6).map { P("M41 30 A9 15 0 1 0 59 30 A9 15 0 1 0 41 30 Z", c(0xFFEC4899), rot = it * 60f, rcx = 50f, rcy = 45f) } +
            listOf(C(50f, 45f, 8f, c(0xFFFDE047))),
        // bóng đá
        listOf(
            C(50f, 50f, 50f, c(0xFF4ADE80)),
            C(50f, 50f, 28f, Color.White, INK, 2f),
            P("M50 38 L61 46 L57 59 L43 59 L39 46Z", INK),
            P("M50 38 L50 23 M61 46 L75 41 M57 59 L66 71 M43 59 L34 71 M39 46 L25 41", null, INK, 2f),
        ),
        // sách
        listOf(
            C(50f, 50f, 50f, c(0xFFC4B5FD)),
            P("M18 34 Q34 28 50 36 L50 76 Q34 68 18 74Z", Color.White, c(0xFF4C1D95), 2.5f),
            P("M82 34 Q66 28 50 36 L50 76 Q66 68 82 74Z", c(0xFFF5F3FF), c(0xFF4C1D95), 2.5f),
            P("M25 44 L42 46 M25 52 L42 54 M58 46 L75 44 M58 54 L75 52", null, c(0xFFA78BFA), 2f),
        ),
        // trăng sao
        listOf(
            C(50f, 50f, 50f, c(0xFF1E1B4B)),
            P("M58 22 A28 28 0 1 0 74 66 A22 22 0 1 1 58 22Z", c(0xFFFDE047)),
            C(26f, 28f, 2f, Color.White), C(76f, 30f, 1.6f, Color.White), C(70f, 80f, 2f, Color.White),
        ),
        // cây
        listOf(
            C(50f, 50f, 50f, c(0xFFBAE6FD)),
            P("M45 62 L55 62 L55 90 L45 90Z", c(0xFF92400E)),
            C(50f, 42f, 22f, c(0xFF16A34A)), C(36f, 52f, 13f, c(0xFF22C55E)), C(64f, 52f, 13f, c(0xFF22C55E)),
            C(44f, 38f, 3f, c(0xFFF87171)), C(58f, 48f, 3f, c(0xFFF87171)), C(40f, 54f, 3f, c(0xFFF87171)),
        ),
        // ly trà sữa
        listOf(
            C(50f, 50f, 50f, c(0xFFFDE2E2)),
            P("M32 30 L68 30 L62 86 L38 86Z", c(0xFFE7C9A9)),
            P("M30 26 L70 26 L70 32 L30 32Z", c(0xFFF472B6)),
            P("M56 10 L52 30", null, c(0xFF7C3AED), 4f),
            C(42f, 76f, 3.5f, c(0xFF3B2416)), C(50f, 79f, 3.5f, c(0xFF3B2416)), C(58f, 75f, 3.5f, c(0xFF3B2416)),
        ),
        // ngôi sao
        listOf(
            C(50f, 50f, 50f, c(0xFF2563EB)),
            P("M50 18 L59 40 L83 41 L64 56 L71 80 L50 66 L29 80 L36 56 L17 41 L41 40Z", c(0xFFFDE047)),
        ),
    )

    // Mũ cử nhân (vẽ tràn lên trên khung ảnh, tọa độ theo khung 100×100 của ảnh)
    private val capOps: List<Op> = listOf(
        // Nhìn từ dưới lên: mép trước dày phía trên, thấy mặt dưới tấm vuông, thân mũ ngắn
        P("M-18 -21 L50 -37 L118 -21 L118 -16 L50 -32 L-18 -16 Z", c(0xFF3B82F6)),
        P("M-18 -16 L50 -32 L118 -16 L50 -2 Z", c(0xFF172F73), c(0xFF0B2A7A), 2f),
        P("M21 -14 L21 8 Q50 17 79 8 L79 -14 Q50 -6 21 -14 Z", c(0xFF1E40AF), c(0xFF0B2A7A), 1.5f),
        P("M21 3 Q50 12 79 3", null, c(0xFF3B5BDB), 2.5f),
        P("M112 -17 L112 14", null, c(0xFFFBBF24), 3f),
        P("M107 14 L117 14 L120 30 L104 30 Z", c(0xFFFBBF24)),
    )

    // Vòng nguyệt quế vàng hai nhánh ôm quanh ảnh, có nơ đỏ phía dưới
    private val laurelOps: List<Op> = buildList {
        for (side in listOf(-1, 1)) {
            for (k in 0 until 8) {
                val a = if (side < 0) 115f + k * 15f else 65f - k * 15f
                val rad = Math.toRadians(a.toDouble())
                val x = (50 + 57 * kotlin.math.cos(rad)).toFloat()
                val y = (50 + 57 * kotlin.math.sin(rad)).toFloat()
                add(E(x, y, 6f, 12f, c(0xFFFACC15), a + 90f + if (side < 0) 25f else -25f))
            }
        }
        add(P("M38 104 L50 98 L62 104 L58 110 L50 106 L42 110Z", c(0xFFEF4444)))
    }

    @Composable
    fun Cap(modifier: Modifier) = Overlay(capOps, modifier)

    @Composable
    fun Laurel(modifier: Modifier) = Overlay(laurelOps, modifier)

    @Composable
    private fun Overlay(ops: List<Op>, modifier: Modifier) {
        Canvas(modifier) {
            val s = size.minDimension / 100f
            scale(s, s, pivot = Offset.Zero) { ops.forEach { draw(it) } }
        }
    }

    @Composable
    fun Draw(code: Int, modifier: Modifier) {
        val ops = if (code >= ICON_BASE) icons[(code - ICON_BASE) % icons.size] else face(code)
        Canvas(modifier) {
            val s = size.minDimension / 100f
            scale(s, s, pivot = Offset.Zero) { ops.forEach { draw(it) } }
        }
    }

    private fun DrawScope.draw(op: Op) {
        when (op) {
            is C -> {
                op.fill?.let { drawCircle(it, op.r, Offset(op.x, op.y)) }
                op.stroke?.let { drawCircle(it, op.r, Offset(op.x, op.y), style = Stroke(op.sw)) }
            }
            is E -> rotate(op.rot, Offset(op.x, op.y)) {
                drawOval(op.fill, Offset(op.x - op.rx, op.y - op.ry), Size(op.rx * 2, op.ry * 2))
            }
            is P -> rotate(op.rot, Offset(op.rcx, op.rcy)) {
                val p = path(op.d)
                op.fill?.let { drawPath(p, it) }
                op.stroke?.let { drawPath(p, it, style = Stroke(op.sw, cap = StrokeCap.Round)) }
            }
        }
    }
}
