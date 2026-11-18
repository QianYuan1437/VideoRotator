package com.videorotator.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videorotator.R
import com.videorotator.ui.theme.PurpleDark
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

/**
 * 删除原视频确认：说明释放的空间，产物只在私有目录时提醒卸载会一并删除。
 */
@Composable
fun DeleteOriginalDialog(
    candidates: List<ConvertEngine.DeleteCandidate>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val originals: List<FileRef> = candidates.map { it.original }.distinctBy { it.uri }
    val size = formatSize(originals.sumOf { it.size })
    val privateOnly = candidates.any { it.outputsOnlyPrivate(context) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_original), fontWeight = FontWeight.Bold, color = PurpleDark) },
        text = {
            Column {
                Text(
                    if (originals.size == 1) {
                        stringResource(R.string.delete_original_msg_one, originals[0].name, size)
                    } else {
                        stringResource(R.string.delete_original_msg_many, originals.size, size)
                    },
                    fontSize = 14.sp, color = PurpleDark, lineHeight = 20.sp
                )
                if (privateOnly) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.delete_original_private_warn), fontSize = 13.sp, color = DialogRed, lineHeight = 19.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.delete), color = DialogRed, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = PurpleDark) }
        }
    )
}

private val DialogRed = Color(0xFFD32F2F)
