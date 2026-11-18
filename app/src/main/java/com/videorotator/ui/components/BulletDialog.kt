package com.videorotator.ui.components

import com.videorotator.ui.theme.CardSurface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videorotator.R
import com.videorotator.ui.theme.PurpleDark
import com.videorotator.ui.theme.PurplePrimary

/** 对话框中的一条要点；warn 为醒目的警示 */
data class Bullet(val text: String, val warn: Boolean = false)

val DangerRed = Color(0xFFD32F2F)

/** 要点列表：每条一行，警示项用图标 + 红字 */
@Composable
fun BulletList(bullets: List<Bullet>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        bullets.forEach { b ->
            Row(verticalAlignment = Alignment.Top) {
                if (b.warn) {
                    Icon(Icons.Filled.WarningAmber, null, tint = DangerRed, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                } else {
                    Text("•", fontSize = 14.sp, color = PurplePrimary, fontWeight = FontWeight.Bold, modifier = Modifier.width(16.dp))
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    b.text,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = if (b.warn) DangerRed else PurpleDark,
                    fontWeight = if (b.warn) FontWeight.Medium else FontWeight.Normal
                )
            }
        }
    }
}

/**
 * 确认对话框：标题 + 要点列表（不再是一整段话），可附加自定义内容（如复选框）。
 */
@Composable
fun BulletDialog(
    title: String,
    bullets: List<Bullet>,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = false,
    extra: (@Composable () -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold, color = PurpleDark) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BulletList(bullets)
                extra?.invoke()
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = if (danger) DangerRed else PurplePrimary, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = PurpleDark) }
        },
        containerColor = CardSurface,
        shape = RoundedCornerShape(20.dp)
    )
}
