package edu.swpu.iot2022.moodlistener

data class MoodEntry(
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val moods: List<String>,
    val category: String,
    val note: String = ""
)