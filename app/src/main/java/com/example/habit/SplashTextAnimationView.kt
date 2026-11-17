package com.example.habit

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import kotlin.math.sin

/**
 * 启动页的标语动画。
 *
 * 三轮循环，每轮三个动作：
 *   1. 英文句子"从左往右"被写出来（用手写体 + 逐字显现模拟笔迹）
 *   2. 倒放：文字从右往左消失（相当于把写字过程倒着播一遍）
 *   3. 中文"好习惯成就大未来"一个字一个字打出来（打字机风格）
 *
 * 为什么自己画而不直接用 TextView + 动画？
 *   因为要"写到一半停住"这种效果，View 动画做不到——
 *   必须自己控制"画到第几个字、画多宽"。
 */
class SplashTextAnimationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** 整段动画播完时回调 */
    var onFinished: (() -> Unit)? = null

    private val english: String = context.getString(R.string.splash_tagline)
    private val chinese: String = context.getString(R.string.splash_tagline_cn)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(17f)
        color = 0xFF9AA4B2.toInt()
        typeface = Typeface.create("cursive", Typeface.NORMAL)
    }

    private val cnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(19f)
        color = 0xFF9AA4B2.toInt()
        typeface = Typeface.create("serif", Typeface.BOLD)
        letterSpacing = 0.06f
    }

    private var startAt = 0L
    private var started = false
    private var finished = false

    // 每个动作的时长（毫秒）
    private val writeMs = 2200L      // 手写英文
    private val eraseMs = 1200L      // 倒放擦除
    private val typeMs = 1600L       // 打字机中文
    private val holdMs = 1200L       // 中文停留
    private val cycleMs = writeMs + eraseMs + typeMs + holdMs
    private val cycles = 2           // 循环两轮（约 12 秒）

    /**
     * 视图尺寸确定后，把字号缩到"正好放得下"。
     * 不这么做的话，17sp 的英文句子比视图还宽，两边会被裁掉。
     */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val maxWidth = w * 0.92f
        if (maxWidth <= 0f) return
        fitTextSize(paint, english, maxWidth)
        fitTextSize(cnPaint, chinese, maxWidth)
    }

    private fun fitTextSize(target: Paint, text: String, maxWidth: Float) {
        val measured = target.measureText(text)
        if (measured > maxWidth && measured > 0f) {
            target.textSize = target.textSize * (maxWidth / measured) * 0.98f
        }
    }

    fun start() {
        startAt = SystemClock.elapsedRealtime()
        started = true
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (paint.textSize * 2.4f).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        if (!started) return

        val elapsed = SystemClock.elapsedRealtime() - startAt
        val total = cycleMs * cycles

        if (elapsed >= total) {
            if (!finished) {
                finished = true
                onFinished?.invoke()
            }
            return
        }

        val inCycle = elapsed % cycleMs
        val cx = width / 2f

        when {
            // ① 手写：从左往右逐渐露出
            inCycle < writeMs -> {
                val p = inCycle.toFloat() / writeMs
                drawWritingEnglish(canvas, cx, p)
            }

            // ② 倒放：从右往左逐渐消失
            inCycle < writeMs + eraseMs -> {
                val p = (inCycle - writeMs).toFloat() / eraseMs
                drawWritingEnglish(canvas, cx, 1f - p)
            }

            // ③ 打字机：中文逐字出现
            inCycle < writeMs + eraseMs + typeMs -> {
                val p = (inCycle - writeMs - eraseMs).toFloat() / typeMs
                drawTypingChinese(canvas, cx, p)
            }

            // ④ 停留
            else -> {
                drawTypingChinese(canvas, cx, 1f)
            }
        }

        invalidate()
    }

    /**
     * 英文：一个字一个字"写"出来，像笔尖从左往右走。
     *
     * 为什么用"截取子串"而不是"裁剪画布"？
     *   裁剪是相对坐标，遇到居中/内边距容易错位；
     *   直接画前 N 个字符，起点固定，从左边长出来，视觉上就是手写过程。
     */
    private fun drawWritingEnglish(canvas: Canvas, cx: Float, progress: Float) {
        if (progress <= 0f) return

        val count = (english.length * progress).toInt().coerceIn(0, english.length)
        if (count == 0) return
        val shown = english.substring(0, count)

        val fullWidth = paint.measureText(english)
        val left = cx - fullWidth / 2f
        val textY = height / 2f - (paint.descent() + paint.ascent()) / 2f

        // 轻微的手写抖动（幅度很小，不然像在发抖）
        val jitter = sin(progress * 12f) * 0.8f

        canvas.drawText(shown, left, textY + jitter, paint)

        // 正在写的时候，末尾有一小段"笔尖"
        if (progress < 1f) {
            val tipX = left + paint.measureText(shown)
            val tip = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF7C8798.toInt()
                strokeWidth = 2.2f
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawLine(
                tipX, textY + paint.ascent() * 0.75f,
                tipX, textY + paint.descent() * 0.75f, tip
            )
        }
    }

    /** 中文：一个字一个字打出来 */
    private fun drawTypingChinese(canvas: Canvas, cx: Float, progress: Float) {
        val count = (chinese.length * progress).toInt().coerceIn(0, chinese.length)
        if (count == 0) return
        val shown = chinese.substring(0, count)

        val textY = height / 2f - (cnPaint.descent() + cnPaint.ascent()) / 2f
        // 用全宽来计算居中位置，避免"每打一个字就跳一下"
        val fullWidth = cnPaint.measureText(chinese)
        val left = cx - fullWidth / 2f

        cnPaint.alpha = 255
        canvas.drawText(shown, left, textY, cnPaint)

        // 光标：正在打字时显示一条竖线
        if (progress < 1f) {
            val cursorX = left + cnPaint.measureText(shown) + 2f
            val cursorPaint = Paint().apply {
                color = 0xFF7C8798.toInt()
                strokeWidth = 2f
            }
            canvas.drawLine(cursorX, textY + cnPaint.ascent(), cursorX, textY + cnPaint.descent(), cursorPaint)
        }
    }

    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)
}
