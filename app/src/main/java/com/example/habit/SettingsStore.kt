package com.example.habit

import android.content.Context

/**
 * 保存"设置"里的三项选择。
 *
 * 用 object 单例，任何地方直接 SettingsStore.xxx 就能用。
 */
object SettingsStore {

    private const val PREFS = "habit_settings"
    private const val KEY_LANGUAGE = "language_tag"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_ICON_HIDDEN = "icon_hidden"

    /** 语言标签，例如 zh-CN、en-US；空字符串表示跟随系统 */
    fun getLanguage(context: Context): String =
        prefs(context).getString(KEY_LANGUAGE, "") ?: ""

    fun setLanguage(context: Context, tag: String) {
        prefs(context).edit().putString(KEY_LANGUAGE, tag).apply()
    }

    /** 0 = 跟随系统，1 = 浅色，2 = 深色 */
    fun getTheme(context: Context): Int = prefs(context).getInt(KEY_THEME, 0)

    fun setTheme(context: Context, mode: Int) {
        prefs(context).edit().putInt(KEY_THEME, mode).apply()
    }

    fun isIconHidden(context: Context): Boolean = prefs(context).getBoolean(KEY_ICON_HIDDEN, false)

    fun setIconHidden(context: Context, hidden: Boolean) {
        prefs(context).edit().putBoolean(KEY_ICON_HIDDEN, hidden).apply()
    }

    // ---------------- 每日提醒 ----------------

    private const val KEY_REMINDER_ON = "reminder_on"
    private const val KEY_REMINDER_HOUR = "reminder_hour"
    private const val KEY_REMINDER_MINUTE = "reminder_minute"

    fun isReminderEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_REMINDER_ON, false)

    fun setReminderEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_REMINDER_ON, enabled).apply()
    }

    /** 默认晚上 21:00 —— 一天快结束时提醒一下，比早上更有用 */
    fun getReminderHour(context: Context): Int = prefs(context).getInt(KEY_REMINDER_HOUR, 21)

    fun getReminderMinute(context: Context): Int = prefs(context).getInt(KEY_REMINDER_MINUTE, 0)

    fun setReminderTime(context: Context, hour: Int, minute: Int) {
        prefs(context).edit()
            .putInt(KEY_REMINDER_HOUR, hour)
            .putInt(KEY_REMINDER_MINUTE, minute)
            .apply()
    }

    // ---------------- 首次启动的用户协议 ----------------

    private const val KEY_AGREED = "agreed_terms_version"

    /** 当前协议版本号。改了协议内容就把这个数字 +1，用户会被再问一次 */
    const val TERMS_VERSION = 1

    fun hasAgreedTerms(context: Context): Boolean =
        prefs(context).getInt(KEY_AGREED, 0) >= TERMS_VERSION

    fun setAgreedTerms(context: Context) {
        prefs(context).edit().putInt(KEY_AGREED, TERMS_VERSION).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
