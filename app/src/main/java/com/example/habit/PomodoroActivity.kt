package com.example.habit

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
 * 用 CountDownTimer：系统自带、按毫秒回调，
 * 期间保持屏幕常亮（不然用户盯着看会熄屏）。
 */
class PomodoroActivity : AppCompatActivity() {

    private lateinit var tvTime: TextView
    private lateinit var tvPhase: TextView
    private lateinit var btnPrimary: MaterialButton

    private var timer: CountDownTimer? = null
    private var focusing = true
    private var remainingMs = 25 * 60 * 1000L
    private var running = false
    private var completedCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pomodoro)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.pomodoroRoot)) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        tvTime = findViewById(R.id.tvPomodoroTime)
        tvPhase = findViewById(R.id.tvPomodoroPhase)
        btnPrimary = findViewById(R.id.btnPomodoroPrimary)

        findViewById<ImageButton>(R.id.btnPomodoroBack).setOnClickListener { finish() }

        btnPrimary.setOnClickListener {
            if (running) pause() else start()
        }
        findViewById<MaterialButton>(R.id.btnPomodoroReset).setOnClickListener {
            stopTimer()
            remainingMs = totalMs()
            render()
        }

        render()
    }

    private fun totalMs(): Long = if (focusing) 25 * 60 * 1000L else 5 * 60 * 1000L

    private fun start() {
        running = true
        btnPrimary.setText(R.string.pomodoro_pause)
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
    }

    private fun stopTimer() {
        running = false
        btnPrimary.setText(R.string.pomodoro_start)
        timer?.cancel()
        timer = null
    }

    /** 一个阶段结束：提示 + 自动切到另一个阶段 */
    private fun finishPhase() {
        stopTimer()
        if (focusing) {
            completedCount++
            Toast.makeText(this, R.string.pomodoro_focus_done, Toast.LENGTH_LONG).show()
            focusing = false
        } else {
            Toast.makeText(this, R.string.pomodoro_break_done, Toast.LENGTH_LONG).show()
            focusing = true
        }
        remainingMs = totalMs()
        render()
    }

    private fun render() {
        val totalSec = (remainingMs / 1000).toInt()
        val mm = totalSec / 60
        val ss = totalSec % 60
        tvTime.text = String.format(Locale.US, "%02d:%02d", mm, ss)

        tvPhase.text = if (focusing) {
            getString(R.string.pomodoro_focusing)
        } else {
            getString(R.string.pomodoro_break)
        }
        btnPrimary.setText(if (running) R.string.pomodoro_pause else R.string.pomodoro_start)
    }

    override fun onDestroy() {
        stopTimer()
        super.onDestroy()
    }
}
