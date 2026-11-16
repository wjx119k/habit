package com.example.habit

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.color.MaterialColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 历史记录页。
 *
 * 上半部分：总积分变化的曲线（PointsChartView 手绘）
 * 下半部分：每一次加分 / 减分 / 兑换的流水
 */
class HistoryActivity : AppCompatActivity() {

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

        render()
    }

    private fun render() {
        val records = HistoryStore.load(this)

        // ---------- 曲线 ----------
        val chart = findViewById<PointsChartView>(R.id.chartPoints)
        val chartTotal = findViewById<TextView>(R.id.tvChartTotal)
        val chartRange = findViewById<TextView>(R.id.tvChartRange)

        chart.setData(records.map { it.time to it.total })

        val current = records.lastOrNull()?.total ?: 0
        chartTotal.text = current.toString()

        if (records.size >= 2) {
            val format = SimpleDateFormat("MM-dd", Locale.getDefault())
            val from = format.format(Date(records.first().time))
            val to = format.format(Date(records.last().time))
            chartRange.text = getString(R.string.history_chart_range, from, to)
        } else {
            chartRange.text = ""
        }

        // ---------- 流水 ----------
        val list = findViewById<LinearLayout>(R.id.historyList)
        val empty = findViewById<TextView>(R.id.tvHistoryEmpty)
        list.removeAllViews()

        empty.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
        list.visibility = if (records.isEmpty()) View.GONE else View.VISIBLE

        val timeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

        // 最新的在最上面
        records.asReversed().forEachIndexed { index, record ->
            val row = layoutInflater.inflate(R.layout.item_history, list, false)

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

            // 依次淡入，别一下子全冒出来
            row.alpha = 0f
            row.translationY = 10f
            row.animate().alpha(1f).translationY(0f)
                .setDuration(200)
                .setStartDelay((index.coerceAtMost(12) * 30).toLong())
                .start()

            list.addView(row)
        }
    }
}
