package com.example.habit

import android.content.Context
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import java.util.Locale

/**
 * 番茄钟：25 分钟专注 + 5 分钟休息。
 *
 * 关键设计：状态要能"离开再回来"。
 *   以前只把计时放在内存里，退出这个页面再进来就归零了——这是 bug。
 *   现在每次开始/暂停都写进 Preferences：
 *     · 运行中：记下"结束时间点"，回来时用 now 一减就知道还剩多久
 *     · 暂停中：记下"还剩多少毫秒"
 *   这样退出页面、切后台、甚至杀进程，回来都能接上。
 */
class PomodoroActivity : AppCompatActivity() {

    private lateinit var tvTime: TextView
    private lateinit var tvPhase: TextView
    private lateinit var btnPrimary: MaterialButton

    private var timer: CountDownTimer? = null
    private var focusing = true
    private var remainingMs = FOCUS_MS
    private var running = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pomodoro)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.pomodoroRoot)) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        PomodoroAlarm.ensureChannel(this)

        tvTime = findViewById(R.id.tvPomodoroTime)
        tvPhase = findViewById(R.id.tvPomodoroPhase)
        btnPrimary = findViewById(R.id.btnPomodoroPrimary)

        findViewById<ImageButton>(R.id.btnPomodoroBack).setOnClickListener { finish() }

        btnPrimary.setOnClickListener { if (running) pause() else start() }
        findViewById<MaterialButton>(R.id.btnPomodoroReset).setOnClickListener { resetAll() }

        restoreState()
        render()
    }

    // ---------------- 状态存取 ----------------

    private fun prefs() = getSharedPreferences("habit_pomodoro", Context.MODE_PRIVATE)

    private fun saveRunning(endAt: Long, focus: Boolean) {
        prefs().edit()
            .putBoolean("running", true)
            .putLong("endAt", endAt)
            .putBoolean("focus", focus)
            .apply()
    }

    private fun savePaused(remain: Long, focus: Boolean) {
        prefs().edit()
            .putBoolean("running", false)
            .putLong("remain", remain)
            .putBoolean("focus", focus)
            .apply()
    }

    private fun clearState() {
        prefs().edit().clear().apply()
    }

    /** 回到页面时，把上次的状态接上 */
    private fun restoreState() {
        val p = prefs()
        focusing = p.getBoolean("focus", true)

        if (p.getBoolean("running", false)) {
            val endAt = p.getLong("endAt", 0L)
            val left = endAt - System.currentTimeMillis()
            if (left > 0) {
                remainingMs = left
                startCountdown(rescheduleAlarm = false)
                return
            }
            remainingMs = 0
            finishPhase()
            return
        }

        remainingMs = p.getLong("remain", totalMs())
        if (remainingMs <= 0L) remainingMs = totalMs()
    }

    // ---------------- 计时 ----------------

    private fun totalMs(): Long = if (focusing) FOCUS_MS else BREAK_MS

    private fun start() {
        if (remainingMs <= 0L) remainingMs = totalMs()
        startCountdown(rescheduleAlarm = true)
    }

    private fun startCountdown(rescheduleAlarm: Boolean) {
        running = true
        btnPrimary.setText(R.string.pomodoro_pause)

        val endAt = System.currentTimeMillis() + remainingMs
        saveRunning(endAt, focusing)
        if (rescheduleAlarm) PomodoroAlarm.schedule(this, endAt, focusing)

        timer?.cancel()
        timer = object : CountDownTimer(remainingMs, 200) {
            override fun onTick(millisUntilFinished: Long) {
                remainingMs = millisUntilFinished
                render()
            }

            override fun onFinish() {
                remainingMs = 0
                render()
                finishPhase()
            }
        }.start()
    }

    private fun pause() {
        running = false
        btnPrimary.setText(R.string.pomodoro_start)
        timer?.cancel()
        timer = null
        savePaused(remainingMs, focusing)
        PomodoroAlarm.cancel(this)
    }

    private fun resetAll() {
        running = false
        timer?.cancel()
        timer = null
        focusing = true
        remainingMs = FOCUS_MS
        clearState()
        PomodoroAlarm.cancel(this)
        render()
    }

    /** 一个阶段结束：提示 + 自动切到另一个阶段 */
    private fun finishPhase() {
        running = false
        timer?.cancel()
        timer = null
        PomodoroAlarm.cancel(this)

        if (focusing) {
            PomodoroAlarm.showNotification(this, true)
            Toast.makeText(this, R.string.pomodoro_focus_done, Toast.LENGTH_LONG).show()
            focusing = false
        } else {
            PomodoroAlarm.showNotification(this, false)
            Toast.makeText(this, R.string.pomodoro_break_done, Toast.LENGTH_LONG).show()
            focusing = true
        }
        remainingMs = totalMs()
        savePaused(remainingMs, focusing)
        render()
    }

    private fun render() {
        val totalSec = (remainingMs / 1000).toInt()
        tvTime.text = String.format(Locale.US, "%02d:%02d", totalSec / 60, totalSec % 60)
        tvPhase.text = if (focusing) {
            getString(R.string.pomodoro_focusing)
        } else {
            getString(R.string.pomodoro_break)
        }
        btnPrimary.setText(if (running) R.string.pomodoro_pause else R.string.pomodoro_start)
    }

    override fun onDestroy() {
        // 只停本页刷新，不动系统闹钟：离开页面后闹钟仍须有效
        timer?.cancel()
        timer = null
        super.onDestroy()
    }

    private companion object {
        const val FOCUS_MS = 25 * 60 * 1000L
        const val BREAK_MS = 5 * 60 * 1000L
    }
}
