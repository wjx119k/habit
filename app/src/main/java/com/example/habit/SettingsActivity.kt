package com.example.habit

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * 设置主页：几个模块入口。
 * 目前只有第一个"应用"是真正可用的，其余先弹提示。
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val root = findViewById<View>(R.id.settingsRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        findViewById<ImageButton>(R.id.btnSettingsBack).setOnClickListener {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, R.anim.slide_out_right)
        }

        bindRow(R.id.rowApp, R.drawable.ic_settings, R.string.settings_app, R.string.settings_app_desc) {
            startActivity(Intent(this, AppSettingsActivity::class.java))
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_in_right, android.R.anim.fade_out)
        }
        bindRow(R.id.rowAutoUrgent, R.drawable.ic_clock, R.string.auto_urgent_title, R.string.auto_urgent_subtitle) {
            showAutoUrgentDialog()
        }
        bindRow(R.id.rowNotifications, R.drawable.ic_notification, R.string.settings_notifications, R.string.settings_notifications_desc) {
            // 直接跳到系统的本应用通知设置：横幅、铃声、免打扰例外都在那里开
            if (!openSystemNotificationSettings()) {
                startActivity(Intent(this, NotificationsActivity::class.java))
            }
        }
        bindRow(R.id.rowBackup, R.drawable.ic_share, R.string.settings_backup, R.string.settings_backup_desc) {
            startActivity(Intent(this, BackupActivity::class.java))
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_in_right, android.R.anim.fade_out)
        }
        bindRow(R.id.rowPrivacy, R.drawable.ic_lock, R.string.settings_privacy, R.string.settings_privacy_desc) {
            startActivity(Intent(this, PrivacyActivity::class.java))
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_in_right, android.R.anim.fade_out)
        }
        // "关于"已从设置里移除：Me 页已有一个独立入口
    }

    /**
     * 打开系统的「本应用通知设置」。
     *
     * 为什么不在 App 里自己做一套？
     *   横幅、铃声、免打扰例外这些开关只有系统页面才能改，
     *   应用内做的开关改不了这些，反而让用户以为开了却没生效。
     */
    private fun openSystemNotificationSettings(): Boolean = runCatching {
        val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
        }
        startActivity(intent)
        true
    }.getOrElse {
        runCatching {
            startActivity(
                Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:" + packageName)
                )
            )
        }.isSuccess
    }
    /** 自动紧急：选天数，0 = 关闭 */
    private fun showAutoUrgentDialog() {
        val options = (0..AutoUrgent.MAX_DAYS).map {
            if (it == 0) getString(R.string.auto_urgent_off)
            else it.toString() + " " + getString(R.string.auto_urgent_unit)
        }.toTypedArray()

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.auto_urgent_title)
            .setSingleChoiceItems(options, AutoUrgent.days(this)) { dialog, which ->
                AutoUrgent.setDays(this, which)
                dialog.dismiss()
                refreshAutoUrgentSubtitle()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    /** 把当前设置写回那一行的小字 */
    private fun refreshAutoUrgentSubtitle() {
        val days = AutoUrgent.days(this)
        findViewById<TextView>(R.id.rowAutoUrgent)
            .findViewById<TextView>(R.id.tvRowSubtitle)
            .text = if (days == 0) getString(R.string.auto_urgent_subtitle)
            else getString(R.string.auto_urgent_desc, days)
    }
    private fun bindRow(rowId: Int, iconRes: Int, titleRes: Int, subtitleRes: Int, onClick: () -> Unit) {
        val row = findViewById<View>(rowId)
        row.findViewById<ImageView>(R.id.ivRowIcon).setImageResource(iconRes)
        row.findViewById<TextView>(R.id.tvRowTitle).setText(titleRes)
        row.findViewById<TextView>(R.id.tvRowSubtitle).setText(subtitleRes)
        row.setOnClickListener { onClick() }
    }
}


