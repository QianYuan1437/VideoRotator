package com.videorotator.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.videorotator.ui.components.BackgroundMode
import com.videorotator.ui.components.ThemeColor
import com.videorotator.ui.components.themeColors

// 深色主题色
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFB39DDB),
    onPrimary = Color(0xFF1A1A2E),
    primaryContainer = Color(0xFF9575CD),
    onPrimaryContainer = Color(0xFFEDE7F6),
    secondary = Color(0xFF9575CD),
    onSecondary = Color(0xFF1A1A2E),
    secondaryContainer = Color(0xFF7E57C2),
    onSecondaryContainer = Color(0xFFEDE7F6),
    tertiary = Color(0xFFCE93D8),
    onTertiary = Color(0xFF1A1A2E),
    background = Color(0xFF1A1A2E),
    onBackground = Color(0xFFE6E6E6),
    surface = Color(0xFF2D2D44),
    onSurface = Color(0xFFE6E6E6),
    surfaceVariant = Color(0xFF3D3D5C),
    onSurfaceVariant = Color(0xFFB0B0B0),
    outline = Color(0xFFB39DDB)
)

@Composable
fun VideoRotatorTheme(
    themeColor: ThemeColor = themeColors[0],
    backgroundMode: BackgroundMode = BackgroundMode.LIGHT,
    content: @Composable () -> Unit
) {
    val isDark = when (backgroundMode) {
        BackgroundMode.LIGHT -> false
        BackgroundMode.DARK -> true
        BackgroundMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (isDark) {
        DarkColorScheme.copy(
            primary = themeColor.primary,
            onPrimary = Color(0xFF1A1A2E),
            primaryContainer = themeColor.secondary,
            onPrimaryContainer = Color(0xFFEDE7F6),
            secondary = themeColor.secondary,
            onSecondary = Color(0xFF1A1A2E),
            tertiary = themeColor.primary,
            onTertiary = Color(0xFF1A1A2E),
            outline = themeColor.primary
        )
    } else {
        lightColorScheme(
            primary = themeColor.primary,
            onPrimary = Color.White,
            primaryContainer = themeColor.light,
            onPrimaryContainer = themeColor.secondary,
            secondary = themeColor.secondary,
            onSecondary = Color.White,
            secondaryContainer = themeColor.light,
            onSecondaryContainer = themeColor.secondary,
            tertiary = themeColor.primary,
            onTertiary = Color.White,
            background = themeColor.bg,
            onBackground = Color(0xFF1A1A2E),
            surface = Color.White,
            onSurface = Color(0xFF1A1A2E),
            surfaceVariant = themeColor.light,
            onSurfaceVariant = Color(0xFF666666),
            outline = themeColor.primary
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
