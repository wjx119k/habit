package com.example.habit

import android.content.Context

/**
 * 冬天彩蛋的"状态机"。
 *
 * 流程：
 *   1. 用户解锁"小试牛刀"成就
 *   2. 在 Project 页连点 App 图标 3 下 → 这里被 arm（进入待触发状态）
 *   3. 去 Me 页点任意菜单 → consumeArmed() 返回 true，播放动画，同时解除待触发
 *
 * 状态存在 SharedPreferences 里，杀进程也不会丢（但一般几秒内就用掉了）。
 */
object WinterEgg {

    private const val PREFS = "habit_winter_egg"
    private const val KEY_ARMED = "armed"
    private const val KEY_SEEN = "seen"
    private const val KEY_FIRST_EGG_SEEN = "first_egg_seen"

    /** 触发条件：小试牛刀成就 */
    private const val ACHIEVEMENT_ID = "novice"

    /** 是否满足前置条件（解锁了"小试牛刀"） */
    fun isAvailable(context: Context): Boolean =
        AchievementStore.isUnlocked(context, ACHIEVEMENT_ID)

    /**
     * 两个前置条件必须同时满足，缺一不可：
     *   ① 已经进入过第一个彩蛋（那句"你瞅啥呢"的空白页）
     *   ② 已经解锁"小试牛刀"
     *
     * 少任何一个，连点图标都不会有反应。
     */
    fun isEligible(context: Context): Boolean =
        hasSeenFirstEgg(context) && isAvailable(context)

    /** 第一个彩蛋被打开过时调用 */
    fun markFirstEggSeen(context: Context) {
        prefs(context).edit().putBoolean(KEY_FIRST_EGG_SEEN, true).apply()
    }

    fun hasSeenFirstEgg(context: Context): Boolean =
        prefs(context).getBoolean(KEY_FIRST_EGG_SEEN, false)

    /** 图标点够 3 下后调用：进入待触发状态 */
    fun arm(context: Context) {
        prefs(context).edit().putBoolean(KEY_ARMED, true).apply()
    }

    fun isArmed(context: Context): Boolean = prefs(context).getBoolean(KEY_ARMED, false)

    /**
     * 取出待触发状态并复位。
     * @return true 表示这就要播动画了
     */
    fun consumeArmed(context: Context): Boolean {
        if (!isArmed(context)) return false
        prefs(context).edit()
            .putBoolean(KEY_ARMED, false)
            .putBoolean(KEY_SEEN, true)
            .apply()
        return true
    }

    /** 是否已经看过这场雪 */
    fun hasSeen(context: Context): Boolean = prefs(context).getBoolean(KEY_SEEN, false)

    fun reset(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
