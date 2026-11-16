package com.example.habit

import android.content.Context

/**
 * 成就的判定口径：
 *   SINGLE_BEST —— 看"某一个项目"的历史最佳连续次数
 *   TOTAL_BEST  —— 看"所有项目"历史最佳连续次数之和
 */
enum class AchievementMetric {
    SINGLE_BEST,
    TOTAL_BEST
}

/**
 * 成就的定义。
 *
 * 前三个看"单个项目"：
 *   小试牛刀  10 次 / 自律专家 100 次 / 自律大师 1000 次
 * 第四个看"全部项目合计"：
 *   自律大神  5000 次
 *
 * 一旦解锁就永久记下来（即使之后减分把 streak 清零、甚至删掉项目，
 * 成就也不会丢）。
 */
data class Achievement(
    val id: String,
    val titleRes: Int,
    val descRes: Int,
    val iconRes: Int,
    val threshold: Int,
    val metric: AchievementMetric = AchievementMetric.SINGLE_BEST
)

object Achievements {

    val ALL = listOf(
        Achievement(
            id = "novice",
            titleRes = R.string.ach_novice_title,
            descRes = R.string.ach_novice_desc,
            iconRes = R.drawable.ic_habit,
            threshold = 10
        ),
        Achievement(
            id = "expert",
            titleRes = R.string.ach_expert_title,
            descRes = R.string.ach_expert_desc,
            iconRes = R.drawable.ic_trophy,
            threshold = 100
        ),
        Achievement(
            id = "master",
            titleRes = R.string.ach_master_title,
            descRes = R.string.ach_master_desc,
            iconRes = R.drawable.ic_crown,
            threshold = 1000
        ),
        Achievement(
            id = "grandmaster",
            titleRes = R.string.ach_grandmaster_title,
            descRes = R.string.ach_grandmaster_desc,
            iconRes = R.drawable.ic_diamond,
            threshold = 5000,
            metric = AchievementMetric.TOTAL_BEST
        )
    )

    /** 这个成就在当前数据下达到了多少 */
    fun progressOf(achievement: Achievement, singleBest: Int, totalBest: Int): Int =
        when (achievement.metric) {
            AchievementMetric.SINGLE_BEST -> singleBest
            AchievementMetric.TOTAL_BEST -> totalBest
        }
}

/** 已解锁成就的本地存储 */
object AchievementStore {

    private const val PREFS = "habit_achievements"
    private const val KEY_UNLOCKED = "unlocked_ids"

    fun unlockedIds(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_UNLOCKED, emptySet())
            ?.toSet()
            ?: emptySet()

    fun isUnlocked(context: Context, id: String): Boolean = id in unlockedIds(context)

    /** 清空所有已解锁记录（重新挑战） */
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_UNLOCKED)
            .apply()
    }

    /** 整体覆盖已解锁列表（导入备份时用） */
    fun setUnlocked(context: Context, ids: Set<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_UNLOCKED, ids)
            .apply()
    }

    /**
     * 根据"单项目最佳"和"全部项目合计最佳"检查是否达成新成就。
     * 返回本次新解锁的成就列表（可能为空）。
     */
    fun checkAndUnlock(context: Context, singleBest: Int, totalBest: Int): List<Achievement> {
        val current = unlockedIds(context).toMutableSet()
        val newly = Achievements.ALL.filter {
            Achievements.progressOf(it, singleBest, totalBest) >= it.threshold && it.id !in current
        }
        if (newly.isNotEmpty()) {
            current.addAll(newly.map { it.id })
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(KEY_UNLOCKED, current)
                .apply()
        }
        return newly
    }
}
