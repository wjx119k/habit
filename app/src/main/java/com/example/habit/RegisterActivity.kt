package com.example.habit

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * 注册界面。
 *
 * 和登录页一样，现在没有服务器，所以做完本地校验后弹一句"未接入服务器"。
 * 以后接后端时，只需要把 showNoServer() 换成真正的注册请求。
 */
class RegisterActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val root = findViewById<View>(R.id.registerRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val layoutUsername = findViewById<TextInputLayout>(R.id.layoutRegUsername)
        val layoutPassword = findViewById<TextInputLayout>(R.id.layoutRegPassword)
        val layoutConfirm = findViewById<TextInputLayout>(R.id.layoutRegConfirm)
        val editUsername = findViewById<TextInputEditText>(R.id.editRegUsername)
        val editPassword = findViewById<TextInputEditText>(R.id.editRegPassword)
        val editConfirm = findViewById<TextInputEditText>(R.id.editRegConfirm)

        findViewById<ImageButton>(R.id.btnRegisterBack).setOnClickListener {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, R.anim.slide_out_down)
        }

        // 已有账户 → 回到登录页
        findViewById<TextView>(R.id.tvGoLogin).setOnClickListener {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, R.anim.slide_out_down)
        }

        findViewById<MaterialButton>(R.id.btnRegisterSubmit).setOnClickListener {
            val name = editUsername.text?.toString()?.trim().orEmpty()
            val pwd = editPassword.text?.toString().orEmpty()
            val confirm = editConfirm.text?.toString().orEmpty()

            layoutUsername.error = null
            layoutPassword.error = null
            layoutConfirm.error = null

            if (name.length < 2) {
                layoutUsername.error = getString(R.string.register_error_username_short)
                editUsername.requestFocus()
                return@setOnClickListener
            }
            if (pwd.length < 6) {
                layoutPassword.error = getString(R.string.register_error_password_short)
                editPassword.requestFocus()
                return@setOnClickListener
            }
            if (pwd != confirm) {
                layoutConfirm.error = getString(R.string.register_error_confirm)
                editConfirm.requestFocus()
                return@setOnClickListener
            }

            // 真正注册：写进本机账号表，并自动登录
            when (val result = AccountStore.register(this, name, pwd)) {
                is AuthResult.Ok -> {
                    Toast.makeText(this, R.string.auth_register_success, Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                    @Suppress("DEPRECATION")
                    overridePendingTransition(android.R.anim.fade_in, R.anim.slide_out_down)
                }
                is AuthResult.Fail -> {
                    layoutUsername.error = getString(result.messageRes)
                    editUsername.requestFocus()
                }
            }
        }
    }

    private fun showNoServer() {
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.login_no_server)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
