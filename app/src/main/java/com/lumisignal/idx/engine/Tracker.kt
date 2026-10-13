package com.lumisignal.idx.engine

import com.lumisignal.idx.data.Bar
import com.lumisignal.idx.data.Signal
import com.lumisignal.idx.data.SignalStatus

/**
 * Replays the sessions after a signal against its plan (entry → TP or SL). Pure and idempotent:
 * status is always recomputed from completed daily bars plus today's running bar, whose high and
 * low are the real intraday extremes from Stockbit (so a TP touched at 10:00 is caught at 12:00).
 * When one session reaches both SL and TP, SL is assumed first (conservative).
 */
object Tracker {

    private data class Step(val bar: Bar, val live: Boolean)

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
        var status = if (custom != null) SignalStatus.ACTIVE else SignalStatus.WAITING
        var entry: Double? = custom
        var entryDate: String? = if (custom != null) start else null
        var exit: Double? = null
        var exitDate: String? = null
        var note = if (custom != null) "Posisi dibuka di ${Fmt.price(custom)}" else "Menunggu harga masuk area beli"
        var held = 0
        val sl = plan.stopLoss
        val tp = plan.tp

        var idx = 0
        loop@ for (step in steps) {
            idx++
            val b = step.bar
            when (status) {
                SignalStatus.WAITING -> {
                    val cancel = plan.cancelBelow
                    if (idx == 1 && cancel != null && b.open > 0 && b.open < cancel - 1e-9) {
                        status = SignalStatus.CANCELLED
                        exitDate = b.date
                        note = "Dibuka ${Fmt.price(b.open)}, di bawah batas batal ${Fmt.price(cancel)}"
                        break@loop
                    }
                    if (b.low <= plan.entryHigh) {
                        val fill = if (b.open in 0.01..plan.entryHigh) b.open else plan.entryHigh
                        if (fill <= sl) {
                            status = SignalStatus.CANCELLED
                            exitDate = b.date
                            note = "Harga langsung di bawah stop loss, sinyal gugur sebelum masuk"
                            break@loop
                        }
                        entry = fill
                        entryDate = b.date
                        status = SignalStatus.ACTIVE
                        held = 1
                        note = "Masuk di ${Fmt.price(fill)} pada ${Market.shortDate(b.date)}"
                        if (b.low <= sl) {
                            status = SignalStatus.SL
                            exit = sl
                            exitDate = b.date
                            note = "Kena stop loss ${Fmt.price(sl)} di hari yang sama dengan masuk"
                            break@loop
                        }
                        if (b.high >= tp) {
                            status = SignalStatus.TP
                            exit = tp
                            exitDate = b.date
                            note = "TP ${Fmt.price(tp)} tercapai (high ${Fmt.price(b.high)}) pada ${Market.shortDate(b.date)}"
                            break@loop
                        }
                    } else if (idx >= 2 && !step.live) {
                        status = SignalStatus.CANCELLED
                        exitDate = b.date
                        note = "Harga tidak menyentuh area beli dalam 2 sesi"
                        break@loop
                    }
                }
                SignalStatus.ACTIVE -> {
                    held++
                    if (b.low <= sl) {
                        status = SignalStatus.SL
                        exit = sl
                        exitDate = b.date
                        note = "Kena stop loss ${Fmt.price(sl)} (low ${Fmt.price(b.low)}) pada ${Market.shortDate(b.date)}"
                        break@loop
                    }
                    if (b.high >= tp) {
                        status = SignalStatus.TP
                        exit = tp
                        exitDate = b.date
                        note = "TP ${Fmt.price(tp)} tercapai (high ${Fmt.price(b.high)}) pada ${Market.shortDate(b.date)}"
                        break@loop
                    }
                    if (held >= plan.maxHoldDays && !step.live) {
                        status = SignalStatus.EXPIRED
                        exit = b.close
                        exitDate = b.date
                        note = "Batas ${plan.maxHoldDays} sesi tercapai, ditutup di ${Fmt.price(b.close)}"
                        break@loop
                    }
                }
                else -> break@loop
            }
        }

        val last = steps.lastOrNull()?.bar?.close ?: sig.lastPrice ?: sig.close
        return sig.copy(
            status = status,
            statusNote = note,
            entryPrice = entry,
            entryDate = entryDate,
            exitPrice = exit,
            exitDate = exitDate,
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
