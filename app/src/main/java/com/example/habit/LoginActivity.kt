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


}
