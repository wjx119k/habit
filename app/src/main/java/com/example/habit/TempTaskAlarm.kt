package com.example.habit

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * 临时任务的提醒闹钟。
 *
 * 为什么用 setAlarmClock 而不是 setExact？
 *   setAlarmClock 是系统里"最高优先级"的闹钟：会精确触发、会在状态栏显示闹钟图标，
 *   系统省电策略也不会把它往后拖——用户设了提醒就应该准时响。
 *
 * 注意：它需要在清单里声明 SCHEDULE_EXACT_ALARM / USE_EXACT_ALARM（本项目已声明）。
 */
object TempTaskAlarm {

    private const val CHANNEL_ID = "habit_task_reminder"
    private const val CHANNEL_NAME = "任务提醒"

    /** 安排一个提醒；triggerAt 在过去则忽略 */
    fun schedule(context: Context, taskId: String, title: String, triggerAt: Long) {
        if (triggerAt <= System.currentTimeMillis()) return

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context, taskId, title, triggerAt)

        runCatching {
            am.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerAt, pi),
                pi
            )
        }.onFailure {
            // 万一没有精确闹钟权限，退化成不精确闹钟，至少别丢提醒
            runCatching { am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi) }
        }
    }

    /** 取消某个任务的提醒 */
    fun cancel(context: Context, taskId: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context, taskId, "", 0L)
        am.cancel(pi)
    }

    private fun pendingIntent(context: Context, taskId: String, title: String, triggerAt: Long): PendingIntent {
        val intent = Intent(context, TempTaskAlarmReceiver::class.java).apply {
            action = "com.example.habit.TASK_REMIND"
            putExtra("taskId", taskId)
            putExtra("title", title)
            // requestCode 用 id 的哈希，保证每个任务一个独立闹钟
            data = android.net.Uri.parse("habit://task/" + taskId)
        }
        return PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 到点时发通知 */
    fun notifyNow(context: Context, taskId: String, title: String) {
        ensureChannel(context)

        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPi = PendingIntent.getActivity(
            context,
            taskId.hashCode(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.task_remind_title))
            .setContentText(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(title))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(contentPi)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(taskId.hashCode(), notification)
        }
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "临时任务到点提醒"
            enableVibration(true)
            enableLights(true)
        }
        nm.createNotificationChannel(channel)
    }
}

/** 闹钟到点后由系统调用 */
class TempTaskAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title").orEmpty()
        val taskId = intent.getStringExtra("taskId").orEmpty()
        TempTaskAlarm.notifyNow(context, taskId, title)
    }
}
