package edu.swpu.iot2022.moodlistener

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.work.Configuration
import androidx.work.WorkManager
import android.content.Context

class MoodListenerApp : Application(), Configuration.Provider {
    override fun onCreate() {
        super.onCreate()
        try {
            val db = MoodDatabase(this)
            val theme = db.getTheme()
            setTheme(getThemeResourceId(theme))
            
            // 从SharedPreferences获取夜间模式设置
            val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val nightMode = prefs.getInt("night_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            AppCompatDelegate.setDefaultNightMode(nightMode)
        } catch (e: Exception) {
            // 如果数据库操作失败，使用默认主题
            setTheme(R.style.Theme_MoodListener_Blue)
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }
    
    private fun getThemeResourceId(theme: String): Int {
        return when (theme) {
            "blue" -> R.style.Theme_MoodListener_Blue
            "pink" -> R.style.Theme_MoodListener_Pink
            "green" -> R.style.Theme_MoodListener_Green
            "gray" -> R.style.Theme_MoodListener_Gray
            else -> R.style.Theme_MoodListener_Blue
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
} 