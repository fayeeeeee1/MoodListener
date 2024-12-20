package edu.swpu.iot2022.moodlistener

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.text.SimpleDateFormat
import java.util.*

class MoodHistoryAdapter(
    private val onEditClick: (MoodEntry) -> Unit,
    private val onDeleteClick: (MoodEntry) -> Unit
) : ListAdapter<MoodEntry, MoodHistoryAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dateTimeText: TextView = view.findViewById(R.id.dateTimeText)
        val moodChipGroup: ChipGroup = view.findViewById(R.id.moodChipGroup)
        val noteText: TextView = view.findViewById(R.id.noteText)
        val editButton: MaterialButton = view.findViewById(R.id.editButton)
        val deleteButton: MaterialButton = view.findViewById(R.id.deleteButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_mood_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = getItem(position)
        val context = holder.itemView.context
        
        // 设置日期时间
        val dateFormat = SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.getDefault())
        holder.dateTimeText.text = dateFormat.format(Date(entry.timestamp))
        
        // 设置心情标签
        holder.moodChipGroup.removeAllViews()
        entry.moods.forEach { mood ->
            val chip = Chip(context).apply {
                text = mood
                isClickable = false
            }
            holder.moodChipGroup.addView(chip)
        }
        
        // 设置备注
        if (entry.note.isNotEmpty()) {
            holder.noteText.visibility = View.VISIBLE
            holder.noteText.text = entry.note
        } else {
            holder.noteText.visibility = View.GONE
        }
        
        // 设置按钮点击事件
        holder.editButton.setOnClickListener { onEditClick(entry) }
        holder.deleteButton.setOnClickListener { onDeleteClick(entry) }
    }

    private class DiffCallback : DiffUtil.ItemCallback<MoodEntry>() {
        override fun areItemsTheSame(oldItem: MoodEntry, newItem: MoodEntry): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MoodEntry, newItem: MoodEntry): Boolean {
            return oldItem == newItem
        }
    }
} 