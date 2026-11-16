package com.example.habit

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton

/**
 * 个人主页（全屏）。
 *
 * 从 Me 页点头像后，用"从右侧滑入"的动画进来。
 * 现在展示：应用图标、应用名、总积分、登录入口（占位）。
 */
class ProfileActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        // 避开状态栏 / 导航条
        val root = findViewById<View>(R.id.profileRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        // 总积分 = 所有项目积分之和 − 已兑换积分
        // （以前这里没减已兑换，导致在商店买东西后个人主页的数字不变）
        val total = Points.round(
            ProjectStore.load(this).sumOf { it.points } - StoreStore.spentPoints(this)
        )
        findViewById<TextView>(R.id.tvProfilePoints).text = Points.format(total)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.fade_in, R.anim.slide_out_right)
        }

        findViewById<MaterialButton>(R.id.btnLogin).setOnClickListener {
            startActivity(android.content.Intent(this, LoginActivity::class.java))
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_in_up, android.R.anim.fade_out)
        }
    }
}
