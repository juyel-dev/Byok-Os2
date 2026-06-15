package com.example.core.common.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme

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

@Composable
fun getByokColors(themeMode: String): ByokColorScheme {
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        "LIGHT" -> false
        "DARK" -> true
        else -> isSystemDark
    }
    
    return if (!isDark) {
        ByokColorScheme(
            background = PremiumBackgroundDay, // Slate 50
            cardBackground = PremiumSurfaceDay,
            topBarBackground = PremiumBackgroundDay, // Slate 200
            textPrimary = PremiumTextPrimaryDay, // Slate 900
            textSecondary = PremiumTextSecondaryDay, // Slate 600
            border = Color(0xFFE2E8F0), // Slate 300
            primaryAccent = PremiumPrimaryDay,
            fieldBackground = Color(0xFFF1F5F9), // Slate 100
            textPlaceholder = Color(0xFF94A3B8), // Slate 400
            drawerContainerColor = PremiumSurfaceDay,
            buttonBackground = Color(0xFFCBD5E1)
        )
    } else {
        ByokColorScheme(
            background = PremiumBackgroundNight, // Dark Slate
            cardBackground = PremiumSurfaceNight, // Slate 800-ish
            topBarBackground = PremiumBackgroundNight,
            textPrimary = PremiumTextPrimaryNight,
            textSecondary = PremiumTextSecondaryNight,
            border = PremiumSurfaceVariantNight,
            primaryAccent = PremiumPrimaryNight,
            fieldBackground = PremiumSurfaceVariantNight,
            textPlaceholder = Color(0xFF475569),
            drawerContainerColor = PremiumBackgroundNight,
            buttonBackground = Color(0xFF1F2937)
        )
    }
}
