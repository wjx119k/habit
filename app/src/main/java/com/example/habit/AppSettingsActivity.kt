package com.example.habit

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * 应用设置：语言、界面模式。
 *
 * 注：「隐藏应用图标」功能已移除（有过把用户锁在外面的风险），
 * 所以这里只剩两项。
 */
class AppSettingsActivity : AppCompatActivity() {

    /** 支持的语言：标签 / 完整名 / 短名（右侧显示用） */
    private val languages = listOf(
        Triple("zh-CN", "简体中文", "简体中文"),
        Triple("zh-TW", "繁體中文", "繁體中文"),
        Triple("en-US", "English (United States)", "English (US)"),
        Triple("en-GB", "English (United Kingdom)", "English (UK)"),
        Triple("ja-JP", "日本語", "日本語"),
        Triple("ru-RU", "Русский", "Русский")
    )

    private lateinit var tvLanguageValue: TextView
    private lateinit var tvThemeValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_settings)

        val root = findViewById<View>(R.id.appSettingsRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        tvLanguageValue = findViewById(R.id.tvLanguageValue)
        tvThemeValue = findViewById(R.id.tvThemeValue)

        findViewById<ImageButton>(R.id.btnAppSettingsBack).setOnClickListener {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, R.anim.slide_out_right)
        }

        findViewById<View>(R.id.cardLanguage).setOnClickListener { showLanguageDialog() }
        findViewById<View>(R.id.cardTheme).setOnClickListener { showThemeDialog() }

        // 插件：暂未开发，点击给出明确说明，不做假按钮
        findViewById<View>(R.id.cardPlugins).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_plugins)
                .setMessage(R.string.settings_plugins_desc)
                .setPositiveButton(R.string.action_confirm, null)
                .show()
        }

        refreshValues()
    }

    private fun refreshValues() {
        val stored = SettingsStore.getLanguage(this)
        val currentTag = if (stored.isNotBlank()) {
            stored
        } else {
            AppCompatDelegate.getApplicationLocales().toLanguageTags().ifBlank {
                java.util.Locale.getDefault().toLanguageTag()
            }
        }
        tvLanguageValue.text =
            languages.firstOrNull { it.first.equals(currentTag, ignoreCase = true) }?.third ?: "简体中文"

        tvThemeValue.setText(
            when (SettingsStore.getTheme(this)) {
                1 -> R.string.theme_light
                2 -> R.string.theme_dark
                else -> R.string.theme_follow_system
            }
        )
    }

    private fun showLanguageDialog() {
        val current = SettingsStore.getLanguage(this)
        val index = languages.indexOfFirst { it.first == current }.coerceAtLeast(0)
        val names = languages.map { it.second }.toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.language_dialog_title)
            .setSingleChoiceItems(names, index) { dialog, which ->
                val tag = languages[which].first
                SettingsStore.setLanguage(this, tag)
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                dialog.dismiss()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showThemeDialog() {
        val modes = listOf(0, 1, 2)
        val labels = arrayOf(
            getString(R.string.theme_follow_system),
            getString(R.string.theme_light),
            getString(R.string.theme_dark)
        )
        val current = SettingsStore.getTheme(this)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.theme_dialog_title)
            .setSingleChoiceItems(labels, current) { dialog, which ->
                val mode = modes[which]
                SettingsStore.setTheme(this, mode)
                AppCompatDelegate.setDefaultNightMode(
                    when (mode) {
                        1 -> AppCompatDelegate.MODE_NIGHT_NO
                        2 -> AppCompatDelegate.MODE_NIGHT_YES
                        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    }
                )
                dialog.dismiss()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }
}

