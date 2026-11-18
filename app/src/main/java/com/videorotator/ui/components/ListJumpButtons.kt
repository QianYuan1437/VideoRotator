package com.videorotator.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.videorotator.R
import com.videorotator.ui.theme.CardSurface
import com.videorotator.ui.theme.OnPrimary
import com.videorotator.ui.theme.PurplePrimary
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 列表右下角的快捷悬浮按钮：
 *  - 回到顶部：离开顶部后出现；点击前的位置会被记住
 *  - 定位：跳到上次浏览的视频（[lastViewedIndex]），或「回到顶部」之前的位置；目标已在屏幕内时隐藏
 */
@Composable
fun ListJumpButtons(
    listState: LazyListState,
    lastViewedIndex: Int?,
    modifier: Modifier = Modifier,
    /** 上次浏览的条目标识：变化时以它为定位目标（覆盖「回到顶部」前记下的位置） */
    lastViewedKey: Any? = lastViewedIndex
) {
    val scope = rememberCoroutineScope()
    var savedIndex by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(lastViewedKey) { if (lastViewedKey != null) savedIndex = null }

    val target = savedIndex ?: lastViewedIndex?.takeIf { it >= 0 }
    val showTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 2 } }
    val targetVisible by remember(target) {
        derivedStateOf {
            target == null || listState.layoutInfo.visibleItemsInfo.any { it.index == target }
        }
    }

    fun jump(index: Int) {
        scope.launch {
            // 距离远时直接跳过去，避免长列表动画滚动很久
            if (abs(listState.firstVisibleItemIndex - index) > 30) listState.scrollToItem(index)
            else listState.animateScrollToItem(index)
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.End
    ) {
        AnimatedVisibility(visible = !targetVisible, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
            SmallFloatingActionButton(
                onClick = { target?.let { jump(it) } },
                containerColor = CardSurface,
                contentColor = PurplePrimary
            ) {
                Icon(Icons.Filled.MyLocation, stringResource(R.string.jump_last_viewed))
            }
        }
        AnimatedVisibility(visible = showTop, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
            SmallFloatingActionButton(
                onClick = {
                    savedIndex = listState.firstVisibleItemIndex
                    jump(0)
                },
                containerColor = PurplePrimary,
                contentColor = OnPrimary
            ) {
                Icon(Icons.Filled.VerticalAlignTop, stringResource(R.string.back_to_top))
            }
        }
    }
}
