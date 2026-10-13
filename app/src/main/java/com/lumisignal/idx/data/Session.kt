package com.lumisignal.idx.data

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URLDecoder

const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"

data class Tokens(val access: String, val refresh: String?)

/** Stores the Stockbit session captured from the login WebView (app-private storage). */
class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("lumi_session", Context.MODE_PRIVATE)

    private val _loggedIn = MutableStateFlow(prefs.getString(KEY_ACCESS, null) != null)
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    private val _expired = MutableStateFlow(false)
    /** Set when the token expired and could not be refreshed: the user must log in again. */
    val expired: StateFlow<Boolean> = _expired.asStateFlow()

    val access: String? get() = prefs.getString(KEY_ACCESS, null)
    val refresh: String? get() = prefs.getString(KEY_REFRESH, null)
    val userAgent: String get() = prefs.getString(KEY_UA, null) ?: DESKTOP_UA
    val connectedAt: Long get() = prefs.getLong(KEY_CONNECTED, 0L)

    val displayName: String
        get() {
            val token = access ?: return ""
            val payload = TokenParser.jwtPayload(token) ?: return ""
            val data = payload["data"] as? JsonObject
            return data?.get("ful").asStr()?.takeIf { it.isNotBlank() }
                ?: data?.get("use").asStr()
                ?: ""
        }

    val accessExpiry: Long get() = access?.let { TokenParser.jwtExp(it) } ?: 0L

    @Synchronized
    fun save(access: String, refresh: String?, ua: String = userAgent) {
        val editor = prefs.edit()
            .putString(KEY_ACCESS, access)
            .putString(KEY_UA, ua)
        if (!refresh.isNullOrBlank()) editor.putString(KEY_REFRESH, refresh)
        if (connectedAt == 0L) editor.putLong(KEY_CONNECTED, System.currentTimeMillis())
        editor.apply()
        _loggedIn.value = true
        _expired.value = false
    }

    fun markExpired() {
        _expired.value = true
    }

    @Synchronized
    fun clear() {
        prefs.edit().clear().apply()
        _loggedIn.value = false
        _expired.value = false
    }

    companion object {
        private const val KEY_ACCESS = "access"
        private const val KEY_REFRESH = "refresh"
        private const val KEY_UA = "ua"
        private const val KEY_CONNECTED = "connected_at"
    }
}

object TokenParser {
    private val JWT_RE = Regex("eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}")

    fun isJwt(s: String?): Boolean = s != null && JWT_RE.matches(s.trim())

    fun jwtPayload(token: String): JsonObject? = try {
        val part = token.split(".").getOrNull(1) ?: return null
        val bytes = Base64.decode(part, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        LumiJson.parseToJsonElement(String(bytes, Charsets.UTF_8)) as? JsonObject
    } catch (e: Exception) {
        null
    }

    /** Expiry in epoch seconds (0 when unknown). */
    fun jwtExp(token: String): Long = jwtPayload(token)?.get("exp").asNum()?.toLong() ?: 0L

    /** Reads `credentialStorage` from a stockbit.com Cookie header. */
    fun fromCookieHeader(cookieHeader: String?): Tokens? {
        if (cookieHeader.isNullOrBlank()) return null
        val part = cookieHeader.split(";").map { it.trim() }
            .firstOrNull { it.startsWith("credentialStorage=") } ?: return null
        var value = part.substringAfter("=")
        repeat(2) {
            if (value.contains("%")) {
                value = try {
                    URLDecoder.decode(value, "UTF-8")
                } catch (e: Exception) {
                    value
                }
            }
        }
        return fromJsonText(value)
    }

    /** Structural search for an access/refresh token pair in any JSON text (cookie, login body). */
    fun fromJsonText(text: String?): Tokens? {
        if (text.isNullOrBlank()) return null
        val root = try {
            LumiJson.parseToJsonElement(text)
        } catch (e: Exception) {
            null
        }
        if (root != null) {
            val access = findToken(root, listOf("access", "access_token", "accessToken", "token"))
            if (access != null) {
                val refresh = findToken(root, listOf("refresh", "refresh_token", "refreshToken"))
                return Tokens(access, refresh?.takeIf { it != access })
            }
        }
        // Last resort: any JWT in the text. The first one is the access token in all observed shapes.
        val all = JWT_RE.findAll(text).map { it.value }.distinct().toList()
        if (all.isEmpty()) return null
        return Tokens(all.first(), null)
    }

    private fun findToken(root: JsonElement, keys: List<String>, depth: Int = 0): String? {
        if (depth > 8) return null
        val obj = root as? JsonObject ?: return null
        for (k in keys) {
            val v = obj[k] ?: continue
            if (v is JsonPrimitive) {
                val s = v.asStr()
                if (isJwt(s)) return s
            }
            if (v is JsonObject) {
                val t = v["token"].asStr()
                if (isJwt(t)) return t
            }
        }
        for (child in obj.values) {
            if (child is JsonObject) {
                val r = findToken(child, keys, depth + 1)
                if (r != null) return r
            }
        }
        return null
    }
}
