package com.example.habit

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * 长文本阅读页：用户协议 / 隐私政策共用。
 *
 * 通过 Intent 里的 type 区分要显示哪一篇。
 */
class LegalActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TYPE = "legal_type"
        const val TYPE_TERMS = 0
        const val TYPE_PRIVACY = 1
        const val TYPE_LICENSES = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_legal)

        val root = findViewById<View>(R.id.legalRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        findViewById<ImageButton>(R.id.btnLegalBack).setOnClickListener {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, R.anim.slide_out_right)
        }

        val type = intent.getIntExtra(EXTRA_TYPE, TYPE_TERMS)
        val titleRes = when (type) {
            TYPE_PRIVACY -> R.string.about_privacy
            TYPE_LICENSES -> R.string.about_licenses
            else -> R.string.about_terms
        }
        val bodyRes = when (type) {
            TYPE_PRIVACY -> R.string.privacy_body
            TYPE_LICENSES -> R.string.about_licenses_text
            else -> R.string.terms_body
        }

        findViewById<TextView>(R.id.tvLegalTitle).setText(titleRes)
        findViewById<TextView>(R.id.tvLegalBody).setText(bodyRes)
    }
}
