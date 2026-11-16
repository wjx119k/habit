package com.example.habit

import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * 冬天彩蛋：一整个屏幕的连贯动画。
 *
 * 全屏沉浸（隐藏状态栏和导航栏）、常亮，
 * 画面由 WinterSceneView 手绘，播完自动淡出关闭。
 */
class WinterActivity : AppCompatActivity() {

    private var scene: WinterSceneView? = null

    /** 用来对齐音效的定时器 */
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 全屏：内容延伸到状态栏/导航栏下面，然后把它们藏起来
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        try {
            val root = FrameLayout(this)
            root.setBackgroundColor(Color.BLACK)

            val view = WinterSceneView(this)
            root.addView(
                view,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            setContentView(root)

            view.setLines(
                listOf(
                    getString(R.string.winter_line_1),
                    getString(R.string.winter_line_2),
                    getString(R.string.winter_line_3),
                    getString(R.string.winter_line_4)
                )
            )
            view.onFinished = { finish() }
            view.start()
            scene = view

            // 画面一起，钢琴声和风声就进来（生成失败也不会影响画面）
            PianoMusic.start()
            WinterAudio.startWind()

            // 到点播放开关门音效（时间点和画面里的小人保持一致）
            val openAt = (WinterSceneView.DOOR_OPEN_AT * 1000).toLong()
            val closeAt = (WinterSceneView.DOOR_CLOSE_AT * 1000).toLong()
            handler.postDelayed({ WinterAudio.playDoorOpen() }, openAt)
            handler.postDelayed({ WinterAudio.playDoorClose() }, closeAt)
        } catch (e: Throwable) {
            // 万一出错，把原因直接写在屏幕上（这种页面没有日志可看）
            showError(e)
        }
    }

    override fun onPause() {
        super.onPause()
        // 退到后台就停，别让用户切走后还有声音
        PianoMusic.stop()
        WinterAudio.stopAll()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        PianoMusic.stop()
        WinterAudio.stopAll()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /** 隐藏状态栏和导航栏，让画面真正铺满整块屏幕 */
    private fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun showError(e: Throwable) {
        val scroll = ScrollView(this)
        val text = TextView(this)
        text.setTextColor(Color.RED)
        text.textSize = 11f
        text.setPadding(24, 120, 24, 24)
        text.text = "WINTER ERROR\n\n" + e.stackTraceToString()
        scroll.addView(text)
        setContentView(scroll)
    }
}
