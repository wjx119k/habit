package com.example.habit

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

/**
 * 计划列表的适配器。
 *
 * 为什么从 LinearLayout 换成 RecyclerView？
 *   因为要实现"长按拖动排序"——ItemTouchHelper 只支持 RecyclerView。
 *   顺带还获得了复用机制，列表再长也不卡。
 */
class ProjectAdapter(
    private val onEdit: (Int) -> Unit,
    private val onAdd: (Int) -> Unit,
    private val onMinus: (Int) -> Unit,
    private val onGraduate: (Int) -> Unit = {}
) : RecyclerView.Adapter<ProjectAdapter.Holder>() {

    private val items = mutableListOf<Project>()
    private var pendingAnimation = false

    /** 整体替换数据；animate = true 时每条依次淡入 */
    fun submit(list: List<Project>, animate: Boolean) {
        items.clear()
        items.addAll(list)
        pendingAnimation = animate
        notifyDataSetChanged()
        pendingAnimation = false
    }

    fun list(): MutableList<Project> = items

    /** 拖动时交换两项 */
    fun moveItem(from: Int, to: Int) {
        if (from == to) return
        if (from !in items.indices || to !in items.indices) return
        val moved = items.removeAt(from)
        items.add(to, moved)
        notifyItemMoved(from, to)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_project, parent, false)
        return Holder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val project = items[position]
        val ctx = holder.itemView.context

        holder.name.text = project.name
        holder.belief.text = project.belief.ifBlank { ctx.getString(R.string.belief_empty) }
        holder.cardPoints.text = ctx.getString(R.string.card_points, Points.format(project.points))
        holder.reward.text = ctx.getString(R.string.points_reward, Points.format(project.rewardPoints))
        holder.penalty.text = ctx.getString(R.string.points_penalty, Points.format(project.penaltyPoints))

        holder.btnGraduate.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION) onGraduate(pos)
        }
        holder.btnEdit.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION) onEdit(pos)
        }
        holder.btnAddPoint.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION) onAdd(pos)
        }
        holder.btnMinusPoint.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION) onMinus(pos)
        }

        holder.itemView.alpha = 1f
        holder.itemView.translationY = 0f
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvName)
        val belief: TextView = view.findViewById(R.id.tvBelief)
        val cardPoints: TextView = view.findViewById(R.id.tvCardPoints)
        val reward: TextView = view.findViewById(R.id.tvReward)
        val penalty: TextView = view.findViewById(R.id.tvPenalty)
        val btnEdit: MaterialButton = view.findViewById(R.id.btnEdit)
        val btnGraduate: android.widget.ImageButton = view.findViewById(R.id.ivGraduate)
        val btnAddPoint: MaterialButton = view.findViewById(R.id.btnAddPoint)
        val btnMinusPoint: MaterialButton = view.findViewById(R.id.btnMinusPoint)
    }
}
