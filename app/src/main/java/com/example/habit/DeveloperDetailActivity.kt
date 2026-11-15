package com.example.habit

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * 开发者详情页。
 *
 * 从"关于"页点某位开发者的卡片进来，展示大头像、角色、个人简介和完整联系方式。
 */
class DeveloperDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_INDEX = "developer_index"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_developer_detail)

        val root = findViewById<View>(R.id.devDetailRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        findViewById<ImageButton>(R.id.btnDevDetailBack).setOnClickListener {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, R.anim.slide_out_right)
        }

        val index = intent.getIntExtra(EXTRA_INDEX, 0)
        val developer = Developers.ALL.getOrNull(index)
        if (developer == null) {
            finish()
            return
        }

        // 头像：有图用图，没图用首字母
        val avatar = findViewById<ImageView>(R.id.ivDetailAvatar)
        val initial = findViewById<TextView>(R.id.tvDetailInitial)
        if (developer.avatarRes != null) {
            avatar.setImageResource(developer.avatarRes)
            avatar.visibility = View.VISIBLE
            initial.visibility = View.GONE
        } else {
            avatar.visibility = View.GONE
            initial.visibility = View.VISIBLE
            initial.text = developer.name.take(1).uppercase()
        }

        findViewById<TextView>(R.id.tvDetailName).text = developer.name
        findViewById<TextView>(R.id.tvDetailRole).setText(developer.roleRes)
        findViewById<TextView>(R.id.tvDetailBio).setText(developer.bioRes)
        findViewById<TextView>(R.id.tvDetailJoined).text =
            getString(R.string.dev_joined, developer.joinedYear, developer.joinedMonth)

        renderFocus(developer)
        renderContacts(developer)
        setupCopyEmail(developer)
    }

    /** 参与模块：用 Chip 标签铺开，多了会自动换行 */
    private fun renderFocus(developer: Developer) {
        val title = findViewById<TextView>(R.id.tvFocusTitle)
        val group = findViewById<com.google.android.material.chip.ChipGroup>(R.id.chipFocus)

        if (developer.focusRes.isEmpty()) {
            title.visibility = View.GONE
            group.visibility = View.GONE
            return
        }

        group.removeAllViews()
        developer.focusRes.forEach { resId ->
            val chip = com.google.android.material.chip.Chip(this).apply {
                setText(resId)
                isClickable = false
                isCheckable = false
                isFocusable = false
            }
            group.addView(chip)
        }
    }

    /** 复制邮箱按钮：只在有邮箱联系方式时出现 */
    private fun setupCopyEmail(developer: Developer) {
        val button = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCopyEmail)
        val email = developer.contacts.firstOrNull { it.isEmail }?.url
        if (email.isNullOrBlank()) {
            button.visibility = View.GONE
            return
        }

        button.visibility = View.VISIBLE
        button.setOnClickListener {
            val clipboard = getSystemService(android.content.ClipboardManager::class.java)
            clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("email", email))
            Toast.makeText(this, R.string.toast_email_copied, Toast.LENGTH_SHORT).show()
        }
    }

    private fun renderContacts(developer: Developer) {
        val title = findViewById<TextView>(R.id.tvContactTitle)
        val list = findViewById<LinearLayout>(R.id.detailContactList)

        if (developer.contacts.isEmpty()) {
            title.visibility = View.GONE
            list.visibility = View.GONE
            return
        }

        list.removeAllViews()
        developer.contacts.forEachIndexed { index, contact ->
            val row = layoutInflater.inflate(R.layout.item_settings_row, list, false)

            val iconView = row.findViewById<ImageView>(R.id.ivRowIcon)
            iconView.setImageResource(contact.iconRes)
            iconView.imageTintList = ColorStateList.valueOf(contact.tintColor)

            row.findViewById<TextView>(R.id.tvRowTitle).setText(contact.labelRes)
            row.findViewById<TextView>(R.id.tvRowSubtitle).text = contact.url

            row.setOnClickListener {
                if (contact.isEmail) openEmail(contact.url) else openUrl(contact.url)
            }

            // 依次淡入
            row.alpha = 0f
            row.translationY = 14f
            row.animate().alpha(1f).translationY(0f)
                .setDuration(240)
                .setStartDelay((index * 60).toLong())
                .start()

            if (index > 0) {
                (row.layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(10)
            }

            list.addView(row)
        }
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
