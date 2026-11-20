package com.example.habit

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * 艾森豪威尔矩阵。
 *
 * 和「临时任务」是同一套数据：
 *   这里加的每一条，都会写进 TempTaskStore，并带上象限标记（quadrant）。
 *   所以：
 *     · 在矩阵里加的任务，会出现在计划页的「临时任务」里
 *     · 在计划页勾掉它，这里立刻显示为划掉状态
 *     · 分数由临时任务的规则统一管理（默认 3 分，可在计划页改）
 *
 * 为什么不各存一份？
 *   同一个任务被记录两次，用户永远搞不清"我到底做完没有"。
 */
class EisenhowerActivity : AppCompatActivity() {

    private class Quadrant(
        val key: String,
        val titleRes: Int,
        val containerId: Int,
        val inputId: Int,
        val addId: Int
    )

    private val quadrants = listOf(
        Quadrant("do", R.string.eisen_do, R.id.listDo, R.id.inputDo, R.id.btnAddDo),
        Quadrant("plan", R.string.eisen_plan, R.id.listPlan, R.id.inputPlan, R.id.btnAddPlan),
        Quadrant("delegate", R.string.eisen_delegate, R.id.listDelegate, R.id.inputDelegate, R.id.btnAddDelegate),
        Quadrant("drop", R.string.eisen_drop, R.id.listDrop, R.id.inputDrop, R.id.btnAddDrop)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_eisenhower)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.eisenRoot)) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }

        findViewById<ImageButton>(R.id.btnEisenBack).setOnClickListener { finish() }

        quadrants.forEach { q ->
            // 行内输入框改成弹窗里的输入，这里直接隐藏
            findViewById<EditText>(q.inputId).visibility = View.GONE
            findViewById<MaterialButton>(q.addId).apply {
                setText(R.string.eisen_add_task)
                setOnClickListener { showAddDialog(q) }
            }
        }

        refreshAll()
    }

    override fun onResume() {
        super.onResume()
        // 从计划页勾完任务回来，状态要跟着变
        refreshAll()
    }

    /**
     * 添加任务：一次把「做什么」和「值多少分」都问清楚。
     * 分数决定完成时加多少分，所以在建的时候就让用户定。
     */
    private fun showAddDialog(q: Quadrant) {
        val content = layoutInflater.inflate(R.layout.dialog_eisen_task, null)
        val editTitle = content.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editEisenTitle)
        val editPoints = content.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editEisenPoints)
        val layoutTitle = content.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.layoutEisenTitle)
        val layoutPoints = content.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.layoutEisenPoints)

        content.findViewById<android.widget.TextView>(R.id.tvEisenDialogSubtitle)
            .setText(getString(R.string.eisen_dialog_subtitle, getString(q.titleRes)))

        editPoints.setText(Points.format(DEFAULT_POINTS))
        editTitle.requestFocus()

        // 提醒时间：先选日期再选时间，选完显示在那一行上
        val tvRemind = content.findViewById<android.widget.TextView>(R.id.tvEisenRemind)
        var remindAt = 0L
        fun showRemindLabel() {
            tvRemind.text = if (remindAt <= 0L) {
                getString(R.string.task_remind_none)
            } else {
                val fmt = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
                getString(R.string.task_remind_set, fmt.format(java.util.Date(remindAt)))
            }
        }
        // 截止日期：只选日期，落在当天 23:59
        val tvDue = content.findViewById<android.widget.TextView>(R.id.tvEisenDue)
        var dueAt = 0L
        fun showDueLabel() {
            tvDue.text = if (dueAt <= 0L) {
                getString(R.string.task_due_none)
            } else {
                val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                getString(R.string.task_due_set, fmt.format(java.util.Date(dueAt)))
            }
        }
        tvDue.setOnClickListener {
            val cal = java.util.Calendar.getInstance()
            android.app.DatePickerDialog(
                this,
                { _, y, m, d ->
                    val c = java.util.Calendar.getInstance()
                    c.set(y, m, d, 23, 59, 0)
                    c.set(java.util.Calendar.MILLISECOND, 0)
                    dueAt = c.timeInMillis
                    showDueLabel()
                },
                cal.get(java.util.Calendar.YEAR),
                cal.get(java.util.Calendar.MONTH),
                cal.get(java.util.Calendar.DAY_OF_MONTH)
            ).show()
        }
        tvRemind.setOnClickListener {
            val cal = java.util.Calendar.getInstance()
            android.app.DatePickerDialog(
                this,
                { _, y, m, d ->
                    android.app.TimePickerDialog(
                        this,
                        { _, hh, mm ->
                            val c = java.util.Calendar.getInstance()
                            c.set(y, m, d, hh, mm, 0)
                            c.set(java.util.Calendar.MILLISECOND, 0)
                            if (c.timeInMillis > System.currentTimeMillis()) {
                                remindAt = c.timeInMillis
                                showRemindLabel()
                            } else {
                                android.widget.Toast.makeText(
                                    this, R.string.task_remind_past, android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        cal.get(java.util.Calendar.HOUR_OF_DAY),
                        cal.get(java.util.Calendar.MINUTE),
                        true
                    ).show()
                },
                cal.get(java.util.Calendar.YEAR),
                cal.get(java.util.Calendar.MONTH),
                cal.get(java.util.Calendar.DAY_OF_MONTH)
            ).show()
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.eisen_dialog_title)
            .setView(content)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.eisen_add) { _, _ ->
                val title = editTitle.text?.toString()?.trim().orEmpty()
                val points = Points.parseInput(editPoints.text?.toString())
                if (title.isEmpty()) {
                    layoutTitle.error = getString(R.string.eisen_error_title)
                    return@setPositiveButton
                }
                if (points == null || !Points.isValidPerAction(points)) {
                    layoutPoints.error = getString(
                        R.string.error_points_range, Points.MIN_PER_ACTION, Points.MAX_PER_ACTION
                    )
                    return@setPositiveButton
                }
                val tasks = TempTaskStore.load(this)
                val task = TempTask(
                    title = title,
                    points = points,
                    quadrant = q.key,
                    remindAt = remindAt,
                    dueAt = dueAt
                )
                tasks.add(task)
                TempTaskStore.save(this, tasks)
                // 有提醒时间就排一个系统闹钟（到点弹通知 + 响铃）
                if (remindAt > 0L) {
                    TempTaskAlarm.schedule(this, task.id, task.title, remindAt)
                }
                refreshAll()
            }
            .create()

        dialog.show()
    }

    private fun refreshAll() {
        val tasks = TempTaskStore.load(this)
        // 先按"自动紧急"规则改写象限，矩阵里展示的就是改写后的结果
        if (AutoUrgent.normalize(this, tasks)) {
            TempTaskStore.save(this, tasks)
        }
        quadrants.forEach { q -> render(q, tasks.filter { it.quadrant == q.key }) }
    }

    private fun render(q: Quadrant, items: List<TempTask>) {
        val container = findViewById<LinearLayout>(q.containerId)
        container.removeAllViews()

        if (items.isEmpty()) {
            container.addView(TextView(this).apply {
                text = getString(R.string.eisen_empty)
                setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
                textSize = 13f
                setPadding(0, dp(6), 0, dp(6))
            })
            return
        }

        items.forEach { task ->
            container.addView(TextView(this).apply {
                // 标题 + 截止日期（逾期用橙色）
                val base = "· " + task.title
                if (task.dueAt > 0L) {
                    val fmt = java.text.SimpleDateFormat("MM-dd", java.util.Locale.getDefault())
                    val overdue = !task.done && task.dueAt < System.currentTimeMillis()
                    val suffix = if (overdue) {
                        "  " + getString(R.string.task_due_overdue) + " " + fmt.format(java.util.Date(task.dueAt))
                    } else {
                        "  " + fmt.format(java.util.Date(task.dueAt))
                    }
                    val span = android.text.SpannableString(base + suffix)
                    span.setSpan(
                        android.text.style.ForegroundColorSpan(
                            if (overdue) 0xFFE8590C.toInt() else 0xFF8A8F99.toInt()
                        ),
                        base.length, span.length,
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    text = span
                } else {
                    text = base
                }
                textSize = 15f
                setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurface))
                setPadding(dp(4), dp(8), dp(4), dp(8))
                if (task.done) {
                    paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                    alpha = 0.45f
                }
                setOnClickListener { confirmRemove(task) }
            })
        }
    }

    /** 从矩阵里移除（同时从临时任务里删掉） */
    private fun confirmRemove(task: TempTask) {
        MaterialAlertDialogBuilder(this)
            .setMessage(getString(R.string.eisen_remove_confirm, task.title))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_confirm) { _, _ ->
                val tasks = TempTaskStore.load(this)
                tasks.removeAll { it.id == task.id }
                TempTaskStore.save(this, tasks)
                TempTaskAlarm.cancel(this, task.id)
                refreshAll()
            }
            .show()
    }

    /** 从当前主题里取颜色（纯白主题下也能看清） */
    private fun themeColor(attr: Int): Int {
        val ta = obtainStyledAttributes(intArrayOf(attr))
        val color = ta.getColor(0, if (isDarkTheme()) 0xFFE6E8EC.toInt() else 0xFF1B1B1F.toInt())
        ta.recycle()
        return color
    }

    private fun isDarkTheme(): Boolean {
        val mask = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return mask == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private companion object {
        /** 矩阵里新建的任务默认值，可在计划页的临时任务里看到并完成 */
        const val DEFAULT_POINTS = 3.0
    }
}





