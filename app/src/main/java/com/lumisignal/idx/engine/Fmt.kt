package com.lumisignal.idx.engine

import java.util.Locale
import kotlin.math.abs

/** Indonesian number formatting shared by the engine (check details) and the UI. */
object Fmt {
    private val ID = Locale("id", "ID")

    fun price(v: Double?): String {
        if (v == null) return "-"
        return if (v >= 1000 || v == Math.floor(v)) String.format(ID, "%,.0f", v) else String.format(ID, "%,.1f", v)
    }

    fun pct(v: Double?, signed: Boolean = true, decimals: Int = 1): String {
        if (v == null) return "-"
        val f = if (signed) "%+.${decimals}f%%" else "%.${decimals}f%%"
        return String.format(ID, f, v)
    }

    fun ratio(v: Double): String = String.format(ID, "%.1fx", v)

    fun num(v: Double, decimals: Int = 1): String = String.format(ID, "%.${decimals}f", v)

    /** Compact IDR: 1,2 M / 340 jt / 1,5 T. */
    fun idr(v: Double?): String {
        if (v == null) return "-"
        val a = abs(v)
        val sign = if (v < 0) "-" else ""
        return when {
            a >= 1e12 -> sign + String.format(ID, "%.2f T", a / 1e12)
            a >= 1e9 -> sign + String.format(ID, "%.2f M", a / 1e9)
            a >= 1e6 -> sign + String.format(ID, "%.0f jt", a / 1e6)
            a >= 1e3 -> sign + String.format(ID, "%.0f rb", a / 1e3)
            else -> sign + String.format(ID, "%.0f", a)
        }
    }

    /** Compact lot count: 12,3 rb lot / 1,2 jt lot. */
    fun lots(v: Double?): String {
        if (v == null) return "-"
        val a = abs(v)
        return when {
            a >= 1e6 -> String.format(ID, "%.2f jt", a / 1e6)
            a >= 1e3 -> String.format(ID, "%.1f rb", a / 1e3)
            else -> String.format(ID, "%.0f", a)
        }
    }

    fun share(v: Double): String = String.format(ID, "%.0f%%", v * 100)
}
