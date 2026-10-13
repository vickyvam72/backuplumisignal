package com.lumisignal.idx.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import com.lumisignal.idx.R
import com.lumisignal.idx.data.SignalStatus
import com.lumisignal.idx.engine.Tracker
import com.lumisignal.idx.ui.theme.Lumi
import com.lumisignal.idx.ui.theme.NumberStyle

/** Navy backdrop with two soft light blooms — the "lumi" glow. */
@Composable
fun LumiBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val glow = rememberInfiniteTransition(label = "glow")
    val drift by glow.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse), label = "drift",
    )
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Lumi.Bg2, Lumi.Bg)))
            .drawBehind {
                val w = size.width
                val h = size.height
                val c1 = Offset(w * (0.78f + 0.08f * drift), h * 0.06f)
                drawCircle(
                    Brush.radialGradient(listOf(Lumi.Blue.copy(alpha = 0.30f), Color.Transparent), center = c1, radius = w * 0.85f),
                    radius = w * 0.85f, center = c1,
                )
                val c2 = Offset(w * (0.05f + 0.06f * (1 - drift)), h * 0.72f)
                drawCircle(
                    Brush.radialGradient(listOf(Color(0xFF6C5CFF).copy(alpha = 0.14f), Color.Transparent), center = c2, radius = w * 0.75f),
                    radius = w * 0.75f, center = c2,
                )
            },
        content = content,
    )
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    padding: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    var m = modifier
        .clip(shape)
        .background(Brush.verticalGradient(listOf(Lumi.SurfaceHi.copy(alpha = 0.72f), Lumi.Surface.copy(alpha = 0.88f))))
        .border(
            1.dp,
            Brush.linearGradient(listOf((accent ?: Lumi.Sky).copy(alpha = if (accent != null) 0.55f else 0.22f), Lumi.Border.copy(alpha = 0.35f))),
            shape,
        )
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(m.padding(padding), content = content)
}

/** The app logo: white disc with the sky-to-blue gradient "S". */
@Composable
fun LumiLogo(size: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.lumi_logo),
        contentDescription = "Lumi Signal IDX",
        modifier = modifier.size(size),
    )
}

/** Four-point sparkle, the brand's accent mark. */
@Composable
fun Sparkle(size: Dp, color: Color = Lumi.Sky, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val p = Path().apply {
            moveTo(w / 2, 0f)
            quadraticBezierTo(w * 0.56f, h * 0.44f, w, h / 2)
            quadraticBezierTo(w * 0.56f, h * 0.56f, w / 2, h)
            quadraticBezierTo(w * 0.44f, h * 0.56f, 0f, h / 2)
            quadraticBezierTo(w * 0.44f, h * 0.44f, w / 2, 0f)
            close()
        }
        drawPath(p, color)
    }
}

@Composable
fun Pill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    icon: ImageVector? = null,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (filled) color else color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = if (filled) 0f else 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (filled) Lumi.Bg else color, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text, style = MaterialTheme.typography.labelMedium,
            color = if (filled) Lumi.Bg else color, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun StatusBadge(status: SignalStatus, modifier: Modifier = Modifier) {
    Pill(Tracker.label(status), Lumi.statusColor(status), modifier)
}

/** Circular score meter (0–100). */
@Composable
fun ScoreRing(score: Int, color: Color, size: Dp = 52.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = this.size.minDimension * 0.1f
            drawArc(
                Lumi.Border, 135f, 270f, false,
                style = Stroke(stroke, cap = StrokeCap.Round),
                topLeft = Offset(stroke / 2, stroke / 2),
                size = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke),
            )
            drawArc(
                Brush.sweepGradient(listOf(color.copy(alpha = 0.6f), color, color.copy(alpha = 0.6f))),
                135f, 270f * (score.coerceIn(0, 100) / 100f), false,
                style = Stroke(stroke, cap = StrokeCap.Round),
                topLeft = Offset(stroke / 2, stroke / 2),
                size = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke),
            )
        }
        Text(
            "$score", style = NumberStyle.copy(fontSize = (size.value * 0.32f).sp, fontWeight = FontWeight.Bold),
            color = Lumi.Text,
        )
    }
}

@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    brush: Brush = Lumi.ButtonGradient,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .clip(shape)
            .background(if (enabled) brush else Brush.horizontalGradient(listOf(Lumi.SurfaceHi, Lumi.SurfaceHi)))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (enabled) Color.White else Lumi.TextMuted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) Color.White else Lumi.TextMuted)
    }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, color: Color = Lumi.Sky) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .clip(shape)
            .border(1.dp, color.copy(alpha = 0.4f), shape)
            .background(color.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Sparkle(10.dp, Lumi.Sky)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = Lumi.Text)
            }
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim)
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, color: Color = Lumi.Text) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Lumi.Bg.copy(alpha = 0.45f))
            .border(1.dp, Lumi.Border.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Lumi.TextMuted)
        Spacer(Modifier.height(3.dp))
        Text(value, style = NumberStyle.copy(fontSize = 18.sp), color = color, maxLines = 1)
    }
}

@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, color: Color = Lumi.Blue, height: Dp = 8.dp) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(Lumi.Border.copy(alpha = 0.6f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height)
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(Lumi.Sky, color))),
        )
    }
}

@Composable
fun LoadingBlock(text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = Lumi.Sky, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(14.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Lumi.TextDim, textAlign = TextAlign.Center)
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 36.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(Lumi.SurfaceHi),
            contentAlignment = Alignment.Center,
        ) { Sparkle(26.dp, Lumi.Sky) }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = Lumi.Text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Lumi.TextDim, textAlign = TextAlign.Center)
    }
}

/** Rounded tile with the strategy's own symbol in its accent colour. */
@Composable
fun StrategyBadge(id: Int, size: Dp = 34.dp) {
    val c = Lumi.strategyColor(id)
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(Brush.linearGradient(listOf(c, c.copy(alpha = 0.55f)))),
        contentAlignment = Alignment.Center,
    ) {
        StrategyIcon(id, size * 0.62f, Color.White)
    }
}
