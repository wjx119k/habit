package com.example.habit

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 一条积分变动记录。
 *
 * 为什么记 total（变动后的总积分）而不只是 delta？
 *   因为历史页要画"总积分随时间变化"的曲线，
 *   只存 +1 / -2 是画不出曲线的——必须知道"那一刻总共还剩多少"。
 */
data class HistoryRecord(
    val time: Long,          // 发生时刻
    val label: String,       // 说明，如"喝水 +1"、"兑换 麦当劳一次"
    val delta: Int,          // 本次变化量（兑换是负数）
    val total: Int,          // 变化之后的总积分
    val projectId: String = "",
    /**
     * 变化之后"这个计划自己"的积分。
     * -1 表示不属于任何计划（兑换、清空积分这类全局操作）。
     * 有这个字段，历史页才能画出单个计划的曲线。
     */
    val projectPoints: Int = -1
)

object HistoryStore {

    private const val PREFS = "habit_history"
    private const val KEY_RECORDS = "records"

    /** 最多保留多少条（防止无限增长） */
    private const val MAX_COUNT = 1000

    /** 读取全部记录，按时间从旧到新 */
    fun load(context: Context): MutableList<HistoryRecord> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_RECORDS, "") ?: ""
        val result = mutableListOf<HistoryRecord>()
        if (raw.isBlank()) return result
        runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                result.add(
                    HistoryRecord(
                        time = o.optLong("time"),
                        label = o.optString("label"),
                        delta = o.optInt("delta"),
                        total = o.optInt("total"),
                        projectId = o.optString("projectId"),
                        projectPoints = o.optInt("projectPoints", -1)
                    )
                )
            }
        }
        result.sortBy { it.time }
        return result
    }

    /** 追加一条记录 */
    fun add(context: Context, record: HistoryRecord) {
        val list = load(context)
        list.add(record)
        // 超出上限就丢掉最老的
        while (list.size > MAX_COUNT) list.removeAt(0)
        save(context, list)
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_RECORDS).apply()
    }

    /** 整体覆盖（导入备份时用） */
    fun saveAll(context: Context, list: List<HistoryRecord>) {
        save(context, list)
    }

    private fun save(context: Context, list: List<HistoryRecord>) {
        val array = JSONArray()
        list.forEach {
            array.put(
                JSONObject().apply {
                    put("time", it.time)
                    put("label", it.label)
                    put("delta", it.delta)
                    put("total", it.total)
                    put("projectId", it.projectId)
                    put("projectPoints", it.projectPoints)
                }
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_RECORDS, array.toString()).apply()
    }
}
