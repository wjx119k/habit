package com.example.habit

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import java.util.Locale

/**
 * 通知设置：每日提醒开关 + 提醒时间 + 测试。
 *
 * 提醒是纯本地的（AlarmManager），不联网、不上传，关掉就彻底不打扰。
 */
class NotificationsActivity : AppCompatActivity() {

    private lateinit var switchReminder: MaterialSwitch
    private lateinit var tvTime: TextView
    private lateinit var rowTime: View

    /** Android 13+ 需要用户授权才能弹通知 */
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                enableReminder(true)
            } else {
                switchReminder.isChecked = false
                Toast.makeText(this, R.string.reminder_permission_denied, Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)

        val root = findViewById<View>(R.id.notifyRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        switchReminder = findViewById(R.id.switchReminder)
        tvTime = findViewById(R.id.tvReminderTime)
        rowTime = findViewById(R.id.rowReminderTime)

        findViewById<ImageButton>(R.id.btnNotifyBack).setOnClickListener {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, R.anim.slide_out_right)
        }

        // 初始状态
        switchReminder.isChecked = SettingsStore.isReminderEnabled(this)
        showTime(SettingsStore.getReminderHour(this), SettingsStore.getReminderMinute(this))
        updateTimeRowEnabled()

        switchReminder.setOnCheckedChangeListener { _, checked ->
            if (checked && !hasNotificationPermission()) {
                // 先要权限，授权回调里再真正打开
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                enableReminder(checked)
            }
        }

        rowTime.setOnClickListener { pickTime() }

        findViewById<MaterialButton>(R.id.btnTestNotification).setOnClickListener {
            if (!hasNotificationPermission()) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                Reminder.showNotification(this)
                Toast.makeText(this, R.string.reminder_test_sent, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun enableReminder(enabled: Boolean) {
        SettingsStore.setReminderEnabled(this, enabled)
        if (enabled) {
            Reminder.schedule(this)
            Toast.makeText(this, R.string.reminder_enabled, Toast.LENGTH_SHORT).show()
        } else {
            Reminder.cancel(this)
        }
        updateTimeRowEnabled()
    }

    private fun updateTimeRowEnabled() {
        val enabled = SettingsStore.isReminderEnabled(this)
        rowTime.alpha = if (enabled) 1f else 0.5f
    }

    private fun pickTime() {
        if (!SettingsStore.isReminderEnabled(this)) {
            Toast.makeText(this, R.string.reminder_enable_first, Toast.LENGTH_SHORT).show()
            return
        }
        val dialog = TimePickerDialog(
            this,
            { _, hour, minute ->
                SettingsStore.setReminderTime(this, hour, minute)
                showTime(hour, minute)
                // 时间变了要重排闹钟
                Reminder.schedule(this)
            },
            SettingsStore.getReminderHour(this),
            SettingsStore.getReminderMinute(this),
            true
        )
        dialog.show()
    }

    private fun showTime(hour: Int, minute: Int) {
        tvTime.text = String.format(Locale.US, "%02d:%02d", hour, minute)
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
}
