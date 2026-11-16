package com.example.habit

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * 隐私说明页。
 *
 * 为什么单独做一页，而不是在设置里弹个对话框？
 *   隐私这件事，用户是"想看清楚"的：数据存在哪、传到哪、怎么删。
 *   一屏几段话说得明白，比一个 2 秒就消失的 Toast 有诚意得多。
 */
class PrivacyActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy)

        val root = findViewById<View>(R.id.privacyRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        findViewById<ImageButton>(R.id.btnPrivacyBack).setOnClickListener {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, R.anim.slide_out_right)
        }
    }
}
