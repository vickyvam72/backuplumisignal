package com.lumisignal.idx.engine

import com.lumisignal.idx.data.Bar
import com.lumisignal.idx.data.Signal
import com.lumisignal.idx.data.SignalStatus

/**
 * Replays the sessions after a signal against its plan (entry → TP or SL). Pure and idempotent:
 * status is always recomputed from completed daily bars plus today's running bar (real intraday
 * high/low). When one session touched both TP and SL, the minute-by-minute path of that day is
 * replayed to find which came first; only without that path is SL assumed first (conservative).
 */
object Tracker {

    private data class Step(val bar: Bar, val live: Boolean)

    /** Mutable replay state for one signal. */
    private class State(var status: SignalStatus, var entry: Double?, var entryDate: String?, var note: String) {
        var exit: Double? = null
        var exitDate: String? = null
        var held = 0
        val closed: Boolean get() = status != SignalStatus.WAITING && status != SignalStatus.ACTIVE
    }

    /** True when this session touched both the stop loss and the target (order unknown from OHLC). */
    fun ambiguous(sig: Signal, bar: Bar): Boolean {
        val plan = sig.eval.plan ?: return false
        return bar.low <= plan.stopLoss && bar.high >= plan.tp
    }

    fun track(sig: Signal): Signal {
        val plan = sig.eval.plan ?: return sig
        val start = sig.startDate ?: sig.refDate
        val bars = sig.trackBars.filter { it.date > start }.sortedBy { it.date }
        val steps = bars.map { Step(it, false) }.toMutableList()
        val live = sig.live
        val lastDate = bars.lastOrNull()?.date ?: start
        if (live != null && live.date > lastDate) {
            steps += Step(Bar(live.date, live.open, live.high, live.low, live.last), true)
        }

        val custom = sig.customEntry
        val st = State(
            status = if (custom != null) SignalStatus.ACTIVE else SignalStatus.WAITING,
            entry = custom,
            entryDate = if (custom != null) start else null,
            note = if (custom != null) "Posisi dibuka di ${Fmt.price(custom)}" else "Menunggu harga masuk area beli",
        )
        val sl = plan.stopLoss
        val tp = plan.tp

        fun hitSl(date: String, low: Double?) {
            st.status = SignalStatus.SL
            st.exit = sl
            st.exitDate = date
            st.note = if (st.entryDate == date) "Kena stop loss ${Fmt.price(sl)} di hari yang sama dengan masuk"
            else "Kena stop loss ${Fmt.price(sl)}${low?.let { " (low ${Fmt.price(it)})" } ?: ""} pada ${Market.shortDate(date)}"
        }

        fun hitTp(date: String, high: Double?) {
            st.status = SignalStatus.TP
            st.exit = tp
            st.exitDate = date
            st.note = "TP ${Fmt.price(tp)} tercapai${high?.let { " (high ${Fmt.price(it)})" } ?: ""} pada ${Market.shortDate(date)}"
        }

        fun fill(price: Double, date: String): Boolean {
            if (price <= sl) {
                st.status = SignalStatus.CANCELLED
                st.exitDate = date
                st.note = "Harga langsung di bawah stop loss, sinyal gugur sebelum masuk"
                return false
            }
            st.entry = price
            st.entryDate = date
            st.status = SignalStatus.ACTIVE
            st.held = 1
            st.note = "Masuk di ${Fmt.price(price)} pada ${Market.shortDate(date)}"
            return true
        }

        var idx = 0
        for (step in steps) {
            if (st.closed) break
            idx++
            val b = step.bar
            val wasActive = st.status == SignalStatus.ACTIVE
            if (wasActive) st.held++

            // Batal: the very first session opens below the cancel level.
            val cancel = plan.cancelBelow
            if (st.status == SignalStatus.WAITING && idx == 1 && cancel != null && b.open > 0 && b.open < cancel - 1e-9) {
                st.status = SignalStatus.CANCELLED
                st.exitDate = b.date
                st.note = "Dibuka ${Fmt.price(b.open)}, di bawah batas batal ${Fmt.price(cancel)}"
                break
            }

            val path = sig.dayPaths[b.date]
            if (path != null && path.isNotEmpty()) {
                // ---- Replay the real minute-by-minute order of the day.
                val ticks = (if (b.open > 0) listOf(b.open) else emptyList()) + path
                for (px in ticks) {
                    if (st.status == SignalStatus.WAITING) {
                        if (px <= plan.entryHigh && !fill(px, b.date)) break
                        if (st.status == SignalStatus.WAITING) continue
                    }
                    if (st.status == SignalStatus.ACTIVE) {
                        if (px <= sl) { hitSl(b.date, px); break }
                        if (px >= tp) { hitTp(b.date, px); break }
                    }
                }
                if (st.closed) break
                // Minute closes can miss a short spike: fall back to the bar's extremes for what the
                // path did not settle.
                if (st.status == SignalStatus.WAITING && b.low <= plan.entryHigh) {
                    if (!fill(plan.entryHigh, b.date)) break
                }
                if (st.status == SignalStatus.ACTIVE) {
                    val slHit = b.low <= sl
                    val tpHit = b.high >= tp
                    if (slHit && !tpHit) { hitSl(b.date, b.low); break }
                    if (tpHit && !slHit) { hitTp(b.date, b.high); break }
                    if (slHit && tpHit) { hitSl(b.date, b.low); break }
                }
                if (st.status == SignalStatus.WAITING && idx >= 2 && !step.live) {
                    st.status = SignalStatus.CANCELLED
                    st.exitDate = b.date
                    st.note = "Harga tidak menyentuh area beli dalam 2 sesi"
                    break
                }
            } else {
                // ---- Daily bar only.
                if (st.status == SignalStatus.WAITING) {
                    if (b.low <= plan.entryHigh) {
                        val price = if (b.open in 0.01..plan.entryHigh) b.open else plan.entryHigh
                        if (!fill(price, b.date)) break
                    } else if (idx >= 2 && !step.live) {
                        st.status = SignalStatus.CANCELLED
                        st.exitDate = b.date
                        st.note = "Harga tidak menyentuh area beli dalam 2 sesi"
                        break
                    }
                }
                if (st.status == SignalStatus.ACTIVE) {
                    // Order unknown without the minute path: stop loss first (conservative).
                    if (b.low <= sl) { hitSl(b.date, b.low); break }
                    if (b.high >= tp) { hitTp(b.date, b.high); break }
                }
            }

            if (st.status == SignalStatus.ACTIVE && wasActive && st.held >= plan.maxHoldDays && !step.live) {
                st.status = SignalStatus.EXPIRED
                st.exit = b.close
                st.exitDate = b.date
                st.note = "Batas ${plan.maxHoldDays} sesi tercapai, ditutup di ${Fmt.price(b.close)}"
            }
        }

        val last = steps.lastOrNull()?.bar?.close ?: sig.lastPrice ?: sig.close
        return sig.copy(
            status = st.status,
            statusNote = st.note,
            entryPrice = st.entry,
            entryDate = st.entryDate,
            exitPrice = st.exit,
            exitDate = st.exitDate,
            lastPrice = last,
        )
    }

    fun label(s: SignalStatus): String = when (s) {
        SignalStatus.WAITING -> "Menunggu entry"
        SignalStatus.ACTIVE -> "Posisi aktif"
        SignalStatus.TP -> "TP tercapai"
        SignalStatus.SL -> "Stop loss"
        SignalStatus.CANCELLED -> "Batal"
        SignalStatus.EXPIRED -> "Kedaluwarsa"
    }
}
