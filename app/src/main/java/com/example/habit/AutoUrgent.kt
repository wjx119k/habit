package com.example.habit

import android.content.Context

/**
 * 自动紧急规则。
 *
 * 用户可以在设置里设一个天数 N：
 *   凡是不是「紧急」象限的任务，只要距离截止日期不足 N 天，就自动按"紧急"对待。
 *   N = 0 表示关闭这个规则。
 *
 * 注意：这只影响"是否标红/是否算急事"，不会改写用户自己选的象限——
 * 分类是用户的判断，我们只加一个提醒层。
 */
object AutoUrgent {

    private const val PREFS = "habit_ui"
    private const val KEY_DAYS = "auto_urgent_days"
    const val DEFAULT_DAYS = 3
    const val MAX_DAYS = 30

    /** 当前设置的天数；0 = 关闭 */
    fun days(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_DAYS, DEFAULT_DAYS)

    fun setDays(context: Context, value: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_DAYS, value.coerceIn(0, MAX_DAYS)).apply()
    }

    /** 这条任务此刻是否应该被当作"紧急" */
    fun isUrgent(context: Context, task: TempTask): Boolean {
        if (task.done) return false
        // 本来就属于紧急象限
        if (task.quadrant == "do" || task.quadrant == "delegate") return true
        // 临近截止
        val days = days(context)
        if (days <= 0 || task.dueAt <= 0L) return false
        val left = task.dueAt - System.currentTimeMillis()
        return left <= days.toLong() * 24 * 60 * 60 * 1000
    }
}
