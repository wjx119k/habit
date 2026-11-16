package com.example.habit

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * 积分的"规矩"都集中在这里。
 *
 * 为什么要单独一个文件？
 *   积分现在支持一位小数，又要有上下限，如果不统一，很容易出现
 *   "某处显示 8.0、某处显示 8、某处又是 8.000000001"的混乱。
 */
object Points {

    /** 单次加分/减分的上限（防止有人填 1000000000000 把数据玩坏） */
    const val MAX_PER_ACTION = 999.9

    /** 单次加分/减分的下限 */
    const val MIN_PER_ACTION = 0.1

    /** 商店奖励价格的上下限 */
    const val MAX_COST = 9999.9
    const val MIN_COST = 0.1

    /** 一位小数：先乘 10 再四舍五入，避免 0.1+0.2=0.30000000000000004 这种鬼东西 */
    fun round(value: Double): Double = (value * 10.0).roundToLong() / 10.0

    /** 显示用：整数不显示小数点，小数保留一位（8 → "8"，8.5 → "8.5"） */
    fun format(value: Double): String {
        val v = round(value)
        if (abs(v) >= 1e15) return String.format(Locale.US, "%.1f", v)
        return if (v == floor(v)) v.toLong().toString() else String.format(Locale.US, "%.1f", v)
    }

    /** 显示用：带正负号（加分 +3、减分 −2） */
    fun formatSigned(value: Double): String {
        val v = round(value)
        return if (v > 0) "+" + format(v) else "−" + format(abs(v))
    }

    /**
     * 解析用户输入。
     * @return 合法则返回四舍五入到一位小数的值；不合法返回 null
     */
    fun parseInput(raw: String?): Double? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        // 最多一位小数，不接受负号、空格、科学计数法
        if (!Regex("^\\d{1,6}(\\.\\d)?$").matches(text)) return null
        val value = text.toDoubleOrNull() ?: return null
        return round(value)
    }

    /** 单次加分/减分是否在允许范围内 */
    fun isValidPerAction(value: Double): Boolean =
        value >= MIN_PER_ACTION && value <= MAX_PER_ACTION

    /** 商店价格是否在允许范围内 */
    fun isValidCost(value: Double): Boolean =
        value >= MIN_COST && value <= MAX_COST
}
