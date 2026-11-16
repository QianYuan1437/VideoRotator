package com.videorotator.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object PermissionUtils {

    fun getRequiredPermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
            // Android 9 及以下写入 Movies / DCIM / Download 需要写权限（10+ 走 MediaStore，无需权限）
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.P ->
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    /** 浏览视频只需要读权限；Android 9 及以下的写权限被拒时仍可浏览，转换到公共目录才会失败 */
    fun hasPermissions(context: Context): Boolean {
        return getRequiredPermissions().take(1).all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}
