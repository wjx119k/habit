package com.example.habit

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONArray
import org.json.JSONObject

/**
 * 记忆卡片：正面问题、背面答案。
 *
 * 数据存本机（habit_flashcards），不上传。
 * 交互：点卡片翻面；左右按钮切换；右下角添加 / 删除。
 */
class FlashcardsActivity : AppCompatActivity() {

    private data class Card(val q: String, val a: String)

    private val cards = mutableListOf<Card>()
    private var index = 0
    private var flipped = false

    private lateinit var tvCard: TextView
    private lateinit var tvCounter: TextView
    private lateinit var cardView: MaterialCardView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_flashcards)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.flashRoot)) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }

        tvCard = findViewById(R.id.tvFlashFace)
        tvCounter = findViewById(R.id.tvFlashCounter)
        cardView = findViewById(R.id.cardFlashFace)

        findViewById<ImageButton>(R.id.btnFlashBack).setOnClickListener { finish() }

        cardView.setOnClickListener {
            if (cards.isEmpty()) return@setOnClickListener
            flipped = !flipped
            render()
        }

        findViewById<MaterialButton>(R.id.btnFlashPrev).setOnClickListener {
            if (cards.isEmpty()) return@setOnClickListener
            index = (index - 1 + cards.size) % cards.size
            flipped = false
            render()
        }
        findViewById<MaterialButton>(R.id.btnFlashNext).setOnClickListener {
            if (cards.isEmpty()) return@setOnClickListener
            index = (index + 1) % cards.size
            flipped = false
            render()
        }

        findViewById<MaterialButton>(R.id.btnFlashAdd).setOnClickListener { addCard() }
        findViewById<MaterialButton>(R.id.btnFlashDelete).setOnClickListener { deleteCard() }

        load()
        render()
    }

    private fun addCard() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val editQ = EditText(this).apply { hint = getString(R.string.flash_hint_question) }
        val editA = EditText(this).apply { hint = getString(R.string.flash_hint_answer) }
        container.addView(editQ)
        container.addView(editA)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.flash_add)
            .setView(container)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_confirm) { _, _ ->
                val q = editQ.text.toString().trim()
                val a = editA.text.toString().trim()
                if (q.isEmpty() || a.isEmpty()) {
                    Toast.makeText(this, R.string.flash_need_both, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                cards.add(Card(q, a))
                index = cards.size - 1
                flipped = false
                save()
                render()
            }
            .show()
    }

    private fun deleteCard() {
        if (cards.isEmpty()) return
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.flash_delete_confirm)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_confirm) { _, _ ->
                cards.removeAt(index)
                if (index >= cards.size) index = (cards.size - 1).coerceAtLeast(0)
                flipped = false
                save()
                render()
            }
            .show()
    }

    private fun render() {
        if (cards.isEmpty()) {
            tvCard.text = getString(R.string.flash_empty)
            tvCounter.text = "0 / 0"
            return
        }
        val c = cards[index]
        tvCard.text = if (flipped) c.a else c.q
        tvCounter.text = "${index + 1} / ${cards.size}"
        tvCard.gravity = Gravity.CENTER
    }

    // ---------------- 存储 ----------------

    private fun prefs() = getSharedPreferences("habit_flashcards", Context.MODE_PRIVATE)

    private fun load() {
        cards.clear()
        val raw = prefs().getString("cards", "") ?: ""
        if (raw.isBlank()) return
        runCatching {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                cards.add(Card(o.optString("q"), o.optString("a")))
            }
        }
    }

    private fun save() {
        val arr = JSONArray()
        cards.forEach { arr.put(JSONObject().put("q", it.q).put("a", it.a)) }
        prefs().edit().putString("cards", arr.toString()).apply()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
