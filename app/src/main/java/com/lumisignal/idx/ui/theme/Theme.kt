package com.lumisignal.idx.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.lumisignal.idx.R
import com.lumisignal.idx.data.SignalStatus

object Lumi {
    val Bg = Color(0xFF070B1A)
    val Bg2 = Color(0xFF0A1024)
    val Surface = Color(0xFF0F1731)
    val SurfaceHi = Color(0xFF16214A)
    val Border = Color(0xFF223062)
    val Text = Color(0xFFEAF1FF)
    val TextDim = Color(0xFF9EABD0)
    val TextMuted = Color(0xFF65739C)
    val Sky = Color(0xFF9EDCFF)
    val Blue = Color(0xFF3D8BFF)
    val DeepBlue = Color(0xFF2A66EE)
    val Green = Color(0xFF2BD9A0)
    val Red = Color(0xFFFF5C7A)
    val Amber = Color(0xFFFFC14D)
    val Purple = Color(0xFFB98BFF)

    val LogoGradient = Brush.linearGradient(listOf(Color(0xFF9EDCFF), Color(0xFF5AA6F7), Color(0xFF2A66EE)))
    val ButtonGradient = Brush.horizontalGradient(listOf(Color(0xFF6CC4FF), Color(0xFF2F6BFF)))

    fun strategyColor(id: Int): Color = when (id) {
        1 -> Color(0xFFFF9F43)
        2 -> Color(0xFF26C6F9)
        3 -> Color(0xFFFF5C8A)
        4 -> Color(0xFF2BD9A0)
        5 -> Color(0xFFB98BFF)
        else -> Blue
    }

    fun brokerTypeColor(type: String): Color = when {
        type.contains("asing", true) || type.contains("foreign", true) -> Color(0xFFFF7AC6)
        type.contains("pemerintah", true) || type.contains("gov", true) -> Amber
        else -> Color(0xFF7FB8FF)
    }

    fun statusColor(s: SignalStatus): Color = when (s) {
        SignalStatus.WAITING -> Sky
        SignalStatus.ACTIVE -> Blue
        SignalStatus.TP -> Green
        SignalStatus.SL -> Red
        SignalStatus.CANCELLED -> TextMuted
        SignalStatus.EXPIRED -> Amber
    }
}

@OptIn(ExperimentalTextApi::class)
private fun jakarta(w: Int) = Font(
    R.font.plus_jakarta_sans,
    FontWeight(w),
    variationSettings = FontVariation.Settings(FontVariation.weight(w)),
)

@OptIn(ExperimentalTextApi::class)
private fun grotesk(w: Int) = Font(
    R.font.space_grotesk,
    FontWeight(w),
    variationSettings = FontVariation.Settings(FontVariation.weight(w)),
)

val JakartaFamily = FontFamily(jakarta(400), jakarta(500), jakarta(600), jakarta(700), jakarta(800))
val GroteskFamily = FontFamily(grotesk(400), grotesk(500), grotesk(600), grotesk(700))

/** Numbers & display figures. */
val NumberStyle = TextStyle(fontFamily = GroteskFamily, fontWeight = FontWeight.SemiBold)

private val LumiTypography = Typography(
    displaySmall = TextStyle(fontFamily = GroteskFamily, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = GroteskFamily, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontFamily = JakartaFamily, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = JakartaFamily, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = JakartaFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 21.sp),
    titleSmall = TextStyle(fontFamily = JakartaFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 19.sp),
    bodyLarge = TextStyle(fontFamily = JakartaFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = JakartaFamily, fontWeight = FontWeight.Normal, fontSize = 13.5.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontFamily = JakartaFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = JakartaFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = JakartaFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = JakartaFamily, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, lineHeight = 14.sp, letterSpacing = 0.4.sp),
)

private val LumiColors = darkColorScheme(
    primary = Lumi.Blue,
    onPrimary = Color.White,
    secondary = Lumi.Sky,
    background = Lumi.Bg,
    onBackground = Lumi.Text,
    surface = Lumi.Surface,
    onSurface = Lumi.Text,
    surfaceVariant = Lumi.SurfaceHi,
    onSurfaceVariant = Lumi.TextDim,
    outline = Lumi.Border,
    error = Lumi.Red,
)

@Composable
fun LumiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LumiColors, typography = LumiTypography, content = content)
}
