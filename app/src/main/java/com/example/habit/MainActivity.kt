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
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
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

    private lateinit var projectList: androidx.recyclerview.widget.RecyclerView
    private lateinit var projectAdapter: ProjectAdapter
    private val tempTasks = mutableListOf<TempTask>()
    private lateinit var emptyHint: TextView
    private lateinit var tvTotalPoints: TextView
    private lateinit var tvAvailablePoints: TextView
    private lateinit var tvStoreSummary: TextView
    private lateinit var storeList: LinearLayout
    private lateinit var redemptionList: LinearLayout
    private lateinit var tvNoRecord: TextView
    private lateinit var tvStoreEmpty: TextView
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var pageProject: View
    private lateinit var pageStore: View
    private lateinit var pageTools: View
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
        pageTools = findViewById(R.id.pageTools)
        pageMe = findViewById(R.id.pageMe)

        projectList = findViewById(R.id.projectList)
        projectAdapter = ProjectAdapter(
            onEdit = { index -> showProjectSheet(existing = projects[index], index = index) },
            onGraduate = { index -> graduateProject(index) },
            onAdd = { index -> onAddPoint(index) },
            onMinus = { index -> onMinusPoint(index) }
        )
        projectList.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        projectList.adapter = projectAdapter
        setupDragSort()
        emptyHint = findViewById(R.id.tvEmpty)
        tvTotalPoints = findViewById(R.id.tvTotalPoints)

        tvAvailablePoints = findViewById(R.id.tvAvailablePoints)
        tvStoreSummary = findViewById(R.id.tvStoreSummary)
        storeList = findViewById(R.id.storeList)
        redemptionList = findViewById(R.id.redemptionList)
        tvNoRecord = findViewById(R.id.tvNoRecord)
        tvStoreEmpty = findViewById(R.id.tvStoreEmpty)

        // Project 栏目下的"清空积分"

        // 商城里"添加奖励"
        findViewById<View>(R.id.btnAddReward).setOnClickListener {
            showStoreItemSheet(null)
        }

        projects.clear()
        projects.addAll(ProjectStore.load(this))
        renderProjects(animate = false)

        setupMePage()
        refreshAccountUi()

        // 右上角积分胶囊：跳到「商店」，顺手能花掉
        findViewById<View>(R.id.btnPointsPill).setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            bottomNav.selectedItemId = R.id.nav_store
        }

        // 右上角小头像：直接进「个人主页」
        findViewById<View>(R.id.btnAvatarTop).setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            startActivity(Intent(this, ProfileActivity::class.java))
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_in_right, android.R.anim.fade_out)
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
                R.id.nav_tools -> {
                    showTab(pageTools)
                    true
                }
                R.id.nav_me -> {
                    showTab(pageMe)
                    true
                }
                else -> false
            }
        }

        findViewById<View>(R.id.btnAddProject).setOnClickListener { showCreateChooser() }

        // 工具页三张卡片
        findViewById<View>(R.id.cardPomodoro).setOnClickListener {
            startActivity(Intent(this, PomodoroActivity::class.java))
        }
        findViewById<View>(R.id.cardFlashcards).setOnClickListener {
            startActivity(Intent(this, FlashcardsActivity::class.java))
        }
        findViewById<View>(R.id.cardEisenhower).setOnClickListener {
            startActivity(Intent(this, EisenhowerActivity::class.java))
        }

        // 临时任务
        findViewById<View>(R.id.btnClearDoneTemp).setOnClickListener { clearDoneTempTasks() }
        tempExpanded = prefsUi().getBoolean("temp_expanded", false)
        findViewById<View>(R.id.rowTempHeader).setOnClickListener {
            tempExpanded = !tempExpanded
            prefsUi().edit().putBoolean("temp_expanded", tempExpanded).apply()
            applyTempExpanded(true)
            if (tempExpanded) renderTempTasks()
        }
        renderTempTasks()

        // 启动时确保提醒通道存在，并清掉历史遗留的旧通道
        TempTaskAlarm.ensureChannel(this)

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
        val layoutReward = content.findViewById<TextInputLayout>(R.id.layoutReward)
        val layoutPenalty = content.findViewById<TextInputLayout>(R.id.layoutPenalty)
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
            if (existing.rewardPoints != 0.0) editReward.setText(Points.format(existing.rewardPoints))
            if (existing.penaltyPoints != 0.0) editPenalty.setText(Points.format(existing.penaltyPoints))
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
            // 加分/减分：支持一位小数，且有上下限（防止填 1000000000000 这种）
            val rewardValue = Points.parseInput(editReward.text?.toString())
            if (rewardValue == null || !Points.isValidPerAction(rewardValue)) {
                layoutReward.error = getString(R.string.error_points_range, Points.MIN_PER_ACTION, Points.MAX_PER_ACTION)
                editReward.requestFocus()
                return@setOnClickListener
            }
            layoutReward.error = null

            val penaltyValue = Points.parseInput(editPenalty.text?.toString()) ?: 0.0
            if (penaltyValue != 0.0 && !Points.isValidPerAction(penaltyValue)) {
                layoutPenalty.error = getString(R.string.error_points_range, Points.MIN_PER_ACTION, Points.MAX_PER_ACTION)
                editPenalty.requestFocus()
                return@setOnClickListener
            }
            layoutPenalty.error = null

            val reward = Points.round(rewardValue)
            val penalty = Points.round(penaltyValue)

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
            projects[index] = existing!!.copy(points = 0.0)
            ProjectStore.save(this, projects)
            recordHistory(
                getString(R.string.history_delete_plan, existing.name),
                -existing.points,
                existing.id,
                0.0
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
            message = getString(R.string.dialog_add_message, Points.format(project.rewardPoints))
        ) {
            val newStreak = project.streak + 1
            projects[index] = project.copy(
                points = Points.round(project.points + project.rewardPoints),
                streak = newStreak,
                bestStreak = maxOf(project.bestStreak, newStreak)
            )
            ProjectStore.save(this, projects)
            recordHistory(
                label = "${project.name} +${Points.format(project.rewardPoints)}",
                delta = project.rewardPoints,
                projectId = project.id,
                projectPoints = projects[index].points
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
            message = getString(R.string.dialog_minus_message, Points.format(project.penaltyPoints))
        ) {
            // 减分：积分扣掉，连续次数清零（"中途减分直接重来"）
            projects[index] = project.copy(
                points = Points.round(project.points - project.penaltyPoints),
                streak = 0
            )
            ProjectStore.save(this, projects)
            recordHistory(
                label = "${project.name} −${Points.format(project.penaltyPoints)}",
                delta = -project.penaltyPoints,
                projectId = project.id,
                projectPoints = projects[index].points
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
        newly.forEachIndexed { index, achievement ->
            window.decorView.postDelayed({
                AchievementPopup.show(this, achievement)
            }, (index * 3600).toLong())
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
        // 商品能否兑换也跟着积分变，顺手刷新一下商城
        renderStore()

        // 顶部总积分 = 所有项目积分之和 − 已兑换积分
        tvTotalPoints.text = Points.format(availablePoints())
        renderTempTasks()


        if (projects.isEmpty()) {
            emptyHint.visibility = View.VISIBLE
            projectList.visibility = View.GONE
            return
        }

        emptyHint.visibility = View.GONE
        projectList.visibility = View.VISIBLE

        projectAdapter.submit(projects.toList(), animate)
    }

    // ============================================================
    //  长按拖动排序
    // ============================================================

    private fun setupDragSort() {
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.adapterPosition
                val to = target.adapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false

                // 适配器和内存里的 projects 一起换位置，保证后面对得上
                projectAdapter.moveItem(from, to)
                val moved = projects.removeAt(from)
                projects.add(to, moved)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    // 手指按住的那一刻震一下
                    viewHolder?.itemView?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    viewHolder?.itemView?.let {
                        it.elevation = 12f * resources.displayMetrics.density
                        it.alpha = 0.95f
                        it.scaleX = 1.02f
                        it.scaleY = 1.02f
                    }
                }
            }

            override fun clearView(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ) {
                super.clearView(recyclerView, viewHolder)
                viewHolder.itemView.elevation = 0f
                viewHolder.itemView.alpha = 1f
                viewHolder.itemView.scaleX = 1f
                viewHolder.itemView.scaleY = 1f

                // 松手后把顺序存下来
                ProjectStore.save(this@MainActivity, projects)
                tvTotalPoints.text = Points.format(availablePoints())
        renderTempTasks()
            }
        }

        ItemTouchHelper(callback).attachToRecyclerView(projectList)
    }

    // ============================================================
    //  清空积分
    // ============================================================

    private fun availablePoints(): Double =
        Points.round(projects.sumOf { it.points } + TempTaskStore.bonus(this) - StoreStore.spentPoints(this))

    /**
     * 记一条积分变动历史。
     * 必须在 projects / 兑换记录都改完之后调用——它要用变动后的总积分。
     */
    private fun recordHistory(
        label: String,
        delta: Double,
        projectId: String = "",
        projectPoints: Double = -1.0
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
        // 任何一次积分变化都顺手同步到云端（未登录时内部会自动跳过）
    }

    /** 商店图标的底色轮换（之前误删了，这里补回来） */
    private val storeColors = listOf(
        0xFF6C8AE4L, 0xFFE46C8A, 0xFFE4A76C,
        0xFF6CE4A7, 0xFFA76CE4, 0xFF6CCFE4
    ).map { it.toInt() }

    private fun renderStore() {
        val bonus = TempTaskStore.bonus(this)
        val earned = Points.round(projects.sumOf { it.points } + bonus)
        val spent = StoreStore.spentPoints(this)
        val available = Points.round(earned - spent)

        tvAvailablePoints.text = Points.format(available)
        tvStoreSummary.text = getString(
            R.string.store_summary,
            Points.format(earned),
            Points.format(spent)
        )

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
                getString(R.string.store_cost, Points.format(item.cost))

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
                getString(R.string.points_penalty, Points.format(record.cost))
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
            .setMessage(getString(R.string.store_revoke_message, record.title, Points.format(record.cost)))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_confirm) { _, _ ->
                val ok = StoreStore.revoke(this, record)
                if (ok) {
                    recordHistory(getString(R.string.history_revoke, record.title), record.cost)
                    Toast.makeText(
                        this,
                        getString(R.string.store_revoke_done, Points.format(record.cost)),
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
            .setMessage(getString(R.string.store_confirm_message, Points.format(item.cost), item.title))
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
            val cost = Points.parseInput(editCost.text?.toString()) ?: 0.0

            if (title.isEmpty()) {
                layoutName.error = getString(R.string.store_error_name)
                editName.requestFocus()
                return@setOnClickListener
            }
            layoutName.error = null

            if (cost <= 0.0 || !Points.isValidCost(cost)) {
                layoutCost.error = getString(
                    R.string.error_cost_range,
                    Points.MIN_COST,
                    Points.MAX_COST
                )
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

    override fun onStop() {
        super.onStop()
        // 退到后台前把还没推的改动立刻发出去
    }

    override fun onResume() {
        super.onResume()
        // 从登录页返回时刷新昵称
        refreshAccountUi()

        // 从其它页面回来时，内存里的 projects 可能是旧的
        // （比如「清空成就」把连续次数归零了，这里必须重新读盘，否则
        //   一加分就会用过期的 bestStreak 立刻再解锁一次成就）
        val latest = ProjectStore.load(this)
        projects.clear()
        projects.addAll(latest)
        renderProjects(animate = false)
    }

    /** Me 页顶部资料卡：显示当前登录的用户名 */
    private fun refreshAccountUi() {
        val nameView = findViewById<TextView>(R.id.tvUserName)
        val hintView = findViewById<TextView>(R.id.tvLoginHint)
        val user = SupabaseAuth.displayName(this)
        if (user == null) {
            nameView.setText(R.string.me_user_name)
            hintView.setText(R.string.me_login_hint)
        } else {
            nameView.text = user
            hintView.setText(R.string.me_logged_in_hint)
        }
    }

    // ============================================================
    //  临时任务
    // ============================================================

        /** 临时任务区是否展开（记住用户选择） */
    private var tempExpanded = false

    private fun prefsUi() = getSharedPreferences("habit_ui", MODE_PRIVATE)

    /** 应用折叠状态：收起时隐藏列表并旋转箭头 */
    private fun applyTempExpanded(animate: Boolean) {
        val list = findViewById<android.widget.LinearLayout>(R.id.tempTaskList)
        val chevron = findViewById<android.widget.TextView>(R.id.ivTempChevron)
        val target = if (tempExpanded) 1f else 0f
        if (animate) {
            chevron.animate().rotation(tempExpandedHeadDeg()).setDuration(180).start()
        } else {
            chevron.rotation = tempExpandedHeadDeg()
        }
        list.visibility = if (tempExpanded) View.VISIBLE else View.GONE
        chevron.alpha = 1f
        @Suppress("UNUSED_EXPRESSION") target
    }

    private fun tempExpandedHeadDeg(): Float = if (tempExpanded) 90f else 0f
private fun renderTempTasks() {
        val list = findViewById<LinearLayout>(R.id.tempTaskList) ?: return
        list.removeAllViews()

        tempTasks.clear()
        tempTasks.addAll(TempTaskStore.load(this))

        // 按"自动紧急"规则改写象限（临近截止 → 紧急），改动写回本地
        if (AutoUrgent.normalize(this, tempTasks)) {
            TempTaskStore.save(this, tempTasks)
        }

        // 排序：未完成在前 → 紧急·重要 → 重要 → 紧急 → 都不 → 无属性
        // 排序：未完成在前 → 快到期的靠前（没设截止的排最后）→ 象限 → 创建时间
        tempTasks.sortWith(
            compareBy<TempTask> { it.done }
                .thenBy { if (it.dueAt > 0L) it.dueAt else Long.MAX_VALUE }
                .thenBy { quadrantOrder(it.quadrant) }
                .thenBy { it.createdAt }
        )

        val countView = findViewById<TextView>(R.id.tvTempCount)
        val undone = tempTasks.count { !it.done }
        // 收起时也能一眼看到"还有几件急事"：紧急未完成 > 0 就加 🔥 并变红
        // 紧急判定统一走 AutoUrgent：象限紧急 + 临近截止（天数可在设置里调）
        val urgentUndone = tempTasks.count { AutoUrgent.isUrgent(this, it) }
        countView.text = if (tempTasks.isEmpty()) {
            ""
        } else if (urgentUndone > 0) {
            "🔥 " + urgentUndone.toString() + "  " + undone.toString() + "/" + tempTasks.size.toString()
        } else {
            undone.toString() + "/" + tempTasks.size.toString()
        }
        countView.setTextColor(
            androidx.core.content.ContextCompat.getColor(
                this,
                if (urgentUndone > 0) R.color.temp_urgent else R.color.verify_hint_color
            )
        )

        if (tempTasks.isEmpty()) {
            list.visibility = View.GONE
            findViewById<View>(R.id.cardTempTasks).visibility = View.GONE
            return
        }
        findViewById<View>(R.id.cardTempTasks).visibility = View.VISIBLE
        applyTempExpanded(false)
        if (!tempExpanded) return
        list.visibility = View.VISIBLE

        tempTasks.forEach { task ->
            val row = layoutInflater.inflate(R.layout.item_temp_task, list, false)
            val title = row.findViewById<TextView>(R.id.tvTaskTitle)
            val points = row.findViewById<TextView>(R.id.tvTaskPoints)
            val check = row.findViewById<android.widget.ImageView>(R.id.ivTaskCheck)
            val dueView = row.findViewById<TextView>(R.id.tvTaskDue)

            // 截止日期：有就显示，逾期未完成标红
            if (task.dueAt > 0L) {
                val fmt = java.text.SimpleDateFormat("MM-dd", java.util.Locale.getDefault())
                val overdue = !task.done && task.dueAt < System.currentTimeMillis()
                dueView.text = if (overdue) {
                    getString(R.string.task_due_overdue) + " · " + fmt.format(java.util.Date(task.dueAt))
                } else {
                    getString(R.string.task_due_set, fmt.format(java.util.Date(task.dueAt)))
                }
                dueView.visibility = View.VISIBLE
                dueView.setTextColor(
                    androidx.core.content.ContextCompat.getColor(
                        this,
                        if (overdue) R.color.temp_urgent else R.color.verify_hint_color
                    )
                )
            } else {
                dueView.visibility = View.GONE
            }

            title.text = task.title
            points.text = "+" + Points.format(task.points)

            // 紧急 / 重要 属性（来自艾森豪威尔象限）
            val tag = row.findViewById<TextView>(R.id.tvTaskTag)
            val tagText = quadrantLabel(task.quadrant)
            if (tagText.isNullOrEmpty()) tag.visibility = View.GONE
            else {
                tag.text = tagText
                tag.visibility = View.VISIBLE
                tag.alpha = if (task.done) 0.5f else 1f
            }

            if (task.done) {
                // 已完成：划掉 + 变灰
                title.paintFlags = title.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                title.alpha = 0.5f
                points.alpha = 0.5f
                check.alpha = 0.9f
            } else {
                title.paintFlags = title.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
                title.alpha = 1f
                points.alpha = 1f
                check.alpha = 0.35f
            }

            row.setOnClickListener {
                if (!task.done) completeTempTask(task) else deleteTempTask(task)
            }
            // 长按：任何状态都能直接删除（未完成的也能删，不用先点成完成）
            row.setOnLongClickListener {
                it.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                confirmDeleteTempTask(task)
                true
            }
            list.addView(row)
        }
    }

    /**
     * 新建目标的统一入口。
     *
     * 为什么合并？
     *   两个区块各放一个"添加"按钮，用户要先想清楚"这算计划还是任务"；
     *   合成一个「＋ 新建目标」，点开再选，认知负担更小。
     */
    private fun showCreateChooser() {
        val content = layoutInflater.inflate(R.layout.dialog_create_goal, null)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.create_goal_title)
            .setView(content)
            .setNegativeButton(R.string.action_cancel, null)
            .create()

        content.findViewById<View>(R.id.choicePlan).setOnClickListener {
            dialog.dismiss()
            showProjectSheet(existing = null, index = -1)
        }
        content.findViewById<View>(R.id.choiceTemp).setOnClickListener {
            dialog.dismiss()
            // 临时任务统一从矩阵进：先分象限，再填分数
            startActivity(Intent(this, EisenhowerActivity::class.java))
        }
        dialog.show()
    }

    /**
     * 习惯"毕业"：用户认为这个习惯已经养成，确认后把它从列表里去掉。
     *
     * 不是"删除"——删除是放弃，毕业是达成，所以文案完全不同。
     */
    private fun graduateProject(index: Int) {
        val project = projects.getOrNull(index) ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.graduate_title)
            .setMessage(getString(R.string.graduate_confirm, project.name))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.graduate_action) { _, _ ->
                // 毕业后积分要保留：把他赚到的分转到 bonus 池里，
                // 否则从 projects 移除的一瞬间总分会掉下去。
                if (project.points != 0.0) TempTaskStore.addBonus(this, project.points)

                projects.removeAt(index)
                ProjectStore.save(this, projects)
                recordHistory(
                    getString(R.string.graduate_history, project.name),
                    0.0,
                    project.id,
                    0.0
                )
                renderProjects(animate = false)
                showGraduationCongrats(project.name, project.points)
            }
            .show()
    }

    /** 毕业祝贺 */
    private fun showGraduationCongrats(name: String, points: Double) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.graduate_congrats_title)
            .setMessage(getString(R.string.graduate_congrats_body, name, Points.format(points)))
            .setPositiveButton(R.string.graduate_congrats_ok, null)
            .show()
    }

    /** 误触关闭弹窗后，底部提示「点击返回」重新打开 */
    private fun showMistapSnackbar(onRetry: () -> Unit) {
        com.google.android.material.snackbar.Snackbar.make(
            findViewById(android.R.id.content),
            R.string.dialog_mistap,
            com.google.android.material.snackbar.Snackbar.LENGTH_LONG
        ).setAction(R.string.dialog_mistap_action) { onRetry() }.show()
    }

    /** 新建临时任务（可带上次输入，用于误触返回） */
    private fun showTempTaskDialog(prefillTitle: String = "", prefillPoints: String = "") {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val inputTitle = android.widget.EditText(this).apply {
            hint = getString(R.string.temp_task_hint_title)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        val inputPoints = android.widget.EditText(this).apply {
            hint = getString(R.string.temp_task_hint_points)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        if (prefillTitle.isNotEmpty()) inputTitle.setText(prefillTitle)
        if (prefillPoints.isNotEmpty()) inputPoints.setText(prefillPoints)
        container.addView(inputTitle)
        container.addView(inputPoints)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.temp_tasks_add)
            .setView(container)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_confirm) { _, _ ->
                val title = inputTitle.text.toString().trim()
                val value = Points.parseInput(inputPoints.text.toString())
                if (title.isEmpty() || value == null || !Points.isValidPerAction(value)) {
                    Toast.makeText(this, R.string.temp_task_invalid, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val tasks = TempTaskStore.load(this)
                tasks.add(TempTask(title = title, points = value))
                TempTaskStore.save(this, tasks)
                renderTempTasks()
                tvTotalPoints.text = Points.format(availablePoints())
            }
            .create()

        // 误触（点外部 / 返回键）关掉时，给一个"点击返回"
        dialog.setOnCancelListener {
            val t = inputTitle.text?.toString().orEmpty()
            val p = inputPoints.text?.toString().orEmpty()
            if (t.isNotBlank() || p.isNotBlank()) {
                showMistapSnackbar { showTempTaskDialog(t, p) }
            }
        }
        dialog.show()
    }

    /** 清除所有已完成任务（积分不回收） */
    private fun clearDoneTempTasks() {
        val tasks = TempTaskStore.load(this)
        val done = tasks.count { it.done }
        if (done == 0) return
        MaterialAlertDialogBuilder(this)
            .setMessage(getString(R.string.temp_tasks_clear_confirm, done))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_confirm) { _, _ ->
                tasks.removeAll { it.done }
                TempTaskStore.save(this, tasks)
                renderTempTasks()
            }
            .show()
    }

    /** 完成：划掉 + 加分 + 记流水 */
    private fun completeTempTask(task: TempTask) {
        val tasks = TempTaskStore.load(this)
        val index = tasks.indexOfFirst { it.id == task.id }
        if (index < 0) return
        tasks[index] = tasks[index].copy(done = true)
        TempTaskStore.save(this, tasks)

        TempTaskStore.addBonus(this, task.points)
        recordHistory(
            label = task.title + " " + Points.formatSigned(task.points),
            delta = task.points,
            projectId = "",
            projectPoints = -1.0
        )

        renderTempTasks()
        tvTotalPoints.text = Points.format(availablePoints())

        // 误触保护：立刻给一个「撤销」，不用去找删除入口
        com.google.android.material.snackbar.Snackbar.make(
            findViewById(android.R.id.content),
            getString(R.string.temp_task_done, Points.format(task.points)),
            com.google.android.material.snackbar.Snackbar.LENGTH_LONG
        ).setAction(R.string.temp_task_undo) { undoCompleteTempTask(task) }.show()
    }

    /** 撤销完成：分数退回，任务恢复成未完成 */
    private fun undoCompleteTempTask(task: TempTask) {
        val tasks = TempTaskStore.load(this)
        val index = tasks.indexOfFirst { it.id == task.id }
        if (index < 0) return
        tasks[index] = tasks[index].copy(done = false)
        TempTaskStore.save(this, tasks)

        // 把刚才加的分扣回去，并留一条反向流水，账才对得上
        TempTaskStore.addBonus(this, -task.points)
        recordHistory(
            label = getString(R.string.temp_task_undo_history, task.title, Points.format(task.points)),
            delta = -task.points,
            projectId = "",
            projectPoints = -1.0
        )

        renderTempTasks()
        tvTotalPoints.text = Points.format(availablePoints())
        Toast.makeText(this, R.string.temp_task_undone, Toast.LENGTH_SHORT).show()
    }

    /** 排序权重：数字越小越靠前 */
    private fun quadrantOrder(quadrant: String): Int = when (quadrant) {
        "do" -> 0
        "plan" -> 1
        "delegate" -> 2
        "drop" -> 3
        else -> 4
    }

    /** 象限 → 人话标签 */
    private fun quadrantLabel(quadrant: String): String? = when (quadrant) {
        "do" -> getString(R.string.tag_urgent_important)
        "plan" -> getString(R.string.tag_important)
        "delegate" -> getString(R.string.tag_urgent)
        "drop" -> getString(R.string.tag_neither)
        else -> null
    }

    /** 长按删除：未完成的也能删 */
    private fun confirmDeleteTempTask(task: TempTask) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.temp_task_delete_title)
            .setMessage(getString(R.string.temp_task_delete_confirm_long, task.title))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.temp_task_delete) { _, _ ->
                val tasks = TempTaskStore.load(this)
                tasks.removeAll { it.id == task.id }
                TempTaskStore.save(this, tasks)
                renderTempTasks()
            }
            .show()
    }

    /** 点已完成的任务：可以选择撤销完成，或者彻底删除 */
    private fun deleteTempTask(task: TempTask) {
        MaterialAlertDialogBuilder(this)
            .setMessage(getString(R.string.temp_task_delete_confirm, task.title))
            .setNegativeButton(R.string.action_cancel, null)
            .setNeutralButton(R.string.temp_task_undo_complete) { _, _ ->
                undoCompleteTempTask(task)
            }
            .setPositiveButton(R.string.action_confirm) { _, _ ->
                val tasks = TempTaskStore.load(this)
                tasks.removeAll { it.id == task.id }
                TempTaskStore.save(this, tasks)
                renderTempTasks()
            }
            .show()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

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
        listOf(pageProject, pageStore, pageTools, pageMe).forEach {
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









