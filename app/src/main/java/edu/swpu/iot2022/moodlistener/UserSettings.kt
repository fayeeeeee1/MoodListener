package edu.swpu.iot2022.moodlistener

data class UserSettings(
    val startTime: String = "09:00",
    val endTime: String = "21:00",
    val interval: Int = 180,
    val enabled: Boolean = true,
    val notificationText: String = "现在感觉怎么样？点击记录一下吧！"
) 