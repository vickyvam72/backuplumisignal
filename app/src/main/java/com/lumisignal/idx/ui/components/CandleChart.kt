package com.lumisignal.idx.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumisignal.idx.data.Bar
import com.lumisignal.idx.data.TradePlan
import com.lumisignal.idx.engine.Fmt
import com.lumisignal.idx.engine.Market
import com.lumisignal.idx.ui.theme.GroteskFamily
import com.lumisignal.idx.ui.theme.Lumi
import kotlin.math.max
import kotlin.math.min

private val MA5 = Color(0xFF9EDCFF)
private val MA20 = Color(0xFFFFC14D)

/**
 * Daily candlestick chart with volume, MA5/MA20 and the trade plan drawn on top
 * (entry band, stop loss, take profit). The signal session is highlighted.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
fun CandleChart(
    bars: List<Bar>,
    plan: TradePlan?,
    modifier: Modifier = Modifier,
    signalDate: String? = null,
    maxBars: Int = 45,
    height: Dp = 280.dp,
) {
    val measurer = rememberTextMeasurer()
    val closes = remember(bars) { bars.map { it.close } }
    val ma5 = remember(bars) { closes.indices.map { Market.sma(closes, 5, it) } }
    val ma20 = remember(bars) { closes.indices.map { Market.sma(closes, 20, it) } }
    val startIdx = max(0, bars.size - maxBars)
    val view = bars.subList(startIdx, bars.size)

    Canvas(modifier.fillMaxWidth().height(height)) {
        if (view.isEmpty()) return@Canvas
        val labelW = 54.dp.toPx()
        val chartW = size.width - labelW
        val priceH = size.height * 0.72f
        val volTop = size.height * 0.78f
        val volH = size.height * 0.17f
        val dateY = size.height * 0.955f

        var lo = view.minOf { it.low }
        var hi = view.maxOf { it.high }
        if (plan != null) {
            lo = min(lo, plan.stopLoss)
            hi = max(hi, plan.tp)
        }
        val pad = (hi - lo) * 0.06 + 0.0001
        lo -= pad
        hi += pad
        fun py(p: Double): Float = (priceH * (1 - (p - lo) / (hi - lo))).toFloat()

        val step = chartW / view.size
        val bodyW = max(2f, step * 0.62f)

        // grid
        for (i in 0..4) {
            val y = priceH * i / 4f
            drawLine(Lumi.Border.copy(alpha = 0.35f), Offset(0f, y), Offset(chartW, y), strokeWidth = 1f)
            val price = hi - (hi - lo) * i / 4.0
            label(measurer, Fmt.price(Market.floorTick(price)), Offset(chartW + 6.dp.toPx(), y - 7.dp.toPx()), Lumi.TextMuted)
        }

        // entry band
        if (plan != null) {
            val top = py(plan.entryHigh)
            val bottom = py(plan.entryLow)
            drawRect(
                Lumi.Blue.copy(alpha = 0.16f),
                topLeft = Offset(0f, min(top, bottom) - 1f),
                size = Size(chartW, max(3f, kotlin.math.abs(bottom - top) + 2f)),
            )
        }

        // highlight signal session
        val sigIdx = view.indexOfFirst { it.date == signalDate }
        if (sigIdx >= 0) {
            drawRect(
                Lumi.Sky.copy(alpha = 0.07f),
                topLeft = Offset(sigIdx * step, 0f),
                size = Size(step, size.height * 0.94f),
            )
        }

        // candles + volume
        val maxVol = view.maxOf { it.volume }.takeIf { it > 0 } ?: 1.0
        view.forEachIndexed { i, b ->
            val cx = i * step + step / 2
            val up = b.close >= b.open
            val color = if (up) Lumi.Green else Lumi.Red
            drawLine(color, Offset(cx, py(b.high)), Offset(cx, py(b.low)), strokeWidth = max(1f, step * 0.1f))
            val top = py(max(b.open, b.close))
            val bottom = py(min(b.open, b.close))
            drawRoundRect(
                color,
                topLeft = Offset(cx - bodyW / 2, top),
                size = Size(bodyW, max(1.5f, bottom - top)),
                cornerRadius = CornerRadius(1.5f, 1.5f),
            )
            val vh = (b.volume / maxVol * volH).toFloat()
            drawRoundRect(
                color.copy(alpha = 0.45f),
                topLeft = Offset(cx - bodyW / 2, volTop + volH - vh),
                size = Size(bodyW, vh),
                cornerRadius = CornerRadius(1.5f, 1.5f),
            )
        }

        // moving averages
        drawSeries(ma5.subList(startIdx, bars.size), step, ::py, MA5)
        drawSeries(ma20.subList(startIdx, bars.size), step, ::py, MA20)

        // plan lines
        if (plan != null) {
            val lines = listOf(
                Triple(py(plan.tp), Lumi.Green, "TP ${Fmt.price(plan.tp)}"),
                Triple(py(plan.entryHigh), Lumi.Blue, "BUY ${Fmt.price(plan.entryHigh)}"),
                Triple(py(plan.stopLoss), Lumi.Red, "SL ${Fmt.price(plan.stopLoss)}"),
            )
            lines.forEach { (y, c, _) -> planLine(y, chartW, c) }
            // Labels never overlap: push them apart when the levels are close together.
            val gap = 17.dp.toPx()
            val ys = lines.map { it.first }.toMutableList()
            for (i in 1 until ys.size) if (ys[i] - ys[i - 1] < gap) ys[i] = ys[i - 1] + gap
            val overflow = ys.last() - (priceH - gap / 2)
            if (overflow > 0) for (i in ys.indices) ys[i] -= overflow
            lines.forEachIndexed { i, (_, c, text) -> planLabel(measurer, ys[i], chartW, c, text) }
        }

        // dates
        val idxs = listOf(0, view.size / 2, view.size - 1).distinct()
        for (i in idxs) {
            val text = Market.shortDate(view[i].date)
            val x = (i * step + step / 2 - 18.dp.toPx()).coerceIn(0f, chartW - 36.dp.toPx())
            label(measurer, text, Offset(x, dateY - 6.dp.toPx()), Lumi.TextMuted)
        }
    }
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.label(measurer: TextMeasurer, text: String, at: Offset, color: Color, bold: Boolean = false) {
    drawText(
        measurer, text, at,
        style = TextStyle(
            fontFamily = GroteskFamily, fontSize = 9.5.sp, color = color,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
        ),
    )
}

private fun DrawScope.planLine(y: Float, chartW: Float, color: Color) {
    drawLine(
        color.copy(alpha = 0.9f), Offset(0f, y), Offset(chartW, y), strokeWidth = 1.6f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f)),
    )
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.planLabel(measurer: TextMeasurer, y: Float, chartW: Float, color: Color, text: String) {
    val layout = measurer.measure(text, TextStyle(fontFamily = GroteskFamily, fontSize = 9.sp, fontWeight = FontWeight.Bold))
    val w = layout.size.width + 10.dp.toPx()
    val h = layout.size.height + 4.dp.toPx()
    val x = chartW - w - 2.dp.toPx()
    drawRoundRect(color, topLeft = Offset(x, y - h / 2), size = Size(w, h), cornerRadius = CornerRadius(h / 2, h / 2))
    drawText(
        measurer, text, Offset(x + 5.dp.toPx(), y - h / 2 + 2.dp.toPx()),
        style = TextStyle(fontFamily = GroteskFamily, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Lumi.Bg),
    )
}

private fun DrawScope.drawSeries(values: List<Double?>, step: Float, py: (Double) -> Float, color: Color) {
    val path = Path()
    var started = false
    values.forEachIndexed { i, v ->
        if (v == null) return@forEachIndexed
        val x = i * step + step / 2
        val y = py(v)
        if (!started) {
            path.moveTo(x, y)
            started = true
        } else {
            path.lineTo(x, y)
        }
    }
    if (started) drawPath(path, color.copy(alpha = 0.85f), style = Stroke(width = 1.8f))
}
