package com.lumisignal.idx.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import com.lumisignal.idx.ui.theme.Lumi
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Radar scope: rings, crosshair, a rotating sweep and blips that light up as the beam passes them.
 * [periodMs] sets the sweep speed; [blips] how many targets are on the screen.
 */
@Composable
fun RadarScope(size: Dp, modifier: Modifier = Modifier, periodMs: Int = 2400, blips: Int = 14, accent: Color = Lumi.Sky) {
    val t = rememberInfiniteTransition(label = "scope")
    val sweep by t.animateFloat(0f, 360f, infiniteRepeatable(tween(periodMs, easing = LinearEasing)), label = "sweep")
    val pulse by t.animateFloat(0.55f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "pulse")
    val points = remember(blips) {
        val r = Random(7)
        List(blips) { Pair(r.nextFloat() * 360f, 0.22f + r.nextFloat() * 0.72f) }
    }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2
            val c = center
            drawCircle(Brush.radialGradient(listOf(Lumi.Blue.copy(alpha = 0.22f), Color.Transparent), c, r), r, c)
            for (i in 1..4) drawCircle(Lumi.Border.copy(alpha = 0.75f), r * i / 4f, c, style = Stroke(1.4f))
            drawLine(Lumi.Border.copy(alpha = 0.6f), Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1.2f)
            drawLine(Lumi.Border.copy(alpha = 0.6f), Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1.2f)
            // beam with a fading tail
            val rad = Math.toRadians(sweep.toDouble())
            rotate(sweep, pivot = c) {
                drawArc(
                    Brush.sweepGradient(0f to Color.Transparent, 0.79f to Color.Transparent, 1f to accent.copy(alpha = 0.45f), center = c),
                    startAngle = -75f, sweepAngle = 75f, useCenter = true,
                    topLeft = Offset(c.x - r, c.y - r), size = androidx.compose.ui.geometry.Size(2 * r, 2 * r),
                )
            }
            drawLine(accent, c, Offset(c.x + r * cos(rad).toFloat(), c.y + r * sin(rad).toFloat()), 3f)
            for ((ang, dist) in points) {
                val since = ((sweep - ang) % 360f + 360f) % 360f
                val glow = (1f - since / 300f).coerceIn(0f, 1f)
                if (glow <= 0f) continue
                val a = Math.toRadians(ang.toDouble())
                val p = Offset(c.x + r * dist * cos(a).toFloat(), c.y + r * dist * sin(a).toFloat())
                drawCircle(Lumi.Green.copy(alpha = 0.25f * glow), r * 0.06f * (1.4f - glow * 0.4f), p)
                drawCircle(Lumi.Green.copy(alpha = glow), r * 0.022f, p)
            }
            drawCircle(accent.copy(alpha = 0.3f * pulse), r * 0.12f * pulse, c)
        }
        Sparkle(size * 0.12f, Lumi.Text)
    }
}
