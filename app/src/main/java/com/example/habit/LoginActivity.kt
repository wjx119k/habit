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


    /** 云端 payload 里有没有真实计划（没有就当作空壳，不能覆盖本机） */
    private fun cloudHasProjects(payload: String): Boolean = runCatching {
        val n = org.json.JSONObject(payload).optJSONArray("projects")?.length() ?: 0
        n > 0
    }.getOrDefault(false)

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
                        // 登录成功：把云端数据拉回来
                        Thread {
                            val payload = SupabaseSync.pull(this)
                            runOnUiThread {
                                if (payload == null) {
                            // 云端为空（首次登录）：把本机数据推上去
                            Thread { runCatching { SupabaseSync.push(this) } }.start()
                        } else if (cloudHasProjects(payload)) {
                            // 云端有真实数据：先备份本机，再覆盖，避免误删
                            runCatching { Backup.writeToCache(this) }
                            SupabaseSync.restore(this, payload)
                        } else {
                            // 云端只是空壳：不要覆盖本机，反向推送
                            Thread { runCatching { SupabaseSync.push(this) } }.start()
                        }
                                Toast.makeText(
                                    this,
                                    getString(R.string.auth_login_success, name),
                                    Toast.LENGTH_SHORT
                                ).show()
                                setResult(RESULT_OK)
                                finish()
                                @Suppress("DEPRECATION")
                                overridePendingTransition(android.R.anim.fade_in, R.anim.slide_out_down)
                            }
                        }.start()
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
