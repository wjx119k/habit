package com.example.habit

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Store（积分商城）的数据层。
 *
 * 和以前最大的不同：商品不再是写死的，而是**用户自己创建的**。
 *
 * 核心规则：
 *   可用积分 = 所有项目累计的积分 − 已经兑换掉的积分
 *   "已兑换"由兑换记录求和得来，不单独存数字，账目永远对得上。
 */

/** 用户自己创建的奖励 */
data class StoreItem(
    val title: String,
    val cost: Int,
    val desc: String = "",
    val id: String = UUID.randomUUID().toString()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("desc", desc)
        put("cost", cost)
    }

    companion object {
        fun fromJson(o: JSONObject) = StoreItem(
            title = o.optString("title"),
            cost = o.optInt("cost"),
            desc = o.optString("desc"),
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() }
        )
    }
}

/** 一条兑换记录（存的是当时的标题和价格，商品删了记录也还在） */
data class Redemption(
    val itemId: String,
    val title: String,
    val cost: Int,
    val time: Long
)

object StoreStore {

    private const val PREFS = "habit_store_mall"
    private const val KEY_ITEMS = "items"
    private const val KEY_REDEMPTIONS = "redemptions"

    // ---------------- 奖励清单 ----------------

    fun items(context: Context): MutableList<StoreItem> {
        val raw = prefs(context).getString(KEY_ITEMS, "") ?: ""
        val result = mutableListOf<StoreItem>()
        if (raw.isBlank()) return result
        runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                result.add(StoreItem.fromJson(array.getJSONObject(i)))
            }
        }
        return result
    }

    fun saveItems(context: Context, list: List<StoreItem>) {
        val array = JSONArray()
        list.forEach { array.put(it.toJson()) }
        prefs(context).edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    fun addItem(context: Context, item: StoreItem) {
        val list = items(context)
        list.add(item)
        saveItems(context, list)
    }

    fun updateItem(context: Context, item: StoreItem) {
        val list = items(context)
        val index = list.indexOfFirst { it.id == item.id }
        if (index >= 0) {
            list[index] = item
            saveItems(context, list)
        }
    }

    fun deleteItem(context: Context, id: String) {
        val list = items(context)
        list.removeAll { it.id == id }
        saveItems(context, list)
    }

    // ---------------- 兑换记录 ----------------

    fun redemptions(context: Context): MutableList<Redemption> {
        val raw = prefs(context).getString(KEY_REDEMPTIONS, "") ?: ""
        val result = mutableListOf<Redemption>()
        if (raw.isBlank()) return result
        runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                result.add(
                    Redemption(
                        itemId = o.optString("itemId"),
                        title = o.optString("title"),
                        cost = o.optInt("cost"),
                        time = o.optLong("time")
                    )
                )
            }
        }
        return result
    }

    /** 已经兑换掉的积分总额 */
    fun spentPoints(context: Context): Int = redemptions(context).sumOf { it.cost }

    /** 兑换：积分不足返回 false */
    fun redeem(context: Context, item: StoreItem, available: Int): Boolean {
        if (available < item.cost) return false

        val list = redemptions(context)
        list.add(0, Redemption(item.id, item.title, item.cost, System.currentTimeMillis()))

        val array = JSONArray()
        list.forEach {
            array.put(
                JSONObject().apply {
                    put("itemId", it.itemId)
                    put("title", it.title)
                    put("cost", it.cost)
                    put("time", it.time)
                }
            )
        }
        prefs(context).edit().putString(KEY_REDEMPTIONS, array.toString()).apply()
        return true
    }

    /** 整体覆盖兑换记录（导入备份、撤销兑换时用） */
    fun saveRedemptions(context: Context, list: List<Redemption>) {
        val array = JSONArray()
        list.forEach {
            array.put(
                JSONObject().apply {
                    put("itemId", it.itemId)
                    put("title", it.title)
                    put("cost", it.cost)
                    put("time", it.time)
                }
            )
        }
        prefs(context).edit().putString(KEY_REDEMPTIONS, array.toString()).apply()
    }

    /**
     * 撤销一条兑换：把记录删掉，积分自然就退回来了（可用积分 = 累计 − 已兑换）。
     * @return 是否真的删掉了
     */
    fun revoke(context: Context, target: Redemption): Boolean {
        val list = redemptions(context)
        val index = list.indexOfFirst {
            it.time == target.time && it.title == target.title && it.cost == target.cost
        }
        if (index < 0) return false
        list.removeAt(index)
        saveRedemptions(context, list)
        return true
    }

    /** 清空兑换记录（"清空积分"时一起重置，避免可用积分变成负数） */
    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_REDEMPTIONS).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
