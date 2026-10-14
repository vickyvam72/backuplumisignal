package com.lumisignal.idx

import com.lumisignal.idx.data.ExitRule
import com.lumisignal.idx.data.LumiJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** TP/SL rebuilt on the phone must equal the backtest simulator (vectors exported from tools/radar). */
class ExitRuleTest {
    @Serializable
    private data class Vector(val e: Double, val rule: ExitRule, val tp: Double, val sl: Double)

    @Test
    fun levelsMatchBacktest() {
        val text = javaClass.classLoader!!.getResource("level_vectors.json")!!.readText()
        val vectors = LumiJson.decodeFromString(ListSerializer(Vector.serializer()), text)
        assert(vectors.size >= 300)
        for (v in vectors) {
            val lv = v.rule.levels(v.e)
            assertNotNull("levels for $v", lv)
            assertEquals("TP for $v", v.tp, lv!!.first, 1e-6)
            assertEquals("SL for $v", v.sl, lv.second, 1e-6)
        }
    }
}
