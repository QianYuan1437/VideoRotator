package com.videorotator.utils

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * 应用内语言。
 * 之前界面文字全部写死为中文，且 MainActivity 是 ComponentActivity，
 * AppCompatDelegate.setApplicationLocales 在 Android 13 以下不生效 —— 切到 English 没有任何变化。
 * 现在文字全部来自 strings.xml（values = 中文，values-en = English），
 * 由 AppCompat 统一切换和持久化（Android 13+ 走系统 LocaleManager）。
 */
object AppLocale {

    /** 当前选择：zh / en / ""（跟随系统） */
    fun currentTag(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return ""
        return when (locales[0]?.language) {
            "zh" -> "zh"
            "en" -> "en"
            else -> ""
        }
    }

    /** 切换语言；AppCompat / 系统会自动重建 Activity */
    fun apply(tag: String) {
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(
                when (tag) {
                    "zh" -> "zh-CN"
                    "en" -> "en"
                    else -> ""
                }
            )
        )
    }

    /**
     * Android 13 以下 AppCompat 只替换 Activity 的语言，Application Context 仍是系统语言。
     * 通知、前台服务等在 Activity 之外取字符串时用这个包一层。
     */
    fun localized(context: Context): Context {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return context
        val config = Configuration(context.resources.configuration)
        config.setLocales(android.os.LocaleList.forLanguageTags(locales.toLanguageTags()))
        return context.createConfigurationContext(config)
    }
}
