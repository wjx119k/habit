package com.example.habit

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

/**
 * 点「Wan」后播放的极简独白。
 *
 * 设计思路：
 *   · 纯白背景、无标题栏、无按钮 —— 只有一个句子在屏幕中央呼吸
 *   · 节奏"慢、短、留白"：每句停留 1.1~1.6 秒，中间有 0.35 秒的空白，
 *     留白比文字更重要，屏幕才不吵
 *   · 前两句用系统无衬线细体（陈述事实），
 *     "For xxx" 四句换成衬线斜体（情绪），靠字形完成"从自我介绍到告白"的转折
 *   · 最后一句停留最久，然后才浮出"轻触屏幕返回"
 */
class DeveloperIntroActivity : AppCompatActivity() {

    private lateinit var tvLine: TextView
    private lateinit var tvHint: TextView
    private val handler = Handler(Looper.getMainLooper())

    /** 一句台词：文字、是否用衬线体、停留时长 */
    private data class Line(val text: String, val serif: Boolean, val hold: Int)

    private val script = listOf(
        Line("Hello, my name is Wan", serif = false, hold = 1700),
        Line("I do lots of things", serif = false, hold = 1400),
        Line("For love", serif = true, hold = 1100),
        Line("For happiness", serif = true, hold = 1100),
        Line("For the world", serif = true, hold = 1100),
        Line("For myself", serif = true, hold = 2600)
    )

    private var index = 0
    private var finished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_developer_intro)

        // 白底 + 浅色状态栏图标，才像"系统画面"
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = 0xFFFFFFFF.toInt()
        window.navigationBarColor = 0xFFFFFFFF.toInt()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        tvLine = findViewById(R.id.tvIntroLine)
        tvHint = findViewById(R.id.tvIntroHint)

        // 整屏点击返回
        findViewById<View>(R.id.introRoot).setOnClickListener { leave() }

        // 先留 900ms 的纯白，再开始说话
        handler.postDelayed({ showNext() }, 900L)
    }

    /** 播下一句：淡入 → 停留 → 淡出 → 下一句 */
    private fun showNext() {
        if (index >= script.size) {
            finishSequence()
            return
        }

        val line = script[index++]
        tvLine.typeface = if (line.serif) {
            android.graphics.Typeface.create("serif", android.graphics.Typeface.ITALIC)
        } else {
            android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
        }
        // 字号随"语气"变化：自我介绍小而克制，"For xxx" 更大、更像告白
        tvLine.textSize = if (line.serif) 32f else 24f
        tvLine.text = line.text
        tvLine.alpha = 0f
        tvLine.translationY = 8f
        tvLine.letterSpacing = if (line.serif) 0.04f else 0.01f

        val fadeIn = 700L
        val fadeOut = 620L
        val gap = 320L

        tvLine.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(fadeIn)
            .start()

        handler.postDelayed({
            tvLine.animate()
                .alpha(0f)
                .translationY(-8f)
                .setDuration(fadeOut)
                .withEndAction {
                    handler.postDelayed({ showNext() }, gap)
                }
                .start()
        }, fadeIn + line.hold)
    }

    /** 全部说完：浮出返回提示 */
    private fun finishSequence() {
        finished = true
        tvHint.animate().alpha(0.55f).setDuration(700).start()
    }

    private fun leave() {
        if (isFinishing) return
        handler.removeCallbacksAndMessages(null)
        findViewById<View>(R.id.introRoot).animate()
            .alpha(0f)
            .setDuration(280)
            .withEndAction { finish() }
            .start()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}

