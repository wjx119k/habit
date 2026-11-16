package com.example.habit

import android.app.DatePickerDialog
import android.app.Dialog
import android.content.Intent
import android.graphics.Rect
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.TouchDelegate
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 主界面：底部两个栏目（Project / Me）。
 */
class MainActivity : AppCompatActivity() {

    private val projects = mutableListOf<Project>()

    private lateinit var projectList: LinearLayout
    private lateinit var emptyHint: TextView
    private lateinit var scrollProjects: View
    private lateinit var tvTotalPoints: TextView
    private lateinit var tvAvailablePoints: TextView
    private lateinit var tvStoreSummary: TextView
    private lateinit var storeList: LinearLayout
    private lateinit var redemptionList: LinearLayout
    private lateinit var tvNoRecord: TextView
    private lateinit var tvStoreEmpty: TextView
    private lateinit var btnClearPoints: MaterialButton
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var pageProject: View
    private lateinit var pageStore: View
    private lateinit var pageMe: View

    /** 用于延时关闭"鼓励"浮层 */
    private val uiHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        bottomNav = findViewById(R.id.bottomNav)
        pageProject = findViewById(R.id.pageProject)
        pageStore = findViewById(R.id.pageStore)
        pageMe = findViewById(R.id.pageMe)

        projectList = findViewById(R.id.projectList)
        emptyHint = findViewById(R.id.tvEmpty)
        scrollProjects = findViewById(R.id.scrollProjects)
        tvTotalPoints = findViewById(R.id.tvTotalPoints)

        tvAvailablePoints = findViewById(R.id.tvAvailablePoints)
        tvStoreSummary = findViewById(R.id.tvStoreSummary)
        storeList = findViewById(R.id.storeList)
        redemptionList = findViewById(R.id.redemptionList)
        tvNoRecord = findViewById(R.id.tvNoRecord)
        tvStoreEmpty = findViewById(R.id.tvStoreEmpty)

        // Project 栏目下的"清空积分"
        btnClearPoints = findViewById(R.id.btnClearPoints)
        btnClearPoints.setOnClickListener { confirmClearPoints() }

        // 商城里"添加奖励"
        findViewById<View>(R.id.btnAddReward).setOnClickListener {
            showStoreItemSheet(null)
        }

        projects.clear()
        projects.addAll(ProjectStore.load(this))
        renderProjects(animate = false)

        setupMePage()

