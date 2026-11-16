package com.example.habit

import android.os.Bundle
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.MaterialColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 历史记录页。
 *
 * 顶部：计划筛选（全部 / 某个计划）
 * 上半：对应的积分曲线——「全部」看总积分，选中某个计划看它自己的积分
 * 下半：对应的流水
 */
class HistoryActivity : AppCompatActivity() {

    /** 当前选中的计划 id，null 表示"全部" */
    private var selectedProjectId: String? = null

    private lateinit var chart: PointsChartView
    private lateinit var tvChartTotal: TextView
    private lateinit var tvChartRange: TextView
    private lateinit var tvChartSubtitle: TextView
    private lateinit var historyList: LinearLayout
    private lateinit var tvHistoryEmpty: TextView
    private lateinit var chipGroup: ChipGroup

    private val timeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    private val dayFormat = SimpleDateFormat("MM-dd", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        val root = findViewById<View>(R.id.historyRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        findViewById<ImageButton>(R.id.btnHistoryBack).setOnClickListener {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, R.anim.slide_out_right)
        }

        chart = findViewById(R.id.chartPoints)
        tvChartTotal = findViewById(R.id.tvChartTotal)
        tvChartRange = findViewById(R.id.tvChartRange)
        tvChartSubtitle = findViewById(R.id.tvChartSubtitle)
        historyList = findViewById(R.id.historyList)
        tvHistoryEmpty = findViewById(R.id.tvHistoryEmpty)
        chipGroup = findViewById(R.id.chipPlans)

        buildChips()
        render()
    }

    // ============================================================
    //  筛选 chip
    // ============================================================

    private fun buildChips() {
        chipGroup.removeAllViews()

        // 「全部」+ 每个计划一个 chip
        chipGroup.addView(makeChip(getString(R.string.history_all), null))
        ProjectStore.load(this).forEach { project ->
            chipGroup.addView(makeChip(project.name, project.id))
        }

        // 默认选中「全部」
        (chipGroup.getChildAt(0) as? Chip)?.isChecked = true
    }

    private fun makeChip(text: String, projectId: String?): Chip = Chip(this).apply {
        this.text = text
        isCheckable = true
        this.tag = projectId
        setOnCheckedChangeListener { chip, checked ->
            if (!checked) return@setOnCheckedChangeListener
            selectedProjectId = projectId
            render()
            // 选中项横向滚到可见位置
            val scroll = chip.parent?.parent as? HorizontalScrollView
            scroll?.smoothScrollTo((chip.left - 60).coerceAtLeast(0), 0)
        }
    }

    // ============================================================
    //  渲染
    // ============================================================

    private fun render() {
        val allRecords = HistoryStore.load(this)
        val plans = ProjectStore.load(this)
        val selectedPlan = plans.firstOrNull { it.id == selectedProjectId }

        // 选中某个计划时，只保留属于它的记录
        val records = if (selectedProjectId == null) {
            allRecords
        } else {
            allRecords.filter { it.projectId == selectedProjectId }
        }

        // ---------- 曲线 ----------
        val chartPoints: List<Pair<Long, Int>> = if (selectedPlan == null) {
            allRecords.map { it.time to it.total }
        } else {
            records.filter { it.projectPoints >= 0 }.map { it.time to it.projectPoints }
        }
        chart.setData(chartPoints)

        // 头部数字：全部看当前总积分，计划看该计划当前积分
        val headline = if (selectedPlan == null) {
            plans.sumOf { it.points } - StoreStore.spentPoints(this)
        } else {
            selectedPlan.points
        }
        tvChartTotal.text = headline.toString()

        tvChartSubtitle.text = if (selectedPlan == null) {
            getString(R.string.history_chart_subtitle)
        } else {
            getString(R.string.history_plan_subtitle, selectedPlan.name)
        }

        tvChartRange.text = if (chartPoints.size >= 2) {
            getString(
                R.string.history_chart_range,
                dayFormat.format(Date(chartPoints.first().first)),
                dayFormat.format(Date(chartPoints.last().first))
            )
        } else {
            ""
        }

        // ---------- 流水 ----------
        historyList.removeAllViews()
        tvHistoryEmpty.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
        historyList.visibility = if (records.isEmpty()) View.GONE else View.VISIBLE

        tvHistoryEmpty.text = if (selectedPlan == null) {
            getString(R.string.history_empty)
        } else {
            getString(R.string.history_plan_empty)
        }

        records.asReversed().forEachIndexed { index, record ->
            val row = layoutInflater.inflate(R.layout.item_history, historyList, false)

            row.findViewById<TextView>(R.id.tvRecordLabel).text = record.label
            row.findViewById<TextView>(R.id.tvRecordTime).text =
                timeFormat.format(Date(record.time)) +
                    " · " + getString(R.string.history_total_after, record.total)

            val deltaView = row.findViewById<TextView>(R.id.tvRecordDelta)
            deltaView.text = if (record.delta > 0) "+${record.delta}" else "${record.delta}"
            val color = if (record.delta > 0) {
                MaterialColors.getColor(deltaView, com.google.android.material.R.attr.colorPrimary)
            } else {
                MaterialColors.getColor(deltaView, com.google.android.material.R.attr.colorError)
            }
            deltaView.setTextColor(color)

            row.alpha = 0f
            row.translationY = 10f
            row.animate().alpha(1f).translationY(0f)
                .setDuration(200)
                .setStartDelay((index.coerceAtMost(12) * 30).toLong())
                .start()

            historyList.addView(row)
        }
    }
}
