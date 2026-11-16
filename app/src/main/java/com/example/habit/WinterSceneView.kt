package com.example.habit

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin
import kotlin.random.Random

/**
 * 冬天的雪夜场景。
 *
 * 整个画面完全由 Canvas 手绘（没有一个图片素材）：
 *   星空 → 月亮 → 远山 → 树林 → 雪地 → 小屋 → 烟囱的烟 → 飘雪 → 诗句 → 淡出
 *
 * 时间轴（单位：秒）
 *   0.0 ~ 2.8   黑屏渐渐变亮
 *   1.6 起      开始下雪
 *   6.5 起      四句诗依次出现（每句间隔 2.2 秒）
 *  15.5 ~ 18.5  缓慢黑屏，收束
 */
class WinterSceneView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** 画面播完时回调 */
    var onFinished: (() -> Unit)? = null

    /** 四句诗，由 Activity 传进来（支持多语言） */
    private var lines: List<String> = emptyList()

    private var startAt = 0L
    private var started = false
    private var finished = false

    private var w = 0f
    private var h = 0f

    // ---------- 画笔 ----------
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val flakePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF3F7FF.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("serif", Typeface.NORMAL)
        letterSpacing = 0.16f
        setShadowLayer(18f, 0f, 3f, 0x99000000.toInt())
    }
    private val overlayPaint = Paint()

    // ---------- 雪的粒子（位置由时间算出来，不占用额外状态） ----------
    private var flakeCount = 0
    private lateinit var flakeX: FloatArray
    private lateinit var flakeY: FloatArray
    private lateinit var flakeR: FloatArray
    private lateinit var flakeSpeed: FloatArray
    private lateinit var flakeDrift: FloatArray
    private lateinit var flakePhase: FloatArray

    // ---------- 复用的路径对象（避免每帧 new，减少 GC） ----------
    private val path = Path()
    private val rect = RectF()

    fun setLines(newLines: List<String>) {
        lines = newLines
    }

    fun start() {
        startAt = SystemClock.elapsedRealtime()
        started = true
        finished = false
        invalidate()
    }

    // ============================================================
    //  尺寸 & 粒子初始化
    // ============================================================

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        this.w = w.toFloat()
        this.h = h.toFloat()
        buildFlakes()
    }

    private fun buildFlakes() {
        flakeCount = (w * h / 9000f).toInt().coerceIn(90, 220)
        val random = Random(20261115)

        flakeX = FloatArray(flakeCount)
        flakeY = FloatArray(flakeCount)
        flakeR = FloatArray(flakeCount)
        flakeSpeed = FloatArray(flakeCount)
        flakeDrift = FloatArray(flakeCount)
        flakePhase = FloatArray(flakeCount)

        for (i in 0 until flakeCount) {
            flakeX[i] = random.nextFloat() * w
            flakeY[i] = random.nextFloat() * h
            // 近处的雪大而快，远处的雪小而慢，才有纵深感
            val depth = random.nextFloat()
            flakeR[i] = (1.2f + depth * 4.2f) * (w / 1080f).coerceAtLeast(0.8f)
            flakeSpeed[i] = (26f + depth * 70f) * (h / 2400f).coerceAtLeast(0.6f)
            flakeDrift[i] = 10f + random.nextFloat() * 34f
            flakePhase[i] = random.nextFloat() * 6.283f
        }
    }

    // ============================================================
    //  绘制
    // ============================================================

    override fun onDraw(canvas: Canvas) {
        // 还没 start() 就什么都不画，也绝对不要让时间轴自己跑起来
        if (!started || w <= 0f || h <= 0f) return

        val t = (SystemClock.elapsedRealtime() - startAt) / 1000f

        // 镜头：随着剧情推进缓慢推近小屋（1.0 → 1.85 倍）
        val zoom = cameraZoom(t)
        canvas.save()
        val pivotX = w * 0.50f
        val pivotY = h * 0.72f
        canvas.translate(pivotX, pivotY)
        canvas.scale(zoom, zoom)
        canvas.translate(-pivotX, -pivotY)

        drawSky(canvas)
        drawStars(canvas, t)
        drawMoon(canvas)
        drawHills(canvas)
        drawForest(canvas)
        drawGround(canvas)
        drawCabin(canvas, t)
        drawSnow(canvas, t)
        drawWalker(canvas, t)

        canvas.restore()

        // 文字和淡入淡出固定在屏幕上，不跟着镜头缩放
        drawPoem(canvas, t)
        drawSeal(canvas, t)
        drawVignette(canvas)
        drawFades(canvas, t)

        if (t < END_TIME) {
            invalidate()
        } else if (!finished) {
            finished = true
            onFinished?.invoke()
        }
    }

    /**
     * 镜头推近曲线。
     * 前 3 秒保持原样（让观众先看清全景），之后用 ease-in-out 慢慢推近，
     * 结尾停在 1.85 倍——小屋占满画面，人物刚好在门前。
     */
    private fun cameraZoom(t: Float): Float {
        val start = 3.0f
        if (t <= start) return 1f
        val p = ((t - start) / (END_TIME - start - 1.5f)).coerceIn(0f, 1f)
        // smoothstep：两端都平滑，不会突然开始/停住
        val eased = p * p * (3f - 2f * p)
        return 1f + 0.85f * eased
    }

    // ---------- 天空 ----------
    private fun drawSky(canvas: Canvas) {
        paint.shader = LinearGradient(
            0f, 0f, 0f, h * 0.9f,
            intArrayOf(0xFF060B14.toInt(), 0xFF0E1B2C.toInt(), 0xFF22344C.toInt()),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
    }

    // ---------- 星星 ----------
    private fun drawStars(canvas: Canvas, t: Float) {
        val random = Random(7)
        paint.style = Paint.Style.FILL
        for (i in 0 until 46) {
            val sx = random.nextFloat() * w
            val sy = random.nextFloat() * h * 0.55f
            val base = random.nextFloat() * 0.5f + 0.2f
            // 缓慢闪烁
            val twinkle = 0.75f + 0.25f * sin(t * 1.3f + i)
            paint.color = Color.WHITE
            paint.alpha = (base * twinkle * 200).toInt().coerceIn(0, 200)
            canvas.drawCircle(sx, sy, (w / 900f).coerceAtLeast(1f) * (base + 0.4f), paint)
        }
    }

    // ---------- 月亮 ----------
    private fun drawMoon(canvas: Canvas) {
        val cx = w * 0.79f
        val cy = h * 0.15f
        val r = w * 0.062f

        // 光晕
        paint.shader = RadialGradient(
            cx, cy, r * 4.2f,
            intArrayOf(0x44DCE9FF, 0x11DCE9FF, 0x00DCE9FF),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r * 4.2f, paint)
        paint.shader = null

        // 月盘
        paint.color = 0xFFF3F7E8.toInt()
        canvas.drawCircle(cx, cy, r, paint)

        // 环形山（稍微深一点的小圆）
        paint.color = 0xFFE2E7D6.toInt()
        canvas.drawCircle(cx - r * 0.28f, cy - r * 0.22f, r * 0.19f, paint)
        canvas.drawCircle(cx + r * 0.3f, cy + r * 0.12f, r * 0.13f, paint)
    }

    // ---------- 远山 ----------
    private fun drawHills(canvas: Canvas) {
        paint.color = 0xFF16263A.toInt()
        path.reset()
        path.moveTo(0f, h * 0.62f)
        path.quadTo(w * 0.18f, h * 0.52f, w * 0.38f, h * 0.60f)
        path.quadTo(w * 0.60f, h * 0.68f, w * 0.78f, h * 0.58f)
        path.quadTo(w * 0.92f, h * 0.52f, w, h * 0.60f)
        path.lineTo(w, h)
        path.lineTo(0f, h)
        path.close()
        canvas.drawPath(path, paint)

        // 第二层山，颜色稍亮，形成层次
        paint.color = 0xFF1D3047.toInt()
        path.reset()
        path.moveTo(0f, h * 0.68f)
        path.quadTo(w * 0.22f, h * 0.60f, w * 0.45f, h * 0.67f)
        path.quadTo(w * 0.70f, h * 0.74f, w, h * 0.63f)
        path.lineTo(w, h)
        path.lineTo(0f, h)
        path.close()
        canvas.drawPath(path, paint)
    }

    // ---------- 小树林 ----------
    private fun drawForest(canvas: Canvas) {
        // 左边一丛，右边一丛，把小屋围在中间
        drawTree(canvas, w * 0.10f, h * 0.735f, h * 0.20f)
        drawTree(canvas, w * 0.19f, h * 0.745f, h * 0.16f)
        drawTree(canvas, w * 0.05f, h * 0.755f, h * 0.13f)
        drawTree(canvas, w * 0.28f, h * 0.745f, h * 0.14f)

        drawTree(canvas, w * 0.82f, h * 0.735f, h * 0.19f)
        drawTree(canvas, w * 0.90f, h * 0.748f, h * 0.15f)
        drawTree(canvas, w * 0.70f, h * 0.750f, h * 0.12f)
        drawTree(canvas, w * 0.97f, h * 0.760f, h * 0.17f)
    }

    /** 一棵三层松树：树干 + 三个三角形 + 顶上的积雪 */
    private fun drawTree(canvas: Canvas, cx: Float, baseY: Float, height: Float) {
        val halfWidth = height * 0.30f

        // 树干
        paint.color = 0xFF2A1F18.toInt()
        canvas.drawRect(
            cx - halfWidth * 0.10f, baseY - height * 0.14f,
            cx + halfWidth * 0.10f, baseY,
            paint
        )

        // 三层枝叶（下面的宽、上面的窄）
        for (layer in 0..2) {
            val bottom = baseY - height * (0.10f + 0.30f * layer)
            val top = bottom - height * 0.42f
            val half = halfWidth * (1f - 0.24f * layer)

            paint.color = if (layer == 0) 0xFF12352C.toInt() else 0xFF173E33.toInt()
            path.reset()
            path.moveTo(cx, top)
            path.lineTo(cx - half, bottom)
            path.lineTo(cx + half, bottom)
            path.close()
            canvas.drawPath(path, paint)

            // 积雪：每层顶端的白色薄片
            paint.color = 0xE6F2F7FF.toInt()
            path.reset()
            path.moveTo(cx, top)
            path.lineTo(cx - half * 0.42f, top + height * 0.11f)
            path.quadTo(cx, top + height * 0.16f, cx + half * 0.42f, top + height * 0.11f)
            path.close()
            canvas.drawPath(path, paint)
        }
    }

    // ---------- 雪地 ----------
    private fun drawGround(canvas: Canvas) {
        paint.shader = LinearGradient(
            0f, h * 0.72f, 0f, h,
            intArrayOf(0xFFF2F7FF.toInt(), 0xFFD6E2F2.toInt(), 0xFFBCCDE4.toInt()),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        path.reset()
        path.moveTo(0f, h * 0.755f)
        path.quadTo(w * 0.25f, h * 0.725f, w * 0.52f, h * 0.748f)
        path.quadTo(w * 0.80f, h * 0.772f, w, h * 0.735f)
        path.lineTo(w, h)
        path.lineTo(0f, h)
        path.close()
        canvas.drawPath(path, paint)
        paint.shader = null

        // 前面一道更亮的雪堆
        paint.color = 0xFFFAFCFF.toInt()
        path.reset()
        path.moveTo(0f, h * 0.86f)
        path.quadTo(w * 0.35f, h * 0.82f, w * 0.62f, h * 0.87f)
        path.quadTo(w * 0.85f, h * 0.91f, w, h * 0.86f)
        path.lineTo(w, h)
        path.lineTo(0f, h)
        path.close()
        canvas.drawPath(path, paint)
    }

    // ---------- 小屋 ----------
    private fun drawCabin(canvas: Canvas, t: Float) {
        val cx = w * 0.50f
        val baseY = h * 0.782f
        val bodyW = w * 0.26f
        val bodyH = h * 0.135f
        val left = cx - bodyW / 2f
        val right = cx + bodyW / 2f
        val top = baseY - bodyH

        // 墙
        paint.shader = LinearGradient(
            left, top, right, baseY,
            intArrayOf(0xFF6E5340.toInt(), 0xFF4A3629.toInt()),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(left, top, right, baseY, paint)
        paint.shader = null

        // 屋顶
        paint.color = 0xFF2E211A.toInt()
        path.reset()
        path.moveTo(left - w * 0.035f, top + 2f)
        path.lineTo(cx, top - h * 0.085f)
        path.lineTo(right + w * 0.035f, top + 2f)
        path.close()
        canvas.drawPath(path, paint)

        // 屋顶的雪
        paint.color = 0xFFF4F8FF.toInt()
        path.reset()
        path.moveTo(cx, top - h * 0.085f)
        path.lineTo(left - w * 0.035f, top + 2f)
        path.quadTo(cx - bodyW * 0.30f, top - h * 0.020f, cx, top - h * 0.045f)
        path.quadTo(cx + bodyW * 0.30f, top - h * 0.020f, right + w * 0.035f, top + 2f)
        path.close()
        canvas.drawPath(path, paint)

        // 窗户（暖黄 + 光晕）
        val winW = bodyW * 0.22f
        val winH = bodyH * 0.34f
        val winLeft = left + bodyW * 0.12f
        val winTop = top + bodyH * 0.30f
        rect.set(winLeft, winTop, winLeft + winW, winTop + winH)
        paint.shader = RadialGradient(
            rect.centerX(), rect.centerY(), winW * 3.4f,
            intArrayOf(0x66FFC46B, 0x22FFC46B, 0x00FFC46B),
            floatArrayOf(0f, 0.4f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(rect.centerX(), rect.centerY(), winW * 3.4f, paint)
        paint.shader = null

        paint.color = 0xFFFFC46B.toInt()
        canvas.drawRect(rect, paint)
        paint.color = 0xFF3A2A1C.toInt()
        canvas.drawRect(rect.centerX() - 1.2f, rect.top, rect.centerX() + 1.2f, rect.bottom, paint)
        canvas.drawRect(rect.left, rect.centerY() - 1.2f, rect.right, rect.centerY() + 1.2f, paint)

        // 门：会打开、会关上
        val doorLeft = cx + bodyW * 0.10f
        val doorRight = cx + bodyW * 0.32f
        val doorTop = baseY - bodyH * 0.58f
        val open = doorOpenAmount(t)

        if (open > 0.01f) {
            // 门洞里透出的暖光
            paint.color = 0xFFFFC46B.toInt()
            paint.alpha = (open * 235).toInt()
            canvas.drawRect(doorLeft, doorTop, doorRight, baseY, paint)
            paint.alpha = 255

            // 光洒在门前的雪地上
            paint.shader = RadialGradient(
                (doorLeft + doorRight) / 2f, baseY, bodyW * 0.42f,
                intArrayOf((0x55FFC46B * open).toInt(), 0x00FFC46B),
                null,
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle((doorLeft + doorRight) / 2f, baseY, bodyW * 0.42f, paint)
            paint.shader = null
        }

        // 门板：关上时是整块，打开时向左侧收窄（透视上的"转开"）
        val panelRight = doorRight - (doorRight - doorLeft) * open * 0.82f
        paint.color = 0xFF31241B.toInt()
        canvas.drawRect(doorLeft, doorTop, panelRight, baseY, paint)

        // 门前积雪
        paint.color = 0xFFF7FAFF.toInt()
        path.reset()
        path.moveTo(cx - bodyW * 0.60f, baseY + h * 0.004f)
        path.quadTo(cx, baseY - h * 0.012f, cx + bodyW * 0.60f, baseY + h * 0.004f)
        path.quadTo(cx, baseY + h * 0.020f, cx - bodyW * 0.60f, baseY + h * 0.004f)
        path.close()
        canvas.drawPath(path, paint)

        // 烟囱 + 炊烟
        val chimX = right - bodyW * 0.22f
        val chimTop = top - h * 0.055f
        paint.color = 0xFF3B2C22.toInt()
        canvas.drawRect(chimX, chimTop, chimX + bodyW * 0.08f, top - h * 0.02f, paint)

        paint.color = 0xFFE8EDF5.toInt()
        for (i in 0 until 4) {
            val progress = ((t * 0.22f + i * 0.25f) % 1f)
            val sy = chimTop - progress * h * 0.075f
            val sx = chimX + bodyW * 0.04f + sin(progress * 3.4f + i) * w * 0.012f
            paint.alpha = ((1f - progress) * 120).toInt().coerceIn(0, 120)
            canvas.drawCircle(sx, sy, w * (0.008f + progress * 0.014f), paint)
        }
        paint.alpha = 255
    }


    // ---------- 门的状态 ----------

    /** 0 = 全关，1 = 全开。开 0.8 秒，关 0.6 秒 */
    private fun doorOpenAmount(t: Float): Float {
        return when {
            t < DOOR_OPEN_AT -> 0f
            t < DOOR_OPEN_AT + 0.8f -> ((t - DOOR_OPEN_AT) / 0.8f).coerceIn(0f, 1f)
            t < DOOR_CLOSE_AT -> 1f
            t < DOOR_CLOSE_AT + 0.6f -> (1f - (t - DOOR_CLOSE_AT) / 0.6f).coerceIn(0f, 1f)
            else -> 0f
        }
    }

    // ---------- 风雪中的小人 ----------

    /**
     * 一个穿着厚大衣的人，低头、缩肩、一步一步挪向小屋。
     *
     * 怎么表现"情绪低落"：
     *   · 身体前倾（不是挺胸走）
     *   · 头低着、肩膀塌下来
     *   · 步子小、节奏慢，每步还有一点停顿感
     */
    private fun drawWalker(canvas: Canvas, t: Float) {
        if (t < WALK_START) return

        val cx = w * 0.50f
        val baseY = h * 0.782f
        val bodyW = w * 0.26f
        val doorX = cx + bodyW * 0.21f
        val stopX = doorX - bodyW * 0.17f   // 停在门外一点，剪影才看得见

        val startX = -w * 0.08f
        val walkP = ((t - WALK_START) / (WALK_END - WALK_START)).coerceIn(0f, 1f)
        // ease-out：快到门口时脚步放慢，像走得很累了
        val eased = 1f - (1f - walkP) * (1f - walkP)
        var x = startX + (stopX - startX) * eased
        var groundY = baseY + h * 0.004f
        var scale = 1f
        var alpha = 255

        // 走进门里：缩小 + 淡出
        if (t > ENTER_START) {
            val ep = ((t - ENTER_START) / (ENTER_END - ENTER_START)).coerceIn(0f, 1f)
            x = stopX + (doorX - stopX) * ep
            groundY = baseY - h * 0.010f * ep
            scale = 1f - 0.45f * ep
            alpha = (255 * (1f - ep)).toInt().coerceIn(0, 255)
        }
        if (t > ENTER_END) return

        // 走路时的上下起伏（进入门里就不抖了）
        val walking = t < ENTER_START
        val bob = if (walking) kotlin.math.abs(sin(t * 4.2f)) * h * 0.0035f else 0f
        val sway = if (walking) sin(t * 4.2f) * 1.5f else 0f

        val height = h * 0.078f * scale
        val headR = height * 0.15f

        paint.alpha = alpha
        paint.color = 0xFF232838.toInt()   // 深色大衣（比门稍亮，否则糊成一片）

        // 脚下的影子
        paint.color = 0x33000000
        canvas.drawOval(x - height * 0.22f, groundY - height * 0.035f,
            x + height * 0.22f, groundY + height * 0.02f, paint)
        paint.color = 0xFF232838.toInt()

        // 大衣：上窄下宽的梯形，略微前倾
        val topY = groundY - height + bob
        val coatTop = topY + headR * 1.9f
        val halfTop = height * 0.145f
        val halfBottom = height * 0.215f
        path.reset()
        path.moveTo(x - halfTop + sway, coatTop)
        path.lineTo(x + halfTop + sway, coatTop)
        path.lineTo(x + halfBottom, groundY - height * 0.10f + bob)
        path.lineTo(x - halfBottom, groundY - height * 0.10f + bob)
        path.close()
        canvas.drawPath(path, paint)

        // 腿：两条细线交替迈步
        if (walking) {
            val legSwing = sin(t * 4.2f) * height * 0.16f
            paint.strokeWidth = height * 0.055f
            paint.style = Paint.Style.STROKE
            canvas.drawLine(x - halfBottom * 0.4f, groundY - height * 0.12f + bob,
                x - halfBottom * 0.4f - legSwing, groundY + bob, paint)
            canvas.drawLine(x + halfBottom * 0.4f, groundY - height * 0.12f + bob,
                x + halfBottom * 0.4f + legSwing, groundY + bob, paint)
            paint.style = Paint.Style.FILL
        }

        // 头：低着，微微前探
        val headY = topY + headR * 1.05f
        canvas.drawCircle(x + sway + headR * 0.25f, headY, headR, paint)

        // 帽子（雪帽），让轮廓更像"裹得厚厚的人"
        paint.color = 0xFF2A3140.toInt()
        canvas.drawCircle(x + sway + headR * 0.25f, headY - headR * 0.45f, headR * 0.72f, paint)

        paint.alpha = 255
    }

    // ---------- 飘雪 ----------
    private fun drawSnow(canvas: Canvas, t: Float) {
        if (t < SNOW_START) return

        // 开始下雪时淡入，不要突然冒出来
        val alpha = ((t - SNOW_START) / 1.6f).coerceIn(0f, 1f)

        for (i in 0 until flakeCount) {
            val fall = (flakeY[i] + flakeSpeed[i] * (t - SNOW_START)) % (h + 60f)
            val x = flakeX[i] + sin(t * 0.9f + flakePhase[i]) * flakeDrift[i]
            val y = fall - 30f

            val near = flakeR[i] / 5.4f
            flakePaint.alpha = ((0.35f + near * 0.65f) * alpha * 255).toInt().coerceIn(0, 255)
            canvas.drawCircle(x, y, flakeR[i], flakePaint)
        }
        flakePaint.alpha = 255
    }

    // ---------- 诗句（竖排，一字一字浮出来） ----------
    private fun drawPoem(canvas: Canvas, t: Float) {
        if (lines.isEmpty()) return

        val columnWidth = w * 0.090f
        val charHeight = w * 0.082f
        val blockWidth = columnWidth * lines.size
        val rightColumnX = w / 2f + blockWidth / 2f - columnWidth / 2f
        val topY = h * 0.135f

        textPaint.textSize = w * 0.058f
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        textPaint.color = 0xFFF7F2E6.toInt()
        textPaint.letterSpacing = 0f

        lines.forEachIndexed { lineIndex, line ->
            val appearAt = TEXT_START + lineIndex * TEXT_GAP
            if (t < appearAt) return@forEachIndexed

            // 第一句在最右边，之后一列一列往左排
            val columnX = rightColumnX - lineIndex * columnWidth

            line.forEachIndexed { charIndex, ch ->
                val charAt = appearAt + charIndex * CHAR_GAP
                if (t < charAt) return@forEachIndexed

                val p = ((t - charAt) / CHAR_FADE).coerceIn(0f, 1f)
                val eased = p * p * (3f - 2f * p)   // 缓慢起、缓慢落

                textPaint.alpha = (eased * 255).toInt()
                // 刚出现时略微偏下，像墨刚落到纸上
                val y = topY + charHeight * (charIndex + 1) + (1f - eased) * charHeight * 0.38f
                canvas.drawText(ch.toString(), columnX, y, textPaint)
            }
        }
        textPaint.alpha = 255
    }

    // ---------- 落款印章 ----------
    private fun drawSeal(canvas: Canvas, t: Float) {
        if (lines.isEmpty()) return

        val sealAt = TEXT_START + (lines.size - 1) * TEXT_GAP + 1.3f
        if (t < sealAt) return

        val p = ((t - sealAt) / 0.9f).coerceIn(0f, 1f)
        val size = w * 0.068f
        val columnWidth = w * 0.090f
        val charHeight = w * 0.082f
        val blockWidth = columnWidth * lines.size
        val leftColumnX = w / 2f - blockWidth / 2f + columnWidth / 2f

        val sealCx = leftColumnX - columnWidth * 0.10f
        val sealCy = h * 0.135f + charHeight * (lines.maxOf { it.length } + 1.05f)

        canvas.save()
        canvas.rotate(-4.5f, sealCx, sealCy)
        textPaint.alpha = (p * 255).toInt()

        // 印面
        paint.style = Paint.Style.FILL
        paint.color = 0xFFB03A2E.toInt()
        paint.alpha = (p * 225).toInt()
        rect.set(sealCx - size / 2f, sealCy - size / 2f, sealCx + size / 2f, sealCy + size / 2f)
        canvas.drawRoundRect(rect, size * 0.14f, size * 0.14f, paint)

        // 印里的字：取第一句的第一个字
        textPaint.textSize = size * 0.62f
        textPaint.color = 0xFFF7F2E6.toInt()
        textPaint.alpha = (p * 255).toInt()
        val glyph = lines.first().take(1)
        val glyphY = sealCy - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(glyph, sealCx, glyphY, textPaint)

        paint.style = Paint.Style.FILL
        paint.alpha = 255
        textPaint.alpha = 255
        canvas.restore()
    }

    // ---------- 四角压暗（电影感） ----------
    private fun drawVignette(canvas: Canvas) {
        paint.shader = RadialGradient(
            w / 2f, h * 0.55f, w * 0.85f,
            intArrayOf(0x00000000, 0x00000000, 0x66000000),
            floatArrayOf(0f, 0.62f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
    }

    // ---------- 黑场过渡 ----------
    private fun drawFades(canvas: Canvas, t: Float) {
        overlayPaint.color = Color.BLACK

        val startAlpha = (1f - (t / BRIGHT_TIME).coerceIn(0f, 1f))
        val endAlpha = ((t - FADE_START) / (END_TIME - FADE_START)).coerceIn(0f, 1f)
        val alpha = maxOf(startAlpha, endAlpha)

        if (alpha > 0f) {
            overlayPaint.alpha = (alpha * 255).toInt().coerceIn(0, 255)
            canvas.drawRect(0f, 0f, w, h, overlayPaint)
        }
    }

    // ============================================================
    //  时间轴
    // ============================================================

    companion object {
        private const val BRIGHT_TIME = 2.8f
        private const val SNOW_START = 1.6f
        private const val TEXT_START = 6.5f
        private const val TEXT_GAP = 2.2f

        /** 每个字之间的间隔（一字一字写出来的节奏） */
        private const val CHAR_GAP = 0.20f

        /** 单个字的显现时长 */
        private const val CHAR_FADE = 0.55f
        private const val FADE_START = 24.0f
        private const val END_TIME = 27.5f

        // 小人走路的时间轴（秒）
        private const val WALK_START = 5.0f
        private const val WALK_END = 15.0f

        private const val ENTER_START = 15.4f
        private const val ENTER_END = 17.2f

        /** 开门 / 关门的时间点（WinterActivity 用它对齐音效） */
        const val DOOR_OPEN_AT = 15.0f
        const val DOOR_CLOSE_AT = 17.6f
    }
}
