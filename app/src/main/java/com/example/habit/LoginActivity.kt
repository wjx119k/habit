package com.example.habit

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * 登录界面。
 *
 * 现在还没有服务器，所以：
 *   · 登录 / 注册 / 四个社交登录 都只弹一句"该应用目前未接入服务器，请稍等"
 *   · 等以后接了后端，只需要把 showNoServer() 换成真正的网络请求即可
 */
class LoginActivity : AppCompatActivity() {

    /** 是否已通过人机验证（本页有效） */
    private var verified = false

    /** 注册成功后，登录页也一起关掉，直接回到个人主页 */
    private val registerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                setResult(RESULT_OK)
                finish()
                @Suppress("DEPRECATION")
                overridePendingTransition(android.R.anim.fade_in, R.anim.slide_out_down)
            }
        }


    /** 滑块人机验证弹窗：拖到最右才算通过 */
    private fun showVerifyDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_verify, null)
        val seek = view.findViewById<android.widget.SeekBar>(R.id.seekVerify)
        val hint = view.findViewById<TextView>(R.id.tvVerifyHint)

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.verify_title)
            .setView(view)
            .setNegativeButton(R.string.action_cancel, null)
            .create()

        seek.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                sb: android.widget.SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {
                if (progress >= 100) {
                    hint.setText(R.string.verify_passed)
                    verified = true
                    dialog.dismiss()
                    // 验证通过后自动继续登录
                    findViewById<MaterialButton>(R.id.btnLoginSubmit).performClick()
                }
            }

            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
        })

        dialog.show()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val root = findViewById<View>(R.id.loginRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        // 键盘弹出时不要遮住输入框
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val layoutUsername = findViewById<TextInputLayout>(R.id.layoutUsername)
        val layoutPassword = findViewById<TextInputLayout>(R.id.layoutPassword)
        val editUsername = findViewById<TextInputEditText>(R.id.editUsername)
        val editPassword = findViewById<TextInputEditText>(R.id.editPassword)

        findViewById<ImageButton>(R.id.btnLoginBack).setOnClickListener {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, R.anim.slide_out_down)
        }

        // 登录
        findViewById<MaterialButton>(R.id.btnLoginSubmit).setOnClickListener {
            val name = editUsername.text?.toString()?.trim().orEmpty()
            val pwd = editPassword.text?.toString().orEmpty()

            if (name.isEmpty()) {
                layoutUsername.error = getString(R.string.login_error_username)
                editUsername.requestFocus()
                return@setOnClickListener
            }
            layoutUsername.error = null

            if (pwd.isEmpty()) {
                layoutPassword.error = getString(R.string.login_error_password)
                editPassword.requestFocus()
                return@setOnClickListener
            }
            layoutPassword.error = null

            // Supabase 用邮箱登录，这里先挡一下明显不是邮箱的输入

            // 走 Supabase 登录（失败返回中文提示，成功返回 null）
            // 关键：网络请求必须在后台线程，否则 Android 直接抛 NetworkOnMainThreadException
            // 人机验证：先过滑块，才允许发登录请求
            if (!verified) {
                showVerifyDialog()
                return@setOnClickListener
            }
            findViewById<MaterialButton>(R.id.btnLoginSubmit).isEnabled = false
            Thread {
                var error = SupabaseAuth.login(this, SupabaseAuth.toEmail(name), pwd)
                // 老账号兼容：早期版本直接用用户名当邮箱注册
                if (error != null && !name.contains("@")) {
                    if (SupabaseAuth.login(this, name, pwd) == null) error = null
                }
                val result = error
                runOnUiThread {
                    findViewById<MaterialButton>(R.id.btnLoginSubmit).isEnabled = true
                    if (result == null) {
                        // 登录成功（不再做任何云端数据同步）
                        Toast.makeText(
                            this,
                            getString(R.string.auth_login_success, name),
                            Toast.LENGTH_SHORT
                        ).show()
                        setResult(RESULT_OK)
                        finish()
                        @Suppress("DEPRECATION")
                        overridePendingTransition(android.R.anim.fade_in, R.anim.slide_out_down)
                    } else {
                        layoutPassword.error = result
                        editPassword.requestFocus()
                    }
                }
            }.start()
        }

        // 注册
        findViewById<TextView>(R.id.tvGoRegister).setOnClickListener {
            registerLauncher.launch(android.content.Intent(this, RegisterActivity::class.java))
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_in_up, android.R.anim.fade_out)
        }

        // 四个社交登录入口：暂未开放，点击无反应
    }

}

