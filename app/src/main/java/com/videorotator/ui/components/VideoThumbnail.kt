package com.videorotator.ui.components

import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.videorotator.ui.theme.PurplePrimary
import com.videorotator.utils.VideoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext

// 视频缩略图内存缓存（按字节计量，上限 32MB）
private val thumbnailCache = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
}

// 缩略图解码限制并发：之前每张卡片都在 IO 线程池里同时解码全尺寸首帧，
// 列表一滑几十个解码器并发，反而拖慢首屏
@OptIn(ExperimentalCoroutinesApi::class)
private val thumbnailDispatcher = Dispatchers.IO.limitedParallelism(4)

/**
 * 视频缩略图：优先系统缩略图缓存，失败再抽帧（见 VideoUtils.loadThumbnail），结果放入内存缓存。
 */
@Composable
fun VideoThumbnail(
    uri: Uri,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val key = uri.toString()
    var bitmap by remember(key) { mutableStateOf(thumbnailCache.get(key)) }

    if (bitmap == null) {
        Icon(
            Icons.Filled.Movie,
            null,
            tint = PurplePrimary.copy(alpha = 0.5f),
            modifier = Modifier.size(32.dp)
        )
        LaunchedEffect(key) {
            val loaded = withContext(thumbnailDispatcher) {
                VideoUtils.loadThumbnail(context, uri, 320)
            }
            if (loaded != null) {
                thumbnailCache.put(key, loaded)
                bitmap = loaded
            }
        }
    } else {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    }
}

/** 删除文件后清掉缓存，避免同名新文件显示旧缩略图 */
fun evictThumbnail(uri: Uri) {
    thumbnailCache.remove(uri.toString())
}
