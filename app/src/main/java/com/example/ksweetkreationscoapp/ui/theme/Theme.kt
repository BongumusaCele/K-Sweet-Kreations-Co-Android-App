package com.example.ksweetkreationscoapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Plum = Color(0xFF4A285A)
val Lilac = Color(0xFFB478BC)
private val Light = lightColorScheme(primary = Plum, onPrimary = Color.White,
    primaryContainer = Color(0xFFF4EDF6), onPrimaryContainer = Plum,
    secondary = Color(0xFF8B4396), secondaryContainer = Color(0xFFF0DDF4),
    background = Color(0xFFFCFAFC), onBackground = Color(0xFF392345),
    surface = Color.White, onSurface = Color(0xFF392345),
    surfaceVariant = Color(0xFFF4EDF6), onSurfaceVariant = Color(0xFF715E7A),
    outline = Color(0xFFB5A6BD), error = Color(0xFF962630))
private val Dark = darkColorScheme(primary = Color(0xFFE4B5EB), onPrimary = Plum,
    primaryContainer = Color(0xFF543262), onPrimaryContainer = Color(0xFFF8E9FC),
    secondary = Color(0xFFD5A0DD), secondaryContainer = Color(0xFF4C3255),
    background = Color(0xFF1E1821), onBackground = Color(0xFFF8EFFB),
    surface = Color(0xFF2B2230), onSurface = Color(0xFFF8EFFB),
    surfaceVariant = Color(0xFF3A2D40), onSurfaceVariant = Color(0xFFD4C1DC), outline = Color(0xFFA58CAE))
@Composable fun KSweetKreationsCoAppTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) Dark else Light, typography = BakeryTypography, content = content)
}
