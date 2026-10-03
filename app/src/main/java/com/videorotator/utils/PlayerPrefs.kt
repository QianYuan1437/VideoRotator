package com.videorotator.utils

import android.content.Context
import android.content.SharedPreferences

/** 持久化播放器偏好：默认倍速、自动播放、应用语言 */
object PlayerPrefs {

    private const val FILE = "player_settings"
    private const val KEY_SPEED = "default_playback_speed"
    private const val KEY_AUTO_PLAY = "auto_play"
    private const val KEY_LANGUAGE = "app_language"

    /** 应用语言标签：zh / en / 空串（跟随系统） */
    fun getLanguage(context: Context): String =
        prefs(context).getString(KEY_LANGUAGE, "") ?: ""

    fun setLanguage(context: Context, tag: String) {
        prefs(context).edit().putString(KEY_LANGUAGE, tag).apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getPlaybackSpeed(context: Context): Float =
        prefs(context).getFloat(KEY_SPEED, 1.0f)

    fun setPlaybackSpeed(context: Context, speed: Float) {
        prefs(context).edit().putFloat(KEY_SPEED, speed).apply()
    }

    fun getAutoPlay(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_PLAY, true)

    fun setAutoPlay(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_PLAY, enabled).apply()
    }
}