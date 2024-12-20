package edu.swpu.iot2022.moodlistener

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView

class MoodOptionsAdapter : RecyclerView.Adapter<MoodOptionsAdapter.ViewHolder>() {
    private val selectedMoods = mutableSetOf<String>()
    private var currentCategory = "积极"
    private val moodOptions = mapOf(
        "积极" to listOf(
            "😊 开心" to "开心",
            "🎉 兴奋" to "兴奋",
            "😌 放松" to "放松",
            "😃 满足" to "满足",
            "🙏 感激" to "感激",
            "✨ 充满希望" to "充满希望",
            "💪 自信" to "自信",
            "🕊️ 平和" to "平和"
        ),
        "平静" to listOf(
            "😶 平静" to "平静",
            "🎯 专注" to "专注",
            "🤔 思考" to "思考",
            "😐 冷静" to "冷静",
            "🧘 淡定" to "淡定",
            "🌅 安宁" to "安宁",
            "☺️ 舒适" to "舒适",
            "🌸 自在" to "自在"
        ),
        "消极" to listOf(
            "😰 焦虑" to "焦虑",
            "😢 沮丧" to "沮丧",
            "😠 生气" to "生气",
            "😫 疲惫" to "疲惫",
            "😕 困惑" to "困惑",
            "😔 孤独" to "孤独",
            "😩 压力" to "压力",
            "😞 失望" to "失望"
        )
    )

    inner class ViewHolder(val checkBox: CheckBox) : RecyclerView.ViewHolder(checkBox)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val checkBox = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_mood_option, parent, false) as CheckBox
        return ViewHolder(checkBox)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (displayText, mood) = moodOptions[currentCategory]?.get(position) ?: return
        holder.checkBox.apply {
            text = displayText
            setOnCheckedChangeListener(null)
            isChecked = selectedMoods.contains(mood)
            setOnCheckedChangeListener { buttonView, isChecked ->
                if (isChecked && selectedMoods.size >= MAX_MOOD_SELECTIONS) {
                    buttonView.isChecked = false
                    Toast.makeText(context, 
                        "最多只能选择${MAX_MOOD_SELECTIONS}个心情", 
                        Toast.LENGTH_SHORT).show()
                } else if (isChecked) {
                    selectedMoods.add(mood)
                } else {
                    selectedMoods.remove(mood)
                }
            }
        }
    }

    override fun getItemCount(): Int = moodOptions[currentCategory]?.size ?: 0

    fun updateCategory(category: String) {
        selectedMoods.clear()
        currentCategory = category
        notifyDataSetChanged()
    }

    fun getSelectedMoods(): Set<String> = selectedMoods.toSet()

    fun clearSelection() {
        selectedMoods.clear()
        notifyDataSetChanged()
    }

    fun setSelectedMoods(moods: List<String>) {
        selectedMoods.clear()
        // 确保不超过最大选择数
        selectedMoods.addAll(moods.take(MAX_MOOD_SELECTIONS))
        notifyDataSetChanged()
    }

    companion object {
        const val MAX_MOOD_SELECTIONS = 3  // 最多选择3个心情
    }
}