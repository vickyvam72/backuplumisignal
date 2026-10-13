package com.lumisignal.idx.engine

import com.lumisignal.idx.data.Bar
import com.lumisignal.idx.data.Signal
import com.lumisignal.idx.data.SignalStatus

/**
 * Replays the sessions after a signal against its trade plan. Pure and idempotent: the status is
 * always recomputed from the stored daily bars (final) plus today's live observations (provisional).
 * When one session touches both stop loss and target, stop loss is assumed first (conservative).
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

        var idx = 0
        loop@ for (step in steps) {
            idx++
            val b = step.bar
            when (status) {
                SignalStatus.WAITING -> {
                    val cancel = plan.cancelBelow
                    if (idx == 1 && !step.live && cancel != null && b.open < cancel - 1e-9) {
                        status = SignalStatus.CANCELLED
                        exitDate = b.date
                        note = "Dibuka ${Fmt.price(b.open)}, di bawah batas batal ${Fmt.price(cancel)}"
                        break@loop
                    }
                    if (b.low <= plan.entryHigh) {
                        val fill = if (b.open <= plan.entryHigh) b.open else plan.entryHigh
                        if (fill <= sl) {
                            status = SignalStatus.CANCELLED
                            exitDate = b.date
                            note = "Harga dibuka di bawah stop loss, sinyal gugur sebelum masuk"
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
                        if (b.open <= plan.entryHigh) {
                            if (b.high >= plan.tp2) {
                                status = SignalStatus.TP2
                                exit = plan.tp2
                                exitDate = b.date
                                note = "TP2 ${Fmt.price(plan.tp2)} tercapai"
                                break@loop
                            } else if (b.high >= plan.tp1) {
                                status = SignalStatus.TP1
                                note = "TP1 ${Fmt.price(plan.tp1)} tercapai — stop loss naik ke harga masuk"
                            }
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
                        note = "Kena stop loss ${Fmt.price(sl)}"
                        break@loop
                    }
                    if (b.high >= plan.tp2) {
                        status = SignalStatus.TP2
                        exit = plan.tp2
                        exitDate = b.date
                        note = "TP2 ${Fmt.price(plan.tp2)} tercapai"
                        break@loop
                    }
                    if (b.high >= plan.tp1) {
                        status = SignalStatus.TP1
                        note = "TP1 ${Fmt.price(plan.tp1)} tercapai — stop loss naik ke harga masuk"
                    } else if (held >= plan.maxHoldDays && !step.live) {
                        status = SignalStatus.EXPIRED
                        exit = b.close
                        exitDate = b.date
                        note = "Batas ${plan.maxHoldDays} sesi tercapai, ditutup di ${Fmt.price(b.close)}"
                        break@loop
                    }
                }
                SignalStatus.TP1 -> {
                    held++
                    val e = entry ?: plan.entryHigh
                    if (b.low <= e) {
                        status = SignalStatus.BE
                        exit = e
                        exitDate = b.date
                        note = "Setelah TP1, harga kembali ke harga masuk (impas untuk sisa posisi)"
                        break@loop
                    }
                    if (b.high >= plan.tp2) {
                        status = SignalStatus.TP2
                        exit = plan.tp2
                        exitDate = b.date
                        note = "TP2 ${Fmt.price(plan.tp2)} tercapai"
                        break@loop
                    }
                    if (held >= plan.maxHoldDays && !step.live) {
                        status = SignalStatus.EXPIRED
                        exit = b.close
                        exitDate = b.date
                        note = "Batas ${plan.maxHoldDays} sesi tercapai setelah TP1, ditutup di ${Fmt.price(b.close)}"
                        break@loop
                    }
                }
                else -> break@loop
            }
        }

        val last = steps.lastOrNull()?.bar?.close ?: sig.lastPrice ?: sig.close
        // TP1-then-breakeven assumes half the position was sold at TP1 and half at the entry price.
        val e = entry
        val realizedExit = if (status == SignalStatus.BE && e != null) (plan.tp1 + e) / 2.0 else exit
        return sig.copy(
            status = status,
            statusNote = note,
            entryPrice = entry,
            entryDate = entryDate,
            exitPrice = realizedExit,
            exitDate = exitDate,
            lastPrice = last,
        )
    }

    fun label(s: SignalStatus): String = when (s) {
        SignalStatus.WAITING -> "Menunggu entry"
        SignalStatus.ACTIVE -> "Posisi aktif"
        SignalStatus.TP1 -> "TP1 tercapai"
        SignalStatus.TP2 -> "TP2 tercapai"
        SignalStatus.BE -> "TP1 lalu impas"
        SignalStatus.SL -> "Stop loss"
        SignalStatus.CANCELLED -> "Batal"
        SignalStatus.EXPIRED -> "Kedaluwarsa"
    }
}
