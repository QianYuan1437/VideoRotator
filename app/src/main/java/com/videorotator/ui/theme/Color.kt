package com.videorotator.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// 主题相关颜色：历史上这几个名字是写死的浅紫色常量，导致「主题颜色 / 深色模式」
// 只对底部导航栏生效。现在改为从当前主题读取（见 Theme.kt 的 AppPalette），
// 名字保持不变，各页面无需改调用方式。
val PurplePrimary: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.primary   // 主色调
val PurpleSecondary: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.secondary // 辅助色
val PurpleDark: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.dark         // 标题 / 强调文字
val PurpleLight: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.light       // 浅色块（chip、选择框）
val PurpleBg: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.bg             // 页面背景
val PurpleAccent: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.primary

/** 卡片 / 对话框底色（浅色模式白色，深色模式深灰） */
val CardSurface: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surface

/** 主色块上的文字 / 图标颜色（柠檬黄等浅色主题自动改用深色字） */
val OnPrimary: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.onPrimary

/** 已转换标识用的绿色 */
val ConvertedGreen = Color(0xFF2E7D32)

val White = Color(0xFFFFFFFF)
val Black = Color(0xFF1A1A2E)
val Gray = Color(0xFF666666)
val LightGray = Color(0xFFF5F5F5)
val DarkSurface = Color(0xFF2D2D44)
