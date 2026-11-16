package com.example.habit

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * 个人主页（全屏）。
 *
 * 展示：应用图标、应用名、总积分、登录状态。
 * 未登录 → 按钮是"登录 / 注册"；已登录 → 按钮变成"退出登录"。
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
        val total = Points.round(
            ProjectStore.load(this).sumOf { it.points } - StoreStore.spentPoints(this)
        )
        findViewById<TextView>(R.id.tvProfilePoints).text = Points.format(total)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, R.anim.slide_out_right)
        }

        refreshLoginState()
    }

    override fun onResume() {
        super.onResume()
        refreshLoginState()
    }

    /** 根据"有没有登录"切换提示文字和按钮行为 */
    private fun refreshLoginState() {
        val btnLogin = findViewById<MaterialButton>(R.id.btnLogin)
        val tvHint = findViewById<TextView>(R.id.tvProfileHint)
        val user = AccountStore.currentUser(this)

        if (user == null) {
            tvHint.setText(R.string.me_login_hint)
            btnLogin.setText(R.string.profile_login_button)
            btnLogin.setOnClickListener {
                startActivity(Intent(this, LoginActivity::class.java))
                @Suppress("DEPRECATION")
                overridePendingTransition(R.anim.slide_in_up, android.R.anim.fade_out)
            }
        } else {
            tvHint.text = getString(R.string.profile_logged_in) + " · " + user
            btnLogin.setText(R.string.profile_logout)
            btnLogin.setOnClickListener {
                MaterialAlertDialogBuilder(this)
                    .setMessage(R.string.profile_logout_confirm)
                    .setNegativeButton(R.string.action_cancel, null)
                    .setPositiveButton(R.string.action_confirm) { _, _ ->
                        AccountStore.logout(this)
                        Toast.makeText(
                            this,
                            getString(R.string.profile_logout),
                            Toast.LENGTH_SHORT
                        ).show()
                        refreshLoginState()
                    }
                    .show()
            }
        }
    }
}
