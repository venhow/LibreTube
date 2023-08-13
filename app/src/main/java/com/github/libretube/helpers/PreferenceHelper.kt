package com.github.libretube.helpers

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.github.libretube.constants.PreferenceKeys
import java.time.Instant

object PreferenceHelper {
    /**
     * for normal preferences
     */
    lateinit var settings: SharedPreferences

    /**
     * For sensitive data (like token)
     */
    private lateinit var authSettings: SharedPreferences

    /**
     * set the context that is being used to access the shared preferences
     */
    fun initialize(context: Context) {
        settings = getDefaultSharedPreferences(context)
        authSettings = getAuthenticationPreferences(context)
    }

    fun putString(key: String, value: String) {
        settings.edit(commit = true) { putString(key, value) }
    }

    fun putBoolean(key: String, value: Boolean) {
        settings.edit(commit = true) { putBoolean(key, value) }
    }

    fun putInt(key: String, value: Int) {
        settings.edit(commit = true) { putInt(key, value) }
    }

    fun putLong(key: String, value: Long) {
        settings.edit(commit = true) { putLong(key, value) }
    }

    fun getString(key: String?, defValue: String): String {
        return settings.getString(key, defValue) ?: defValue
    }

    fun getBoolean(key: String?, defValue: Boolean): Boolean {
        return settings.getBoolean(key, defValue)
    }

    fun getInt(key: String?, defValue: Int): Int {
        return runCatching {
            settings.getInt(key, defValue)
        }.getOrElse { settings.getLong(key, defValue.toLong()).toInt() }
    }

    fun getLong(key: String?, defValue: Long): Long {
        return settings.getLong(key, defValue)
    }

    fun getStringSet(key: String?, defValue: Set<String>): Set<String> {
        return settings.getStringSet(key, defValue).orEmpty()
    }

    fun clearPreferences() {
        settings.edit { clear() }
    }

    fun getToken(): String {
        return authSettings.getString(PreferenceKeys.TOKEN, "")!!
    }

    fun setToken(newValue: String) {
        authSettings.edit { putString(PreferenceKeys.TOKEN, newValue) }
    }

    fun getUsername(): String {
        return authSettings.getString(PreferenceKeys.USERNAME, "")!!
    }

    fun setUsername(newValue: String) {
        authSettings.edit { putString(PreferenceKeys.USERNAME, newValue) }
    }

    fun setLastSeenVideoId(videoId: String) {
        putString(PreferenceKeys.LAST_STREAM_VIDEO_ID, videoId)
    }

    fun getLastSeenVideoId(): String {
        return getString(PreferenceKeys.LAST_STREAM_VIDEO_ID, "")
    }

    fun updateLastFeedWatchedTime() {
        putLong(PreferenceKeys.LAST_WATCHED_FEED_TIME, Instant.now().epochSecond)
    }

    fun getLastCheckedFeedTime(): Long {
        return getLong(PreferenceKeys.LAST_WATCHED_FEED_TIME, 0)
    }

    fun saveErrorLog(log: String) {
        putString(PreferenceKeys.ERROR_LOG, log)
    }

    fun getErrorLog(): String {
        return getString(PreferenceKeys.ERROR_LOG, "")
    }

    fun getIgnorableNotificationChannels(): List<String> {
        return getString(PreferenceKeys.IGNORED_NOTIFICATION_CHANNELS, "").split(",")
    }

    fun isChannelNotificationIgnorable(channelId: String): Boolean {
        return getIgnorableNotificationChannels().any { it == channelId }
    }

    fun toggleIgnorableNotificationChannel(channelId: String) {
        val ignorableChannels = getIgnorableNotificationChannels().toMutableList()
        if (ignorableChannels.contains(channelId)) {
            ignorableChannels.remove(channelId)
        } else {
            ignorableChannels.add(channelId)
        }
        settings.edit {
            val channelsString = ignorableChannels.joinToString(",")
            putString(PreferenceKeys.IGNORED_NOTIFICATION_CHANNELS, channelsString)
        }
    }

    private fun getDefaultSharedPreferences(context: Context): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(context)
    }

    private fun getAuthenticationPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PreferenceKeys.AUTH_PREF_FILE, Context.MODE_PRIVATE)
    }
}
