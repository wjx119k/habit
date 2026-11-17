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
import com.google.android.material.materialswitch.MaterialSwitch

/**
 * 关于页。
 *
 * 开发者部分做成了"列表驱动"：开发者写在 Developers.ALL 里，
 * 界面按列表渲染，未来加人只需要改数据，不用动布局。
 */
class AboutActivity : AppCompatActivity() {

    private companion object {
        /** 仓库地址：README / 版本发布 / 议题 都从它拼出来 */
        const val REPO_URL = "https://github.com/wjx119k/habit"

        /** 赞助、合作等事宜的联系邮箱 */
        const val SPONSOR_EMAIL = "wan.business@qq.com"
    }

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

        // 反馈：直接唤起邮件，主题和正文预填机型与版本
        bindRow(R.id.aboutRowFeedback, R.drawable.ic_email, R.string.about_feedback, R.string.sub_feedback) {
            sendFeedback()
        }

        // 自动更新开关
        val autoRow = findViewById<View>(R.id.aboutRowAutoUpdate)
        autoRow.findViewById<ImageView>(R.id.ivSwitchIcon).setImageResource(R.drawable.ic_update)
        autoRow.findViewById<TextView>(R.id.tvSwitchTitle).setText(R.string.about_auto_update)
        autoRow.findViewById<TextView>(R.id.tvSwitchSubtitle).setText(R.string.about_auto_update_desc)

        val switchAuto = autoRow.findViewById<MaterialSwitch>(R.id.switchValue)
        switchAuto.isChecked = SettingsStore.isAutoUpdate(this)
        switchAuto.setOnCheckedChangeListener { _, checked ->
            SettingsStore.setAutoUpdate(this, checked)
            if (checked) checkForUpdate(silentIfLatest = false)
        }

        // 开关打开时，进入本页自动检查一次（已是最新则不打扰）
        if (switchAuto.isChecked) checkForUpdate(silentIfLatest = true)

        // 当前版本：显示 版本名 + 构建号，点一下手动检查更新
        // （原来点它是打开发布页，现在改成"检查更新"，发布页另有入口）
        bindRow(R.id.aboutRowVersion, R.drawable.ic_info, R.string.about_current_version, R.string.sub_version) {
            checkForUpdate(silentIfLatest = false)
        }
        val versionCode = runCatching {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionCode
        }.getOrDefault(0)
        findViewById<View>(R.id.aboutRowVersion)
            .findViewById<TextView>(R.id.tvRowValue)
            .apply {
                text = "$versionName ($versionCode)"
                visibility = View.VISIBLE
            }

        // 仓库相关：README / 版本发布 / 议题 / 赞助
        bindRow(R.id.aboutRowReadme, R.drawable.ic_habit, R.string.about_readme, R.string.sub_readme) {
            openUrl(REPO_URL + "#readme")
        }
        bindRow(R.id.aboutRowReleases, R.drawable.ic_verified, R.string.about_releases, R.string.sub_releases) {
            openUrl(REPO_URL + "/releases")
        }
        bindRow(R.id.aboutRowIssues, R.drawable.ic_help, R.string.about_issues, R.string.sub_issues) {
            openUrl(REPO_URL + "/issues")
        }
        bindRow(R.id.aboutRowSponsor, R.drawable.ic_crown, R.string.about_sponsor, R.string.sub_sponsor) {
            showSponsorDialog()
        }

