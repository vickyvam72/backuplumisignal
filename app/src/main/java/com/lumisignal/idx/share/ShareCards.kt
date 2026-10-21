package com.lumisignal.idx.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.lumisignal.idx.R
import com.lumisignal.idx.data.RadarDay
import com.lumisignal.idx.data.RadarPick
import com.lumisignal.idx.data.Signal
import com.lumisignal.idx.data.SignalStatus
import com.lumisignal.idx.data.SOURCE_PORTO
import com.lumisignal.idx.engine.Fmt
import com.lumisignal.idx.engine.Market
import com.lumisignal.idx.engine.RadarPorto
import java.io.File
import java.io.FileOutputStream
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Share cards: a picture (PNG, 1080 px wide) plus a short text, sent through the Android share sheet.
 * - a buy signal: signal date, ticker, entry, TP, SL and why it entered the radar;
 * - all buy signals of a day on one card;
 * - a history entry: ticker, entry, TP, SL, P/L, buy date and sell date.
 */
object ShareCards {
    private const val W = 1080
    private const val PAD = 72f

    private const val BG_TOP = 0xFF0C1736.toInt()
    private const val BG_BOTTOM = 0xFF060A18.toInt()
    private const val SURFACE = 0xFF111B3A.toInt()
    private const val BORDER = 0xFF24325C.toInt()
    private const val TEXT = 0xFFEAF1FF.toInt()
    private const val DIM = 0xFF9EABD0.toInt()
    private const val MUTED = 0xFF65739C.toInt()
    private const val SKY = 0xFF9EDCFF.toInt()
    private const val BLUE = 0xFF3D8BFF.toInt()
    private const val GREEN = 0xFF2BD9A0.toInt()
    private const val RED = 0xFFFF5C7A.toInt()
    private const val AMBER = 0xFFFFC14D.toInt()
    private const val PROFIT = 0xFFFF9F43.toInt()
    private const val WINRATE = 0xFF22D3EE.toInt()
    private const val INK = 0xFF0B1020.toInt()

    private fun jenisColor(j: String) = if (j == "winrate") WINRATE else PROFIT

    private class Fonts(ctx: Context) {
        val sans: Typeface = runCatching { ResourcesCompat.getFont(ctx, R.font.plus_jakarta_sans) }.getOrNull() ?: Typeface.SANS_SERIF
        val num: Typeface = runCatching { ResourcesCompat.getFont(ctx, R.font.space_grotesk) }.getOrNull() ?: Typeface.DEFAULT_BOLD
    }

    /** Drawing context: canvas, fonts and helpers. */
    private class Pen(val ctx: Context, val c: Canvas, val f: Fonts) {
        /** Same fonts on a throwaway canvas, for measuring a block before drawing its background. */
        fun measuring() = Pen(ctx, Canvas(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)), f)

