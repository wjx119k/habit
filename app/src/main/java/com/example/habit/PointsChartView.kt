package com.example.habit

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import kotlin.math.max

/**
 * 总积分变化曲线。
 *
 * 输入一串（时间, 总积分），画成一条平滑的折线 + 渐变填充。
 * 全部手绘，不依赖任何图表库。
 */
class PointsChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** (时间戳, 那一刻的总积分) */
    private var data: List<Pair<Long, Int>> = emptyList()

    private val density = resources.displayMetrics.density

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11f * density
        textAlign = Paint.Align.LEFT
    }

    private val linePath = Path()
    private val fillPath = Path()

    fun setData(points: List<Pair<Long, Int>>) {
        data = points
        invalidate()
    }

    private fun colorOnSurface(alpha: Int): Int {
        val tv = TypedValue()
        context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, tv, true)
        return (tv.data and 0x00FFFFFF) or (alpha shl 24)
    }

    private fun colorPrimary(alpha: Int): Int {
        val tv = TypedValue()
        context.theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, tv, true)
        return (tv.data and 0x00FFFFFF) or (alpha shl 24)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        linePaint.color = colorPrimary(255)
        gridPaint.color = colorOnSurface(30)
        dotPaint.color = colorPrimary(255)
        textPaint.color = colorOnSurface(160)

        val padLeft = 6f * density
        val padRight = 6f * density
        val padTop = 14f * density
        val padBottom = 14f * density
        val chartW = width - padLeft - padRight
        val chartH = height - padTop - padBottom
        if (chartW <= 0 || chartH <= 0) return

        // 没有数据时给个提示
        if (data.size < 2) {
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.color = colorOnSurface(120)
            canvas.drawText(
                context.getString(R.string.chart_need_more),
                width / 2f,
                height / 2f + textPaint.textSize / 3f,
                textPaint
            )
            return
        }

        val values = data.map { it.second }
        val maxV = max(values.maxOrNull() ?: 0, 0)
        val minV = minOf(values.minOrNull() ?: 0, 0)
        // 上下留 12% 余量，曲线不会贴边
        val span = max((maxV - minV).toFloat(), 1f)
        val top = maxV + span * 0.12f
        val bottom = minV - span * 0.12f
        val range = top - bottom

        val tMin = data.first().first
        val tMax = data.last().first
        val tSpan = (tMax - tMin).toFloat()

        fun xOf(index: Int, t: Long): Float {
            val ratio = if (tSpan <= 0f) index.toFloat() / (data.size - 1) else (t - tMin) / tSpan
            return padLeft + chartW * ratio
        }

        fun yOf(v: Int): Float = padTop + chartH * (1f - (v - bottom) / range)

        // 三条横向网格
        for (i in 0..2) {
            val y = padTop + chartH * i / 2f
            canvas.drawLine(padLeft, y, padLeft + chartW, y, gridPaint)
        }

        // 走一条平滑曲线（用相邻中点的二次贝塞尔）
        linePath.reset()
        linePath.moveTo(xOf(0, data[0].first), yOf(data[0].second))
        for (i in 1 until data.size) {
            val px = xOf(i - 1, data[i - 1].first)
            val py = yOf(data[i - 1].second)
            val cx = xOf(i, data[i].first)
            val cy = yOf(data[i].second)
            val midX = (px + cx) / 2f
            val midY = (py + cy) / 2f
            linePath.quadTo(px, py, midX, midY)
            if (i == data.size - 1) linePath.lineTo(cx, cy)
        }

        // 填充：把曲线下面涂成渐变
        fillPath.set(linePath)
        fillPath.lineTo(xOf(data.size - 1, data.last().first), padTop + chartH)
        fillPath.lineTo(xOf(0, data.first().first), padTop + chartH)
        fillPath.close()

        fillPaint.shader = LinearGradient(
            0f, padTop, 0f, padTop + chartH,
            intArrayOf(colorPrimary(90), colorPrimary(0)),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(fillPath, fillPaint)
        fillPaint.shader = null

        canvas.drawPath(linePath, linePaint)

        // 最后一个点：加个圆点，表示"现在的积分"
        val lastX = xOf(data.size - 1, data.last().first)
        val lastY = yOf(data.last().second)
        canvas.drawCircle(lastX, lastY, 4f * density, dotPaint)
        dotPaint.color = colorOnSurface(255)
        canvas.drawCircle(lastX, lastY, 1.6f * density, dotPaint)

        // 极值标注
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = colorOnSurface(150)
        canvas.drawText(maxV.toString(), padLeft + 3f * density, padTop + textPaint.textSize, textPaint)
        canvas.drawText(
            minV.toString(),
            padLeft + 3f * density,
            padTop + chartH - 3f * density,
            textPaint
        )
    }
}
