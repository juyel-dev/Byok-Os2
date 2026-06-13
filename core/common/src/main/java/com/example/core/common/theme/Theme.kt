package com.example.core.common.theme

import androidx.compose.ui.graphics.Color

data class ByokColorScheme(
    val background: Color,
    val cardBackground: Color,
    val topBarBackground: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val border: Color,
    val primaryAccent: Color,
    val fieldBackground: Color,
    val textPlaceholder: Color,
    val drawerContainerColor: Color,
    val buttonBackground: Color
)

fun getByokColors(themeMode: String): ByokColorScheme {
    return when (themeMode) {
        "LIGHT" -> ByokColorScheme(
            background = Color(0xFFF8FAFC), // Slate 50
            cardBackground = Color.White,
            topBarBackground = Color(0xFFE2E8F0), // Slate 200
            textPrimary = Color(0xFF0F172A), // Slate 900
            textSecondary = Color(0xFF475569), // Slate 600
            border = Color(0xFFCBD5E1), // Slate 300
            primaryAccent = Teal600,
            fieldBackground = Color(0xFFF1F5F9), // Slate 100
            textPlaceholder = Color(0xFF94A3B8), // Slate 400
            drawerContainerColor = Color(0xFFE2E8F0),
            buttonBackground = Color(0xFFCBD5E1)
        )
        "SYSTEM SLATE" -> ByokColorScheme(
            background = Color(0xFF0F172A), // Slate 900
            cardBackground = Color(0xFF1E293B), // Slate 800
            topBarBackground = Color(0xFF0F172A),
            textPrimary = Color.White,
            textSecondary = Color(0xFF94A3B8), // Slate 400
            border = Color(0xFF334155), // Slate 700
            primaryAccent = TealGlow,
            fieldBackground = Color(0xFF1E293B),
            textPlaceholder = Color(0xFF475569),
            drawerContainerColor = Color(0xFF0F172A),
            buttonBackground = Color(0xFF334155)
        )
        else -> // "DARK"
        ByokColorScheme(
            background = Color(0xFF090D16), // Dark Slate
            cardBackground = Color(0xFF111827), // Slate 800-ish
            topBarBackground = Color(0xFF090D16),
            textPrimary = Color.White,
            textSecondary = Color(0xFF8E9EB4),
            border = Color(0xFF1E293B),
            primaryAccent = TealGlow,
            fieldBackground = Color(0xFF111827),
            textPlaceholder = Color(0xFF475569),
            drawerContainerColor = Color(0xFF090D16),
            buttonBackground = Color(0xFF1F2937)
        )
    }
}
