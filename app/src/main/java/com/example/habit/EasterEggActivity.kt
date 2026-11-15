package com.example.habit

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton

/**
 * 彩蛋页。
 *
 * 表面上是一片空白，只有一句"你瞅啥呢"。
 * 但表情会一直上下浮动——点它会晃、会留下手印似的反馈；
 * 连点 5 下之后，它就不装了：换表情、换台词，并把出口交出来。
 */
class EasterEggActivity : AppCompatActivity() {

    private val uiHandler = Handler(Looper.getMainLooper())

    private var taps = 0
    private var revealed = false
    private var floating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_easter_egg)

        val root = findViewById<View>(R.id.eggRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        // 来过这里，就等于"经历过第一个彩蛋"——冬天彩蛋的条件之一
        WinterEgg.markFirstEggSeen(this)

        val emoji = findViewById<TextView>(R.id.tvEggEmoji)
        val message = findViewById<TextView>(R.id.tvEggMessage)
        val leave = findViewById<MaterialButton>(R.id.btnEggLeave)

        // 表情自己先"呼吸"起来：空白的页面上有东西在动，才显得可疑
        startFloating(emoji)

        val onTap = View.OnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            startFloating(emoji)
            wobble(emoji)

            if (!revealed) {
                taps++
                if (taps >= TAPS_TO_REVEAL) {
                    reveal(emoji, message, leave)
                }
            }
        }

        root.setOnClickListener(onTap)

        leave.setOnClickListener {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, R.anim.slide_out_right)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        floating = false
        uiHandler.removeCallbacksAndMessages(null)
    }

    // ============================================================
    //  动画
    // ============================================================

    /** 一直轻轻上下浮动 */
    private fun startFloating(view: View) {
        if (floating) return
        floating = true
        floatStep(view, up = true)
    }

    private fun floatStep(view: View, up: Boolean) {
        if (isFinishing || !floating) return

        val target = if (up) -10f else 10f
        view.animate()
            .translationY(target)
            .setDuration(1500)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction { floatStep(view, !up) }
            .start()
    }

    /** 被戳了一下：晃两下 */
    private fun wobble(view: View) {
        view.animate().cancel()
        view.animate()
            .rotation(14f).scaleX(1.12f).scaleY(1.12f)
            .setDuration(90)
            .withEndAction {
                view.animate()
                    .rotation(-12f)
                    .setDuration(110)
                    .withEndAction {
                        view.animate()
                            .rotation(0f).scaleX(1f).scaleY(1f)
                            .setDuration(120)
                            .start()
                        floatStep(view, up = true)
                    }
                    .start()
            }
            .start()
    }

    /** 不装了，摊牌 */
    private fun reveal(emoji: TextView, message: TextView, leave: MaterialButton) {
        revealed = true

        emoji.text = "🤪"
        emoji.animate()
            .scaleX(1.35f).scaleY(1.35f)
            .setDuration(170)
            .setInterpolator(OvershootInterpolator(2f))
            .withEndAction {
                emoji.animate().scaleX(1f).scaleY(1f).setDuration(180).start()
            }
            .start()

        message.animate()
            .alpha(0f)
            .setDuration(150)
            .withEndAction {
                message.setText(R.string.egg_message_found)
                message.animate().alpha(1f).setDuration(280).start()
            }
            .start()

        leave.visibility = View.VISIBLE
        leave.alpha = 0f
        leave.animate()
            .alpha(1f)
            .setDuration(320)
            .setStartDelay(220)
            .start()
    }

    companion object {
        private const val TAPS_TO_REVEAL = 5
    }
}
