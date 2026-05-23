package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = OneUiPrimary,
    secondary = OneUiSecondary,
    tertiary = OneUiTertiary,
    background = OneUiDarkBg,
    surface = OneUiDarkSurface,
    onBackground = OneUiTextDarkPrimary,
    onSurface = OneUiTextDarkPrimary,
    surfaceVariant = OneUiDarkCard,
    onSurfaceVariant = OneUiTextDarkSecondary
  )

private val LightColorScheme =
  lightColorScheme(
    primary = OneUiPrimary,
    secondary = OneUiSecondary,
    tertiary = OneUiTertiary,
    background = OneUiLightBg,
    surface = OneUiLightSurface,
    onBackground = OneUiTextLightPrimary,
    onSurface = OneUiTextLightPrimary,
    surfaceVariant = OneUiLightCard,
    onSurfaceVariant = OneUiTextLightSecondary
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