        // 右上角小头像：切到 Me 栏目
        findViewById<View>(R.id.btnAvatarTop).setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            bottomNav.selectedItemId = R.id.nav_me
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_project -> {
                    showTab(pageProject)
                    true
                }
                R.id.nav_store -> {
                    showTab(pageStore)
                    renderStore()
                    true
                }
                R.id.nav_me -> {
                    showTab(pageMe)
                    true
                }
                else -> false
            }
        }

        findViewById<View>(R.id.btnAddProject).setOnClickListener {
            showProjectSheet(existing = null, index = -1)
        }

        setupWinterEgg()

        bottomNav.selectedItemId = R.id.nav_project
        pageProject.visibility = View.VISIBLE
        pageStore.visibility = View.GONE
        pageMe.visibility = View.GONE
    }

    // ============================================================
    //  新建 / 编辑 项目（同一个抽屉，两种模式）
    // ============================================================

    private fun showProjectSheet(existing: Project?, index: Int) {
        val isEdit = existing != null

        val dialog = BottomSheetDialog(this, R.style.Theme_Habit_BottomSheet)
        val content = layoutInflater.inflate(R.layout.dialog_new_project, null)
        dialog.setContentView(content)

        dialog.setOnShowListener {
            val sheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            sheet?.layoutParams?.height = (resources.displayMetrics.heightPixels * 0.75f).toInt()
            sheet?.requestLayout()
            // 每次打开都从表单顶部开始，避免直接停在"删除/清空"那一屏
            content.findViewById<androidx.core.widget.NestedScrollView>(R.id.scrollForm)?.scrollTo(0, 0)
        }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val header = content.findViewById<View>(R.id.sheetHeader)
        ViewCompat.setOnApplyWindowInsetsListener(content) { _, insets ->
            val imeVisible = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom > 0
            header.visibility = if (imeVisible) View.GONE else View.VISIBLE
            insets
        }

        val layoutHabit = content.findViewById<TextInputLayout>(R.id.layoutHabit)
        val editHabit = content.findViewById<TextInputEditText>(R.id.editHabit)
        val editBelief = content.findViewById<TextInputEditText>(R.id.editBelief)
        val editReason = content.findViewById<TextInputEditText>(R.id.editReason)
        val editReward = content.findViewById<TextInputEditText>(R.id.editReward)
        val editPenalty = content.findViewById<TextInputEditText>(R.id.editPenalty)
        val tvSheetTitle = content.findViewById<TextView>(R.id.tvSheetTitle)
        val btnSave = content.findViewById<MaterialButton>(R.id.btnSave)
        val rangeDanger = content.findViewById<View>(R.id.sectionDanger)

        // 编辑模式：改标题、显示删除/清空、回填数据
        if (isEdit) {
            tvSheetTitle.setText(R.string.sheet_title_edit)
            btnSave.setText(R.string.action_save_edit)
            rangeDanger.visibility = View.VISIBLE

            editHabit.setText(existing!!.name)
            editBelief.setText(existing.belief)
            editReason.setText(existing.reason)
            if (existing.rewardPoints != 0) editReward.setText(existing.rewardPoints.toString())
            if (existing.penaltyPoints != 0) editPenalty.setText(existing.penaltyPoints.toString())
        } else {
            rangeDanger.visibility = View.GONE
        }

        // ---- 两个小问号 ----
        content.findViewById<View>(R.id.btnHelpReward).setOnClickListener {
            Toast.makeText(this, R.string.help_reward, Toast.LENGTH_SHORT).show()
        }
        content.findViewById<View>(R.id.btnHelpPenalty).setOnClickListener {
            Toast.makeText(this, R.string.help_penalty, Toast.LENGTH_SHORT).show()
        }

        // ---- 起始日期 ----
        val tvDateValue = content.findViewById<TextView>(R.id.tvDateValue)
        val rowDate = content.findViewById<View>(R.id.rowDate)
        var pickedDate = existing?.startDate?.ifBlank { todayString() } ?: todayString()
        tvDateValue.text = pickedDate

        rowDate.setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val cal = Calendar.getInstance()
            val picker = DatePickerDialog(
                this,
                { _, year, month, day ->
                    pickedDate = String.format("%04d-%02d-%02d", year, month + 1, day)
                    tvDateValue.text = pickedDate
                    tvDateValue.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            )
            picker.datePicker.calendarViewShown = true
            picker.datePicker.spinnersShown = false
            picker.show()
            picker.window?.decorView?.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }

        // ---- 取消 / 保存 ----
        content.findViewById<MaterialButton>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val name = editHabit.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) {
                layoutHabit.error = getString(R.string.error_habit_empty)
                editHabit.requestFocus()
                return@setOnClickListener
            }
            layoutHabit.error = null

            val belief = editBelief.text?.toString()?.trim().orEmpty()
            val reason = editReason.text?.toString()?.trim().orEmpty()
            val reward = editReward.text?.toString()?.trim()?.toIntOrNull() ?: 0
            val penalty = editPenalty.text?.toString()?.trim()?.toIntOrNull() ?: 0

            if (isEdit) {
                projects[index] = existing!!.copy(
                    name = name,
                    belief = belief,
                    reason = reason,
                    rewardPoints = reward,
                    penaltyPoints = penalty,
                    startDate = pickedDate
                )
                Toast.makeText(this, R.string.toast_updated, Toast.LENGTH_SHORT).show()
            } else {
                projects.add(
                    Project(
                        name = name,
                        belief = belief,
                        reason = reason,
                        rewardPoints = reward,
                        penaltyPoints = penalty,
                        startDate = pickedDate
                    )
                )
                Toast.makeText(this, getString(R.string.toast_saved, name), Toast.LENGTH_SHORT).show()
            }

            ProjectStore.save(this, projects)
            renderProjects(animate = !isEdit)
            dialog.dismiss()
        }

        // ---- 删除 ----
        content.findViewById<MaterialButton>(R.id.btnDelete).setOnClickListener {
            if (!isEdit) return@setOnClickListener
            confirmDialog(
                titleRes = R.string.dialog_delete_title,
                message = getString(R.string.dialog_delete_message)
            ) {
                val removed = projects.removeAt(index)
                ProjectStore.save(this, projects)
                recordHistory(
                    getString(R.string.history_delete_plan, removed.name),
                    -removed.points,
                    removed.id
                )
                renderProjects(animate = false)
                Toast.makeText(this, getString(R.string.toast_deleted, removed.name), Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }

        // ---- 清空该项目积分 ----
        content.findViewById<MaterialButton>(R.id.btnClear).setOnClickListener {
            if (!isEdit) return@setOnClickListener
            projects[index] = existing!!.copy(points = 0)
            ProjectStore.save(this, projects)
            recordHistory(
                getString(R.string.history_delete_plan, existing.name),
                -existing.points,
                existing.id,
                0
            )
            renderProjects(animate = false)
            Toast.makeText(this, getString(R.string.toast_cleared, existing.name), Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    // ============================================================
    //  加分 / 减分
    // ============================================================

    private fun onAddPoint(index: Int) {
        val project = projects[index]
        confirmDialog(
            titleRes = R.string.dialog_add_title,
            message = getString(R.string.dialog_add_message, project.rewardPoints)
        ) {
            val newStreak = project.streak + 1
            projects[index] = project.copy(
                points = project.points + project.rewardPoints,
                streak = newStreak,
                bestStreak = maxOf(project.bestStreak, newStreak)
            )
            ProjectStore.save(this, projects)
            recordHistory(
                label = "${project.name} +${project.rewardPoints}",
                delta = project.rewardPoints,
                projectId = project.id,
                projectPoints = project.points + project.rewardPoints
            )
            renderProjects(animate = false)
            checkAchievements()
            showCheer("🎉", getString(R.string.cheer_done), isPositive = true)
        }
    }

    private fun onMinusPoint(index: Int) {
        val project = projects[index]
        confirmDialog(
            titleRes = R.string.dialog_minus_title,
            message = getString(R.string.dialog_minus_message, project.penaltyPoints)
        ) {
            // 减分：积分扣掉，连续次数清零（"中途减分直接重来"）
            projects[index] = project.copy(
                points = project.points - project.penaltyPoints,
                streak = 0
            )
            ProjectStore.save(this, projects)
            recordHistory(
                label = "${project.name} −${project.penaltyPoints}",
                delta = -project.penaltyPoints,
                projectId = project.id,
                projectPoints = project.points - project.penaltyPoints
            )
            renderProjects(animate = false)
            showCheer("💪", getString(R.string.cheer_miss), isPositive = false)
        }
    }

    /** 每次加分后检查成就；解锁了新成就就弹一个庆祝 */
    private fun checkAchievements() {
        // 单项目历史最佳（"小试牛刀/专家/大师"看这个）
        val singleBest = projects.maxOfOrNull { it.bestStreak } ?: 0
        // 所有项目的历史最佳之和（"自律大神"看这个）
        val totalBest = projects.sumOf { it.bestStreak }

        val newly = AchievementStore.checkAndUnlock(this, singleBest, totalBest)
        if (newly.isNotEmpty()) {
            val best = newly.maxByOrNull { it.threshold } ?: return
            showCheer("🏆", getString(R.string.ach_unlocked_toast, getString(best.titleRes)), true)
        }
    }

    /** 统一的"取消 / 确认"对话框 */
    private fun confirmDialog(titleRes: Int, message: String, onConfirm: () -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setTitle(titleRes)
            .setMessage(message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_confirm) { _, _ -> onConfirm() }
            .show()
    }

    /**
     * 鼓励 / 安慰 浮层：
     * 透明背景的 Dialog + 弹跳放大的表情 + 一句话，1.8 秒后自动消失。
     */
    private fun showCheer(emoji: String, message: String, isPositive: Boolean) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_cheer)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
        }
        dialog.setCancelable(true)

        val emojiView = dialog.findViewById<TextView>(R.id.tvEmoji)
        val msgView = dialog.findViewById<TextView>(R.id.tvCheerMessage)
        emojiView.text = emoji
        msgView.text = message

        emojiView.alpha = 0f
        emojiView.scaleX = 0.3f
        emojiView.scaleY = 0.3f
        msgView.alpha = 0f

        dialog.show()

        emojiView.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(450)
            .setInterpolator(OvershootInterpolator(1.6f))
            .start()
        msgView.animate().alpha(1f).setStartDelay(200).setDuration(300).start()

        uiHandler.postDelayed({
            if (!isFinishing && dialog.isShowing) dialog.dismiss()
        }, 1800)
    }

    // ============================================================
    //  冬天彩蛋：连点左上角 App 图标三下
    // ============================================================

    private var appIconTaps = 0

    private fun setupWinterEgg() {
        val icon = findViewById<View>(R.id.ivAppIcon)

        // 图标只有 30dp，手指很难点准；把它的"触摸范围"向外扩一圈
        icon.post {
            val parent = icon.parent as? View ?: return@post
            val rect = Rect()
            icon.getHitRect(rect)
            val extra = (22 * resources.displayMetrics.density).toInt()
            rect.inset(-extra, -extra)
            parent.touchDelegate = TouchDelegate(rect, icon)
        }

        icon.setOnClickListener {
            // 每点一下都震一下
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onAppIconTapped()
        }
    }

    private fun onAppIconTapped() {
        // 两个前置条件缺一不可：进过第一个彩蛋 + 解锁"小试牛刀"
        if (!WinterEgg.isEligible(this)) {
            appIconTaps = 0
            return
        }

        appIconTaps++
        if (appIconTaps >= 3) {
            appIconTaps = 0
            WinterEgg.arm(this)
            Toast.makeText(this, R.string.egg_changed, Toast.LENGTH_LONG).show()
        }
    }

    // ============================================================
    //  Me 页面
    // ============================================================

    private fun setupMePage() {
        // 资料卡 → 个人主页（从右侧滑入）
        findViewById<View>(R.id.cardProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_in_right, android.R.anim.fade_out)
        }
        bindMeRow(R.id.rowAchievement, R.drawable.ic_trophy, R.string.me_achievement)
        bindMeRow(R.id.rowHistory, R.drawable.ic_history, R.string.me_history)
        bindMeRow(R.id.rowAbout, R.drawable.ic_info, R.string.me_about)
        bindMeRow(R.id.rowSettings, R.drawable.ic_settings, R.string.me_settings)
        bindMeRow(R.id.rowEasterEgg, R.drawable.ic_egg, R.string.me_easter_egg)

        // 每个菜单入口都先问一句"冬天彩蛋是不是该触发了"
        openMeDestination(R.id.rowAchievement) {
            startActivity(Intent(this, AchievementsActivity::class.java))
        }
        openMeDestination(R.id.rowHistory) {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        openMeDestination(R.id.rowAbout) {
            startActivity(Intent(this, AboutActivity::class.java))
        }
        openMeDestination(R.id.rowSettings) {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        openMeDestination(R.id.rowEasterEgg) {
            startActivity(Intent(this, EasterEggActivity::class.java))
        }
    }

    /**
     * Me 页统一入口。
     *
     * 如果冬天彩蛋正处于"待触发"状态（在 Project 页连点了 App 图标三下），
     * 这一次点击就被它"劫持"掉，改成播放整屏动画。
     */
    private fun openMeDestination(rowId: Int, action: () -> Unit) {
        findViewById<View>(rowId).setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

            // 前置条件始终要满足：成就被清空后，残留的 armed 也不该生效
            if (WinterEgg.isEligible(this) && WinterEgg.consumeArmed(this)) {
                startActivity(Intent(this, WinterActivity::class.java))
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
                return@setOnClickListener
            }

            action()
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_in_right, android.R.anim.fade_out)
        }
    }

    private fun bindMeRow(rowId: Int, iconRes: Int, titleRes: Int) {
        val row = findViewById<View>(rowId)
        row.findViewById<ImageView>(R.id.ivIcon).setImageResource(iconRes)
        row.findViewById<TextView>(R.id.tvTitle).setText(titleRes)

        val label = getString(titleRes)
        row.setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            Toast.makeText(this, getString(R.string.coming_soon, label), Toast.LENGTH_SHORT).show()
        }
    }

    // ============================================================
    //  项目列表
    // ============================================================

    private fun renderProjects(animate: Boolean) {
        projectList.removeAllViews()

        // 商品能否兑换也跟着积分变，顺手刷新一下商城
        renderStore()

        // 顶部总积分 = 所有项目积分之和 − 已兑换积分
        tvTotalPoints.text = availablePoints().toString()

        // 只有在真的存在积分时，才显示"清空积分"
        btnClearPoints.visibility =
            if (projects.any { it.points != 0 }) View.VISIBLE else View.GONE

        if (projects.isEmpty()) {
            emptyHint.visibility = View.VISIBLE
            scrollProjects.visibility = View.GONE
            return
        }

        emptyHint.visibility = View.GONE
        scrollProjects.visibility = View.VISIBLE

        projects.forEachIndexed { index, project ->
            val item = layoutInflater.inflate(R.layout.item_project, projectList, false)

            item.findViewById<TextView>(R.id.tvName).text = project.name
            item.findViewById<TextView>(R.id.tvBelief).text =
                project.belief.ifBlank { getString(R.string.belief_empty) }
            item.findViewById<TextView>(R.id.tvCardPoints).text =
                getString(R.string.card_points, project.points)
            item.findViewById<TextView>(R.id.tvReward).text =
                getString(R.string.points_reward, project.rewardPoints)
            item.findViewById<TextView>(R.id.tvPenalty).text =
                getString(R.string.points_penalty, project.penaltyPoints)

            item.findViewById<View>(R.id.btnEdit).setOnClickListener {
                showProjectSheet(existing = projects[index], index = index)
            }
            item.findViewById<View>(R.id.btnAddPoint).setOnClickListener { onAddPoint(index) }
            item.findViewById<View>(R.id.btnMinusPoint).setOnClickListener { onMinusPoint(index) }

            if (animate) {
                item.alpha = 0f
                item.translationY = 24f
                item.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(260)
                    .setStartDelay((index * 45).toLong())
                    .start()
            }

            projectList.addView(item)
        }
    }

    // ============================================================
    //  清空积分
    // ============================================================

    private fun confirmClearPoints() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.clear_points)
            .setMessage(R.string.clear_points_confirm)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_confirm) { _, _ ->
                val before = availablePoints()
                // 所有项目的积分归零（用 copy，Project 的字段都是 val）
                for (i in projects.indices) {
                    projects[i] = projects[i].copy(points = 0)
                }
                ProjectStore.save(this, projects)

                // 兑换记录一起清空：否则"可用积分 = 0 − 已兑换"会变成负数
                StoreStore.clear(this)
                recordHistory(getString(R.string.history_clear_points), -before)

                renderProjects(animate = true)
                renderStore()
                Toast.makeText(this, R.string.clear_points_done, Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    // ============================================================
    //  Store（积分商城）
    // ============================================================

    /** 奖励图标的配色循环 */
    private val storeColors = listOf(
        0xFF7E57C2.toInt(), 0xFFEC407A.toInt(), 0xFFEF5350.toInt(),
        0xFF42A5F5.toInt(), 0xFF26A69A.toInt(), 0xFFFFA726.toInt()
    )

    /** 可用积分 = 所有项目累计 − 已兑换 */
    private fun availablePoints(): Int =
        projects.sumOf { it.points } - StoreStore.spentPoints(this)

    /**
     * 记一条积分变动历史。
     * 必须在 projects / 兑换记录都改完之后调用——它要用变动后的总积分。
     */
    private fun recordHistory(
        label: String,
        delta: Int,
        projectId: String = "",
        projectPoints: Int = -1
    ) {
        HistoryStore.add(
            this,
            HistoryRecord(
                time = System.currentTimeMillis(),
                label = label,
                delta = delta,
                total = availablePoints(),
                projectId = projectId,
                projectPoints = projectPoints
            )
        )
    }

    private fun renderStore() {
        val earned = projects.sumOf { it.points }
        val spent = StoreStore.spentPoints(this)
        val available = earned - spent

        tvAvailablePoints.text = available.toString()
        tvStoreSummary.text = getString(R.string.store_summary, earned, spent)

        // 奖励清单（用户自己创建的）
        val items = StoreStore.items(this)
        storeList.removeAllViews()
        tvStoreEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        storeList.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE

        items.forEachIndexed { index, item ->
            val card = layoutInflater.inflate(R.layout.item_store, storeList, false)

            val bg = ContextCompat.getDrawable(this, R.drawable.bg_social_circle)?.mutate()
            bg?.setTint(storeColors[index % storeColors.size])
            card.findViewById<View>(R.id.itemIconBg).background = bg
            card.findViewById<TextView>(R.id.tvItemInitial).text = item.title.take(1).uppercase()

            card.findViewById<TextView>(R.id.tvItemTitle).text = item.title

            val descView = card.findViewById<TextView>(R.id.tvItemDesc)
            if (item.desc.isBlank()) {
                descView.visibility = View.GONE
            } else {
                descView.visibility = View.VISIBLE
                descView.text = item.desc
            }

            card.findViewById<TextView>(R.id.tvItemCost).text =
                getString(R.string.store_cost, item.cost)

            val button = card.findViewById<MaterialButton>(R.id.btnRedeem)
            val enough = available >= item.cost
            button.isEnabled = enough
            button.text = getString(if (enough) R.string.store_redeem else R.string.store_not_enough)
            button.setOnClickListener { onRedeem(item) }

            // 点卡片本身 = 编辑这个奖励
            card.setOnClickListener { showStoreItemSheet(item) }

            card.alpha = 0f
            card.translationY = 14f
            card.animate().alpha(1f).translationY(0f)
                .setDuration(240)
                .setStartDelay((index * 50).toLong())
                .start()

            storeList.addView(card)
        }

        // 兑换记录
        val records = StoreStore.redemptions(this)
        redemptionList.removeAllViews()
        tvNoRecord.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
        redemptionList.visibility = if (records.isEmpty()) View.GONE else View.VISIBLE

        val timeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        records.take(10).forEach { record ->
            val row = layoutInflater.inflate(R.layout.item_redemption, redemptionList, false)
            row.findViewById<TextView>(R.id.tvRedemptionTitle).text = record.title
            row.findViewById<TextView>(R.id.tvRedemptionTime).text = timeFormat.format(Date(record.time))
            row.findViewById<TextView>(R.id.tvRedemptionCost).text =
                getString(R.string.points_penalty, record.cost)
            row.findViewById<MaterialButton>(R.id.btnRevoke).setOnClickListener {
                confirmRevoke(record)
            }
            redemptionList.addView(row)
        }
    }

    /** 撤销一次兑换：删掉记录，积分原路退回 */
    private fun confirmRevoke(record: Redemption) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.store_revoke_title)
            .setMessage(getString(R.string.store_revoke_message, record.title, record.cost))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_confirm) { _, _ ->
                val ok = StoreStore.revoke(this, record)
                if (ok) {
                    recordHistory(getString(R.string.history_revoke, record.title), record.cost)
                    Toast.makeText(
                        this,
                        getString(R.string.store_revoke_done, record.cost),
                        Toast.LENGTH_SHORT
                    ).show()
                    renderStore()
                    renderProjects(animate = false)
                }
            }
            .show()
    }

    private fun onRedeem(item: StoreItem) {
        val available = availablePoints()

        if (available < item.cost) {
            Toast.makeText(this, R.string.store_not_enough, Toast.LENGTH_SHORT).show()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.store_confirm_title)
            .setMessage(getString(R.string.store_confirm_message, item.cost, item.title))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_confirm) { _, _ ->
                val ok = StoreStore.redeem(this, item, available)
                if (ok) {
                    recordHistory(getString(R.string.history_redeem, item.title), -item.cost)
                    Toast.makeText(this, getString(R.string.store_toast_done, item.title), Toast.LENGTH_LONG).show()
                    renderProjects(animate = false)
                    renderStore()
                }
            }
            .show()
    }

    /** 新建 / 编辑奖励的底部抽屉 */
    private fun showStoreItemSheet(existing: StoreItem?) {
        val isEdit = existing != null

        val dialog = BottomSheetDialog(this, R.style.Theme_Habit_BottomSheet)
        val content = layoutInflater.inflate(R.layout.dialog_store_item, null)
        dialog.setContentView(content)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        content.findViewById<TextView>(R.id.tvStoreSheetTitle)
            .setText(if (isEdit) R.string.store_edit_title else R.string.store_add_title)

        val layoutName = content.findViewById<TextInputLayout>(R.id.layoutRewardName)
        val layoutCost = content.findViewById<TextInputLayout>(R.id.layoutRewardCost)
        val editName = content.findViewById<TextInputEditText>(R.id.editRewardName)
        val editDesc = content.findViewById<TextInputEditText>(R.id.editRewardDesc)
        val editCost = content.findViewById<TextInputEditText>(R.id.editRewardCost)
        val deleteButton = content.findViewById<MaterialButton>(R.id.btnStoreDelete)

        if (isEdit) {
            editName.setText(existing!!.title)
            editDesc.setText(existing.desc)
            editCost.setText(existing.cost.toString())
            deleteButton.visibility = View.VISIBLE
            deleteButton.setOnClickListener {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.action_delete)
                    .setMessage(R.string.store_delete_confirm)
                    .setNegativeButton(R.string.action_cancel, null)
                    .setPositiveButton(R.string.action_confirm) { _, _ ->
                        StoreStore.deleteItem(this, existing.id)
                        renderStore()
                        Toast.makeText(this, R.string.store_deleted, Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                    .show()
            }
        }

        content.findViewById<MaterialButton>(R.id.btnStoreCancel).setOnClickListener { dialog.dismiss() }

        content.findViewById<MaterialButton>(R.id.btnStoreSave).setOnClickListener {
            val title = editName.text?.toString()?.trim().orEmpty()
            val desc = editDesc.text?.toString()?.trim().orEmpty()
            val cost = editCost.text?.toString()?.trim()?.toIntOrNull() ?: 0

            if (title.isEmpty()) {
                layoutName.error = getString(R.string.store_error_name)
                editName.requestFocus()
                return@setOnClickListener
            }
            layoutName.error = null

            if (cost <= 0) {
                layoutCost.error = getString(R.string.store_error_cost)
                editCost.requestFocus()
                return@setOnClickListener
            }
            layoutCost.error = null

            if (isEdit) {
                StoreStore.updateItem(this, existing!!.copy(title = title, desc = desc, cost = cost))
            } else {
                StoreStore.addItem(this, StoreItem(title = title, cost = cost, desc = desc))
            }

            renderStore()
            Toast.makeText(this, R.string.store_saved, Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun todayString(): String {
        val cal = Calendar.getInstance()
        return String.format(
            "%04d-%02d-%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    /** 切换栏目：三个页面里只显示目标页，其余隐藏（带 180ms 淡入） */
    private fun showTab(target: View) {
        listOf(pageProject, pageStore, pageMe).forEach {
            if (it !== target) it.visibility = View.GONE
        }
        if (target.visibility != View.VISIBLE) {
            target.alpha = 0f
            target.visibility = View.VISIBLE
            target.animate().alpha(1f).setDuration(180).start()
        }
    }

    override fun onDestroy() {
        uiHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
