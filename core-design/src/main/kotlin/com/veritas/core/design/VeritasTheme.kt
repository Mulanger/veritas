@file:Suppress("MagicNumber")

package com.veritas.core.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private val manropeFamily =
    FontFamily(
        Font(R.font.manrope_variable, weight = FontWeight.W300),
        Font(R.font.manrope_variable, weight = FontWeight.W400),
        Font(R.font.manrope_variable, weight = FontWeight.W500),
        Font(R.font.manrope_variable, weight = FontWeight.W600),
        Font(R.font.manrope_variable, weight = FontWeight.W700),
        Font(R.font.manrope_variable, weight = FontWeight.W800),
    )

private val jetBrainsMonoFamily =
    FontFamily(
        Font(R.font.jetbrains_mono_variable, weight = FontWeight.W400),
        Font(R.font.jetbrains_mono_variable, weight = FontWeight.W500),
        Font(R.font.jetbrains_mono_variable, weight = FontWeight.W700),
        Font(R.font.jetbrains_mono_variable, weight = FontWeight.W800),
    )

@Stable
object VeritasColors {
    val bg = Color(0xFF0A0B0D)
    val panel = Color(0xFF111318)
    val panel2 = Color(0xFF171A21)
    val line = Color(0xFF22262F)
    val line2 = Color(0xFF2C3140)
    val ink = Color(0xFFE9ECF1)
    val inkDim = Color(0xFF9BA3B4)
    val inkMute = Color(0xFF5C6473)
    val ok = Color(0xFF3FD69B)
    val okDim = Color(0xFF1F6B4F)
    val warn = Color(0xFFF5B642)
    val warnDim = Color(0xFF7A5A1D)
    val bad = Color(0xFFFF5A5A)
    val badDim = Color(0xFF7A2828)
    val accent = Color(0xFF8AB4FF)
}

@Stable
object VeritasType {
    val displayXl =
        TextStyle(
            fontFamily = manropeFamily,
            fontWeight = FontWeight.W300,
            fontSize = 40.sp,
            lineHeight = 44.sp,
            letterSpacing = (-0.03).em,
        )
    val displayLg =
        TextStyle(
            fontFamily = manropeFamily,
            fontWeight = FontWeight.W300,
            fontSize = 34.sp,
            lineHeight = 38.sp,
            letterSpacing = (-0.03).em,
        )
    val displayMd =
        TextStyle(
            fontFamily = manropeFamily,
            fontWeight = FontWeight.W300,
            fontSize = 28.sp,
            lineHeight = 32.sp,
            letterSpacing = (-0.02).em,
        )
    val headingLg =
        TextStyle(
            fontFamily = manropeFamily,
            fontWeight = FontWeight.W600,
            fontSize = 22.sp,
            lineHeight = 28.sp,
        )
    val headingMd =
        TextStyle(
            fontFamily = manropeFamily,
            fontWeight = FontWeight.W700,
            fontSize = 18.sp,
            lineHeight = 24.sp,
        )
    val headingSm =
        TextStyle(
            fontFamily = manropeFamily,
            fontWeight = FontWeight.W600,
            fontSize = 16.sp,
            lineHeight = 22.sp,
        )
    val bodyLg =
        TextStyle(
            fontFamily = manropeFamily,
            fontWeight = FontWeight.W400,
            fontSize = 16.sp,
            lineHeight = 24.sp,
        )
    val bodyMd =
        TextStyle(
            fontFamily = manropeFamily,
            fontWeight = FontWeight.W400,
            fontSize = 14.sp,
            lineHeight = 21.sp,
        )
    val bodySm =
        TextStyle(
            fontFamily = manropeFamily,
            fontWeight = FontWeight.W400,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
    val monoSm =
        TextStyle(
            fontFamily = jetBrainsMonoFamily,
            fontWeight = FontWeight.W700,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.15.em,
        )
    val monoXs =
        TextStyle(
            fontFamily = jetBrainsMonoFamily,
            fontWeight = FontWeight.W500,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            letterSpacing = 0.15.em,
        )
}

@Stable
object VeritasSpacing {
    val unit = 4.dp
    val space4 = 4.dp
    val space8 = 8.dp
    val space12 = 12.dp
    val space16 = 16.dp
    val space20 = 20.dp
    val space24 = 24.dp
    val space32 = 32.dp
    val space48 = 48.dp
    val space64 = 64.dp
}

@Stable
object VeritasRadius {
    val sm = 6.dp
    val md = 10.dp
    val lg = 14.dp
    val xl = 22.dp
    val pill = 999.dp
}

internal val LocalVeritasColors = staticCompositionLocalOf { VeritasColors }
internal val LocalVeritasType = staticCompositionLocalOf { VeritasType }
internal val LocalVeritasSpacing = staticCompositionLocalOf { VeritasSpacing }
internal val LocalVeritasRadius = staticCompositionLocalOf { VeritasRadius }

private val materialTypography =
    Typography(
        displayLarge = VeritasType.displayXl,
        displayMedium = VeritasType.displayLg,
        displaySmall = VeritasType.displayMd,
        headlineLarge = VeritasType.headingLg,
        headlineMedium = VeritasType.headingMd,
        headlineSmall = VeritasType.headingSm,
        bodyLarge = VeritasType.bodyLg,
        bodyMedium = VeritasType.bodyMd,
        bodySmall = VeritasType.bodySm,
        labelLarge = VeritasType.monoSm,
        labelMedium = VeritasType.monoXs,
        labelSmall = VeritasType.monoXs,
    )

private val materialShapes =
    Shapes(
        small = RoundedCornerShape(VeritasRadius.sm),
        medium = RoundedCornerShape(VeritasRadius.md),
        large = RoundedCornerShape(VeritasRadius.lg),
    )

private val materialColors =
    darkColorScheme(
        background = VeritasColors.bg,
        surface = VeritasColors.bg,
        surfaceVariant = VeritasColors.panel,
        primary = VeritasColors.accent,
        secondary = VeritasColors.ok,
        tertiary = VeritasColors.warn,
        error = VeritasColors.bad,
        onBackground = VeritasColors.ink,
        onSurface = VeritasColors.ink,
        onSurfaceVariant = VeritasColors.inkDim,
        onPrimary = VeritasColors.bg,
        onSecondary = VeritasColors.bg,
        onTertiary = VeritasColors.bg,
        onError = VeritasColors.bg,
        outline = VeritasColors.line,
        outlineVariant = VeritasColors.line2,
    )

@Suppress("FunctionName")
@Composable
fun VeritasTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalVeritasColors provides VeritasColors,
        LocalVeritasType provides VeritasType,
        LocalVeritasSpacing provides VeritasSpacing,
        LocalVeritasRadius provides VeritasRadius,
    ) {
        MaterialTheme(
            colorScheme = materialColors,
            typography = materialTypography,
            shapes = materialShapes,
            content = content,
        )
    }
}