        fun paint(size: Float, color: Int, bold: Boolean = false, num: Boolean = false, align: Paint.Align = Paint.Align.LEFT) =
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = if (num) f.num else f.sans
                textSize = size
                this.color = color
                isFakeBoldText = bold
                textAlign = align
            }

        /** Draws [s] with its top at [top]; returns the bottom. */
        fun text(s: String, x: Float, top: Float, p: Paint): Float {
            val fm = p.fontMetrics
            c.drawText(s, x, top - fm.ascent, p)
            return top + (fm.descent - fm.ascent)
        }

        /** Wrapped text block; returns the bottom. */
        fun block(s: String, x: Float, top: Float, width: Int, p: TextPaint, spacing: Float = 1.2f): Float {
            val l = StaticLayout.Builder.obtain(s, 0, s.length, p, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, spacing).build()
            c.save()
            c.translate(x, top)
            l.draw(c)
            c.restore()
            return top + l.height
        }

        fun round(r: RectF, radius: Float, fill: Int, stroke: Int? = null, strokeW: Float = 3f) {
            c.drawRoundRect(r, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill })
            if (stroke != null) c.drawRoundRect(r, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = strokeW; color = stroke
            })
        }

        fun pill(text: String, x: Float, top: Float, color: Int, filled: Boolean, size: Float = 30f): Float {
            val p = paint(size, if (filled) INK else color, bold = true)
            val w = p.measureText(text) + 52f
            val h = size + 30f
            round(RectF(x, top, x + w, top + h), h / 2, if (filled) color else alpha(color, 0.16f), if (filled) null else alpha(color, 0.6f))
            val fm = p.fontMetrics
            c.drawText(text, x + 26f, top + h / 2 - (fm.ascent + fm.descent) / 2, p)
            return x + w
        }

        /** Price tile: label, value, optional % line. */
        fun tile(r: RectF, label: String, value: String, sub: String?, color: Int) {
            round(r, 30f, alpha(color, 0.12f), alpha(color, 0.55f))
            text(label, r.left + 28f, r.top + 24f, paint(26f, color, bold = true))
            text(value, r.left + 28f, r.top + 66f, paint(56f, TEXT, bold = true, num = true))
            if (sub != null) text(sub, r.left + 28f, r.top + 136f, paint(28f, color, num = true))
        }
    }

    private fun alpha(c: Int, a: Float): Int = (c and 0x00FFFFFF) or ((a * 255).toInt().coerceIn(0, 255) shl 24)

    private fun background(c: Canvas, h: Int, accent: Int) {
        c.drawRect(0f, 0f, W.toFloat(), h.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(), BG_TOP, BG_BOTTOM, Shader.TileMode.CLAMP)
        })
        c.drawCircle(W * 0.92f, 120f, 560f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(W * 0.92f, 120f, 560f, alpha(accent, 0.30f), 0, Shader.TileMode.CLAMP)
        })
        c.drawCircle(0f, h * 0.85f, 620f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(0f, h * 0.85f, 620f, alpha(BLUE, 0.20f), 0, Shader.TileMode.CLAMP)
        })
        // Radar rings, the app's mark.
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = alpha(SKY, 0.10f) }
        for (r in listOf(140f, 260f, 380f)) c.drawCircle(W - 60f, 150f, r, ring)
    }

    /** Logo + app name + subtitle on the right; returns the bottom. */
    private fun header(p: Pen, top: Float, label: String): Float {
        ContextCompat.getDrawable(p.ctx, R.drawable.lumi_logo)?.let {
            it.setBounds(PAD.toInt(), top.toInt(), PAD.toInt() + 96, top.toInt() + 96)
            it.draw(p.c)
        }
        p.text("Lumi Signal IDX", PAD + 120f, top + 6f, p.paint(40f, TEXT, bold = true))
        p.text(label, PAD + 120f, top + 56f, p.paint(28f, SKY))
        return top + 96f
    }

    private fun footer(p: Pen, top: Float, note: String): Float {
        p.c.drawLine(PAD, top, W - PAD, top, Paint().apply { color = BORDER; strokeWidth = 2f })
        var y = p.block(note, PAD, top + 28f, (W - 2 * PAD).toInt(), p.paint(25f, MUTED))
        val stamp = Market.now().format(DateTimeFormatter.ofPattern("EEEE, d MMM yyyy · HH:mm 'WIB'", Locale("id", "ID")))
        y = p.text("Dibagikan $stamp", PAD, y + 12f, p.paint(25f, DIM))
        return y + PAD
    }

    /** Renders twice: once to measure the height, once for real. */
    private fun render(ctx: Context, accent: Int, draw: (Pen) -> Float): Bitmap {
        val f = Fonts(ctx)
        val scratch = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val h = draw(Pen(ctx, Canvas(scratch), f)).toInt().coerceIn(800, 6000)
        scratch.recycle()
        val bmp = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        background(c, h, accent)
        draw(Pen(ctx, c, f))
        return bmp
    }

    /** Sends one or more pictures (no text) through the share sheet. */
    fun sendImages(ctx: Context, bitmaps: List<Bitmap>, name: String, title: String) {
        if (bitmaps.isEmpty()) return
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { if (System.currentTimeMillis() - it.lastModified() > 3_600_000) it.delete() }
        val uris = ArrayList<android.net.Uri>()
        bitmaps.forEachIndexed { i, bmp ->
            val file = File(dir, if (bitmaps.size == 1) "$name.png" else "$name-${i + 1}.png")
            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bmp.recycle()
            uris += FileProvider.getUriForFile(ctx, ctx.packageName + ".share", file)
        }
        val send = if (uris.size == 1) Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uris[0]) }
        else Intent(Intent.ACTION_SEND_MULTIPLE).apply { putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris) }
        send.type = "image/png"
        send.clipData = ClipData.newRawUri(title, uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private const val DISCLAIMER = "Saham pantauan, bukan rekomendasi investasi. Harga entry, TP dan SL diumumkan di aplikasi " +
        "begitu saham terbeli di sesi tersebut. Selalu gunakan stop loss."

    // ------------------------------------------------------------------ buy signal

    fun sharePick(ctx: Context, d: RadarDay, jenis: String, p: RadarPick, sid: Int) =
        sendImages(ctx, listOf(pickBitmap(ctx, d, jenis, p, sid)), "sinyal-${p.ticker}-${RadarPorto.nextSession(d.date)}", "Bagikan sinyal ${p.ticker}")

    fun pickBitmap(ctx: Context, d: RadarDay, jenis: String, p: RadarPick, sid: Int): Bitmap {
        val color = jenisColor(jenis)
        val buy = RadarPorto.nextSession(d.date)
        val meta = d.strategy(sid)
        return render(ctx, color) { pen ->
            var y = header(pen, PAD, "Radar Besok · Saham Pantauan")
            y += 56f
            pen.pill(d.jenisLabel(jenis).uppercase(), PAD, y, color, filled = true, size = 32f)
            y += 100f
            // Date first and big: the card is always tied to a session.
            y = pen.text("SINYAL BELI UNTUK", PAD, y, pen.paint(28f, GREEN, bold = true))
            y = pen.text(Market.prettyDate(buy), PAD, y + 6f, pen.paint(58f, TEXT, bold = true))
            y += 50f
            y = pen.text(p.ticker, PAD - 6f, y, pen.paint(170f, TEXT, bold = true, num = true))
            if (p.name.isNotBlank()) y = pen.block(p.name, PAD, y + 4f, (W - 2 * PAD).toInt(), pen.paint(32f, DIM))
            y += 44f
            y = watchBox(pen, y) + 40f
            // Why it entered the radar: measured on a scratch canvas, then drawn over its box.
            val reasons = p.why.ifEmpty { listOfNotNull(meta?.desc) }
            val boxTop = y
            val boxBottom = whyBox(pen.measuring(), boxTop, color, meta?.name, reasons)
            pen.round(RectF(PAD, boxTop, W - PAD, boxBottom), 34f, alpha(SURFACE, 0.85f), alpha(color, 0.45f))
            val by = whyBox(pen, boxTop, color, meta?.name, reasons)
            y = by + 50f
            footer(pen, y, DISCLAIMER)
        }
    }

    /** "Saham pantauan" note: entry, TP and SL are announced once the stock is bought. Returns the bottom. */
    private fun watchBox(pen: Pen, top: Float): Float {
        val inner = (W - 2 * PAD - 80f).toInt()
        val text = "Entry, TP dan SL diumumkan di Riwayat begitu saham ini terbeli di sesi tersebut, lengkap dengan notifikasi."
        val h = 36f + 40f + 10f + StaticLayout.Builder.obtain(text, 0, text.length, pen.paint(30f, DIM), inner).build().height + 36f
        pen.round(RectF(PAD, top, W - PAD, top + h), 30f, alpha(BLUE, 0.12f), alpha(BLUE, 0.5f))
        val y = pen.text("👁  SAHAM PANTAUAN", PAD + 40f, top + 36f, pen.paint(30f, SKY, bold = true))
        pen.block(text, PAD + 40f, y + 10f, inner, pen.paint(30f, DIM))
        return top + h
    }

    /** "Kenapa masuk radar" box contents; returns the bottom of the box. */
    private fun whyBox(pen: Pen, top: Float, color: Int, title: String?, reasons: List<String>): Float {
        val inner = (W - 2 * PAD - 80f).toInt()
        var by = pen.text("KENAPA MASUK RADAR", PAD + 40f, top + 36f, pen.paint(28f, color, bold = true))
        title?.let { by = pen.block(it, PAD + 40f, by + 10f, inner, pen.paint(36f, TEXT, bold = true)) }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        for (r in reasons) {
            by += 14f
            pen.c.drawCircle(PAD + 52f, by + 22f, 7f, dot)
            by = pen.block(r, PAD + 76f, by, inner - 36, pen.paint(30f, DIM))
        }
        return by + 36f
    }

    // ------------------------------------------------------------------ all signals of a day

    private fun dayLists(d: RadarDay, tier: String) = listOf("profit", "winrate").map { j -> j to d.board(tier, j)?.top5?.filter { it.valid }.orEmpty() }

    fun dayBitmap(ctx: Context, d: RadarDay, tier: String): Bitmap {
        val buy = RadarPorto.nextSession(d.date)
        val lists = dayLists(d, tier)
        return render(ctx, GREEN) { pen ->
            var y = header(pen, PAD, "Radar Besok · Saham Pantauan")
            y += 60f
            y = pen.text("SINYAL BELI UNTUK", PAD, y, pen.paint(28f, GREEN, bold = true))
            y = pen.text(Market.prettyDate(buy), PAD, y + 6f, pen.paint(62f, TEXT, bold = true))
            y += 30f
            y = watchBox(pen, y) + 10f
            for ((j, picks) in lists) {
                val color = jenisColor(j)
                y += 20f
                // Loud section title.
                val hh = 96f
                pen.c.drawRoundRect(RectF(PAD, y, W - PAD, y + hh), 30f, 30f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(PAD, y, W - PAD, y, color, alpha(color, 0.5f), Shader.TileMode.CLAMP)
                })
                val tp = pen.paint(44f, INK, bold = true)
                pen.text(d.jenisLabel(j).uppercase(), PAD + 32f, y + (hh - 56f) / 2, tp)
                pen.text("${picks.size} saham", W - PAD - 32f, y + (hh - 40f) / 2, pen.paint(30f, INK, bold = true, align = Paint.Align.RIGHT))
                y += hh + 16f
                if (picks.isEmpty()) {
                    y = pen.block("Tidak ada saham yang lolos untuk sesi ini.", PAD + 8f, y + 8f, (W - 2 * PAD).toInt(), pen.paint(30f, MUTED)) + 10f
                }
                for (p in picks) {
                    val rowTop = y
                    val inner = (W - 2 * PAD - 64f).toInt()
                    val why = p.why.firstOrNull() ?: d.strategy(p.sid ?: 0)?.name ?: ""
                    // Measure the row on the scratch pass layout height.
                    val whyH = StaticLayout.Builder.obtain(why, 0, why.length, pen.paint(27f, DIM), inner).build().height
                    val rh = 30f + 64f + 12f + whyH + 26f
                    pen.round(RectF(PAD, rowTop, W - PAD, rowTop + rh), 28f, alpha(SURFACE, 0.9f), alpha(color, 0.35f))
                    var ry = rowTop + 30f
                    pen.text(p.ticker, PAD + 32f, ry - 6f, pen.paint(60f, TEXT, bold = true, num = true))
                    d.strategy(p.sid ?: 0)?.name?.let {
                        pen.text(it, W - PAD - 32f, ry + 14f, pen.paint(28f, SKY, bold = true, align = Paint.Align.RIGHT))
                    }
                    ry += 64f + 12f
                    pen.block(why, PAD + 32f, ry, inner, pen.paint(27f, DIM))
                    y = rowTop + rh + 16f
                }
            }
            y += 30f
            footer(pen, y, DISCLAIMER)
        }
    }

    fun shareDay(ctx: Context, d: RadarDay, tier: String) =
        sendImages(ctx, listOf(dayBitmap(ctx, d, tier)), "sinyal-${RadarPorto.nextSession(d.date)}", "Bagikan sinyal beli")

    // ------------------------------------------------------------------ history entry

    /** TP and SL of a trade: from the real entry price once bought, else the plan. */
    fun levels(sig: Signal): Pair<Double?, Double?> {
        val plan = sig.eval.plan
        val e = sig.entryPrice ?: sig.customEntry
        val rule = plan?.rule
        if (e != null && rule != null) rule.levels(e)?.let { return it.first to it.second }
        return plan?.tp?.takeIf { it > 0 } to plan?.stopLoss?.takeIf { it > 0 }
    }

    private fun statusInfo(sig: Signal): Pair<String, Int> = when (sig.status) {
        SignalStatus.TP -> "TAKE PROFIT ✓" to GREEN
        SignalStatus.SL -> "STOP LOSS" to RED
        SignalStatus.ACTIVE -> "MASIH DIPEGANG" to BLUE
        SignalStatus.WAITING -> "MENUNGGU ENTRY" to SKY
        SignalStatus.CANCELLED -> "BATAL · TIDAK TERBELI" to MUTED
        SignalStatus.EXPIRED -> "KELUAR · WAKTU HABIS" to AMBER
    }

    private fun entryOf(sig: Signal) = sig.entryPrice ?: sig.customEntry
    private fun pnlOf(sig: Signal) = sig.pnlPct.takeIf { sig.status != SignalStatus.CANCELLED && sig.entryPrice != null }

    fun signalBitmap(ctx: Context, sig: Signal): Bitmap {
        val (tp, sl) = if (entryOf(sig) != null) levels(sig) else null to null
        val entry = entryOf(sig)
        val pnl = pnlOf(sig)
        val (stLabel, stColor) = statusInfo(sig)
        val pnlColor = when {
            pnl == null -> MUTED
            pnl >= 0 -> GREEN
            else -> RED
        }
        return render(ctx, if (pnl == null) BLUE else pnlColor) { pen ->
            var y = header(pen, PAD, if (sig.source == SOURCE_PORTO) "Riwayat Portofolio" else "Riwayat Strategi")
            y += 56f
            var x = pen.pill(stLabel, PAD, y, stColor, filled = true, size = 32f) + 16f
            sig.radar?.let { pen.pill(if (it.jenis == "winrate") "WINRATE TINGGI" else "PROFIT TINGGI", x, y, jenisColor(it.jenis), false, 30f) }
            y += 110f
            y = pen.text(sig.symbol, PAD - 6f, y, pen.paint(170f, TEXT, bold = true, num = true))
            val sub = listOfNotNull(sig.name.takeIf { it.isNotBlank() }, sig.radar?.name).joinToString(" · ")
            if (sub.isNotBlank()) y = pen.block(sub, PAD, y + 4f, (W - 2 * PAD).toInt(), pen.paint(32f, DIM))
            y += 40f
            // P/L, big.
            y = pen.text(if (sig.isOpen) "P/L BERJALAN" else "PROFIT / LOSS", PAD, y, pen.paint(28f, pnlColor, bold = true))
            y = pen.text(pnl?.let { Fmt.pct(it, decimals = 2) } ?: "–", PAD - 4f, y + 4f, pen.paint(130f, pnlColor, bold = true, num = true))
            val exitLine = when {
                sig.exitPrice != null && sig.entryPrice != null -> "Beli ${Fmt.price(sig.entryPrice)} → jual ${Fmt.price(sig.exitPrice)}"
                sig.isOpen && sig.entryPrice != null && sig.lastPrice != null -> "Beli ${Fmt.price(sig.entryPrice)} · harga terakhir ${Fmt.price(sig.lastPrice)}"
                else -> null
            }
            if (exitLine != null) y = pen.text(exitLine, PAD, y + 6f, pen.paint(30f, DIM, num = true))
            y += 44f
            val gap = 24f
            val tw = (W - 2 * PAD - 2 * gap) / 3
            val th = 160f
            pen.tile(RectF(PAD, y, PAD + tw, y + th), "ENTRY", Fmt.price(entry), null, BLUE)
            pen.tile(RectF(PAD + tw + gap, y, PAD + 2 * tw + gap, y + th), "TAKE PROFIT", Fmt.price(tp), null, GREEN)
            pen.tile(RectF(PAD + 2 * (tw + gap), y, W - PAD, y + th), "STOP LOSS", Fmt.price(sl), null, RED)
            y += th + 40f
            // Dates: buy → sell.
            val boxH = 200f
            pen.round(RectF(PAD, y, W - PAD, y + boxH), 32f, alpha(SURFACE, 0.9f), BORDER)
            val half = (W - 2 * PAD) / 2
            pen.text("TANGGAL BELI", PAD + 40f, y + 34f, pen.paint(26f, BLUE, bold = true))
            pen.text(sig.entryDate?.let { Market.prettyDate(it) } ?: if (sig.status == SignalStatus.CANCELLED) "Tidak terbeli" else "Belum terbeli",
                PAD + 40f, y + 76f, pen.paint(34f, TEXT, bold = true))
            pen.text("TANGGAL JUAL", PAD + half + 30f, y + 34f, pen.paint(26f, GREEN, bold = true))
            pen.text(sig.exitDate?.takeIf { sig.entryDate != null }?.let { Market.prettyDate(it) } ?: "–",
                PAD + half + 30f, y + 76f, pen.paint(34f, TEXT, bold = true))
            pen.c.drawLine(PAD + half, y + 34f, PAD + half, y + boxH - 34f, Paint().apply { color = BORDER; strokeWidth = 2f })
            val hold = sig.holdDays?.takeIf { sig.entryDate != null }
            pen.text("Sinyal beli untuk ${Market.shortDate(RadarPorto.nextSession(sig.refDate))}${hold?.let { " · hold $it hari" } ?: ""}", PAD + 40f, y + 136f, pen.paint(26f, MUTED))
            y += boxH + 50f
            footer(pen, y, "Hasil dicatat otomatis oleh Lumi Signal IDX dari harga Stockbit. Bukan rekomendasi investasi.")
        }
    }

    fun shareSignal(ctx: Context, sig: Signal) =
        sendImages(ctx, listOf(signalBitmap(ctx, sig)), "riwayat-${sig.symbol}-${sig.refDate}", "Bagikan ${sig.symbol}")

    // ------------------------------------------------------------------ list report (history / backtest)

    /** One trade line of a list report. [signal] is the closing date; it is shown as the session to buy. */
    data class ReportRow(
        val ticker: String,
        val signal: String,
        val buy: String?,
        val sell: String?,
        val hold: Int?,
        val entry: Double?,
        val tp: Double?,
        val sl: Double?,
        val pnl: Double?,
        /** TP, SL, Waktu, Hold, Menunggu, Batal */
        val status: String,
    )

    data class Report(
        val kind: String,
        val jenis: String?,
        val period: String,
        val rows: List<ReportRow>,
        val tag: String? = "Top 5 Saham IDX",
    )

    fun rowOf(sig: Signal): ReportRow {
        val (tp, sl) = levels(sig)
        val bought = sig.entryDate != null && sig.status != SignalStatus.CANCELLED
        return ReportRow(
            ticker = sig.symbol,
            signal = sig.refDate,
            buy = sig.entryDate.takeIf { bought },
            sell = sig.exitDate.takeIf { bought && !sig.isOpen },
            hold = sig.holdDays.takeIf { bought },
            entry = sig.entryPrice.takeIf { bought },
            tp = tp.takeIf { bought },
            sl = sl.takeIf { bought },
            pnl = pnlOf(sig),
            status = when (sig.status) {
                SignalStatus.TP -> "TP"
                SignalStatus.SL -> "SL"
                SignalStatus.EXPIRED -> "Waktu"
                SignalStatus.ACTIVE -> "Hold"
                SignalStatus.WAITING -> "Menunggu"
                SignalStatus.CANCELLED -> "Batal"
            },
        )
    }

    fun rowOf(t: com.lumisignal.idx.data.BtTrade) = ReportRow(
        ticker = t.ticker, signal = t.signal, buy = t.buy, sell = t.sell, hold = t.hold,
        entry = t.entry, tp = t.tp, sl = t.sl, pnl = t.pnl, status = t.exit,
    )

    private fun statusColor(s: String) = when (s) {
        "TP" -> GREEN
        "SL" -> RED
        "Waktu" -> AMBER
        "Hold" -> BLUE
        "Menunggu" -> SKY
        else -> MUTED
    }

    private const val FIRST_ROWS = 18
    private const val PAGE_ROWS = 26
    private const val ROW_H = 66f

    /** Column x positions (left edges) and widths of the trade table. */
    private val COLS = listOf(
        "KODE" to 148f, "BELI" to 112f, "JUAL" to 112f, "HOLD" to 82f,
        "ENTRY" to 118f, "TP" to 118f, "SL" to 116f, "P/L" to 130f,
    )

    /** The report as one or more pages (pictures) of the same size. */
    fun reportPages(ctx: Context, r: Report): List<Bitmap> {
        val rows = r.rows
        val chunks = buildList {
            add(rows.take(FIRST_ROWS))
            var i = FIRST_ROWS
            while (i < rows.size) { add(rows.subList(i, minOf(rows.size, i + PAGE_ROWS))); i += PAGE_ROWS }
        }
        val accent = r.jenis?.let { jenisColor(it) } ?: BLUE
        return chunks.mapIndexed { n, chunk -> reportPage(ctx, r, chunk, n, chunks.size, accent) }
    }

    private fun reportPage(ctx: Context, r: Report, chunk: List<ReportRow>, page: Int, pages: Int, accent: Int): Bitmap =
        render(ctx, accent) { pen ->
            var y = header(pen, PAD, r.kind)
            y += 44f
            if (page == 0) {
                var x = PAD
                r.jenis?.let { x = pen.pill(if (it == "winrate") "WINRATE TINGGI" else "PROFIT TINGGI", PAD, y, accent, filled = true, size = 30f) + 14f }
                r.tag?.let { pen.pill(it, x, y, SKY, false, 28f) }
                y += 96f
                y = pen.text("PERIODE", PAD, y, pen.paint(26f, GREEN, bold = true))
                y = pen.block(r.period, PAD, y + 4f, (W - 2 * PAD).toInt(), pen.paint(46f, TEXT, bold = true))
                y += 34f
                // Summary tiles.
                val closed = r.rows.filter { it.status == "TP" || it.status == "SL" || it.status == "Waktu" }
                val acc = closed.mapNotNull { it.pnl }.takeIf { it.isNotEmpty() }?.sum()
                val gap = 18f
                val tw = (W - 2 * PAD - 3 * gap) / 4
                val th = 150f
                val tiles = listOf(
                    Triple("TRANSAKSI", "${r.rows.size}", SKY),
                    Triple("KENA TP", "${r.rows.count { it.status == "TP" }}", GREEN),
                    Triple("KENA SL", "${r.rows.count { it.status == "SL" }}", RED),
                    Triple("AKUMULASI P/L", acc?.let { Fmt.pct(it, decimals = 2) } ?: "–", if ((acc ?: 0.0) >= 0) GREEN else RED),
                )
                tiles.forEachIndexed { i, (label, value, color) ->
                    val left = PAD + i * (tw + gap)
                    val rect = RectF(left, y, left + tw, y + th)
                    pen.round(rect, 26f, alpha(color, 0.12f), alpha(color, 0.5f))
                    pen.text(label, left + 22f, y + 24f, pen.paint(22f, color, bold = true))
                    var size = 54f
                    var vp = pen.paint(size, TEXT, bold = true, num = true)
                    while (vp.measureText(value) > tw - 40f && size > 24f) { size -= 2f; vp = pen.paint(size, TEXT, bold = true, num = true) }
                    pen.text(value, left + 22f, y + 64f, vp)
                }
                y += th + 16f
                val extra = listOfNotNull(
                    r.rows.count { it.status == "Waktu" }.takeIf { it > 0 }?.let { "$it keluar karena waktu habis" },
                    r.rows.count { it.status == "Hold" }.takeIf { it > 0 }?.let { "$it masih dipegang" },
                    r.rows.count { it.status == "Menunggu" }.takeIf { it > 0 }?.let { "$it menunggu entry" },
                    r.rows.count { it.status == "Batal" }.takeIf { it > 0 }?.let { "$it batal (tidak terbeli)" },
                )
                if (extra.isNotEmpty()) y = pen.block(extra.joinToString(" · "), PAD, y, (W - 2 * PAD).toInt(), pen.paint(25f, DIM))
                y += 30f
            } else {
                y = pen.text(r.period, PAD, y, pen.paint(32f, TEXT, bold = true))
                y += 26f
            }
            // Table header.
            pen.round(RectF(PAD - 12f, y, W - PAD + 12f, y + 56f), 18f, alpha(accent, 0.22f))
            var cx = PAD
            for ((label, w) in COLS) {
                val right = label == "P/L"
                pen.text(label, if (right) cx + w - 8f else cx, y + 14f, pen.paint(22f, TEXT, bold = true, align = if (right) Paint.Align.RIGHT else Paint.Align.LEFT))
                cx += w
            }
            y += 66f
            if (chunk.isEmpty()) {
                y = pen.block("Belum ada transaksi di periode ini.", PAD, y + 10f, (W - 2 * PAD).toInt(), pen.paint(28f, MUTED)) + 20f
            }
            chunk.forEachIndexed { i, row ->
                if (i % 2 == 0) pen.round(RectF(PAD - 12f, y, W - PAD + 12f, y + ROW_H - 6f), 14f, alpha(SURFACE, 0.75f))
                val sc = statusColor(row.status)
                pen.c.drawRoundRect(RectF(PAD - 12f, y + 8f, PAD - 4f, y + ROW_H - 14f), 4f, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = sc })
                val ty = y + 14f
                val cells = listOf(
                    row.ticker,
                    row.buy?.let { Market.shortDate(it) } ?: if (row.status == "Batal") "batal" else "–",
                    row.sell?.let { Market.shortDate(it) } ?: "–",
                    row.hold?.let { "$it hr" } ?: "–",
                    row.entry?.let { Fmt.price(it) } ?: "–",
                    Fmt.price(row.tp),
                    Fmt.price(row.sl),
                    row.pnl?.let { Fmt.pct(it, decimals = 2) } ?: "–",
                )
                cx = PAD
                cells.forEachIndexed { k, v ->
                    val (_, w) = COLS[k]
                    val paint = when (k) {
                        0 -> pen.paint(30f, TEXT, bold = true, num = true)
                        7 -> pen.paint(27f, row.pnl?.let { if (it >= 0) GREEN else RED } ?: MUTED, bold = true, num = true, align = Paint.Align.RIGHT)
                        4 -> pen.paint(26f, SKY, num = true)
                        5 -> pen.paint(26f, GREEN, num = true)
                        6 -> pen.paint(26f, RED, num = true)
                        else -> pen.paint(25f, DIM, num = k == 3)
                    }
                    pen.text(v, if (k == 7) cx + w - 8f else cx, ty + if (k == 0 || k == 7) 0f else 3f, paint)
                    cx += w
                }
                y += ROW_H
            }
            y += 16f
            // Legend.
            var lx = PAD
            for ((lab, col) in listOf("TP" to GREEN, "SL" to RED, "Waktu habis" to AMBER, "Hold" to BLUE, "Menunggu" to SKY, "Batal" to MUTED)) {
                pen.c.drawRoundRect(RectF(lx, y + 8f, lx + 8f, y + 34f), 4f, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col })
                lx = pen.text(lab, lx + 16f, y + 4f, pen.paint(22f, DIM)).let { lx + 16f + pen.paint(22f, DIM).measureText(lab) + 26f }
            }
            y += 50f
            y = pen.block("P/L dari harga beli ke harga jual, tanpa fee; akumulasi hanya transaksi yang sudah selesai.",
                PAD, y, (W - 2 * PAD).toInt(), pen.paint(22f, MUTED))
            y += 20f
            if (pages > 1) y = pen.text("Halaman ${page + 1} dari $pages", PAD, y, pen.paint(26f, SKY, bold = true)) + 10f
            footer(pen, y + 10f, "Lumi Signal IDX · bukan rekomendasi investasi.")
        }

    fun shareReport(ctx: Context, r: Report, name: String) =
        sendImages(ctx, reportPages(ctx, r), name, "Bagikan ${r.kind}")

    /** "15 Okt – 20 Okt 2026" with buy dates. */
    fun periodLabel(fromSignal: String, toSignal: String): String {
        val a = RadarPorto.nextSession(fromSignal)
        val b = RadarPorto.nextSession(toSignal)
        return if (a == b) Market.prettyDate(a) else "${Market.prettyDate(a, false)} – ${Market.prettyDate(b, false)}"
    }

    // ------------------------------------------------------------------ stock check (Cek Saham)

    /**
     * Analysis of one stock: price, buy area, TP & SL, two supports and two resistances, a small candle
     * chart with those levels, and whether it is in today's Top 5.
     */
    fun analysisBitmap(
        ctx: Context,
        symbol: String,
        name: String,
        refDate: String,
        bars: List<com.lumisignal.idx.data.Bar>,
        plan: com.lumisignal.idx.data.TradePlan,
        sr: com.lumisignal.idx.engine.SupportResistance,
        radarLabel: String?,
    ): Bitmap {
        val last = bars.last()
        val prev = bars.getOrNull(bars.size - 2)
        val chg = Market.changePct(last, prev)
        return render(ctx, SKY) { pen ->
            var y = header(pen, PAD, "Cek Saham · Hasil Analisa")
            y += 50f
            var x = PAD
            if (radarLabel != null) x = pen.pill(radarLabel, PAD, y, GREEN, filled = true, size = 28f) + 14f
            pen.pill("Data closing ${Market.prettyDate(refDate, false)}", x, y, SKY, false, 28f)
            y += 92f
            pen.text(symbol, PAD - 6f, y, pen.paint(150f, TEXT, bold = true, num = true))
            val px = pen.paint(70f, TEXT, bold = true, num = true, align = Paint.Align.RIGHT)
            pen.text(Fmt.price(last.close), W - PAD, y + 20f, px)
            pen.text(Fmt.pct(chg), W - PAD, y + 110f, pen.paint(34f, if (chg >= 0) GREEN else RED, bold = true, num = true, align = Paint.Align.RIGHT))
            y += 176f
            if (name.isNotBlank()) y = pen.block(name, PAD, y, (W - 2 * PAD).toInt(), pen.paint(32f, DIM))
            y += 36f
            // Plan tiles.
            val gap = 24f
            val tw = (W - 2 * PAD - 2 * gap) / 3
            val th = 190f
            val ref = plan.entryHigh
            pen.tile(RectF(PAD, y, PAD + tw, y + th), "AREA BELI", Fmt.price(ref), "maks. harga beli", BLUE)
            pen.tile(RectF(PAD + tw + gap, y, PAD + 2 * tw + gap, y + th), "TAKE PROFIT", Fmt.price(plan.tp), Fmt.pct(Market.pct(plan.tp, ref), decimals = 2), GREEN)
            pen.tile(RectF(PAD + 2 * (tw + gap), y, W - PAD, y + th), "STOP LOSS", Fmt.price(plan.stopLoss), Fmt.pct(Market.pct(plan.stopLoss, ref), decimals = 2), RED)
            y += th + 30f
            // Support & resistance.
            val resColor = 0xFFFF9F43.toInt()
            val supColor = 0xFFB98BFF.toInt()
            val lv = listOf(
                Triple("RESISTANCE 2", sr.resistances.getOrNull(1)?.price, resColor),
                Triple("RESISTANCE 1", sr.resistances.getOrNull(0)?.price, resColor),
                Triple("SUPPORT 1", sr.supports.getOrNull(0)?.price, supColor),
                Triple("SUPPORT 2", sr.supports.getOrNull(1)?.price, supColor),
            )
            val sw = (W - 2 * PAD - 3 * 18f) / 4
            lv.forEachIndexed { i, (label, price, col) ->
                val left = PAD + i * (sw + 18f)
                pen.round(RectF(left, y, left + sw, y + 130f), 24f, alpha(col, 0.12f), alpha(col, 0.5f))
                pen.text(label, left + 20f, y + 22f, pen.paint(21f, col, bold = true))
                pen.text(Fmt.price(price), left + 20f, y + 58f, pen.paint(44f, TEXT, bold = true, num = true))
            }
            y += 130f + 30f
            // Small candle chart with the levels.
            val chartTop = y
            val ch = 520f
            pen.round(RectF(PAD, chartTop, W - PAD, chartTop + ch), 30f, alpha(SURFACE, 0.85f), BORDER)
            val show = bars.takeLast(60)
            val levels = listOfNotNull(
                plan.tp.takeIf { it > 0 }?.let { it to GREEN },
                ref.let { it to BLUE },
                plan.stopLoss.takeIf { it > 0 }?.let { it to RED },
            ) + lv.mapNotNull { (_, p, c) -> p?.let { it to c } }
            val hi = maxOf(show.maxOf { it.high }, levels.maxOf { it.first })
            val lo = minOf(show.minOf { it.low }, levels.minOf { it.first })
            val left = PAD + 24f
            val right = W - PAD - 130f
            val top = chartTop + 30f
            val bottom = chartTop + ch - 30f
            fun yOf(v: Double) = (bottom - (v - lo) / (hi - lo).coerceAtLeast(1e-9) * (bottom - top)).toFloat()
            val step = (right - left) / show.size
            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 2f; style = Paint.Style.STROKE; pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 8f), 0f) }
            for ((v, c) in levels) {
                line.color = alpha(c, 0.85f)
                val ly = yOf(v)
                pen.c.drawLine(left, ly, right + 8f, ly, line)
                pen.text(Fmt.price(v), right + 16f, ly - 15f, pen.paint(22f, c, bold = true, num = true))
            }
            val body = Paint(Paint.ANTI_ALIAS_FLAG)
            show.forEachIndexed { i, b ->
                val cx = left + step * i + step / 2
                body.color = if (b.close >= b.open) GREEN else RED
                body.strokeWidth = 2f
                pen.c.drawLine(cx, yOf(b.high), cx, yOf(b.low), body)
                val t = yOf(maxOf(b.open, b.close))
                val btm = yOf(minOf(b.open, b.close)).coerceAtLeast(t + 2f)
                pen.c.drawRect(cx - step * 0.35f, t, cx + step * 0.35f, btm, body)
            }
            y = chartTop + ch + 16f
            y = pen.block("Candle harian 60 sesi terakhir · garis: area beli (biru), TP (hijau), SL (merah), resistance (oranye), support (ungu)", PAD, y, (W - 2 * PAD).toInt(), pen.paint(22f, MUTED))
            y += 30f
            footer(pen, y, "Hasil analisa otomatis dari support & resistance terdekat, bukan rekomendasi investasi. Selalu gunakan stop loss.")
        }
    }

    fun shareAnalysis(
        ctx: Context, symbol: String, name: String, refDate: String, bars: List<com.lumisignal.idx.data.Bar>,
        plan: com.lumisignal.idx.data.TradePlan, sr: com.lumisignal.idx.engine.SupportResistance, radarLabel: String?,
    ) = sendImages(ctx, listOf(analysisBitmap(ctx, symbol, name, refDate, bars, plan, sr, radarLabel)), "analisa-$symbol-$refDate", "Bagikan analisa $symbol")
}
