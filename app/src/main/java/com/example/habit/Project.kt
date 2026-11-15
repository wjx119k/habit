package com.example.habit

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 一个"习惯项目"的数据模型。
 *
 * 字段含义：
 *   id            唯一编号
 *   name          习惯名称
 *   belief        信念
 *   reason        契机
 *   rewardPoints  每次"加分"加多少
 *   penaltyPoints 每次"减分"扣多少
 *   startDate     起始日期
 *   points        累计积分
 *   streak        当前"连续加分"次数（减分就清零）
 *   bestStreak    历史最长连续次数（成就解锁用，不会清零）
 */
data class Project(
    val name: String,
    val id: String = UUID.randomUUID().toString(),
    val belief: String = "",
    val reason: String = "",
    val rewardPoints: Int = 0,
    val penaltyPoints: Int = 0,
    val startDate: String = "",
    val points: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0
) {

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("belief", belief)
        put("reason", reason)
        put("reward", rewardPoints)
        put("penalty", penaltyPoints)
        put("startDate", startDate)
        put("points", points)
        put("streak", streak)
        put("bestStreak", bestStreak)
    }

    companion object {

        fun fromJson(json: JSONObject): Project = Project(
            name = json.optString("name"),
            id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
            belief = json.optString("belief"),
            reason = json.optString("reason"),
            rewardPoints = json.optInt("reward"),
            penaltyPoints = json.optInt("penalty"),
            startDate = json.optString("startDate"),
            points = json.optInt("points"),
            streak = json.optInt("streak"),
            bestStreak = json.optInt("bestStreak")
        )

        fun listToJson(list: List<Project>): String {
            val array = JSONArray()
            list.forEach { array.put(it.toJson()) }
            return array.toString()
        }

        fun listFromJson(raw: String): MutableList<Project> {
            val result = mutableListOf<Project>()
            if (raw.isBlank()) return result
            runCatching {
                val array = JSONArray(raw)
                for (i in 0 until array.length()) {
                    result.add(fromJson(array.getJSONObject(i)))
                }
            }
            return result
        }
    }
}
