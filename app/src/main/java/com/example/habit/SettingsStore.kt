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

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
