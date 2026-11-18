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
 * 启动页标语的动画。
 *
 * 现在的播放顺序（一轮约 11 秒）：
 *   1. 英文 "A good habit builds everything" —— 手写笔迹，从左往右写出来
 *   2. 倒放：英文从右往左擦掉（像把写字过程倒着播一遍）
 *   3. 中文「好习惯成就大未来」—— 打字机，一个字一个字冒出
 *   4. 日语「良い習慣が未来をつくる」—— 打字机
 *   5. 俄语「Хорошая привычка строит будущее」—— 打字机
 *
 * 为什么自己画而不用 TextView + 动画？
 *   因为要「写到一半停住」这种效果，View 动画做不到——
 *   必须自己控制「画到第几个字、画多宽」。
 */
class SplashTextAnimationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** 整段动画播完时回调 */
    var onFinished: (() -> Unit)? = null

    private val english: String = context.getString(R.string.splash_tagline)
    private val chinese: String = context.getString(R.string.splash_tagline_cn)
    private val japanese: String = context.getString(R.string.splash_tagline_ja)
    private val russian: String = context.getString(R.string.splash_tagline_ru)

    private val englishPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(17f)
        color = 0xFF9AA4B2.toInt()
        typeface = Typeface.create("cursive", Typeface.NORMAL)
    }

    private val cnPaint = typePaint(18f)
    private val jaPaint = typePaint(18f)
    private val ruPaint = typePaint(17f)

    private fun typePaint(size: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(size)
        color = 0xFF9AA4B2.toInt()
        typeface = Typeface.create("serif", Typeface.BOLD)
        letterSpacing = 0.04f
    }

    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF7C8798.toInt()
        strokeWidth = 2f
    }

    private var startAt = 0L
    private var started = false
    private var finished = false

    // 每段时长（毫秒）
    private val writeMs = 2200L      // 手写英文
    private val eraseMs = 1100L      // 倒放擦除
    private val typeMs = 1500L       // 打字机（每种语言）
    private val holdMs = 900L        // 停留

    private val tWrite = writeMs
    private val tErase = tWrite + eraseMs
    private val tCn = tErase + typeMs
    private val tCnHold = tCn + holdMs
    private val tJa = tCnHold + typeMs
    private val tJaHold = tJa + holdMs
    private val tRu = tJaHold + typeMs
    private val tEnd = tRu + holdMs

    /** 视图尺寸确定后，把字号缩到「正好放得下」 */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val maxWidth = w * 0.92f
        if (maxWidth <= 0f) return
        fitTextSize(englishPaint, english, maxWidth)
        fitTextSize(cnPaint, chinese, maxWidth)
        fitTextSize(jaPaint, japanese, maxWidth)
        fitTextSize(ruPaint, russian, maxWidth)
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
        val height = (englishPaint.textSize * 2.4f).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        if (!started) return

        val elapsed = SystemClock.elapsedRealtime() - startAt
        if (elapsed >= tEnd) {
            if (!finished) {
                finished = true
                onFinished?.invoke()
            }
            return
        }

        val cx = width / 2f

        when {
            // ① 手写英文：从左往右逐渐露出
            elapsed < tWrite -> drawWriting(canvas, cx, elapsed.toFloat() / writeMs)

            // ② 倒放：从右往左逐渐消失
            elapsed < tErase ->
                drawWriting(canvas, cx, 1f - (elapsed - tWrite).toFloat() / eraseMs)

            // ③ 中文打字机
            elapsed < tCn -> drawTyping(canvas, cnPaint, chinese, cx, (elapsed - tErase).toFloat() / typeMs)

            // ④ 中文停留
            elapsed < tCnHold -> drawTyping(canvas, cnPaint, chinese, cx, 1f)

            // ⑤ 日语打字机
            elapsed < tJa -> drawTyping(canvas, jaPaint, japanese, cx, (elapsed - tCnHold).toFloat() / typeMs)

            // ⑥ 日语停留
            elapsed < tJaHold -> drawTyping(canvas, jaPaint, japanese, cx, 1f)

            // ⑦ 俄语打字机
            elapsed < tRu -> drawTyping(canvas, ruPaint, russian, cx, (elapsed - tJaHold).toFloat() / typeMs)

            // ⑧ 俄语停留
            else -> drawTyping(canvas, ruPaint, russian, cx, 1f)
        }

        invalidate()
    }

    /**
     * 英文：一个字一个字「写」出来，像笔尖从左往右走。
     *
     * 为什么用「截取子串」而不是裁剪画布？
     *   裁剪是相对坐标，遇到居中/内边距容易错位；
     *   直接画前 N 个字符，起点固定，视觉上就是手写过程。
     */
    private fun drawWriting(canvas: Canvas, cx: Float, progress: Float) {
        if (progress <= 0f) return

        val count = (english.length * progress).toInt().coerceIn(0, english.length)
        if (count == 0) return
        val shown = english.substring(0, count)

        val fullWidth = englishPaint.measureText(english)
        val left = cx - fullWidth / 2f
        val textY = height / 2f - (englishPaint.descent() + englishPaint.ascent()) / 2f

        // 轻微的手写抖动（幅度很小，不然像在发抖）
        val jitter = sin(progress * 12f) * 0.8f
        canvas.drawText(shown, left, textY + jitter, englishPaint)

        // 正在写的时候，末尾有一小段「笔尖」
        if (progress < 1f) {
            val tipX = left + englishPaint.measureText(shown)
            canvas.drawLine(
                tipX, textY + englishPaint.ascent() * 0.75f,
                tipX, textY + englishPaint.descent() * 0.75f, cursorPaint
            )
        }
    }

    /** 打字机：一个字一个字打出来 */
    private fun drawTyping(canvas: Canvas, paint: Paint, text: String, cx: Float, progress: Float) {
        val count = (text.length * progress).toInt().coerceIn(0, text.length)
        if (count == 0) return
        val shown = text.substring(0, count)

        val textY = height / 2f - (paint.descent() + paint.ascent()) / 2f
        // 用全宽来计算居中位置，避免「每打一个字就跳一下」
        val left = cx - paint.measureText(text) / 2f
        canvas.drawText(shown, left, textY, paint)

        // 光标：正在打字时显示一条竖线
        if (progress < 1f) {
            val cursorX = left + paint.measureText(shown) + 2f
            canvas.drawLine(
                cursorX, textY + paint.ascent(),
                cursorX, textY + paint.descent(), cursorPaint
            )
        }
    }

    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)
}
