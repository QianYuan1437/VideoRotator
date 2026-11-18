package com.videorotator.ui.components

import androidx.compose.material.icons.filled.HourglassTop
import com.videorotator.R
import com.videorotator.utils.VideoInfo
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.StayCurrentLandscape
import androidx.compose.material.icons.filled.StayCurrentPortrait
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.annotation.StringRes
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

private enum class Shape(val icon: ImageVector, @StringRes val label: Int) {
    PORTRAIT(Icons.Filled.StayCurrentPortrait, R.string.orientation_portrait),
    LANDSCAPE(Icons.Filled.StayCurrentLandscape, R.string.orientation_landscape),
    SQUARE(Icons.Filled.CropSquare, R.string.orientation_square)
}

private fun shapeOf(video: VideoInfo) = when {
    video.displayWidth > video.displayHeight -> Shape.LANDSCAPE
    video.displayWidth < video.displayHeight -> Shape.PORTRAIT
    else -> Shape.SQUARE
}

/**
 * 缩略图右下角的方向标识：手机图标（竖握 / 横握）+「竖屏 / 横屏」文字。
 * 之前是 chip 里一块小长方形，含义不直观。
 */
@Composable
fun OrientationBadge(video: VideoInfo, modifier: Modifier = Modifier) {
    val shape = shapeOf(video)
    Surface(
        shape = RoundedCornerShape(topStart = 10.dp, bottomEnd = 12.dp),
        color = Color.Black.copy(alpha = 0.62f),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(shape.icon, null, tint = Color.White, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(2.dp))
            Text(stringResource(shape.label), fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/** 方向标签（与 InfoChip 同高）：用于转换页等信息行 */
@Composable
fun OrientationChip(video: VideoInfo) {
    val shape = shapeOf(video)
    Surface(shape = RoundedCornerShape(12.dp), color = PurpleLight) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(shape.icon, null, tint = PurplePrimary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(3.dp))
            Text(stringResource(shape.label), fontSize = 12.sp, color = PurpleDark, maxLines = 1, softWrap = false)
        }
    }
}

/**
 * 缩略图左上角的转换标识：
 *  - 在转换队列中：灰色「转换中」
 *  - 已转换的原视频：绿色「已转换」
 *  - 转换产物：主题色「转换产物」+ 角度
 */
@Composable
fun ConvertBadge(mark: ConvertMark, degrees: Int?, modifier: Modifier = Modifier) {
    if (mark == ConvertMark.NONE) return
    val isOutput = mark == ConvertMark.OUTPUT
    val bg: Color = when (mark) {
        ConvertMark.OUTPUT -> PurplePrimary
        ConvertMark.QUEUED -> Color(0xFF757575)
        else -> ConvertedGreen
    }
    val fg: Color = if (isOutput) OnPrimary else Color.White
    val label = when (mark) {
        ConvertMark.OUTPUT -> stringResource(R.string.badge_output) +
            (degrees?.let { " ${if (it == 270) "↺" else "↻"}${if (it == 270) 90 else it}°" } ?: "")
        ConvertMark.QUEUED -> stringResource(R.string.badge_queued)
        else -> stringResource(R.string.badge_converted)
    }
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
                when (mark) {
                    ConvertMark.OUTPUT -> Icons.Filled.Rotate90DegreesCw
                    ConvertMark.QUEUED -> Icons.Filled.HourglassTop
                    else -> Icons.Filled.CheckCircle
                },
                null,
                tint = fg,
                modifier = Modifier.size(11.dp)
            )
            Spacer(Modifier.width(3.dp))
            Text(label, fontSize = 10.sp, color = fg, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}
