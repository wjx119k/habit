package com.example.habit

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors

/** Steam 风格的成就弹窗：从下方滑入，3 秒后自动消失，不打断操作。 */
object AchievementPopup {

    fun show(context: Context, achievement: Achievement) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(true)

        val density = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val card = MaterialCardView(context).apply {
            radius = dp(18).toFloat()
            cardElevation = dp(8).toFloat()
            setCardBackgroundColor(
                MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerHigh)
            )
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP
                setMargins(dp(16), dp(88), dp(16), 0)
            }
        }

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }

        val icon = ImageView(context).apply {
            setImageResource(achievement.iconRes)
            imageTintList = android.content.res.ColorStateList.valueOf(
                MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary)
            )
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
        }
        row.addView(icon)

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(dp(14), 0, 0, 0)
        }

        column.addView(TextView(context).apply {
            text = context.getString(R.string.ach_popup_label)
            textSize = 12f
            setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary))
        })
        column.addView(TextView(context).apply {
            text = context.getString(achievement.titleRes)
            textSize = 17f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
        })
        column.addView(TextView(context).apply {
            text = context.getString(achievement.descRes)
            textSize = 12f
            setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
        })
        row.addView(column)

        card.addView(row)

        val root = FrameLayout(context).apply { setBackgroundColor(Color.TRANSPARENT) }
        root.addView(card)
        dialog.setContentView(root)

        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            setGravity(Gravity.TOP)
            attributes = attributes.apply { dimAmount = 0f }
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }

        card.translationY = -dp(120).toFloat()
        card.alpha = 0f
        dialog.show()
        card.animate().translationY(0f).alpha(1f).setDuration(280).start()

        card.postDelayed({
            card.animate().translationY(-dp(120).toFloat()).alpha(0f).setDuration(240).withEndAction {
                runCatching { dialog.dismiss() }
            }.start()
        }, 3000)
    }
}
