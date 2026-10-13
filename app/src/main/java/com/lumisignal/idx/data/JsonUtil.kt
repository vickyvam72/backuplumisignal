package com.lumisignal.idx.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Shared lenient JSON instance. */
val LumiJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
    explicitNulls = false
    coerceInputValues = true
}

fun JsonElement?.asObj(): JsonObject? = this as? JsonObject

fun JsonElement?.asArr(): JsonArray? = this as? JsonArray

fun JsonElement?.asStr(): String? {
    val p = this as? JsonPrimitive ?: return null
    if (p is JsonNull) return null
    val c = p.content
    return if (c == "null") null else c
}

/**
 * Reads a number from a JSON value the way Stockbit sends it: plain numbers, strings with
 * thousands separators ("1,234.5"), percentages ("12.5%"), or wrappers {raw: ..} / {value: ..}.
 * Missing / empty values return null, never zero.
 */
fun JsonElement?.asNum(): Double? {
    when (this) {
        null, is JsonNull -> return null
        is JsonPrimitive -> {
            val cleaned = content.replace(",", "").replace("%", "").replace("Rp", "").trim()
            if (cleaned.isEmpty() || cleaned == "-" || cleaned == "null") return null
            val v = cleaned.toDoubleOrNull() ?: return null
            return if (v.isFinite()) v else null
        }
        is JsonObject -> {
            this["raw"]?.let { return it.asNum() }
            this["value"]?.let { return it.asNum() }
            return null
        }
        else -> return null
    }
}

fun JsonElement?.path(vararg keys: String): JsonElement? {
    var node: JsonElement? = this
    for (k in keys) {
        node = (node as? JsonObject)?.get(k) ?: return null
    }
    return node
}

/** `data` of a Stockbit envelope, unwrapping one level of `{data:{data:..}}` nesting. */
fun JsonElement?.dataOf(): JsonElement? {
    val d = this.path("data") ?: return null
    val inner = d.path("data")
    return if (inner is JsonObject) inner else d
}

/** Finds all arrays of objects anywhere under [root] (depth-first, bounded). */
fun findObjectArrays(root: JsonElement?, maxDepth: Int = 5): List<JsonArray> {
    val out = mutableListOf<JsonArray>()
    fun walk(n: JsonElement?, depth: Int) {
        if (n == null || depth > maxDepth) return
        when (n) {
            is JsonArray -> {
                if (n.isNotEmpty() && n.first() is JsonObject) out.add(n)
                n.forEach { walk(it, depth + 1) }
            }
            is JsonObject -> n.values.forEach { walk(it, depth + 1) }
            else -> Unit
        }
    }
    walk(root, 0)
    return out
}
