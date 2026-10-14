package com.lumisignal.idx.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp

/** A piece of a radar icon on a 24-unit grid: filled or stroked, "cut" = darker detail on the fill. */
private class Part(val path: Path, val stroke: Boolean, val cut: Boolean = false, val width: Float = 2f)

private fun f(d: String) = Part(PathParser().parsePathString(d).toPath(), stroke = false)
private fun s(d: String, w: Float = 2f) = Part(PathParser().parsePathString(d).toPath(), stroke = true, width = w)
private fun cf(d: String) = Part(PathParser().parsePathString(d).toPath(), stroke = false, cut = true)
private fun cs(d: String, w: Float = 1.6f) = Part(PathParser().parsePathString(d).toPath(), stroke = true, cut = true, width = w)

/** One symbol per Radar Besok strategy (keys come from the radar spec). */
private fun radarParts(key: String): List<Part> = when (key) {
    // ---- profit besar
    "squeeze" -> listOf(
        s("M2 12H8.3M5.6 9.3L8.3 12L5.6 14.7"), s("M22 12H15.7M18.4 9.3L15.7 12L18.4 14.7"),
        f("M10.8 4.5H13.2V19.5H10.8Z"),
    )
    "bounce" -> listOf(
        s("M2.5 20.5H21.5"), s("M3.5 8.5C6 8.5 7.4 19.6 9.6 19.6C11.8 19.6 12.6 11 14.6 9.4", 1.8f),
        f("M18 4A2.8 2.8 0 1 0 18.01 4Z"),
    )
    "engulf" -> listOf(
        s("M12 2.5V6M12 18V21.5"), s("M7.5 6H16.5V18H7.5Z"), f("M10.3 9.6H13.7V14.4H10.3Z"),
    )
    "gapfill" -> listOf(
        f("M3.5 3.5H8.5V10.5H3.5Z"), f("M10 13.5H14V20.5H10Z"),
        s("M15.8 17C19.3 17 20.3 13 20.3 7.8M17.8 10.3L20.3 7.8L22.8 10.3"),
    )
    "bolt" -> listOf(f("M13.8 1.8L4.6 13.4H11L9.4 22.2L19.4 9.6H13Z"))
    "stairs" -> listOf(
        s("M2.5 20.5H7.5V15.5H12.5V10.5H17.5V5.5H21.5"), f("M19.5 1.8L22.4 4.8H16.6Z"),
    )
    "spring" -> listOf(
        s("M6.5 21H17.5"),
        s("M8 18.5C8 17 16 17.4 16 15.8C16 14.2 8 14.6 8 13C8 11.4 16 11.8 16 10.2", 1.8f),
        s("M12 7.6V2.4M9.4 5L12 2.4L14.6 5"),
    )
    "parachute" -> listOf(
        f("M2.5 11.2A9.5 8 0 0 1 21.5 11.2Z"),
        s("M3 11.4L12 18M21 11.4L12 18M9 11.4L12 18M15 11.4L12 18", 1.3f),
        f("M10.3 17.6H13.7V21.4H10.3Z"),
    )
    "volume" -> listOf(
        s("M2.5 21.5H21.5", 1.6f), s("M5.5 19.5V15", 3f), s("M10 19.5V12.5", 3f), s("M14.5 19.5V16", 3f), s("M19 19.5V4.5", 3f),
    )
    "higherlow" -> listOf(
        s("M2 19.5L6.8 9L11 16.2L14.6 6.8L17.6 12.8L22 3.5", 1.9f),
        f("M11 14A2 2 0 1 0 11.01 14Z"), f("M17.6 10.6A2 2 0 1 0 17.61 10.6Z"),
    )
    "uturn" -> listOf(
        s("M6.5 2.5V14A5.5 5.5 0 0 0 17.5 14V6.5", 2.2f), s("M14 9.8L17.5 6.3L21 9.8", 2.2f),
    )
    "rocket" -> listOf(
        f("M12 1.8C15.8 4.4 16.9 8.6 16.3 13.2L12 16.2L7.7 13.2C7.1 8.6 8.2 4.4 12 1.8Z"),
        cf("M12 6A2 2 0 1 0 12.01 6Z"),
        f("M7.8 11.2L4.6 15.4L8.4 16.2Z"), f("M16.2 11.2L19.4 15.4L15.6 16.2Z"),
        f("M10.4 17.2H13.6Q13.4 20.4 12 22.4Q10.6 20.4 10.4 17.2Z"),
    )
    "drop" -> listOf(
        s("M12 2.8C12 2.8 5.8 9.8 5.8 14.6A6.2 6.2 0 0 0 18.2 14.6C18.2 9.8 12 2.8 12 2.8Z"),
        s("M9.2 15A2.8 2.8 0 0 0 12 17.8", 1.6f),
    )
    "diamond" -> listOf(
        f("M6.6 3.5H17.4L21.6 9.2L12 21L2.4 9.2Z"),
        cs("M2.6 9.2H21.4M8.8 3.6L12 9.2L15.2 3.6M12 9.2V20.6", 1.3f),
    )
    "sprout" -> listOf(
        s("M3.5 21.5H20.5"), s("M12 21.5V11"),
        f("M12 13.4C12 9.4 9.2 6.6 4.6 7C4.6 11.2 7.6 13.8 12 13.4Z"),
        f("M12 11.2C12 6.8 15 3.6 20 4C20 8.8 16.4 11.6 12 11.2Z"),
    )
    "anchor" -> listOf(
        s("M12 2.6A2.1 2.1 0 1 0 12.01 2.6Z"), s("M12 6.8V21"), s("M8 10.2H16"),
        s("M4.4 13.8C4.4 18 7.8 21 12 21C16.2 21 19.6 18 19.6 13.8"),
        f("M2.4 15.4L4.4 12.4L6.4 15.4Z"), f("M17.6 15.4L19.6 12.4L21.6 15.4Z"),
    )
    // ---- winrate tinggi
    "sunrise" -> listOf(
        s("M2 18H22"), f("M6.2 18A5.8 5.8 0 0 1 17.8 18Z"),
        s("M12 4V6.8M4.4 8.4L6.3 10.3M19.6 8.4L17.7 10.3M2.4 13.8H4.6M19.4 13.8H21.6", 1.8f), s("M7 21.4H17", 1.6f),
    )
    "wave" -> listOf(
        s("M2 8.6C4.4 5.8 6.6 5.8 9 8.6S13.6 11.4 16 8.6S20.2 5.8 22 7.6"),
        s("M2 15.4C4.4 12.6 6.6 12.6 9 15.4S13.6 18.2 16 15.4S20.2 12.6 22 14.4"),
    )
    "magnet" -> listOf(
        s("M6.5 3V11.5A5.5 5.5 0 0 0 17.5 11.5V3", 3f),
        cf("M4.9 2.4H8.1V6.2H4.9Z"), cf("M15.9 2.4H19.1V6.2H15.9Z"),
        s("M8.6 21.6L9.6 19.6M12 22.4V20.2M15.4 21.6L14.4 19.6", 1.5f),
    )
    "snowflake" -> listOf(
        s("M12 2V22M3.3 7L20.7 17M20.7 7L3.3 17", 1.9f),
        s("M9.6 3.6L12 6L14.4 3.6M9.6 20.4L12 18L14.4 20.4", 1.6f),
    )
    "coin" -> listOf(
        f("M12 2.6A9.4 9.4 0 1 0 12.01 2.6Z"),
        cs("M12 16.6V7.8M8.8 11L12 7.8L15.2 11", 2f),
    )
    "hourglass" -> listOf(
        s("M5.5 2.5H18.5M5.5 21.5H18.5"),
        s("M7.5 2.5C7.5 8 12 9.6 12 12C12 14.4 7.5 16 7.5 21.5M16.5 2.5C16.5 8 12 9.6 12 12C12 14.4 16.5 16 16.5 21.5", 1.8f),
        f("M9 21C9 18.2 12 16.6 12 15.4C12 16.6 15 18.2 15 21Z"), f("M9.8 6.4H14.2C13.6 8 12.4 8.8 12 9.8C11.6 8.8 10.4 8 9.8 6.4Z"),
    )
    "pulse" -> listOf(s("M2 12.6H6.8L9 6.6L12.6 18.2L15.2 9.6L16.8 12.6H22", 2.1f))
    "mountain" -> listOf(
        f("M1.8 20.5L8.6 8.8L12.2 14.4L15.8 7.6L22.2 20.5Z"),
        cs("M15.8 7.6L14.4 10.4L16.2 11.2L17.4 10.6", 1.3f),
        f("M6 3.2A2 2 0 1 0 6.01 3.2Z"),
    )
    "flag" -> listOf(
        s("M5.5 22V2.8"), f("M5.5 3.4H18.6L15.8 7.6L18.6 11.8H5.5Z"), s("M3 22H8.5"),
    )
    "tag" -> listOf(
        f("M2.8 4.4V11.8L12.2 21.2L21.2 12.2L11.8 2.8H4.4A1.6 1.6 0 0 0 2.8 4.4Z"),
        cf("M7.6 6A1.7 1.7 0 1 0 7.61 6Z"),
        cs("M11 15.6L15.6 11M11.4 11.6L11.42 11.6M15.2 15.2L15.22 15.2", 1.8f),
    )
    "boomerang" -> listOf(
        f("M3.4 19.6L10.6 3.6L14 5.2L9 16.4L20.6 15.8L20.2 19.6Z"),
        s("M15.8 2.6A6.4 6.4 0 0 1 21.4 8.2", 1.6f),
    )
    "target" -> listOf(
        s("M12 3A9 9 0 1 0 12.01 3Z", 1.8f), s("M12 7.2A4.8 4.8 0 1 0 12.01 7.2Z", 1.8f),
        f("M12 10A2 2 0 1 0 12.01 10Z"), s("M12 0.8V3.6M12 20.4V23.2M0.8 12H3.6M20.4 12H23.2", 1.6f),
    )
    "compass" -> listOf(
        s("M12 2.4A9.6 9.6 0 1 0 12.01 2.4Z"),
        f("M16.4 7.6L13.4 13.4L7.6 16.4L10.6 10.6Z"), cf("M12 10.8A1.2 1.2 0 1 0 12.01 10.8Z"),
    )
    "crown" -> listOf(
        f("M2.6 7.8L7.4 12L12 4.4L16.6 12L21.4 7.8L19.2 18H4.8Z"), s("M4.8 21.2H19.2"),
        cf("M12 11.8A1.4 1.4 0 1 0 12.01 11.8Z"),
    )
    "moon" -> listOf(
        f("M20.4 14.6A8.6 8.6 0 1 1 9.4 3.6A7 7 0 0 0 20.4 14.6Z"),
        f("M17.6 2.4L18.3 4L19.9 4.7L18.3 5.4L17.6 7L16.9 5.4L15.3 4.7L16.9 4Z"),
    )
    "pacman" -> listOf(
        f("M10.2 12L17.6 7.4A8.6 8.6 0 1 0 17.6 16.6Z"), cf("M9.6 5.4A1.3 1.3 0 1 0 9.61 5.4Z"),
        f("M20.6 10.5A1.5 1.5 0 1 0 20.61 10.5Z"),
    )
    else -> listOf(f("M12 2L14.6 9.4L22 12L14.6 14.6L12 22L9.4 14.6L2 12L9.4 9.4Z"))
}

