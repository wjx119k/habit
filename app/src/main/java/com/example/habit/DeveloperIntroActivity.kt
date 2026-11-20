package com.example.habit

import android.animation.ValueAnimator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

/**
 * 点「Wan」后播放的独白。
 *
 * 画面是一张插画（戴耳机的女孩，天空与风），文字压在她身前的浅色留白上。
 *
 * 结构上分两层：
 *   · 主文（大）：一句陈述 → 四句「For xxx」的告白
 *   · 耳语（小）：晚 140ms 浮现，像话说完之后，心里又轻轻补了一句
 *
 * 节奏：句间留白约 0.9 秒，最后一句停 2.4 秒，然后一条细线从中心长出来。
 */
class DeveloperIntroActivity : AppCompatActivity() {

    private lateinit var monologue: MonologueView
    private lateinit var rule: View
    private lateinit var sign: TextView
    private lateinit var hint: TextView
    private val handler = Handler(Looper.getMainLooper())

    /** 一句台词：主文、耳语、是否衬线、字号、停留 */
    private data class Line(
        val main: String,
        val whisper: String,
        val serif: Boolean,
        val size: Float,
        val hold: Long,
        val flicker: Boolean = false
    )

    private val script = listOf(
        Line("Hello", "", serif = false, size = 28f, hold = 2400, flicker = true),
        Line("I'm Wan", "", serif = false, size = 26f, hold = 1100),
        Line("I make small things", "with quiet hands", serif = false, size = 23f, hold = 1150),
        Line("For love", "the quiet kind", serif = true, size = 32f, hold = 900),
        Line("For happiness", "the kind that stays", serif = true, size = 32f, hold = 900),
        Line("For the world", "a little softer than before", serif = true, size = 32f, hold = 900),
        Line("For myself", "still on the way", serif = true, size = 32f, hold = 2200)
    )

    private var index = 0
    private var finished = false

    /** 当前句的自动推进任务，手动点击时要能取消 */
    private var pendingAdvance: Runnable? = null

    // 触摸手势
    private var downX = 0f
    private var downY = 0f
    private var dragging = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_developer_intro)

        // 画是浅色的，状态栏图标要变深
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        monologue = findViewById(R.id.monologue)
        rule = findViewById(R.id.introRule)
        sign = findViewById(R.id.tvIntroSign)
        hint = findViewById(R.id.tvIntroHint)

        // 文字落在人物身前那片浅色留白上
        monologue.anchorY = 0.70f   // 落在她身前的浅色衣物上，字最干净

        // 触摸：按下点灯、拖动做视差、轻点推进、收尾后轻点返回
        findViewById<View>(R.id.introRoot).setOnTouchListener { _, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    dragging = false
                    monologue.lightUp(event.x, event.y)
                    true
                }

                android.view.MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    if (!dragging && (kotlin.math.abs(dx) > dp(10f) || kotlin.math.abs(dy) > dp(10f))) {
                        dragging = true
                    }
                    monologue.moveLight(event.x, event.y)

                    // 水平拖动 = 轻微视差，像歪头看这张画
                    if (dragging) {
                        val parallax = (dx * 0.05f).coerceIn(-dp(16f), dp(16f))
                        findViewById<android.widget.ImageView>(R.id.ivIntroBg).translationX = parallax
                    }
                    true
                }

                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    monologue.fadeLight()
                    // 松手回正
                    findViewById<android.widget.ImageView>(R.id.ivIntroBg).animate()
                        .translationX(0f).setDuration(420).start()

                    if (!dragging) {
                        if (finished) leave() else advance()
                    }
                    true
                }

                else -> false
            }
        }

        // 画面从白里缓缓浮出，同时极慢地推近 —— 静图也要有呼吸
        val bg = findViewById<android.widget.ImageView>(R.id.ivIntroBg)
        bg.alpha = 0f
        bg.scaleX = 1f
        bg.scaleY = 1f
        bg.animate().alpha(1f).setDuration(900).start()
        bg.animate()
            .scaleX(1.07f)
            .scaleY(1.07f)
            .translationY(-dp(14f))
            .setDuration(26000)
            .setInterpolator(android.view.animation.LinearInterpolator())
            .start()

        // 进来先留半秒空白（让人先看见画面），再开始说话
        handler.postDelayed({ showNext() }, 520L)
    }

    private fun showNext() {
        if (index >= script.size) {
            finishSequence()
            return
        }

        val line = script[index++]
        val revealMs = if (line.serif) 820L else 720L
        monologue.show(line.main, line.whisper, line.serif, line.size, revealMs, line.flicker)

        val task = Runnable {
            monologue.hide(if (line.serif) 640L else 540L)
            val next = Runnable { showNext() }
            pendingAdvance = next
            handler.postDelayed(next, if (line.serif) 880L else 760L)
        }
        pendingAdvance = task
        handler.postDelayed(task, revealMs + line.hold)
    }

    /** 读者轻点：不等自然节奏，立刻推进到下一句 */
    private fun advance() {
        pendingAdvance?.let { handler.removeCallbacks(it) }
        pendingAdvance = null
        monologue.hide(220)
        handler.postDelayed({ showNext() }, 240L)
    }

    /** 收尾：细线从中心生长 → 签名浮现 → 返回提示 */
    private fun finishSequence() {
        finished = true
        val lp = rule.layoutParams as FrameLayout.LayoutParams
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        lp.topMargin = (monologue.height * monologue.anchorY + dp(52f)).toInt()
        rule.layoutParams = lp

        rule.alpha = 0f
        rule.animate().alpha(0.55f).setDuration(600).start()

        ValueAnimator.ofInt(0, dp(46f).toInt()).apply {
            duration = 900
            interpolator = DecelerateInterpolator(1.4f)
            addUpdateListener {
                val w = it.animatedValue as Int
                rule.layoutParams.width = w
                rule.requestLayout()
            }
            start()
        }

        // 暗角缓缓压下，画面"沉"下去
        findViewById<View>(R.id.introVignette).animate().alpha(1f).setDuration(1400).start()

        handler.postDelayed({ sign.animate().alpha(0.6f).setDuration(800).start() }, 620L)
        handler.postDelayed({ hint.animate().alpha(0.42f).setDuration(900).start() }, 1200L)
    }

    private fun leave() {
        if (isFinishing) return
        handler.removeCallbacksAndMessages(null)
        findViewById<View>(R.id.introRoot).animate()
            .alpha(0f)
            .setDuration(320)
            .withEndAction { finish() }
            .start()
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}










