package com.example.habit

/**
 * 开发者信息。
 *
 * 为什么要做成"列表"？
 *   以后会有更多开发者加入，每个人的联系方式也不一样（有人只留 B 站，有人只留邮箱）。
 *   所以每个人的联系方式是一个可变的列表，加人 = 往列表里加一项，不用改界面。
 */

/** 一个联系方式：图标、品牌色、跳转地址、显示名 */
data class DeveloperContact(
    val labelRes: Int,
    val iconRes: Int,
    val tintColor: Int,
    val url: String,
    /** true 表示这是邮箱，用 mailto: 打开 */
    val isEmail: Boolean = false
)

/** 一个开发者：名字、角色、简介、加入时间、参与模块、头像（可选）、联系方式 */
data class Developer(
    val name: String,
    val roleRes: Int,
    val bioRes: Int,
    val joinedYear: Int,
    val joinedMonth: Int,
    val focusRes: List<Int> = emptyList(),
    val avatarRes: Int? = null,
    val contacts: List<DeveloperContact> = emptyList()
)

object Developers {

    private const val URL_BILIBILI = "https://space.bilibili.com/3537113087740802"
    private const val URL_YOUTUBE = "https://www.youtube.com/@wandgundiant_official"
    private const val EMAIL_WAN = "wan.business@qq.com"
    private const val URL_DEEPSEEK = "https://www.deepseek.com"

    const val COLOR_BILIBILI = 0xFFFB7299.toInt()
    const val COLOR_YOUTUBE = 0xFFFF0000.toInt()
    const val COLOR_EMAIL = 0xFF4285F4.toInt()
    const val COLOR_DEEPSEEK = 0xFF4D6BFE.toInt()

    /**
     * 开发者列表。
     *
     * 以后加人只需要在这里追加一项：
     *   Developer("名字", R.string.role_xxx, R.string.bio_xxx, 2026, 11,
     *             avatarRes = R.drawable.avatar_wan,
            focusRes = listOf(...), avatarRes = ..., contacts = listOf(...))
     * 不填 avatarRes 就用名字首字母当头像。
     */
    val ALL: List<Developer> = listOf(
        Developer(
            name = "Wan",
            roleRes = R.string.role_founder,
            bioRes = R.string.dev_bio_wan,
            joinedYear = 2026,
            joinedMonth = 11,
            avatarRes = R.drawable.avatar_wan,
            focusRes = listOf(
                R.string.focus_product,
                R.string.focus_ui,
                R.string.focus_founder
            ),
            contacts = listOf(
                DeveloperContact(R.string.about_bilibili, R.drawable.ic_bilibili, COLOR_BILIBILI, URL_BILIBILI),
                DeveloperContact(R.string.about_youtube, R.drawable.ic_youtube, COLOR_YOUTUBE, URL_YOUTUBE),
                DeveloperContact(R.string.about_email, R.drawable.ic_email, COLOR_EMAIL, EMAIL_WAN, isEmail = true)
            )
        ),
        Developer(
            name = "DeepSeek Harness",
            roleRes = R.string.role_ai_assistant,
            bioRes = R.string.dev_bio_deepseek,
            joinedYear = 2026,
            joinedMonth = 11,
            focusRes = listOf(
                R.string.focus_env,
                R.string.focus_code,
                R.string.focus_build
            ),
            avatarRes = R.drawable.avatar_deepseek,
            contacts = listOf(
                DeveloperContact(R.string.about_website, R.drawable.ic_link, COLOR_DEEPSEEK, URL_DEEPSEEK)
            )
        )
    )
}
