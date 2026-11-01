package com.manoj.kaasu.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.manoj.kaasu.R

@OptIn(ExperimentalTextApi::class)
private fun geist(weight: Int) = Font(
    R.font.geist,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Geist: precise, slightly technical, built for numbers. One family across the app. */
val Geist = FontFamily(geist(400), geist(500), geist(600), geist(700))

/** Kept for the shared report image only. */
val Jakarta = Geist

/** Tabular figures so amounts line up and don't jump while typing. */
private const val TABULAR = "tnum"

/** Big figures get a little negative tracking; small text never does. */
object AmountStyles {
    val display = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = 60.sp, lineHeight = 64.sp, letterSpacing = (-0.035).em, fontFeatureSettings = TABULAR)
    val hero = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = 40.sp, lineHeight = 46.sp, letterSpacing = (-0.03).em, fontFeatureSettings = TABULAR)
    val input = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = 52.sp, lineHeight = 60.sp, letterSpacing = (-0.03).em, fontFeatureSettings = TABULAR)
    val large = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.02).em, fontFeatureSettings = TABULAR)
    val row = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp, fontFeatureSettings = TABULAR)
    val small = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, fontFeatureSettings = TABULAR)
}

private val base = Typography()

val KaasuTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Geist, fontWeight = FontWeight.Medium, letterSpacing = (-0.03).em),
    displayMedium = base.displayMedium.copy(fontFamily = Geist, fontWeight = FontWeight.Medium, letterSpacing = (-0.03).em),
    displaySmall = base.displaySmall.copy(fontFamily = Geist, fontWeight = FontWeight.Medium, letterSpacing = (-0.02).em),
    headlineLarge = base.headlineLarge.copy(fontFamily = Geist, fontWeight = FontWeight.Medium, letterSpacing = (-0.02).em),
    headlineMedium = base.headlineMedium.copy(fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = 26.sp, letterSpacing = (-0.02).em),
    headlineSmall = base.headlineSmall.copy(fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = 22.sp, letterSpacing = (-0.015).em),
    titleLarge = base.titleLarge.copy(fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = 20.sp, letterSpacing = (-0.01).em),
    titleMedium = base.titleMedium.copy(fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = 16.sp, letterSpacing = 0.sp),
    titleSmall = base.titleSmall.copy(fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 0.sp),
    bodyLarge = base.bodyLarge.copy(fontFamily = Geist, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.sp),
    bodyMedium = base.bodyMedium.copy(fontFamily = Geist, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
    bodySmall = base.bodySmall.copy(fontFamily = Geist, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp),
    labelLarge = base.labelLarge.copy(fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 0.sp),
    labelMedium = base.labelMedium.copy(fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.sp),
    labelSmall = base.labelSmall.copy(fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.sp),
)
