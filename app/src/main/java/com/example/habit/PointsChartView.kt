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

    /** 双指缩放：记录上一帧两指间距 */
    private var lastPinchSpan = 0f

    /** 双指过程中忽略单指拖动 */
    private var isPinching = false

    /** 双指缩放：自己记录两指间距，不依赖 ScaleGestureDetector */

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
            if (isPinching) return false
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
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            android.view.MotionEvent.ACTION_UP,
            android.view.MotionEvent.ACTION_CANCEL -> {
                isPressed = false
                isPinching = false
                lastPinchSpan = 0f
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
            }
        }

        // ---- 双指缩放：自己算间距，不依赖 ScaleGestureDetector ----
        if (event.pointerCount >= 2) {
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_POINTER_DOWN -> {
                    isPinching = true
                    lastPinchSpan = spanOf(event)
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val span = spanOf(event)
                    if (lastPinchSpan > 0f && span > 0f) {
                        val factor = span / lastPinchSpan
                        if (factor > 1.01f || factor < 0.99f) {
                            applyZoom(factor, (event.getX(0) + event.getX(1)) / 2f)
                            lastPinchSpan = span
                        }
                    }
                }
            }
        }

        // 手势检测始终收到事件（双击、单指拖动都靠它）
        gestureDetector.onTouchEvent(event)
        invalidate()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun spanOf(event: android.view.MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val dx = event.getX(1) - event.getX(0)
        val dy = event.getY(1) - event.getY(0)
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    /** 以 focusX 为中心缩放：factor > 1 表示两指张开 => 放大（窗口变窄） */
    private fun applyZoom(factor: Float, focusX: Float) {
        val before = fractionAt(focusX)
        val oldSpan = viewSpanFrac
        viewSpanFrac = (viewSpanFrac / factor).coerceIn(minSpanFrac, 1f)
        val after = fractionAt(focusX)
        viewEndFrac = (viewEndFrac + (before - after) * oldSpan).coerceIn(viewSpanFrac, 1f)
        clampWindow()
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

    /** 横轴时间标签：左边是窗口起点，右边是窗口终点 */
    private fun drawAxisLabels(
        canvas: Canvas, startMs: Long, endMs: Long,
        padLeft: Float, bottomY: Float, chartW: Float
    ) {
        val fmt = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = colorOnSurface(130)
        val y = bottomY + textPaint.textSize + 2f * density
        canvas.drawText(fmt.format(java.util.Date(startMs)), padLeft + 2f * density, y, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(fmt.format(java.util.Date(endMs)), padLeft + chartW - 2f * density, y, textPaint)
        textPaint.textAlign = Paint.Align.LEFT
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
        val padBottom = 26f * density
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

        // ---- 横轴：显示当前窗口左右两端的时间 ----
        drawAxisLabels(canvas, tStart.toLong(), tEnd.toLong(), padLeft, padTop + chartH, chartW)

        // 极值标注
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = colorOnSurface(150)
        canvas.drawText(Points.format(maxV), padLeft + 3f * density, padTop + textPaint.textSize, textPaint)
        canvas.drawText(
            Points.format(minV),
            padLeft + 3f * density,
            padTop + chartH - 6f * density,
            textPaint
        )
    }
}
