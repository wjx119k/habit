package com.example.habit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 到点被 AlarmManager 叫醒，发一条提醒通知。 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminder.showNotification(context)
    }
}

/** 开机后重新排闹钟（否则重启一次提醒就没了）。 */
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED &&
            SettingsStore.isReminderEnabled(context)
        ) {
            Reminder.schedule(context)
        }
    }
}
