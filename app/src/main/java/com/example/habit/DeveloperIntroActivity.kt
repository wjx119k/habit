package com.example.habit

import android.animation.ValueAnimator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

/**
 * 点「Wan」后播放的极简独白。
 *
 * 设计逻辑（按"语气"分层）：
 *   · 前两句是陈述：细体、小字、从中心揭开
 *   · 后四句是告白：衬线斜体、大字、节奏逐渐放缓
 *   · 句与句之间留白约 0.3 秒，最后一句停 2.4 秒
 *   · 收尾不是"结束"，而是一条细线从中心长出来 + 一个极小的 WAN 签名
 *
 * 全程不出现按钮、颜色、图标——只有黑与白。
 */
class DeveloperIntroActivity : AppCompatActivity() {

    private lateinit var monologue: MonologueView
    private lateinit var rule: View
    private lateinit var sign: TextView
    private lateinit var hint: TextView
    private val handler = Handler(Looper.getMainLooper())

    private data class Line(val text: String, val serif: Boolean, val size: Float, val hold: Long)

    private val script = listOf(
        Line("Hello, my name is Wan", serif = false, size = 24f, hold = 1500),
        Line("I do lots of things", serif = false, size = 24f, hold = 1200),
        Line("For love", serif = true, size = 34f, hold = 950),
        Line("For happiness", serif = true, size = 34f, hold = 950),
        Line("For the world", serif = true, size = 34f, hold = 950),
        Line("For myself", serif = true, size = 34f, hold = 2400)
    )

    private var index = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_developer_intro)

        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = 0xFFFFFFFF.toInt()
        window.navigationBarColor = 0xFFFFFFFF.toInt()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        monologue = findViewById(R.id.monologue)
        rule = findViewById(R.id.introRule)
        sign = findViewById(R.id.tvIntroSign)
        hint = findViewById(R.id.tvIntroHint)

        findViewById<View>(R.id.introRoot).setOnClickListener { leave() }

        // 先留 800ms 纯白，让眼睛适应"空"
        handler.postDelayed({ showNext() }, 800L)
    }

    private fun showNext() {
        if (index >= script.size) {
            finishSequence()
            return
        }

        val line = script[index++]
        val revealMs = if (line.serif) 820L else 700L
        monologue.show(line.text, line.serif, line.size, revealMs)

        handler.postDelayed({
            monologue.hide(if (line.serif) 620L else 520L)
            handler.postDelayed({ showNext() }, if (line.serif) 900L else 780L)
        }, revealMs + line.hold)
    }

    /** 收尾：细线从中心生长 → 签名浮现 → 返回提示 */
    private fun finishSequence() {
        val targetWidth = (resources.displayMetrics.density * 44).toInt()

        rule.alpha = 0f
        rule.animate().alpha(1f).setDuration(600).start()
        ValueAnimator.ofInt(0, targetWidth).apply {
            duration = 900
            interpolator = DecelerateInterpolator(1.4f)
            addUpdateListener {
                val w = it.animatedValue as Int
                rule.layoutParams.width = w
                rule.requestLayout()
            }
            start()
        }

        handler.postDelayed({
            sign.animate().alpha(0.75f).setDuration(800).start()
        }, 620L)

        handler.postDelayed({
            hint.animate().alpha(0.45f).setDuration(900).start()
        }, 1200L)
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

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
