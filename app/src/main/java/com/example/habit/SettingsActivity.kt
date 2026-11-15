package com.example.habit

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * 设置主页：几个模块入口。
 * 目前只有第一个"应用"是真正可用的，其余先弹提示。
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val root = findViewById<View>(R.id.settingsRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        findViewById<ImageButton>(R.id.btnSettingsBack).setOnClickListener {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, R.anim.slide_out_right)
        }

        bindRow(R.id.rowApp, R.drawable.ic_settings, R.string.settings_app, R.string.settings_app_desc) {
            startActivity(Intent(this, AppSettingsActivity::class.java))
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_in_right, android.R.anim.fade_out)
        }
        bindRow(R.id.rowNotifications, R.drawable.ic_reason, R.string.settings_notifications, R.string.settings_notifications_desc) {
            Toast.makeText(this, getString(R.string.coming_soon, getString(R.string.settings_notifications)), Toast.LENGTH_SHORT).show()
        }
        bindRow(R.id.rowPrivacy, R.drawable.ic_lock, R.string.settings_privacy, R.string.settings_privacy_desc) {
            Toast.makeText(this, getString(R.string.coming_soon, getString(R.string.settings_privacy)), Toast.LENGTH_SHORT).show()
        }
        bindRow(R.id.rowAbout, R.drawable.ic_info, R.string.settings_about, R.string.settings_about_desc) {
            startActivity(Intent(this, AboutActivity::class.java))
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_in_right, android.R.anim.fade_out)
        }
    }

    private fun bindRow(rowId: Int, iconRes: Int, titleRes: Int, subtitleRes: Int, onClick: () -> Unit) {
        val row = findViewById<View>(rowId)
        row.findViewById<ImageView>(R.id.ivRowIcon).setImageResource(iconRes)
        row.findViewById<TextView>(R.id.tvRowTitle).setText(titleRes)
        row.findViewById<TextView>(R.id.tvRowSubtitle).setText(subtitleRes)
        row.setOnClickListener { onClick() }
    }
}
