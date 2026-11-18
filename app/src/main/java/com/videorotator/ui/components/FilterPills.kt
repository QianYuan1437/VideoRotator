package com.videorotator.ui.components

import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videorotator.ui.theme.CardSurface
import com.videorotator.ui.theme.OnPrimary
import com.videorotator.ui.theme.PurpleDark
import com.videorotator.ui.theme.PurplePrimary

/** 筛选标签：选中为主题色实心 + 勾，未选中为描边 */
@Composable
fun FilterPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector? = null
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) PurplePrimary else CardSurface,
        border = if (selected) null else BorderStroke(1.dp, PurplePrimary.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selected || icon != null) {
                Icon(
                    if (selected) Icons.Filled.Check else icon!!,
                    null,
                    tint = if (selected) OnPrimary else PurpleDark,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text,
                fontSize = 13.sp,
                color = if (selected) OnPrimary else PurpleDark,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

/**
 * 可释放空间的提示条：「N 个原视频仍在，共 X」+ 一键删除按钮。
 */
@Composable
fun FreeSpaceBanner(
    text: String,
    actionText: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = DangerRed.copy(alpha = 0.08f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.DeleteSweep, null, tint = DangerRed, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text,
                fontSize = 13.sp,
                color = PurpleDark,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(actionText, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

/**
 * 当前筛选项因批量删除 / 重试等操作被清空时回调（用于自动回到「全部」）。
 * 只在同一筛选项的数量由 >0 变为 0 时触发：用户主动点开一个本就为空的筛选项不受影响。
 */
@Composable
fun <T> OnFilterEmptied(filter: T, count: Int, onEmptied: () -> Unit) {
    var last by remember { mutableStateOf(filter to count) }
    LaunchedEffect(filter, count) {
        if (last.first == filter && last.second > 0 && count == 0) onEmptied()
        last = filter to count
    }
}
