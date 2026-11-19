package com.example.habit

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.sin
import kotlin.random.Random

/**
 * 极简独白的画布：一句话 + 空气里的尘埃。
 *
 * 三个刻意为之的细节：
 *   1. 文字从中心向两侧"揭开"（clip 一小段一小段变宽），而不是整体淡入——
 *      眼睛会跟着走，像有人在纸上慢慢写出来
 *   2. 字号不缩放，只做极轻的位移（6dp → 0），避免廉价的"弹入"
 *   3. 背景飘着十几粒灰点，透明度只有 4%，静止时看不见，动起来才觉得"有空气"
 */
class MonologueView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF111111.toInt()
        textAlign = Paint.Align.CENTER
    }

    private val dustPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF111111.toInt()
        alpha = 11          // ≈4%，几乎看不见
    }

    private var line = ""
    private var reveal = 0f
    private var alphaValue = 0f
    private var rise = 0f          // 未使用缩放，用位移代替

    private var animator: ValueAnimator? = null

    /** 尘埃：位置、半径、上浮速度 */
    private class Speck(val x: Float, val y: Float, val r: Float, val speed: Float)
    private val specks = mutableListOf<Speck>()
    private val startAt = SystemClock.elapsedRealtime()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (specks.isNotEmpty()) return
        val rnd = Random(7)
        repeat(16) {
            specks.add(
                Speck(
                    x = rnd.nextFloat() * w,
                    y = rnd.nextFloat() * h,
                    r = dp(0.8f + rnd.nextFloat() * 1.6f),
                    speed = dp(0.10f + rnd.nextFloat() * 0.22f)
                )
            )
        }
    }

    /** 显示一句话：从中心揭开 + 轻微上浮 */
    fun show(text: String, serif: Boolean, sizeSp: Float, revealMs: Long = 760) {
        line = text
        textPaint.textSize = sp(sizeSp)
        textPaint.typeface = if (serif) {
            Typeface.create("serif", Typeface.ITALIC)
        } else {
            Typeface.create("sans-serif-light", Typeface.NORMAL)
        }
        textPaint.letterSpacing = if (serif) 0.035f else 0.008f

        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = revealMs
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener {
                val p = it.animatedValue as Float
                reveal = p
                alphaValue = (p * 1.35f).coerceAtMost(1f)
                rise = (1f - p) * dp(6f)
                invalidate()
            }
            start()
        }
    }

    /** 淡出当前句 */
    fun hide(duration: Long = 560) {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(alphaValue, 0f).apply {
            this.duration = duration
            addUpdateListener {
                alphaValue = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        drawDust(canvas)
        if (line.isEmpty() || alphaValue <= 0.01f) return

        val cx = width / 2f
        val cy = height / 2f
        val fullWidth = textPaint.measureText(line)

        // 不复制整段文字，而是把画布裁成"中间一条越来越宽的窗口"
        val half = fullWidth / 2f * reveal + dp(2f)
        canvas.save()
        canvas.clipRect(cx - half, 0f, cx + half, height.toFloat())

        textPaint.alpha = (alphaValue * 255).toInt().coerceIn(0, 255)
        val baseline = cy - (textPaint.descent() + textPaint.ascent()) / 2f + rise
        canvas.drawText(line, cx, baseline, textPaint)
        canvas.restore()
    }

    private fun drawDust(canvas: Canvas) {
        if (specks.isEmpty()) return
        val t = (SystemClock.elapsedRealtime() - startAt) / 1000f
        specks.forEachIndexed { i, s ->
            val y = (s.y - t * s.speed * 60f).mod(height.toFloat())
            val drift = sin(t * 0.35f + i) * dp(3f)
            canvas.drawCircle(s.x + drift, y, s.r, dustPaint)
        }
        // 持续重绘，尘埃才会飘
        if (isAttachedToWindow) postInvalidateOnAnimation()
    }

    private fun sp(v: Float) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics
    )

    private fun dp(v: Float) = v * resources.displayMetrics.density

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }
}
