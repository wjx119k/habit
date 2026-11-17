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
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/** 两步注册：先填邮箱，再设密码（Supabase 邮箱+密码，不发确认邮件）。 */
class RegisterActivity : AppCompatActivity() {

    private var step = 1
    private lateinit var layoutEmail: TextInputLayout
    private lateinit var layoutPwd: TextInputLayout
    private lateinit var layoutConfirm: TextInputLayout
    private lateinit var editEmail: TextInputEditText
    private lateinit var editPwd: TextInputEditText
    private lateinit var editConfirm: TextInputEditText
    private lateinit var btnSubmit: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.registerRoot)) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        layoutEmail = findViewById(R.id.layoutRegUsername)
        layoutPwd = findViewById(R.id.layoutRegPassword)
        layoutConfirm = findViewById(R.id.layoutRegConfirm)
        editEmail = findViewById(R.id.editRegUsername)
        editPwd = findViewById(R.id.editRegPassword)
        editConfirm = findViewById(R.id.editRegConfirm)
        btnSubmit = findViewById(R.id.btnRegisterSubmit)

        layoutEmail.hint = "用户名"
        layoutPwd.hint = "密码（至少 6 位）"
        layoutConfirm.hint = "再输一次密码"
        layoutPwd.visibility = View.GONE
        layoutConfirm.visibility = View.GONE
        btnSubmit.text = "下一步"

        findViewById<ImageButton>(R.id.btnRegisterBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvGoLogin).setOnClickListener { finish() }

        btnSubmit.setOnClickListener { if (step == 1) toStep2() else doRegister() }
    }

    private fun toStep2() {
        val name = editEmail.text?.toString()?.trim().orEmpty()
        if (name.length < 2) {
            layoutEmail.error = "用户名至少 2 个字符"
            return
        }
        layoutEmail.error = null
        step = 2
        layoutEmail.visibility = View.GONE
        layoutPwd.visibility = View.VISIBLE
        layoutConfirm.visibility = View.VISIBLE
        btnSubmit.text = "注册"
        editPwd.requestFocus()
    }

    private fun doRegister() {
        val name = editEmail.text?.toString()?.trim().orEmpty()
        val email = SupabaseAuth.toEmail(name)
        val pwd = editPwd.text?.toString().orEmpty()
        val confirm = editConfirm.text?.toString().orEmpty()

        if (pwd.length < 6) { layoutPwd.error = "密码至少 6 位"; return }
        if (pwd != confirm) { layoutConfirm.error = "两次密码不一致"; return }
        layoutPwd.error = null
        layoutConfirm.error = null

        btnSubmit.isEnabled = false
        Thread {
            val error = SupabaseAuth.register(this, email, pwd)
            runOnUiThread {
                btnSubmit.isEnabled = true
                if (error != null) layoutEmail.error = error
                else {
                    Toast.makeText(this, R.string.auth_register_success, Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK); finish()
                }
            }
        }.start()
    }
}
