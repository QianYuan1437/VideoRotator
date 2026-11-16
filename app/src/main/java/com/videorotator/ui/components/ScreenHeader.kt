package com.videorotator.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videorotator.ui.theme.PurpleDark

/** 四个主 Tab 页统一的外边距：左右 16dp、顶部 16dp */
val ScreenHorizontalPadding = 16.dp
val ScreenTopPadding = 16.dp

/**
 * 四个主 Tab 页统一的标题行：24sp 粗体、48dp 高，右侧可放操作按钮。
 * 之前「视频列表」没有标题、「转换列表」标题是 22sp 且上边距只有 8dp，
 * 与「转换配置」「软件设置」不一致。
 */
@Composable
fun ScreenTitle(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = PurpleDark,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        actions()
    }
}
