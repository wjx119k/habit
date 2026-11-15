package com.example.habit

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * 启动页（Splash）。
 *
 * 职责只有两个：
 *   1. 播放一段进场动画（图标放大淡入 + 标语上浮淡入）
 *   2. 动画播完，自动跳到主界面，然后把自己关掉
 *
 * 为什么要单独一个界面？
 *   因为启动时要读数据、初始化，如果直接进主界面会"白屏闪一下"。
 *   用启动页遮住这段时间，观感更顺滑。
 */
class SplashActivity : AppCompatActivity() {

    // Handler 用来"延迟一段时间后执行某件事"，这里用于定时跳转
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        playIntroAnimation()

        // 2000 毫秒 = 2 秒后跳转到主界面
        handler.postDelayed({ goToMain() }, 2000L)
    }

    /**
     * 进场动画。
     *
     * 动画的本质是"把属性从 A 慢慢变到 B"：
     *   · alpha 透明度：0（透明）→ 1（不透明），看起来就是"淡入"
     *   · scale  缩放：  0.75（小）→ 1（正常），配合淡入像"轻轻放大浮现"
     *   · translationY 竖直位移：40f → 0f，看起来是"从下往上浮"
     *
     * setDuration 是动画时长（毫秒），setStartDelay 是延迟多久开始。
     * DecelerateInterpolator 让动画"先快后慢"，这是"丝滑感"的关键：
     * 匀速运动会显得机械，减速停下才自然。
     */
    private fun playIntroAnimation() {
        val icon = findViewById<View>(R.id.splashIcon)
        val tagline = findViewById<TextView>(R.id.splashTagline)

        // 先把初始状态设好，否则动画会从"默认值"开始，看不出效果
        icon.alpha = 0f
        icon.scaleX = 0.75f
        icon.scaleY = 0.75f

        tagline.alpha = 0f
        tagline.translationY = 40f

        // 图标：0.7 秒淡入 + 放大
        icon.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(700)
            .setInterpolator(DecelerateInterpolator())
            .start()

        // 标语：延迟 0.3 秒开始，0.8 秒内淡入并上浮到位（和图标错开，有层次感）
        tagline.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(800)
            .setStartDelay(300)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))

        // 两个界面切换时的淡入淡出，避免"啪"地一下硬切
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)

        // 关掉自己：这样用户按返回键时直接退出 App，而不是回到启动页
        finish()
    }

    override fun onDestroy() {
        // 页面销毁时取消定时任务，防止内存泄漏（这是一种好习惯）
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
