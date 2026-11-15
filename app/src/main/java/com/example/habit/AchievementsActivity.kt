package com.example.habit

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * 成就页。
 *
 * 四个成就：
 *   小试牛刀 10 / 自律专家 100 / 自律大师 1000（单个项目）
 *   自律大神 5000（所有项目合计）
 *
 * 已解锁的成就永久保留，直到用户点「清空成就」。
 */
class AchievementsActivity : AppCompatActivity() {

    private lateinit var summaryView: TextView
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_achievements)

        val root = findViewById<View>(R.id.achRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        summaryView = findViewById(R.id.tvAchSummary)
        list = findViewById(R.id.achievementList)

        findViewById<ImageButton>(R.id.btnAchBack).setOnClickListener {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, R.anim.slide_out_right)
        }

        findViewById<MaterialButton>(R.id.btnClearAchievements).setOnClickListener {
            showClearConfirm()
        }

        render(animate = true)
    }

    /** 重新渲染整页（解锁状态变化后调用） */
    private fun render(animate: Boolean) {
        val unlocked = AchievementStore.unlockedIds(this)
        val projects = ProjectStore.load(this)
        val singleBest = projects.maxOfOrNull { it.bestStreak } ?: 0
        val totalBest = projects.sumOf { it.bestStreak }

        summaryView.text = getString(
            R.string.achievements_summary,
            Achievements.ALL.count { it.id in unlocked },
            Achievements.ALL.size
        )

        list.removeAllViews()

        Achievements.ALL.forEachIndexed { index, achievement ->
            val isUnlocked = achievement.id in unlocked
            val item = layoutInflater.inflate(R.layout.item_achievement, list, false)

            item.findViewById<ImageView>(R.id.ivAchIcon).setImageResource(achievement.iconRes)
            item.findViewById<TextView>(R.id.tvAchTitle).setText(achievement.titleRes)
            item.findViewById<TextView>(R.id.tvAchDesc).setText(achievement.descRes)

            val progressView = item.findViewById<TextView>(R.id.tvAchProgress)
            val stateView = item.findViewById<ImageView>(R.id.ivAchState)
            val iconBg = item.findViewById<View>(R.id.achIconBg)
            val iconView = item.findViewById<ImageView>(R.id.ivAchIcon)

            if (isUnlocked) {
                progressView.setText(R.string.ach_done)
                stateView.setImageResource(R.drawable.ic_check_circle)
                stateView.imageTintList = ColorStateList.valueOf(getColor(R.color.unlocked_green))
                iconView.imageTintList = ColorStateList.valueOf(getColor(R.color.unlocked_green))
                iconBg.alpha = 1f
            } else {
                val current = Achievements.progressOf(achievement, singleBest, totalBest)
                // 单项目成就写"最佳连续"，全部项目合计写"累计"
                val labelRes = if (achievement.metric == AchievementMetric.TOTAL_BEST) {
                    R.string.ach_progress_total
                } else {
                    R.string.ach_progress
                }
                progressView.text = getString(
                    labelRes,
                    minOf(current, achievement.threshold),
                    achievement.threshold
                )
                stateView.setImageResource(R.drawable.ic_lock)
                stateView.imageTintList = ColorStateList.valueOf(getColor(R.color.locked_gray))
                iconView.imageTintList = ColorStateList.valueOf(getColor(R.color.locked_gray))
                iconBg.alpha = 0.6f
            }

            if (animate) {
                item.alpha = 0f
                item.translationY = 20f
                item.animate().alpha(1f).translationY(0f)
                    .setDuration(260)
                    .setStartDelay((index * 60).toLong())
                    .start()
            }

            list.addView(item)
        }
    }

    /**
     * 清空成就前的确认弹窗。
     *
     * 采用"彻底重置"方案：除了清掉奖章，还会把所有项目的
     * 当前连续次数和历史最佳连续次数一起归零，
     * 这样用户必须重新打一遍才能再解锁。
     */
    private fun showClearConfirm() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.ach_clear)
            .setMessage(R.string.ach_clear_confirm)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_confirm) { _, _ ->
                // 1) 清掉奖章
                AchievementStore.clear(this)

                // 2) 把所有项目的连续记录归零（积分不动）
                val projects = ProjectStore.load(this)
                ProjectStore.save(this, projects.map { it.copy(streak = 0, bestStreak = 0) })

                render(animate = false)
                Toast.makeText(this, R.string.ach_cleared, Toast.LENGTH_SHORT).show()
            }
            .show()
    }
}
