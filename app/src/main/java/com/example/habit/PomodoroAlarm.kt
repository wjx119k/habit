package com.example.habit

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * 番茄钟到点提醒。
 *
 * 为什么用 AlarmManager 而不是只靠 CountDownTimer？
 *   用户切到别的应用、甚至锁屏后，Activity 里的计时器会被系统冻结。
 *   排一个系统闹钟，到点由广播叫醒我们，才能真正"像手机闹钟一样"响。
 *
 * 通知渠道用 IMPORTANCE_HIGH + 闹钟铃声 + 震动，所以：
 *   响铃、锁屏上能看到、点一下直接回到番茄钟界面。
 */
object PomodoroAlarm {

    const val CHANNEL_ID = "habit_pomodoro_alarm"
    private const val REQUEST_CODE = 3001
    private const val NOTIFICATION_ID = 3001

    fun schedule(context: Context, triggerAtMillis: Long, isFocus: Boolean) {
        ensureChannel(context)
        val intent = Intent(context, PomodoroAlarmReceiver::class.java).apply {
            putExtra("focus", isFocus)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        // setAlarmClock：系统最高优先级，Doze 也会准时叫醒（相当于用户设的闹钟）
        manager.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerAtMillis, pending),
            pending
        )
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, PomodoroAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.cancel(pending)
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.pomodoro_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.pomodoro_channel_desc)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 400, 200, 400)
            setSound(
                sound,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            enableLights(true)
        }
        manager.createNotificationChannel(channel)
    }

    /** 发通知（直接打开番茄钟页面） */
    fun showNotification(context: Context, isFocus: Boolean) {
        ensureChannel(context)

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, PomodoroActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_clock)
            .setContentTitle(
                context.getString(
                    if (isFocus) R.string.pomodoro_focus_done else R.string.pomodoro_break_done
                )
            )
            .setContentText(context.getString(R.string.pomodoro_notification_body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setFullScreenIntent(contentIntent, true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }
}

/** 到点被系统叫醒 */
class PomodoroAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PomodoroAlarm.showNotification(context, intent.getBooleanExtra("focus", true))
    }
}
