package com.example.habit

import android.content.Context
import android.graphics.Canvas
import android.view.GestureDetector
import android.view.ScaleGestureDetector
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
    private var data: List<Pair<Long, Double>> = emptyList()

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

    // ---------------- 缩放 / 拖动 ----------------

    /** 可见窗口占整段时间的比例：1 = 全部，0.15 = 只看到最近一小段 */
    private var viewSpanFrac = 1f

    /** 可见窗口右边缘在整段里的位置：1 = 最右边（最新） */
    private var viewEndFrac = 1f

    private val minSpanFrac = 0.12f

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val focusX = detector.focusX
            val before = fractionAt(focusX)
            val oldSpan = viewSpanFrac
            viewSpanFrac = (viewSpanFrac / detector.scaleFactor).coerceIn(minSpanFrac, 1f)
            val after = fractionAt(focusX)
            viewEndFrac = (viewEndFrac + (before - after) * oldSpan).coerceIn(viewSpanFrac, 1f)
            clampWindow()
            invalidate()
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: android.view.MotionEvent): Boolean = true

        /** 双击：没缩放就放大到 40%，已缩放则还原（单指也能操作） */
        override fun onDoubleTap(e: android.view.MotionEvent): Boolean {
            if (viewSpanFrac >= 0.999f) {
                viewSpanFrac = 0.4f
                viewEndFrac = 1f
            } else {
                viewSpanFrac = 1f
                viewEndFrac = 1f
            }
            clampWindow()
            invalidate()
            return true
        }

        override fun onScroll(
            e1: android.view.MotionEvent?,
            e2: android.view.MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            val w = width.toFloat().coerceAtLeast(1f)
            viewEndFrac = (viewEndFrac + distanceX / w * viewSpanFrac).coerceIn(viewSpanFrac, 1f)
            clampWindow()
            invalidate()
            return true
        }
    })


    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                isPressed = true
                // 关键：告诉外面的 NestedScrollView「别抢我的手势」
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            android.view.MotionEvent.ACTION_UP,
            android.view.MotionEvent.ACTION_CANCEL -> {
                isPressed = false
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
            }
        }
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        invalidate()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    /** 某个 x 坐标对应整段时间的哪个比例 */
    private fun fractionAt(x: Float): Float {
        val padLeft = 6f * density
        val padRight = 6f * density
        val w = (width - padLeft - padRight).coerceAtLeast(1f)
        return ((x - padLeft) / w).coerceIn(0f, 1f)
    }

    private fun clampWindow() {
        viewSpanFrac = viewSpanFrac.coerceIn(minSpanFrac, 1f)
        viewEndFrac = viewEndFrac.coerceIn(viewSpanFrac, 1f)
    }

    init {
        // 自定义 View 必须显式可点击，否则触摸事件可能被忽略
        isClickable = true
        isFocusable = true
    }

    fun setData(points: List<Pair<Long, Double>>) {
        val sameData = data == points
        data = points
        // 换数据时默认停在"最近的那一端"；同数据刷新则保留用户当前的缩放
        if (!sameData) {
            viewSpanFrac = 1f
            viewEndFrac = 1f
        }
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

        // ---- 可见窗口 ----
        val tMin = data.first().first
        val tMax = data.last().first
        val tSpan = (tMax - tMin).toDouble().coerceAtLeast(1.0)

        val winSpan = tSpan * viewSpanFrac
        val tEnd = tMin + tSpan * viewEndFrac
        val tStart = tEnd - winSpan

        // 窗口内的下标（左右各多取一个，曲线才能连到边缘）
        var from = data.indexOfLast { it.first <= tStart.toLong() }
        if (from < 0) from = 0
        var to = data.indexOfFirst { it.first >= tEnd.toLong() }
        if (to < 0) to = data.size - 1
        from = (from - 1).coerceAtLeast(0)
        to = (to + 1).coerceAtMost(data.size - 1)
        if (to - from < 1) return

        // ---- 纵向范围跟着窗口内的数据走（放大后自动重新标定） ----
        var maxV = 0.0
        var minV = 0.0
        for (i in from..to) {
            val v = data[i].second
            if (v > maxV) maxV = v
            if (v < minV) minV = v
        }
        val span = max((maxV - minV).toFloat(), 1f)
        val top = maxV + span * 0.12f
        val bottom = minV - span * 0.12f
        val range = top - bottom

        fun xOf(t: Long): Float {
            val ratio = ((t - tStart) / winSpan).coerceIn(0.0, 1.0)
            return padLeft + chartW * ratio.toFloat()
        }

        fun yOf(v: Double): Float = padTop + chartH * (1f - ((v - bottom) / range).toFloat())

        // 三条横向网格
        for (i in 0..2) {
            val y = padTop + chartH * i / 2f
            canvas.drawLine(padLeft, y, padLeft + chartW, y, gridPaint)
        }

        // 平滑曲线
        linePath.reset()
        linePath.moveTo(xOf(data[from].first), yOf(data[from].second))
        for (i in (from + 1)..to) {
            val px = xOf(data[i - 1].first)
            val py = yOf(data[i - 1].second)
            val cx = xOf(data[i].first)
            val cy = yOf(data[i].second)
            val midX = (px + cx) / 2f
            val midY = (py + cy) / 2f
            linePath.quadTo(px, py, midX, midY)
            if (i == to) linePath.lineTo(cx, cy)
        }

        // 填充
        fillPath.set(linePath)
        fillPath.lineTo(xOf(data[to].first), padTop + chartH)
        fillPath.lineTo(xOf(data[from].first), padTop + chartH)
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

        // 最新点：只有它落在窗口里才画
        if (data.last().first in tStart.toLong()..tEnd.toLong()) {
            val lastX = xOf(data.last().first)
            val lastY = yOf(data.last().second)
            canvas.drawCircle(lastX, lastY, 4f * density, dotPaint)
            dotPaint.color = colorOnSurface(255)
            canvas.drawCircle(lastX, lastY, 1.6f * density, dotPaint)
            dotPaint.color = colorPrimary(255)
        }

        // 极值标注
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = colorOnSurface(150)
        canvas.drawText(Points.format(maxV), padLeft + 3f * density, padTop + textPaint.textSize, textPaint)
        canvas.drawText(
            Points.format(minV),
            padLeft + 3f * density,
            padTop + chartH - 3f * density,
            textPaint
        )
    }
}
