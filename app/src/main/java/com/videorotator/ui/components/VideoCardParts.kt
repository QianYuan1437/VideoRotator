package com.videorotator.ui.components

import com.videorotator.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videorotator.ui.theme.ConvertedGreen
import com.videorotator.ui.theme.OnPrimary
import com.videorotator.ui.theme.PurpleDark
import com.videorotator.ui.theme.PurpleLight
import com.videorotator.ui.theme.PurplePrimary
import com.videorotator.utils.ConvertMark

/** 卡片上的小标签（时长 / 分辨率等） */
@Composable
fun InfoChip(text: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = PurpleLight) {
        Text(
            text,
            fontSize = 12.sp,
            color = PurpleDark,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

/**
 * 视频方向指示器：用一块长方形表示横竖屏方向。
 * - 横屏（landscape）→ 横长方形
 * - 竖屏（portrait）→ 竖长方形
 * 容器尺寸与 InfoChip 一致，视觉上与其他 chip 等高。
 */
@Composable
fun OrientationIndicator(isLandscape: Boolean) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isLandscape) PurplePrimary.copy(alpha = 0.2f) else PurpleLight
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .size(width = 18.dp, height = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(
                        width = if (isLandscape) 18.dp else 8.dp,
                        height = if (isLandscape) 8.dp else 14.dp
                    )
                    .clip(RoundedCornerShape(2.dp))
                    .background(PurpleDark)
            )
        }
    }
}

/**
 * 缩略图左上角的转换标识：
 *  - 已转换的原视频：绿色「已转换」
 *  - 转换产物：主题色「转换产物」+ 角度
 */
@Composable
fun ConvertBadge(mark: ConvertMark, degrees: Int?, modifier: Modifier = Modifier) {
    if (mark == ConvertMark.NONE) return
    val isOutput = mark == ConvertMark.OUTPUT
    val bg: Color = if (isOutput) PurplePrimary else ConvertedGreen
    val fg: Color = if (isOutput) OnPrimary else Color.White
    val label = if (isOutput) {
        stringResource(R.string.badge_output) + (degrees?.let { " ${if (it == 270) "↺" else "↻"}${if (it == 270) 90 else it}°" } ?: "")
    } else stringResource(R.string.badge_converted)
    Surface(
        shape = RoundedCornerShape(topStart = 12.dp, bottomEnd = 10.dp),
        color = bg.copy(alpha = 0.92f),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isOutput) Icons.Filled.Rotate90DegreesCw else Icons.Filled.CheckCircle,
                null,
                tint = fg,
                modifier = Modifier.size(11.dp)
            )
            Spacer(Modifier.width(3.dp))
            Text(label, fontSize = 10.sp, color = fg, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}
