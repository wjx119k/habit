package com.example.habit

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import org.json.JSONArray
import org.json.JSONObject

/**
 * 艾森豪威尔矩阵：按「重要 × 紧急」把任务分到四个格子。
 *
 *   重要 + 紧急   → 立刻做
 *   重要 + 不紧急 → 安排时间做
 *   紧急 + 不重要 → 尽量交给别人
 *   都不紧急     → 有空再说
 *
 * 每个格子：输入框 + 添加按钮 + 任务列表（点任务可删除）。
 * 数据存本机 habit_eisenhower。
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

    private val data = mutableMapOf<String, MutableList<String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_eisenhower)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.eisenRoot)) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }

        findViewById<ImageButton>(R.id.btnEisenBack).setOnClickListener { finish() }

        load()

        quadrants.forEach { q ->
            data.getOrPut(q.key) { mutableListOf() }

            val input = findViewById<EditText>(q.inputId)
            findViewById<MaterialButton>(q.addId).setOnClickListener {
                val text = input.text.toString().trim()
                if (text.isEmpty()) return@setOnClickListener
                data.getValue(q.key).add(text)
                input.setText("")
                save()
                render(q)
            }
            render(q)
        }
    }

    /** 重新渲染某个格子的任务列表 */
    private fun render(q: Quadrant) {
        val container = findViewById<LinearLayout>(q.containerId)
        container.removeAllViews()
        val items = data.getOrPut(q.key) { mutableListOf() }

        if (items.isEmpty()) {
            container.addView(TextView(this).apply {
                text = getString(R.string.eisen_empty)
                setTextColor(0xFF9AA4B2.toInt())
                textSize = 13f
                setPadding(0, dp(6), 0, dp(6))
            })
            return
        }

        items.forEachIndexed { i, task ->
            container.addView(TextView(this).apply {
                text = "· $task"
                textSize = 15f
                setTextColor(0xFFE6E8EC.toInt())
                setPadding(dp(4), dp(8), dp(4), dp(8))
                setOnClickListener { confirmDelete(q, i, task) }
            })
        }
    }

    private fun confirmDelete(q: Quadrant, index: Int, task: String) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setMessage(getString(R.string.eisen_delete_confirm, task))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_confirm) { _, _ ->
                data.getValue(q.key).removeAt(index)
                save()
                render(q)
            }
            .show()
    }

    // ---------------- 存储 ----------------

    private fun prefs() = getSharedPreferences("habit_eisenhower", Context.MODE_PRIVATE)

    private fun load() {
        val raw = prefs().getString("data", "") ?: ""
        if (raw.isBlank()) return
        runCatching {
            val obj = JSONObject(raw)
            quadrants.forEach { q ->
                val arr = obj.optJSONArray(q.key) ?: JSONArray()
                val list = mutableListOf<String>()
                for (i in 0 until arr.length()) list.add(arr.optString(i))
                data[q.key] = list
            }
        }
    }

    private fun save() {
        val obj = JSONObject()
        data.forEach { (k, v) ->
            val arr = JSONArray()
            v.forEach { arr.put(it) }
            obj.put(k, arr)
        }
        prefs().edit().putString("data", obj.toString()).apply()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
