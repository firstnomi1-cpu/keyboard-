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
    primary = ShahGold,
    secondary = ShahAmber,
    tertiary = ShahVelvet,
    background = ObsidianDark,
    surface = VelvetViolet,
    onPrimary = ObsidianDark,
    onSecondary = ObsidianDark,
    onTertiary = GoldGlow,
    onBackground = GoldGlow,
    onSurface = LightContrast,
  )

private val LightColorScheme =
  lightColorScheme(
    primary = ShahBrass,
    secondary = ShahAmber,
    tertiary = ShahVelvet,
    background = LightContrast,
    surface = androidx.compose.ui.graphics.Color(0xFFFFFDF5),
    onPrimary = androidx.compose.ui.graphics.Color.White,
    onSecondary = ObsidianDark,
    onTertiary = androidx.compose.ui.graphics.Color.White,
    onBackground = DarkContrast,
    onSurface = DarkContrast,
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
