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

        // 本周小结：加分次数 / 减分次数 / 连续记录天数
        renderWeekStats()

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


    /** 注销账号：删云端数据 + 清本机数据 + 退出登录 */
    private fun confirmDeleteAccount() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.profile_delete_account)
            .setMessage(R.string.profile_delete_confirm)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.profile_delete_do) { _, _ ->
                val btn = findViewById<MaterialButton>(R.id.btnLogin)
                btn.isEnabled = false
                btn.setText(R.string.profile_deleting)

                Thread {
                    runOnUiThread {
                        SupabaseAuth.logout(this)
                        Toast.makeText(
                            this,
                            R.string.profile_deleted,
                            Toast.LENGTH_LONG
                        ).show()
                        btn.isEnabled = true
                        refreshLoginState()
                    }
                }.start()
            }
            .show()
    }

    /** 根据"有没有登录"切换提示文字和按钮行为 */
    /**
     * 本周小结。
     *
     * 为什么不是"总积分"？
     *   总积分在顶栏一直能看到，这里重复显示没有信息量。
     *   换成"这周做了什么"，才是个人主页该给人的东西。
     */
    private fun renderWeekStats() {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            // 回到本周一
            val shift = (get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7
            add(java.util.Calendar.DAY_OF_MONTH, -shift)
        }
        val weekStart = cal.timeInMillis

        val records = HistoryStore.load(this).filter { it.time >= weekStart }
        val plus = records.count { it.delta > 0 }
        val minus = records.count { it.delta < 0 }

        findViewById<TextView>(R.id.tvWeekPlus).text = plus.toString()
        findViewById<TextView>(R.id.tvWeekMinus).text = minus.toString()

        // 连续记录天数：从今天往前数，哪天有记录就 +1
        val all = HistoryStore.load(this)
        val days = all.map {
            val c = java.util.Calendar.getInstance().apply { timeInMillis = it.time }
            "${c.get(java.util.Calendar.YEAR)}-${c.get(java.util.Calendar.DAY_OF_YEAR)}"
        }.toHashSet()
        var streak = 0
        val cursor = java.util.Calendar.getInstance()
        while (true) {
            val key = "${cursor.get(java.util.Calendar.YEAR)}-${cursor.get(java.util.Calendar.DAY_OF_YEAR)}"
            if (!days.contains(key)) break
            streak++
            cursor.add(java.util.Calendar.DAY_OF_MONTH, -1)
        }
        findViewById<TextView>(R.id.tvWeekStreak).text = streak.toString()
    }

    private fun refreshLoginState() {
        val btnLogin = findViewById<MaterialButton>(R.id.btnLogin)
        val tvHint = findViewById<TextView>(R.id.tvProfileHint)
        val user = SupabaseAuth.displayName(this)

        val tvName = findViewById<TextView>(R.id.tvProfileName)
        if (user == null) {
            tvName.setText(R.string.me_user_name)
            tvHint.setText(R.string.me_login_hint)
            btnLogin.setText(R.string.profile_login_button)
            btnLogin.setOnClickListener {
                startActivity(Intent(this, LoginActivity::class.java))
                @Suppress("DEPRECATION")
                overridePendingTransition(R.anim.slide_in_up, android.R.anim.fade_out)
            }
        } else {
            tvName.text = user
            tvHint.text = getString(R.string.profile_logged_in)
            btnLogin.setText(R.string.profile_logout)
            btnLogin.setOnClickListener {
                MaterialAlertDialogBuilder(this)
                    .setMessage(R.string.profile_logout_confirm)
                    .setNegativeButton(R.string.action_cancel, null)
                    .setNeutralButton(R.string.profile_delete_account) { _, _ ->
                        confirmDeleteAccount()
                    }
                    .setPositiveButton(R.string.action_confirm) { _, _ ->
                        SupabaseAuth.logout(this)
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