        // 三个文本页
        bindRow(R.id.aboutRowLicenses, R.drawable.ic_verified, R.string.about_licenses, R.string.sub_licenses) {
            openLegal(LegalActivity.TYPE_LICENSES)
        }
        bindRow(R.id.aboutRowTerms, R.drawable.ic_info, R.string.about_terms, R.string.sub_terms) {
            openLegal(LegalActivity.TYPE_TERMS)
        }
        bindRow(R.id.aboutRowPrivacy, R.drawable.ic_lock, R.string.about_privacy, R.string.sub_privacy) {
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

    /**
     * 一行入口。
     * @param subtitleRes 大字下面的小字注释，0 表示不显示
     */
    private fun bindRow(
        rowId: Int,
        iconRes: Int,
        titleRes: Int,
        subtitleRes: Int = 0,
        onClick: () -> Unit
    ) {
        val row = findViewById<View>(rowId)
        row.findViewById<ImageView>(R.id.ivRowIcon).setImageResource(iconRes)
        row.findViewById<TextView>(R.id.tvRowTitle).setText(titleRes)

        val subtitle = row.findViewById<TextView>(R.id.tvRowSubtitle)
        if (subtitleRes != 0) {
            subtitle.setText(subtitleRes)
            subtitle.visibility = View.VISIBLE
        } else {
            subtitle.visibility = View.GONE
        }
        row.setOnClickListener { onClick() }
    }

    /**
     * 检查更新。
     * @param silentIfLatest 已是最新时不弹提示（启动时自动检查用）
     */
    private fun checkForUpdate(silentIfLatest: Boolean) {
        Toast.makeText(this, R.string.update_checking, Toast.LENGTH_SHORT).show()
        Thread {
            val result = UpdateChecker.check(this)
            runOnUiThread {
                when (result) {
                    is UpdateChecker.Result.Latest -> {
                        if (!silentIfLatest) {
                            Toast.makeText(this, R.string.update_latest, Toast.LENGTH_SHORT).show()
                        }
                    }
                    is UpdateChecker.Result.Newer -> offerUpdate(result)
                    is UpdateChecker.Result.NoRelease -> {
                        if (!silentIfLatest) {
                            Toast.makeText(this, R.string.update_no_release, Toast.LENGTH_LONG).show()
                        }
                    }
                    is UpdateChecker.Result.Failed -> {
                        if (!silentIfLatest) {
                            Toast.makeText(
                                this,
                                getString(R.string.update_failed, result.message),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        }.start()
    }

    /** 发现新版本：确认后下载，再拉起系统安装器 */
    private fun offerUpdate(info: UpdateChecker.Result.Newer) {
        if (info.apkUrl.isBlank()) {
            Toast.makeText(this, R.string.update_no_asset, Toast.LENGTH_LONG).show()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.update_found, info.tag))
            .setMessage(R.string.update_install_message_simple)
            .setNegativeButton(R.string.update_install_later, null)
            .setPositiveButton(R.string.update_install_now) { _, _ -> downloadAndInstall(info) }
            .show()
    }

    private fun downloadAndInstall(info: UpdateChecker.Result.Newer) {
        Toast.makeText(this, R.string.update_downloading, Toast.LENGTH_LONG).show()
        Thread {
            val apk = UpdateChecker.download(this, info.apkUrl)
            runOnUiThread {
                if (apk == null) {
                    Toast.makeText(this, R.string.update_download_failed, Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                // 拉起系统安装界面（这一步必须由用户点「安装」）
                if (!UpdateChecker.install(this, apk)) {
                    Toast.makeText(this, R.string.update_need_permission, Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    /**
     * 赞助说明。
     *
     * 没有接支付渠道，所以不做"假按钮"：
     * 老实说明 Habit 免费无广告，并给出能联系到开发者的方式。
     */
    private fun showSponsorDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.about_sponsor)
            .setMessage(R.string.sponsor_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.sponsor_contact) { _, _ ->
                openEmail(SPONSOR_EMAIL)
            }
            .show()
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

    /** 反馈：把机型、系统、版本一并带上，省得用户自己填 */
    private fun sendFeedback() {
        val version = runCatching {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull() ?: "?"
        val body = getString(
            R.string.feedback_body_template,
            version,
            android.os.Build.MODEL,
            "Android " + android.os.Build.VERSION.RELEASE +
                " (API " + android.os.Build.VERSION.SDK_INT + ")"
        )
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:" + SPONSOR_EMAIL)
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.feedback_subject))
            putExtra(Intent.EXTRA_TEXT, body)
        }
        runCatching { startActivity(intent) }.onFailure {
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
