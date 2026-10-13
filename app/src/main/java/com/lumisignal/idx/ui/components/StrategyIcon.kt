package com.lumisignal.idx.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp

private class IconPart(val path: Path, val stroke: Boolean, val alpha: Float = 1f)

private fun p(d: String): Path = PathParser().parsePathString(d).toPath()

/** Symbol for each strategy, drawn on a 24-unit grid. */
private fun partsFor(id: Int): List<IconPart> = when (id) {
    // S1 — Closing Kuat: flame
    1 -> listOf(
        IconPart(p("M12 2C12.8 5.6 17.5 8.2 17.5 13.8C17.5 18.2 15 21.5 12 21.5C9 21.5 6.5 18.6 6.5 15C6.5 12.2 8.1 10.4 9.3 8.6C9.6 10.4 10.4 11.6 11.6 12C11 8.6 11 5.2 12 2Z"), false),
        IconPart(p("M12 13.6C13.4 15.1 14.4 16.3 14.4 17.8C14.4 19.2 13.3 20.2 12 20.2C10.7 20.2 9.6 19.2 9.6 17.8C9.6 16.3 10.8 15.1 12 13.6Z"), false, 0.0f),
    )
    // S2 — Breakout Volume: arrow punching through a ceiling over volume bars
    2 -> listOf(
        IconPart(p("M2.5 9.5H8.5M15.5 9.5H21.5"), true),
        IconPart(p("M12 21V4M7.5 8.5L12 4L16.5 8.5"), true),
        IconPart(p("M5.5 21V15.5M18.5 21V13.5"), true),
    )
    // S3 — ARA Hunter: target with an escaping arrow
    3 -> listOf(
        IconPart(p("M11 3.5A8 8 0 1 0 19 11.5"), true),
        IconPart(p("M11 7.5A4 4 0 1 0 15 11.5"), true),
        IconPart(p("M11 11.5L20.5 2.5M16.5 2.5H20.5V6.5"), true),
        IconPart(p("M11 9.7A1.8 1.8 0 1 1 10.99 9.7Z"), false),
    )
    // S4 — Pantulan Modal Bandar: shield with a bounce arrow
    4 -> listOf(
        IconPart(p("M12 2.5L19.5 5.5V11.2C19.5 16 16.3 19.8 12 21.5C7.7 19.8 4.5 16 4.5 11.2V5.5Z"), true),
        IconPart(p("M8 10.8L11 14L16 8.6M13 8.4H16.2V11.6"), true),
    )
    // S5 — Gorengan Bangun: bomb with a lit fuse
    else -> listOf(
        IconPart(p("M10.5 7.7A6.8 6.8 0 1 1 10.49 7.7Z"), false),
        IconPart(p("M15 10C16.2 7.8 17.6 6.6 19.6 6.4"), true),
        IconPart(p("M20.5 0.8Q20.9 3.6 23.6 4Q20.9 4.4 20.5 7.2Q20.1 4.4 17.4 4Q20.1 3.6 20.5 0.8Z"), false),
        IconPart(p("M7.2 12.4A3.4 3.4 0 0 1 9.6 10.6"), true, 0.0f),
    )
}

@Composable
fun StrategyIcon(id: Int, size: Dp, color: Color, modifier: Modifier = Modifier) {
    val parts = remember(id) { partsFor(id) }
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 24f
        scale(s, s, pivot = Offset.Zero) {
            for (part in parts) {
                // alpha 0 marks a "cut-out" detail: drawn darker so it reads on any accent colour.
                val c = if (part.alpha == 0f) Color.Black.copy(alpha = 0.22f) else color.copy(alpha = part.alpha)
                if (part.stroke) {
                    drawPath(part.path, c, style = Stroke(width = 2.1f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                } else {
                    drawPath(part.path, c)
                }
            }
        }
    }
}
