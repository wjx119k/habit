package com.example.habit

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 数据备份：把 App 里所有东西打包成一个 JSON 文件。
 *
 * 为什么用 JSON 而不是数据库导出？
 *   · 人眼能看懂（万一哪天工具没了，还能手动翻）
 *   · 跨版本兼容容易（缺字段就跳过，多字段就忽略）
 *   · 一份文件就是一次快照，方便分享/存档
 */
object Backup {

    private const val FORMAT_VERSION = 1

    /** 最近一次导出的时间（只是为了在界面上显示） */
    private const val PREFS = "habit_backup"
    private const val KEY_LAST_EXPORT = "last_export_at"

    // ============================================================
    //  导出
    // ============================================================

    /** 把全部数据拼成一个 JSON 字符串 */
    fun toJson(context: Context): String {
        val root = JSONObject()
        root.put("app", "Habit")
        root.put("formatVersion", FORMAT_VERSION)
        root.put("versionName", runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "unknown")
        root.put("exportedAt", System.currentTimeMillis())

        // 计划
        val plans = JSONArray()
        ProjectStore.load(context).forEach { plans.put(it.toJson()) }
        root.put("plans", plans)

        // 商店奖励
        val items = JSONArray()
        StoreStore.items(context).forEach { items.put(it.toJson()) }
        root.put("storeItems", items)

        // 兑换记录
        val redemptions = JSONArray()
        StoreStore.redemptions(context).forEach {
            redemptions.put(
                JSONObject().apply {
                    put("itemId", it.itemId)
                    put("title", it.title)
                    put("cost", it.cost)
                    put("time", it.time)
                }
            )
        }
        root.put("redemptions", redemptions)

        // 历史记录
        val history = JSONArray()
        HistoryStore.load(context).forEach {
            history.put(
                JSONObject().apply {
                    put("time", it.time)
                    put("label", it.label)
                    put("delta", it.delta)
                    put("total", it.total)
                    put("projectId", it.projectId)
                }
            )
        }
        root.put("history", history)

        // 成就
        val achievements = JSONArray()
        AchievementStore.unlockedIds(context).forEach { achievements.put(it) }
        root.put("achievements", achievements)

        return root.toString(2)
    }

    /** 生成一个导出文件，返回它 */
    fun writeToCache(context: Context): File {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date())
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "habit-backup-$stamp.json")
        file.writeText(toJson(context), Charsets.UTF_8)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_LAST_EXPORT, System.currentTimeMillis()).apply()
        return file
    }

    fun lastExportAt(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_EXPORT, 0L)

    // ============================================================
    //  概览（给界面显示"这次导出包含什么"）
    // ============================================================

    data class Summary(
        val plans: Int,
        val items: Int,
        val redemptions: Int,
        val history: Int,
        val achievements: Int
    )

    fun summary(context: Context): Summary = Summary(
        plans = ProjectStore.load(context).size,
        items = StoreStore.items(context).size,
        redemptions = StoreStore.redemptions(context).size,
        history = HistoryStore.load(context).size,
        achievements = AchievementStore.unlockedIds(context).size
    )

    // ============================================================
    //  导入
    // ============================================================

    /**
     * 从 JSON 字符串恢复数据。
     * @return 成功返回 null；失败返回错误说明（直接给用户看）
     */
    fun restore(context: Context, raw: String): String? {
        val root = runCatching { JSONObject(raw) }.getOrNull()
            ?: return context.getString(R.string.backup_bad_file)
        if (root.optString("app") != "Habit") {
            return context.getString(R.string.backup_bad_file)
        }

        // 计划
        root.optJSONArray("plans")?.let { array ->
            val list = mutableListOf<Project>()
            for (i in 0 until array.length()) {
                list.add(Project.fromJson(array.getJSONObject(i)))
            }
            ProjectStore.save(context, list)
        }

        // 商店奖励
        root.optJSONArray("storeItems")?.let { array ->
            val list = mutableListOf<StoreItem>()
            for (i in 0 until array.length()) {
                list.add(StoreItem.fromJson(array.getJSONObject(i)))
            }
            StoreStore.saveItems(context, list)
        }

        // 兑换记录
        root.optJSONArray("redemptions")?.let { array ->
            val list = mutableListOf<Redemption>()
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                list.add(
                    Redemption(
                        itemId = o.optString("itemId"),
                        title = o.optString("title"),
                        cost = o.optDouble("cost", 0.0),
                        time = o.optLong("time")
                    )
                )
            }
            StoreStore.saveRedemptions(context, list)
        }

        // 历史
        root.optJSONArray("history")?.let { array ->
            val list = mutableListOf<HistoryRecord>()
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                list.add(
                    HistoryRecord(
                        time = o.optLong("time"),
                        label = o.optString("label"),
                        delta = o.optDouble("delta", 0.0),
                        total = o.optDouble("total", 0.0),
                        projectId = o.optString("projectId")
                    )
                )
            }
            HistoryStore.saveAll(context, list)
        }

        // 成就
        root.optJSONArray("achievements")?.let { array ->
            val ids = mutableSetOf<String>()
            for (i in 0 until array.length()) ids.add(array.getString(i))
            AchievementStore.setUnlocked(context, ids)
        }

        return null
    }
}
