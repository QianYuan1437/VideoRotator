package com.videorotator.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.videorotator.R
import com.videorotator.utils.FileRef
import com.videorotator.utils.VideoUtils
import com.videorotator.viewmodel.ConvertEngine

/** 产物是否只存在应用私有目录（卸载应用时会被一并删除） */
fun ConvertEngine.DeleteCandidate.outputsOnlyPrivate(context: Context): Boolean {
    val dir = VideoUtils.privateOutputDir(context).absolutePath
    return outputs.all { it.path?.startsWith(dir) == true }
}

fun formatSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) "%.2f GB".format(mb / 1024) else "%.1f MB".format(mb)
}

/** 删除原视频确认：删什么、释放多少、保留什么，分点列出 */
@Composable
fun DeleteOriginalDialog(
    candidates: List<ConvertEngine.DeleteCandidate>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val originals: List<FileRef> = candidates.map { it.original }.distinctBy { it.uri }
    val what = if (originals.size == 1) originals[0].name else stringResource(R.string.b_n_originals, originals.size)
    val bullets = buildList {
        add(Bullet(stringResource(R.string.b_delete_target, what)))
        add(Bullet(stringResource(R.string.b_free_space, formatSize(originals.sumOf { it.size }))))
        add(Bullet(stringResource(R.string.b_keep_converted)))
        add(Bullet(stringResource(R.string.b_not_kept_tracks)))
        if (candidates.any { it.outputsOnlyPrivate(context) }) add(Bullet(stringResource(R.string.b_private_warn), warn = true))
    }
    BulletDialog(
        title = stringResource(R.string.delete_original),
        bullets = bullets,
        confirmText = stringResource(R.string.delete),
        danger = true,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

/**
 * 删除产物文件确认。[onlyCopies]：其中原视频已不在设备上的数量（删除后无法找回）。
 */
@Composable
fun DeleteOutputsDialog(
    names: List<String>,
    totalBytes: Long,
    onlyCopies: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val what = if (names.size == 1) names[0] else stringResource(R.string.b_n_files, names.size)
    val bullets = buildList {
        add(Bullet(stringResource(R.string.b_delete_target, what)))
        add(Bullet(stringResource(R.string.b_free_space, formatSize(totalBytes))))
        when {
            onlyCopies == 0 -> add(Bullet(stringResource(R.string.b_original_kept)))
            names.size == 1 -> add(Bullet(stringResource(R.string.delete_file_only_copy), warn = true))
            else -> add(Bullet(stringResource(R.string.b_only_copies, onlyCopies), warn = true))
        }
        add(Bullet(stringResource(R.string.b_irreversible)))
    }
    BulletDialog(
        title = stringResource(R.string.delete_file_title),
        bullets = bullets,
        confirmText = stringResource(R.string.delete),
        danger = true,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}
