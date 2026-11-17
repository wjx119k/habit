package com.example.habit

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * 应用内反馈：登录后直接写、直接提交，不需要邮箱。
 *
 * 为什么要求登录？
 *   反馈要能追溯是谁提的（也防止匿名刷屏），而登录体系已经有了。
 */
class FeedbackActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feedback)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.feedbackRoot)) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }

        val edit = findViewById<TextInputEditText>(R.id.editFeedback)
        val layout = findViewById<TextInputLayout>(R.id.layoutFeedback)
        val btn = findViewById<MaterialButton>(R.id.btnFeedbackSend)

        findViewById<ImageButton>(R.id.btnFeedbackBack).setOnClickListener { finish() }

        btn.setOnClickListener {
            val text = edit.text?.toString()?.trim().orEmpty()
            if (text.length < 3) {
                layout.error = getString(R.string.feedback_too_short)
                return@setOnClickListener
            }
            layout.error = null
            btn.isEnabled = false
            btn.setText(R.string.feedback_sending)

            Thread {
                val error = FeedbackSender.send(this, text)
                runOnUiThread {
                    btn.isEnabled = true
                    btn.setText(R.string.feedback_send)
                    if (error == null) {
                        Toast.makeText(this, R.string.feedback_sent, Toast.LENGTH_LONG).show()
                        finish()
                    } else {
                        layout.error = error
                    }
                }
            }.start()
        }
    }
}
