package com.example.habit

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * 关于页。
 *
 * 开发者部分做成了"列表驱动"：开发者写在 Developers.ALL 里，
 * 界面按列表渲染，未来加人只需要改数据，不用动布局。
 */
class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        val root = findViewById<View>(R.id.aboutRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        findViewById<ImageButton>(R.id.btnAboutBack).setOnClickListener {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, R.anim.slide_out_right)
        }

        // 版本号（从安装包读取，不写死）
        val versionName = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull() ?: "1.0"
        findViewById<TextView>(R.id.tvAboutVersion).text =
            getString(R.string.about_version) + " " + versionName

        // 三个文本页
        bindRow(R.id.aboutRowLicenses, R.drawable.ic_verified, R.string.about_licenses) {
            openLegal(LegalActivity.TYPE_LICENSES)
        }
        bindRow(R.id.aboutRowTerms, R.drawable.ic_info, R.string.about_terms) {
            openLegal(LegalActivity.TYPE_TERMS)
        }
        bindRow(R.id.aboutRowPrivacy, R.drawable.ic_lock, R.string.about_privacy) {
            openLegal(LegalActivity.TYPE_PRIVACY)
        }

        renderDevelopers()
    }

    // ============================================================
    //  开发者列表
    // ============================================================

    private fun renderDevelopers() {
        val container = findViewById<LinearLayout>(R.id.developerList)
        container.removeAllViews()

        Developers.ALL.forEachIndexed { index, developer ->
            val card = layoutInflater.inflate(R.layout.item_developer, container, false)

            // 头像：有图用图，没图用名字首字母
            val avatarView = card.findViewById<ImageView>(R.id.ivDevAvatar)
            val initialView = card.findViewById<TextView>(R.id.tvDevInitial)
            if (developer.avatarRes != null) {
                avatarView.setImageResource(developer.avatarRes)
                avatarView.visibility = View.VISIBLE
                initialView.visibility = View.GONE
            } else {
                avatarView.visibility = View.GONE
                initialView.visibility = View.VISIBLE
                initialView.text = developer.name.take(1).uppercase()
            }

            card.findViewById<TextView>(R.id.tvDevName).text = developer.name
            card.findViewById<TextView>(R.id.tvDevRole).setText(developer.roleRes)

            // 联系方式图标
            val contactRow = card.findViewById<LinearLayout>(R.id.contactRow)
            if (developer.contacts.isEmpty()) {
                contactRow.visibility = View.GONE
            } else {
                contactRow.visibility = View.VISIBLE
                developer.contacts.forEachIndexed { contactIndex, contact ->
                    contactRow.addView(buildContactIcon(contact, contactIndex == 0))
                }
            }

            // 点整张卡片进入开发者详情
            card.setOnClickListener {
                startActivity(
                    Intent(this, DeveloperDetailActivity::class.java).apply {
                        putExtra(DeveloperDetailActivity.EXTRA_INDEX, index)
                    }
                )
                @Suppress("DEPRECATION")
                overridePendingTransition(R.anim.slide_in_right, android.R.anim.fade_out)
            }

            // 依次淡入
            card.alpha = 0f
            card.translationY = 16f
            card.animate().alpha(1f).translationY(0f)
                .setDuration(260)
                .setStartDelay((index * 70).toLong())
                .start()

            container.addView(card)
        }
    }

    /** 生成一个圆形的联系方式图标按钮 */
    private fun buildContactIcon(contact: DeveloperContact, isFirst: Boolean): View {
        val size = dp(36)
        val circle = FrameLayout(this)
        val params = LinearLayout.LayoutParams(size, size).apply {
            if (!isFirst) marginStart = dp(10)
        }
        circle.layoutParams = params

        // 圆形背景 + 品牌色
        val bg = ContextCompat.getDrawable(this, R.drawable.bg_social_circle)?.mutate()
        bg?.setTint(contact.tintColor)
        circle.background = bg
        circle.isClickable = true
        circle.isFocusable = true

        val icon = ImageView(this)
        icon.layoutParams = FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER)
        icon.setImageResource(contact.iconRes)
        icon.imageTintList = ColorStateList.valueOf(Color.WHITE)
        circle.addView(icon)

        circle.contentDescription = getString(contact.labelRes)

        circle.setOnClickListener {
            if (contact.isEmail) openEmail(contact.url) else openUrl(contact.url)
        }
        return circle
    }

    // ============================================================
    //  工具方法
    // ============================================================

    private fun bindRow(rowId: Int, iconRes: Int, titleRes: Int, onClick: () -> Unit) {
        val row = findViewById<View>(rowId)
        row.findViewById<ImageView>(R.id.ivRowIcon).setImageResource(iconRes)
        row.findViewById<TextView>(R.id.tvRowTitle).setText(titleRes)
        row.setOnClickListener { onClick() }
    }

    private fun showTextDialog(title: String, content: String) {
        // 目前所有长文本都走 LegalActivity，这个方法保留给以后的短提示
    }

    /** 打开用户协议 / 隐私政策阅读页 */
    private fun openLegal(type: Int) {
        startActivity(
            Intent(this, LegalActivity::class.java).apply {
                putExtra(LegalActivity.EXTRA_TYPE, type)
            }
        )
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.slide_in_right, android.R.anim.fade_out)
    }

    private fun openUrl(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            Toast.makeText(this, R.string.about_cannot_open, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openEmail(address: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$address")))
        }.onFailure {
            Toast.makeText(this, R.string.about_cannot_open, Toast.LENGTH_SHORT).show()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
