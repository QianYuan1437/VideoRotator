package com.videorotator.ui.components

import androidx.annotation.StringRes
import com.videorotator.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videorotator.ui.theme.PurpleDark
import com.videorotator.ui.theme.PurplePrimary
import com.videorotator.ui.theme.OnPrimary
import com.videorotator.ui.theme.PurpleLight
import com.videorotator.ui.theme.White

// 预定义主题色
data class ThemeColor(
    @StringRes val nameRes: Int,
    val primary: Color,
    val secondary: Color,
    val light: Color,
    val bg: Color
)

val themeColors = listOf(
    ThemeColor(R.string.theme_purple, Color(0xFFB39DDB), Color(0xFF9575CD), Color(0xFFEDE7F6), Color(0xFFF8F4FC)),
    ThemeColor(R.string.theme_pink, Color(0xFFF48FB1), Color(0xFFEC407A), Color(0xFFFCE4EC), Color(0xFFFDF2F5)),
    ThemeColor(R.string.theme_blue, Color(0xFF90CAF9), Color(0xFF42A5F5), Color(0xFFE3F2FD), Color(0xFFF0F7FF)),
    ThemeColor(R.string.theme_green, Color(0xFF80CBC4), Color(0xFF26A69A), Color(0xFFE0F2F1), Color(0xFFF0FAF9)),
    ThemeColor(R.string.theme_orange, Color(0xFFFFCC80), Color(0xFFFFA726), Color(0xFFFFF3E0), Color(0xFFFFFAF2)),
    ThemeColor(R.string.theme_red, Color(0xFFE57373), Color(0xFFEF5350), Color(0xFFFFEBEE), Color(0xFFFFF5F5)),
    ThemeColor(R.string.theme_yellow, Color(0xFFFFF59D), Color(0xFFFFEE58), Color(0xFFFFFDE7), Color(0xFFFFFEF0)),
    ThemeColor(R.string.theme_gray, Color(0xFFB0BEC5), Color(0xFF78909C), Color(0xFFECEFF1), Color(0xFFF5F7FA))
)

// 背景模式
enum class BackgroundMode(@StringRes val labelRes: Int) {
    LIGHT(R.string.mode_light),
    DARK(R.string.mode_dark),
    SYSTEM(R.string.mode_system)
}

@Composable
fun ThemeColorSelector(
    currentColor: ThemeColor,
    onColorSelected: (ThemeColor) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // 第一行：前4个颜色
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            themeColors.take(4).forEach { color ->
                ColorItem(
                    color = color,
                    isSelected = color == currentColor,
                    onClick = { onColorSelected(color) }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        // 第二行：后4个颜色
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            themeColors.drop(4).forEach { color ->
                ColorItem(
                    color = color,
                    isSelected = color == currentColor,
                    onClick = { onColorSelected(color) }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        // 当前选中颜色名称
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = PurpleLight
        ) {
            Text(
                stringResource(R.string.current_theme, stringResource(currentColor.nameRes)),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = PurpleDark,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun ColorItem(
    color: ThemeColor,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp, 40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(color.primary)
                .then(
                    if (isSelected) Modifier.border(3.dp, PurpleDark, RoundedCornerShape(12.dp))
                    else Modifier.border(1.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    Icons.Filled.Check,
                    stringResource(R.string.selected),
                    tint = if (color.primary.luminance() > 0.6f) Color(0xFF3A3A3A) else White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(color.nameRes),
            fontSize = 11.sp,
            color = if (isSelected) PurpleDark else Color.Gray,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun BackgroundModeSelector(
    currentMode: BackgroundMode,
    onModeSelected: (BackgroundMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BackgroundMode.values().forEach { mode ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (mode == currentMode) PurplePrimary else PurpleLight,
                onClick = { onModeSelected(mode) },
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    stringResource(mode.labelRes),
                    fontSize = 14.sp,
                    fontWeight = if (mode == currentMode) FontWeight.Bold else FontWeight.Medium,
                    color = if (mode == currentMode) OnPrimary else PurpleDark,
                    modifier = Modifier.padding(vertical = 12.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}
