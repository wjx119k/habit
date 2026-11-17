package com.example.habit

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * 启动页（Splash）。
 *
 * 现在的流程：
 *   1. 图标淡入放大
 *   2. 标语区循环播放（手写英文 → 倒放擦除 → 打字机中文），两轮约 12 秒
 *   3. 右上角「跳过」在 1.2 秒后出现，点了立即结束
 *   4. 动画结束或点了跳过 → 整屏渐隐 260ms → 进主界面
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var tagline: SplashTextAnimationView
    private lateinit var tvTapHint: TextView
    private var leaving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        playIntroAnimation()

        tagline = findViewById(R.id.splashTagline)
        tvTapHint = findViewById(R.id.tvTapHint)

        tagline.onFinished = { leave() }
        tagline.start()

        // 1.2 秒后底部浮现「轻触屏幕继续」，并轻微呼吸
        tvTapHint.postDelayed({
            if (leaving) return@postDelayed
            tvTapHint.visibility = View.VISIBLE
            tvTapHint.alpha = 0f
            tvTapHint.animate().alpha(0.75f).setDuration(400).start()
            tvTapHint.animate()
                .alpha(0.35f)
                .setStartDelay(400)
                .setDuration(900)
                .withEndAction {
                    if (!leaving) {
                        tvTapHint.animate().alpha(0.85f).setDuration(900).start()
                    }
                }
                .start()
        }, 1200L)

        // 整屏任意位置点击都能继续
        findViewById<View>(android.R.id.content).setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            leave()
        }
    }

    /**
     * 图标进场动画：淡入 + 轻微放大。
     * DecelerateInterpolator 让动画"先快后慢"，静止时不显机械。
     */
    private fun playIntroAnimation() {
        val icon = findViewById<View>(R.id.splashIcon)
        icon.alpha = 0f
        icon.scaleX = 0.75f
        icon.scaleY = 0.75f
        icon.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(700)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    /** 结束启动页：渐隐 → 处理协议 / 进主界面 */
    private fun leave() {
        if (leaving) return
        leaving = true
        tvTapHint.visibility = View.GONE

        val root = findViewById<View>(android.R.id.content)
        root.animate()
            .alpha(0f)
            .setDuration(260)
            .withEndAction {
                if (SettingsStore.hasAgreedTerms(this)) goToMain() else showTermsDialog()
            }
            .start()
    }

    /**
     * 首次启动的用户协议弹窗。
     *
     * 规则：
     *   · 只能点「同意」或「不同意」，点外面和返回键都关不掉
     *   · 同意后写进 Preferences，以后不再打扰
     *   · 不同意就直接退出应用
     */
    private fun showTermsDialog() {
        // 渐隐后背景是透明的，弹窗前恢复，否则背后一片黑
        findViewById<View>(android.R.id.content).alpha = 1f

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.terms_dialog_title)
            .setMessage(R.string.terms_dialog_message)
            .setCancelable(false)
            .setPositiveButton(R.string.terms_dialog_agree) { _, _ ->
                SettingsStore.setAgreedTerms(this)
                goToMain()
            }
            .setNegativeButton(R.string.terms_dialog_disagree) { _, _ ->
                finishAffinity()
            }
            .setNeutralButton(R.string.terms_dialog_detail) { _, _ -> }
            .create()

        dialog.setOnShowListener {
            // 「查看详情」不关闭弹窗，只打开阅读页
            dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                startActivity(Intent(this, LegalActivity::class.java))
            }
        }
        dialog.show()
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}
