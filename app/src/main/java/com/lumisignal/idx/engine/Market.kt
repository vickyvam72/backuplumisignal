package com.lumisignal.idx.engine

import com.lumisignal.idx.data.Bar
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** IDX market rules and calendar helpers (Asia/Jakarta). */
object Market {
    val ZONE: ZoneId = ZoneId.of("Asia/Jakarta")
    private val ISO = DateTimeFormatter.ISO_LOCAL_DATE
    private val ID_LOCALE = Locale("id", "ID")

    /** After this time a session's closing data is treated as final. */
    val FINAL_CUTOFF: LocalTime = LocalTime.of(16, 30)

    fun now(): ZonedDateTime = ZonedDateTime.now(ZONE)
    fun today(): String = now().toLocalDate().format(ISO)

    /** WIB calendar day of an epoch-millis timestamp. */
    fun dayOf(millis: Long): String =
        java.time.Instant.ofEpochMilli(millis).atZone(ZONE).toLocalDate().format(ISO)

    fun isWeekday(d: LocalDate = now().toLocalDate()): Boolean =
        d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY

    /** True while a session may still be trading (weekday 08:45 – 16:30 WIB). */
    fun sessionInProgress(t: ZonedDateTime = now()): Boolean =
        isWeekday(t.toLocalDate()) && t.toLocalTime() >= LocalTime.of(8, 45) && t.toLocalTime() < FINAL_CUTOFF

    /** True during continuous trading hours, when a live price is meaningful. */
    fun marketOpen(t: ZonedDateTime = now()): Boolean =
        isWeekday(t.toLocalDate()) && t.toLocalTime() >= LocalTime.of(9, 0) && t.toLocalTime() <= LocalTime.of(16, 5)

    /**
     * Drops today's bar while the session is not final yet, so analysis always uses the last
     * COMPLETED session's close (e.g. scanning Thursday 12:00 uses Wednesday's close).
     */
    fun completedOnly(bars: List<Bar>, t: ZonedDateTime = now()): List<Bar> {
        if (bars.isEmpty()) return bars
        val today = t.toLocalDate().format(ISO)
        return if (bars.last().date >= today && sessionInProgress(t)) bars.filter { it.date < today } else bars
    }

    fun prettyDate(iso: String, withDay: Boolean = true): String = try {
        val d = LocalDate.parse(iso, ISO)
        val pattern = if (withDay) "EEEE, d MMM yyyy" else "d MMM yyyy"
        d.format(DateTimeFormatter.ofPattern(pattern, ID_LOCALE))
    } catch (e: Exception) {
        iso
    }

    fun shortDate(iso: String): String = try {
        LocalDate.parse(iso, ISO).format(DateTimeFormatter.ofPattern("d MMM", ID_LOCALE))
    } catch (e: Exception) {
        iso
    }

    // ------------------------------------------------------------- price rules

    /** IDX tick size (fraksi harga). */
    fun tick(price: Double): Double = when {
        price < 200 -> 1.0
        price < 500 -> 2.0
        price < 2000 -> 5.0
        price < 5000 -> 10.0
        else -> 25.0
    }

    fun floorTick(price: Double): Double {
        if (price <= 0) return 0.0
        val t = tick(price)
        return floor(price / t + 1e-9) * t
    }

    fun ceilTick(price: Double): Double {
        if (price <= 0) return 0.0
        val t = tick(price)
        return ceil(price / t - 1e-9) * t
    }

    fun minusTicks(price: Double, n: Int = 1): Double {
        var p = floorTick(price)
        repeat(n) { p = floorTick(p - tick(p - 0.0001)) }
        return max(p, 1.0)
    }

    fun plusTicks(price: Double, n: Int = 1): Double {
        var p = ceilTick(price)
        repeat(n) { p += tick(p) }
        return p
    }

    /** Auto-rejection upper limit as a fraction of the previous close. */
    fun araFraction(prevClose: Double): Double = when {
        prevClose <= 200 -> 0.35
        prevClose <= 5000 -> 0.25
        else -> 0.20
    }

    fun changePct(bar: Bar, prev: Bar?): Double =
        if (prev == null || prev.close <= 0) 0.0 else (bar.close / prev.close - 1.0) * 100.0

    fun isAra(bar: Bar, prev: Bar?): Boolean {
        if (prev == null) return false
        val chg = changePct(bar, prev) / 100.0
        return chg >= araFraction(prev.close) * 0.88 && bar.close >= bar.high
    }

    fun isArb(bar: Bar, prev: Bar?): Boolean {
        if (prev == null) return false
        val chg = changePct(bar, prev) / 100.0
        return bar.close <= bar.low && (chg <= -araFraction(prev.close) * 0.88 || chg <= -0.14)
    }

    // ------------------------------------------------------------- indicators

    fun sma(values: List<Double>, n: Int, endIndex: Int = values.lastIndex): Double? {
        if (endIndex - n + 1 < 0 || endIndex > values.lastIndex) return null
        var s = 0.0
        for (i in endIndex - n + 1..endIndex) s += values[i]
        return s / n
    }

    fun atr(bars: List<Bar>, n: Int = 14): Double {
        if (bars.size < 2) return bars.lastOrNull()?.let { it.high - it.low } ?: 0.0
        val trs = (1 until bars.size).map { i ->
            val b = bars[i]
            val pc = bars[i - 1].close
            max(b.high - b.low, max(abs(b.high - pc), abs(b.low - pc)))
        }
        val tail = trs.takeLast(n)
        return tail.average()
    }

    /** Close position inside the day's range, 0 = at low, 1 = at high. */
    fun closePos(b: Bar): Double = if (b.high > b.low) (b.close - b.low) / (b.high - b.low) else if (b.close >= b.open) 1.0 else 0.0

    /** Swing highs (higher than [k] bars on each side), oldest first. */
    fun swingHighs(bars: List<Bar>, k: Int = 2): List<Double> {
        val out = mutableListOf<Double>()
        for (i in k until bars.size - k) {
            val h = bars[i].high
            var ok = true
            for (j in i - k..i + k) if (j != i && bars[j].high > h) ok = false
            if (ok) out.add(h)
        }
        return out
    }

    fun lowestLow(bars: List<Bar>): Double = bars.minOfOrNull { it.low } ?: 0.0
    fun highestHigh(bars: List<Bar>): Double = bars.maxOfOrNull { it.high } ?: 0.0

    fun pct(a: Double, b: Double): Double = if (b == 0.0) 0.0 else (a / b - 1.0) * 100.0

    fun clamp01(x: Double): Double = min(1.0, max(0.0, x))
}
