package com.videorotator.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.videorotator.ui.components.BackgroundMode
import com.videorotator.ui.components.ThemeColor
import com.videorotator.ui.components.themeColors

/** 各页面直接使用的主题色板（PurplePrimary 等 getter 从这里取值） */
data class AppPalette(
    val primary: Color,
    val secondary: Color,
    val dark: Color,
    val light: Color,
    val bg: Color,
    val onPrimary: Color
)

val LocalAppPalette = staticCompositionLocalOf {
    val c = themeColors[0]
    AppPalette(c.primary, c.secondary, Color(0xFF7E57C2), c.light, c.bg, Color.White)
}

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

/** 浅色主题色（柠檬黄等）上白字看不清时改用深色字 */
private fun onColorFor(bg: Color): Color =
    if (bg.luminance() > 0.6f) Color(0xFF3A3A3A) else Color.White

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

    // 按钮等 Material 组件默认用 onPrimary：浅色主题色（柠檬黄等）上改用深色字
    val schemeOnPrimary = onColorFor(themeColor.primary)
    val finalScheme = colorScheme.copy(onPrimary = schemeOnPrimary)

    val palette = if (isDark) {
        AppPalette(
            primary = themeColor.primary,
            secondary = themeColor.secondary,
            // 深色背景上的标题文字：主题色提亮
            dark = lerp(themeColor.primary, Color.White, 0.55f),
            light = lerp(colorScheme.surface, themeColor.primary, 0.18f),
            bg = colorScheme.background,
            onPrimary = onColorFor(themeColor.primary)
        )
    } else {
        AppPalette(
            primary = themeColor.primary,
            secondary = themeColor.secondary,
            // 浅色背景上的标题文字：主题辅助色压暗，保证柠檬黄等浅色主题也可读
            dark = lerp(themeColor.secondary, Color.Black, 0.35f),
            light = themeColor.light,
            bg = themeColor.bg,
            onPrimary = onColorFor(themeColor.primary)
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

    CompositionLocalProvider(LocalAppPalette provides palette) {
        MaterialTheme(
            colorScheme = finalScheme,
            typography = Typography,
            content = content
        )
    }
}
