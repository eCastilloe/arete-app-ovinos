package com.arete.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.arete.app.R

val BarlowCondensed = FontFamily(
    Font(R.font.barlow_condensed_bold, FontWeight.Bold)
)

val AtkinsonHyperlegible = FontFamily(
    Font(R.font.atkinson_hyperlegible_regular, FontWeight.Normal),
    Font(R.font.atkinson_hyperlegible_bold, FontWeight.Bold)
)

val Typography = Typography(
    // Big numbers: tag folios, weights, indicators
    displayLarge = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 48.sp, lineHeight = 52.sp),
    displayMedium = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 44.sp),
    // Screen titles
    headlineLarge = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp),
    // Section titles
    titleLarge = TextStyle(fontFamily = AtkinsonHyperlegible, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = AtkinsonHyperlegible, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 26.sp),
    // Body text (17-19 sp per the UI guide, never below 16 sp)
    bodyLarge = TextStyle(fontFamily = AtkinsonHyperlegible, fontWeight = FontWeight.Normal, fontSize = 18.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontFamily = AtkinsonHyperlegible, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 24.sp),
    bodySmall = TextStyle(fontFamily = AtkinsonHyperlegible, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    // Buttons and labels
    labelLarge = TextStyle(fontFamily = AtkinsonHyperlegible, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp),
    labelMedium = TextStyle(fontFamily = AtkinsonHyperlegible, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp),
    labelSmall = TextStyle(fontFamily = AtkinsonHyperlegible, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp)
)