@Composable
fun RadarIcon(key: String, size: Dp, color: Color, modifier: Modifier = Modifier) {
    val parts = remember(key) { radarParts(key) }
    Canvas(modifier.size(size)) {
        val k = this.size.minDimension / 24f
        scale(k, k, pivot = Offset.Zero) {
            for (p in parts) {
                val c = if (p.cut) Color.Black.copy(alpha = 0.28f) else color
                if (p.stroke) drawPath(p.path, c, style = Stroke(width = p.width, cap = StrokeCap.Round, join = StrokeJoin.Round))
                else drawPath(p.path, c)
            }
        }
    }
}

/** "#RRGGBB" from the radar spec. */
fun hexColor(hex: String, fallback: Color = Color(0xFF3D8BFF)): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (e: Exception) {
    fallback
}

/**
 * Badge of a radar strategy: its own symbol on a gradient of its own colour. Profit strategies sit on a
 * rounded square, winrate strategies on a circle, so the two signal types are told apart at a glance.
 */
@Composable
fun RadarBadge(icon: String, color: String, jenis: String, size: Dp, modifier: Modifier = Modifier) {
    val c = hexColor(color)
    val shape = if (jenis == "winrate") CircleShape else RoundedCornerShape(size * 0.3f)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(Brush.linearGradient(listOf(c, c.copy(alpha = 0.62f), Color(0xFF16214A)), start = Offset.Zero, end = Offset.Infinite))
            .border(Dp(if (size.value >= 40f) 1.2f else 1f), Color.White.copy(alpha = 0.18f), shape),
        contentAlignment = Alignment.Center,
    ) {
        RadarIcon(icon, size * 0.6f, Color.White)
    }
}
