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

    /** 文字背后的一团柔光：插画再亮，字也能浮起来 */
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }

    /** 逐词浮现时每个词的透明度 */
    private val wordAlphas = FloatArray(16)

    /** 指尖处的柔光：位置与透明度 */
    private var touchX = -1f
    private var touchY = -1f
    private var touchGlowAlpha = 0f

    /** 第一个词的闪烁亮度（0~1）：先像灯管一样闪几下，再定住 */
    private var firstWordPulse = 1f
    private var flickerActive = false

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
    fun show(text: String, whisper: String, serif: Boolean, sizeSp: Float, revealMs: Long = 760, flicker: Boolean = false) {
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

        // 只有被点名的句子（如 "Hello,"）才闪；频率调低：两次呼吸，共 1.2 秒
        flickerActive = flicker
        if (flicker) {
            firstWordPulse = 0f
            // 一边渐显、一边闪烁：整体亮度在往上爬，中途明暗交替三次
            // 先快速渐显到全亮（约 0.25 秒），再在原地闪 5 次，最后定格
            ValueAnimator.ofFloat(
                0f, 1f, 0.35f, 0.95f, 0.4f, 1f, 0.45f, 1f, 0.5f, 1f, 0.6f, 1f
            ).apply {
                duration = 2600
                addUpdateListener {
                    firstWordPulse = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            firstWordPulse = 1f
        }

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            startDelay = if (flicker) 2550L else 0L
            duration = revealMs
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener {
                val p = it.animatedValue as Float
                reveal = p
                alphaValue = (p * 1.35f).coerceAtMost(1f)
                // 闪烁句（Hello）只做渐显，不做"从下往上滑"的位移
                rise = if (flickerActive) 0f else (1f - p) * dp(6f)
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

    /** 手指按下时，在指尖点亮一团柔光 */
    fun lightUp(x: Float, y: Float) {
        touchX = x
        touchY = y
        ValueAnimator.ofFloat(touchGlowAlpha, 1f).apply {
            duration = 260
            addUpdateListener {
                touchGlowAlpha = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    /** 跟手移动 */
    fun moveLight(x: Float, y: Float) {
        touchX = x
        touchY = y
        invalidate()
    }

    /** 抬手后柔光散开 */
    fun fadeLight() {
        ValueAnimator.ofFloat(touchGlowAlpha, 0f).apply {
            duration = 520
            addUpdateListener {
                touchGlowAlpha = it.animatedValue as Float
                invalidate()
            }
            start()
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
        if (mainText.isEmpty() || (alphaValue <= 0.01f && firstWordPulse <= 0.01f)) return

        val cx = width / 2f
        val baseY = height * anchorY - (mainPaint.descent() + mainPaint.ascent()) / 2f + rise
        val fullWidth = mainPaint.measureText(mainText)

        // ① 柔光：文字区域垫一层极淡的白，亮度差把字托出来
        val glowRadius = (fullWidth * 0.62f).coerceAtLeast(dp(160f))
        glowPaint.shader = android.graphics.RadialGradient(
            cx, baseY - dp(10f), glowRadius,
            intArrayOf(
                (alphaValue * 0x66).toInt() shl 24 or 0xFFFFFF,
                (alphaValue * 0x22).toInt() shl 24 or 0xFFFFFF,
                0x00FFFFFF
            ),
            floatArrayOf(0f, 0.55f, 1f),
            android.graphics.Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, baseY - dp(10f), glowRadius, glowPaint)

        // 指尖柔光：读者触碰哪里，哪里就亮起来
        if (touchGlowAlpha > 0.01f && touchX >= 0f) {
            val bloom = Paint(Paint.ANTI_ALIAS_FLAG)
            val r = dp(120f)
            bloom.shader = android.graphics.RadialGradient(
                touchX, touchY, r,
                intArrayOf(
                    (touchGlowAlpha * 0x4A).toInt() shl 24 or 0xFFFFFF,
                    (touchGlowAlpha * 0x16).toInt() shl 24 or 0xFFFFFF,
                    0x00FFFFFF
                ),
                floatArrayOf(0f, 0.5f, 1f),
                android.graphics.Shader.TileMode.CLAMP
            )
            canvas.drawCircle(touchX, touchY, r, bloom)
        }

        // ② 逐词揭开：每个词比前一个晚一点点出现
        drawWords(canvas, cx, baseY)

        // 耳语：晚一点出现，轻轻跟在下面
        if (whisperText.isNotEmpty() && whisperAlpha > 0.01f) {
            whisperPaint.alpha = (whisperAlpha * 150).toInt().coerceIn(0, 255)
            canvas.drawText(whisperText, cx, baseY + dp(28f), whisperPaint)
        }
    }

    /** 逐词揭开：每个词有自己的 alpha 与位移，像一句一句被说出来 */
    private fun drawWords(canvas: Canvas, cx: Float, baseY: Float) {
        val words = mainText.split(" ")
        if (words.size <= 1) {
            // 单句（比如 "Hello"）：闪烁期间要靠 pulse 亮度显示，
            // 否则 alphaValue 还是 0，字根本画不出来
            val single = if (flickerActive) {
                kotlin.math.max(alphaValue, if (reveal < 1f) firstWordPulse * (1f - reveal) else 0f)
            } else {
                alphaValue
            }
            if (single <= 0.01f) return
            mainPaint.alpha = (single * 255).toInt().coerceIn(0, 255)
            canvas.drawText(mainText, cx, baseY, mainPaint)
            return
        }

        val spaceWidth = mainPaint.measureText(" ")
        val widths = words.map { mainPaint.measureText(it) }
        val totalWidth = widths.sum() + spaceWidth * (words.size - 1)
        var x = cx - totalWidth / 2f

        words.forEachIndexed { i, word ->
            // 每个词比前一个晚 18% 的进度
            val local = ((reveal * (1f + 0.18f * words.size)) - 0.18f * i).coerceIn(0f, 1f)

            // 第一个词：先单独闪烁几下，再跟着整句走
            val effAlpha = if (i == 0 && flickerActive) {
                kotlin.math.max(local, if (reveal < 1f) firstWordPulse * (1f - reveal) else 0f)
            } else {
                local
            }

            if (effAlpha <= 0.01f) { x += widths[i] + spaceWidth; return@forEachIndexed }

            mainPaint.alpha = (effAlpha * 255).toInt().coerceIn(0, 255)
            mainPaint.textAlign = Paint.Align.LEFT
            val wordRise = if (flickerActive) 0f else (1f - local) * dp(5f) + rise
            canvas.drawText(word, x, baseY + wordRise, mainPaint)
            x += widths[i] + spaceWidth
        }
        mainPaint.textAlign = Paint.Align.CENTER
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













