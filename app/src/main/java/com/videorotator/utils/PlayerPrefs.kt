package com.videorotator.utils

import android.content.Context
import android.content.SharedPreferences

/** 持久化播放器偏好：默认倍速、自动播放 */
object PlayerPrefs {

    private const val FILE = "player_settings"
    private const val KEY_SPEED = "default_playback_speed"
    private const val KEY_AUTO_PLAY = "auto_play"

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