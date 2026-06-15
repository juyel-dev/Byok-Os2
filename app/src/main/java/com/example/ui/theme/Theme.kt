package com.example.ui.theme

// Cache buster: 1

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

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

private val DarkColorScheme =
  darkColorScheme(
    primary = PremiumPrimaryNight,
    onPrimary = PremiumOnPrimaryNight,
    secondary = PremiumSecondaryNight,
    tertiary = EmeraldGlow,
    background = PremiumBackgroundNight,
    surface = PremiumSurfaceNight,
    surfaceVariant = PremiumSurfaceVariantNight,
    onBackground = PremiumTextPrimaryNight,
    onSurface = PremiumTextPrimaryNight,
    onSurfaceVariant = PremiumTextSecondaryNight,
    outline = Slate700
  )

private val LightColorScheme =
  lightColorScheme(
    primary = PremiumPrimaryDay,
    onPrimary = PremiumOnPrimaryDay,
    secondary = PremiumSecondaryDay,
    tertiary = EmeraldGlow,
    background = PremiumBackgroundDay,
    surface = PremiumSurfaceDay,
    surfaceVariant = Color(0xFFF1F5F9),
    onBackground = PremiumTextPrimaryDay,
    onSurface = PremiumTextPrimaryDay,
    onSurfaceVariant = PremiumTextSecondaryDay,
    outline = Color(0xFFE2E8F0)
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
