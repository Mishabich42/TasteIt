package com.example.tesltls.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Палітра кольорів з підтримкою світлої та темної теми. */
object FoodColors {
    val Background: Color
        @Composable get() = if (LocalIsDarkTheme.current) Color(0xFF121212) else Color(0xFFFFFDF9)

    val BottomBar: Color
        @Composable get() = if (LocalIsDarkTheme.current) Color(0xFF1E1E1E) else Color(0xFFFFFEF8)

    val Divider: Color
        @Composable get() = if (LocalIsDarkTheme.current) Color(0xFF2C2C2C) else Color(0xFFEDEBDD)

    val TextPrimary: Color
        @Composable get() = if (LocalIsDarkTheme.current) Color(0xFFE0E0E0) else Color(0xFF242D26)

    val TextSecondary: Color
        @Composable get() = if (LocalIsDarkTheme.current) Color(0xFFA0A6A1) else Color(0xFF6A726C)

    val TextMuted: Color
        @Composable get() = if (LocalIsDarkTheme.current) Color(0xFF757A72) else Color(0xFF83887D)

    val Green: Color
        @Composable get() = if (LocalIsDarkTheme.current) Color(0xFF4CAF50) else Color(0xFF3F6548)

    val GreenLabel: Color
        @Composable get() = if (LocalIsDarkTheme.current) Color(0xFF81C784) else Color(0xFF5A8464)

    val GreenSoft: Color
        @Composable get() = if (LocalIsDarkTheme.current) Color(0xFF1B3820) else Color(0xFFE7EDCF)

    val SearchField: Color
        @Composable get() = if (LocalIsDarkTheme.current) Color(0xFF252525) else Color(0xFFF1F1E9)

    val Pill: Color
        @Composable get() = if (LocalIsDarkTheme.current) Color(0xFF2A2D2A) else Color(0xFFE9EDDF)

    val CardGreen: Color
        @Composable get() = if (LocalIsDarkTheme.current) Color(0xFF1F2E21) else Color(0xFFE7EDDC)

    val CardPlaces: Color
        @Composable get() = if (LocalIsDarkTheme.current) Color(0xFF33271D) else Color(0xFFF5E7D5)

    val CardDelivery: Color
        @Composable get() = if (LocalIsDarkTheme.current) Color(0xFF1E2A27) else Color(0xFFE6ECE9)

    val CardNeutral: Color
        @Composable get() = if (LocalIsDarkTheme.current) Color(0xFF222222) else Color(0xFFF1F1E9)

    val IconBadge: Color
        @Composable get() = if (LocalIsDarkTheme.current) Color(0xFF2D322B) else Color(0xFFF7F9F1)

    val Danger: Color
        @Composable get() = if (LocalIsDarkTheme.current) Color(0xFFE57373) else Color(0xFFC95A4F)

    val DangerSoft: Color
        @Composable get() = if (LocalIsDarkTheme.current) Color(0xFF3D2321) else Color(0xFFFBE4E1)

    val YouTubeRed: Color = Color(0xFFE53935)
}
