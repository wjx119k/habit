package com.example.habit

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
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
 * 极简独白的画布：一句主文 + 一句耳语 + 空气里的尘埃。
 *
 * 四个刻意为之的细节：
 *   1. 主文从中心向两侧"揭开"（clip 窗口逐渐变宽），不是整体淡入——
 *      眼睛会跟着走，像有人慢慢写出来
 *   2. 主文下面有一行小一号的衬线耳语，跟着主文稍晚 120ms 浮现，
 *      像话说完之后心里又补了一句
 *   3. 只做极轻的位移（6dp → 0），不用缩放，避免廉价的"弹入"
 *   4. 背景飘着十几粒灰点，透明度只有 3%，静止看不见，动起来才觉得"有空气"
 */
class MonologueView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val mainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF14161B.toInt()
        textAlign = Paint.Align.CENTER
    }

    private val whisperPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF2A2E36.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("serif", Typeface.NORMAL)
        textSize = sp(13f)
        letterSpacing = 0.08f
    }

    private val dustPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1B1F26.toInt()
        alpha = 8            // ≈3%
    }

    private var mainText = ""
    private var whisperText = ""
    private var reveal = 0f
    private var alphaValue = 0f
    private var whisperAlpha = 0f
    private var rise = 0f

    /** 文字块在纵向上的位置（0=顶部，1=底部），画像里"留白最多"的区域 */
    var anchorY = 0.66f

    private var animator: ValueAnimator? = null
    private var whisperAnimator: ValueAnimator? = null

    private class Speck(val x: Float, val y: Float, val r: Float, val speed: Float)
    private val specks = mutableListOf<Speck>()
    private val startAt = SystemClock.elapsedRealtime()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (specks.isNotEmpty()) return
        val rnd = Random(7)
        repeat(14) {
            specks.add(
                Speck(
                    x = rnd.nextFloat() * w,
                    y = rnd.nextFloat() * h,
                    r = dp(0.8f + rnd.nextFloat() * 1.5f),
                    speed = dp(0.08f + rnd.nextFloat() * 0.18f)
                )
            )
        }
    }

    /** 显示一句：主文 + 可选的耳语 */
    fun show(text: String, whisper: String, serif: Boolean, sizeSp: Float, revealMs: Long = 760) {
        mainText = text
        whisperText = whisper
        mainPaint.textSize = sp(sizeSp)
        mainPaint.typeface = if (serif) {
            Typeface.create("serif", Typeface.ITALIC)
        } else {
            Typeface.create("sans-serif-light", Typeface.NORMAL)
        }
        mainPaint.letterSpacing = if (serif) 0.035f else 0.01f
        whisperPaint.textSize = sp(if (serif) 13f else 12f)

        animator?.cancel()
        whisperAnimator?.cancel()
        whisperAlpha = 0f

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

        if (whisper.isNotEmpty()) {
            whisperAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                startDelay = 140
                duration = (revealMs * 1.15f).toLong()
                addUpdateListener {
                    whisperAlpha = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }
    }

    /** 淡出整块 */
    fun hide(duration: Long = 560) {
        animator?.cancel()
        whisperAnimator?.cancel()
        animator = ValueAnimator.ofFloat(alphaValue, 0f).apply {
            this.duration = duration
            addUpdateListener {
                alphaValue = it.animatedValue as Float
                whisperAlpha = alphaValue * 0.9f
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        drawDust(canvas)
        if (mainText.isEmpty() || alphaValue <= 0.01f) return

        val cx = width / 2f
        val baseY = height * anchorY - (mainPaint.descent() + mainPaint.ascent()) / 2f + rise
        val fullWidth = mainPaint.measureText(mainText)

        // 中心揭开的窗口
        val half = fullWidth / 2f * reveal + dp(2f)
        canvas.save()
        canvas.clipRect(cx - half, 0f, cx + half, height.toFloat())
        mainPaint.alpha = (alphaValue * 255).toInt().coerceIn(0, 255)
        canvas.drawText(mainText, cx, baseY, mainPaint)
        canvas.restore()

        // 耳语：晚一点出现，轻轻跟在下面
        if (whisperText.isNotEmpty() && whisperAlpha > 0.01f) {
            whisperPaint.alpha = (whisperAlpha * 150).toInt().coerceIn(0, 255)
            canvas.drawText(whisperText, cx, baseY + dp(28f), whisperPaint)
        }
    }

    private fun drawDust(canvas: Canvas) {
        if (specks.isEmpty()) return
        val t = (SystemClock.elapsedRealtime() - startAt) / 1000f
        specks.forEachIndexed { i, s ->
            val y = (s.y - t * s.speed * 60f).mod(height.toFloat())
            val drift = sin(t * 0.35f + i) * dp(3f)
            canvas.drawCircle(s.x + drift, y, s.r, dustPaint)
        }
        if (isAttachedToWindow) postInvalidateOnAnimation()
    }

    private fun sp(v: Float) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics
    )

    private fun dp(v: Float) = v * resources.displayMetrics.density

    override fun onDetachedFromWindow() {
        animator?.cancel()
        whisperAnimator?.cancel()
        super.onDetachedFromWindow()
    }
}
