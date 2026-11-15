package com.example.habit

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.google.android.material.color.DynamicColors

/**
 * 整个 App 的"总开关"类。
 *
 * Application 是最早被创建的对象，适合做全局设置：
 *   1. 开启动态取色（Material You）
 *   2. 应用上次保存的"界面模式"（浅色 / 深色 / 跟随系统）
 *   3. 应用上次保存的"语言"
 */
class HabitApp : Application() {

    override fun onCreate() {
        super.onCreate()

        DynamicColors.applyToActivitiesIfAvailable(this)

        // 清理旧版本「隐藏图标」功能遗留的常驻通知（该功能已移除）
        runCatching {
            androidx.core.app.NotificationManagerCompat.from(this).cancel(114514)
        }

        // 界面模式
        AppCompatDelegate.setDefaultNightMode(
            when (SettingsStore.getTheme(this)) {
                1 -> AppCompatDelegate.MODE_NIGHT_NO
                2 -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )

        // 语言（空字符串 = 跟随系统，不做处理）
        val tag = SettingsStore.getLanguage(this)
        if (tag.isNotBlank()) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        }
    }
}
