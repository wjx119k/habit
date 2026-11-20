package com.example.habit

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 临时任务。
 *
 * 和"习惯计划"的区别：
 *   计划是长期的、每天反复做的；临时任务是「一次性」的，
 *   做完就划掉，并立刻拿到一笔积分。
 *
 * 积分记在 bonus 里（不属于任何计划），总积分 = 计划积分之和 + bonus − 已兑换。
 */
data class TempTask(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val points: Double,
    val done: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    /** 归属象限：空 = 普通临时任务；do/plan/delegate/drop = 来自艾森豪威尔矩阵 */
    val quadrant: String = "",
    /** 用户本来选的象限（自动紧急改写后仍保留，可回退） */
    val baseQuadrant: String = "",
    /** 提醒时间戳；0 = 不提醒 */
    val remindAt: Long = 0L,
    /** 截止日期时间戳（当天 23:59）；0 = 不设截止 */
    val dueAt: Long = 0L
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("points", points)
        put("done", done)
        put("createdAt", createdAt)
        put("quadrant", quadrant)
        put("baseQuadrant", baseQuadrant)
        put("remindAt", remindAt)
    }

    companion object {
        fun fromJson(o: JSONObject) = TempTask(
            id = o.optString("id"),
            title = o.optString("title"),
            points = o.optDouble("points", 0.0),
            done = o.optBoolean("done", false),
            createdAt = o.optLong("createdAt"),
            quadrant = o.optString("quadrant"),
            baseQuadrant = o.optString("baseQuadrant"),
            remindAt = o.optLong("remindAt"),
            dueAt = o.optLong("dueAt")
        )
    }
}

object TempTaskStore {

    private const val PREFS = "habit_temp_tasks"
    private const val KEY_TASKS = "tasks"
    private const val KEY_BONUS = "bonus_points"

    fun load(context: Context): MutableList<TempTask> {
        val raw = prefs(context).getString(KEY_TASKS, "") ?: ""
        val list = mutableListOf<TempTask>()
        if (raw.isBlank()) return list
        runCatching {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) list.add(TempTask.fromJson(arr.getJSONObject(i)))
        }
        return list
    }

    fun save(context: Context, tasks: List<TempTask>) {
        val arr = JSONArray()
        tasks.forEach { arr.put(it.toJson()) }
        prefs(context).edit().putString(KEY_TASKS, arr.toString()).apply()
    }

    /** 临时任务累计赚到的积分（不受计划影响） */
    fun bonus(context: Context): Double =
        Points.round(prefs(context).getFloat(KEY_BONUS, 0f).toDouble())

    fun addBonus(context: Context, value: Double) {
        val next = Points.round(bonus(context) + value)
        prefs(context).edit().putFloat(KEY_BONUS, next.toFloat()).apply()
    }

    /** 清空所有临时任务（积分不动，避免误伤） */
    fun clearTasks(context: Context) {
        prefs(context).edit().remove(KEY_TASKS).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}




