package edu.swpu.iot2022.moodlistener

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.Gravity
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import androidx.appcompat.app.AlertDialog
import androidx.work.WorkManager
import android.app.ActivityOptions
import android.app.ProgressDialog
import android.os.Handler
import android.os.Looper
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*
import androidx.preference.PreferenceManager

/**
 * MoodListener - 心情记录应用
 * 
 * @author fayeeeeee1 (https://github.com/fayeeeeee1)
 * @version 1.0.0
 */
class SettingsActivity : AppCompatActivity() {
    private lateinit var db: MoodDatabase
    
    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 首先初始化数据库
        db = MoodDatabase(this)
        // 获取并应用当前主题
        val currentTheme = db.getTheme()
        setTheme(getThemeResourceId(currentTheme))
        // 设置布局
        setContentView(R.layout.activity_settings)
        
        // 设置工具栏
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        val settings = db.getUserSettings()
        
        // 初始化UI组件
        val startTimePicker = findViewById<TimePicker>(R.id.startTimePicker)
        val endTimePicker = findViewById<TimePicker>(R.id.endTimePicker)
        val intervalEditText = findViewById<EditText>(R.id.intervalEditText)
        val enabledSwitch = findViewById<Switch>(R.id.enabledSwitch)
        val notificationTextEdit = findViewById<EditText>(R.id.notificationTextEdit)
        val themeSpinner = findViewById<Spinner>(R.id.themeSpinner)
        val exportFormatSpinner = findViewById<Spinner>(R.id.exportFormatSpinner)
        val nightModeSwitch = findViewById<Switch>(R.id.nightModeSwitch)
        
        // 从 SharedPreferences 加载已保存的通知文本
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val savedNotificationText = sharedPreferences.getString(
            "notification_text",
            settings.notificationText // 如果 SharedPreferences 中没有，则使用数据库中的值
        )
        
        // 设置通知文本
        notificationTextEdit.setText(savedNotificationText)
        
        // 设置当前值
        val startTime = settings.startTime.split(":")
        startTimePicker.hour = startTime[0].toInt()
        startTimePicker.minute = startTime[1].toInt()
        
        val endTime = settings.endTime.split(":")
        endTimePicker.hour = endTime[0].toInt()
        endTimePicker.minute = endTime[1].toInt()
        
        // 设置时间间隔输入框
        intervalEditText.setText(settings.interval.toString())
        intervalEditText.hint = "请输入分钟数（默认180分钟）"
        
        // 监听间隔输入变化
        intervalEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val input = intervalEditText.text.toString()
                if (input.isEmpty()) {
                    intervalEditText.setText("180")
                } else {
                    try {
                        val interval = input.toInt()
                        if (interval < 0) {
                            intervalEditText.setText("180")
                            Toast.makeText(this, "间隔时间不能为负数", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: NumberFormatException) {
                        intervalEditText.setText("180")
                        Toast.makeText(this, "请输入有效的数字", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        
        enabledSwitch.isChecked = settings.enabled
        
        // 设置主题选项
        val themes = arrayOf("蓝色", "粉色", "绿色", "灰色")
        themeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, themes)
        
        // 设置导出格式选项
        val exportFormats = arrayOf("CSV", "TXT", "JSON")
        exportFormatSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, exportFormats)
        
        // 设置当前主题选中状态
        val currentThemeIndex = when(currentTheme) {
            "blue" -> 0
            "pink" -> 1
            "green" -> 2
            "gray" -> 3
            else -> 0
        }
        themeSpinner.setSelection(currentThemeIndex)
        
        // 设置当前夜间模式状态
        nightModeSwitch.isChecked = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES
        
        // 处理夜间模式切换
        nightModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            val nightMode = if (isChecked) {
                AppCompatDelegate.MODE_NIGHT_YES
            } else {
                AppCompatDelegate.MODE_NIGHT_NO
            }
            
            // 保存夜间模式设置
            getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                .edit()
                .putInt("night_mode", nightMode)
                .apply()
            
            AppCompatDelegate.setDefaultNightMode(nightMode)
            
            // 使用Handler延迟重建活动，避免闪烁
            Handler(Looper.getMainLooper()).postDelayed({
                recreate()
            }, 100)
        }
        
        // 设置时间选择器的启用状态
        startTimePicker.isEnabled = settings.enabled
        endTimePicker.isEnabled = settings.enabled
        
        // 处理开关状态变化
        enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            startTimePicker.isEnabled = isChecked
            endTimePicker.isEnabled = isChecked
        }
        
        // 添加导出按钮点击事件
        findViewById<Button>(R.id.exportButton).setOnClickListener {
            when (exportFormatSpinner.selectedItem.toString()) {
                "CSV" -> exportMoodData("csv")
                "TXT" -> exportMoodData("txt")
                "JSON" -> exportMoodData("json")
            }
        }
        
        // 保存按钮点击事件
        findViewById<Button>(R.id.saveButton).setOnClickListener {
            try {
                // 验证输入
                val interval = intervalEditText.text.toString().toIntOrNull()
                if (interval == null || interval <= 0) {
                    Toast.makeText(this, "请输入有效的时间间隔", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val notificationText = notificationTextEdit.text.toString()
                if (notificationText.isBlank()) {
                    Toast.makeText(this, "通知提醒文字不能为空", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val oldSettings = db.getUserSettings()
                val oldTheme = db.getTheme()
                
                val progressDialog = ProgressDialog(this).apply {
                    setMessage("正在保存设置...")
                    setCancelable(false)
                    show()
                }
                
                lifecycleScope.launch {
                    try {
                        // 保存主题设置
                        val selectedTheme = when(themeSpinner.selectedItemPosition) {
                            0 -> "blue"
                            1 -> "pink"
                            2 -> "green"
                            3 -> "gray"
                            else -> "blue"
                        }

                        // 检查主题是否发生变化
                        val themeChanged = oldTheme != selectedTheme
                        
                        // 保存通知设置到数据库
                        val newSettings = UserSettings(
                            startTime = "${startTimePicker.hour}:${startTimePicker.minute}",
                            endTime = "${endTimePicker.hour}:${endTimePicker.minute}",
                            interval = interval,
                            enabled = enabledSwitch.isChecked,
                            notificationText = notificationText
                        )

                        withContext(Dispatchers.IO) {
                            db.updateUserSettings(newSettings)
                            if (themeChanged) {
                                db.updateTheme(selectedTheme)
                            }
                            
                            // 同时保存到 SharedPreferences
                            PreferenceManager.getDefaultSharedPreferences(this@SettingsActivity)
                                .edit()
                                .putString("notification_text", notificationText)
                                .apply()
                        }

                        // 如果关闭了通知，立即取消所有通知工作
                        if (!newSettings.enabled) {
                            WorkManager.getInstance(applicationContext).cancelUniqueWork("mood_notification")
                        }

                        progressDialog.dismiss()
                        Toast.makeText(this@SettingsActivity, "设置已保存", Toast.LENGTH_SHORT).show()
                        
                        // 设置返回结果
                        val resultIntent = Intent().apply {
                            putExtra("NOTIFICATION_CHANGED", true)
                            putExtra("THEME_CHANGED", themeChanged)
                        }
                        setResult(RESULT_OK, resultIntent)
                        finish()
                        
                    } catch (e: Exception) {
                        progressDialog.dismiss()
                        Toast.makeText(this@SettingsActivity, "保存设置失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this, "保存设置失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        
        // 添加 About 按钮点击事件
        findViewById<Button>(R.id.aboutButton).setOnClickListener {
            showAboutDialog()
        }
        
        setupBottomNavigation()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
    
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun exportMoodData(format: String) {
        try {
            val content = when (format.toLowerCase()) {
                "csv" -> exportAsCSV()
                "txt" -> exportAsTXT()
                "json" -> exportAsJSON()
                else -> exportAsCSV()
            }
            
            if (content.isBlank()) {
                Toast.makeText(this, "没有可导出的数据", Toast.LENGTH_SHORT).show()
                return
            }
            
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())
            val fileName = "心情记录_${timestamp}.${format.toLowerCase()}"
            val contentUri = saveFileToDownloads(fileName, content)
            
            // 显示成功提示
            Toast.makeText(this, "数据已导出到：$fileName", Toast.LENGTH_LONG).show()
            
            // 询问是否分享文件
            AlertDialog.Builder(this)
                .setTitle("导出成功")
                .setMessage("数据已导出到：$fileName\n是否要分享该文件？")
                .setPositiveButton("分享") { _, _ ->
                    shareFile(contentUri, fileName)
                }
                .setNegativeButton("关闭", null)
                .show()
            
        } catch (e: Exception) {
            Toast.makeText(this, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }
    
    private fun exportAsCSV(): String {
        val entries = db.getAllMoodEntries()
        val csv = StringBuilder()
        // 添加CSV表头，包含所有字段
        csv.append("时间,心情,类别,备注\n")
        
        val dateFormat = SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss", Locale.CHINA)
        entries.forEach { entry ->
            // 确保CSV格式正确，处理可能包含逗号的字段
            val timestamp = dateFormat.format(Date(entry.timestamp))
            val moods = entry.moods.joinToString("|")
            val category = entry.category ?: ""
            val note = entry.note?.replace(",", "，") ?: "" // 将英文逗号替换为中文逗号，避免CSV解析错误
            
            csv.append("\"$timestamp\",\"$moods\",\"$category\",\"$note\"\n")
        }
        
        return csv.toString()
    }
    
    private fun exportAsTXT(): String {
        val entries = db.getAllMoodEntries()
        val txt = StringBuilder()
        val dateFormat = SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss", Locale.CHINA)
        
        entries.forEach { entry ->
            txt.append("━━━━━━━━━━━━━━━━━━━━━━\n")
            txt.append("时间：${dateFormat.format(Date(entry.timestamp))}\n")
            txt.append("心情：${entry.moods.joinToString("、")}\n")
            if (!entry.category.isNullOrEmpty()) {
                txt.append("类别：${entry.category}\n")
            }
            if (!entry.note.isNullOrEmpty()) {
                txt.append("备注：${entry.note}\n")
            }
            txt.append("\n")
        }
        
        if (entries.isEmpty()) {
            txt.append("暂无心情记录数据")
        }
        
        return txt.toString()
    }
    
    private fun exportAsJSON(): String {
        val entries = db.getAllMoodEntries()
        val dateFormat = SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss", Locale.CHINA)
        
        return """
        {
            "心情记录": {
                "总记录数": ${entries.size},
                "导出时间": "${dateFormat.format(Date())}",
                "记录列表": [
                    ${entries.joinToString(",\n                    ") { entry ->
                        """
                        {
                            "时间": "${dateFormat.format(Date(entry.timestamp))}",
                            "心情": ${entry.moods.map { "\"${it.escapeJson()}\"" }},
                            "类别": "${(entry.category ?: "").escapeJson()}",
                            "备注": "${(entry.note ?: "").escapeJson()}"
                        }
                        """.trimIndent()
                    }}
                ]
            }
        }
        """.trimIndent()
    }
    
    // 添加扩展函数处理 JSON 字符串转义
    private fun String.escapeJson(): String {
        return this.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
    }
    
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveFileToDownloads(fileName: String, content: String): Uri {
        val mimeType = when {
            fileName.endsWith(".csv") -> "text/csv"
            fileName.endsWith(".txt") -> "text/plain"
            fileName.endsWith(".json") -> "application/json"
            else -> "text/plain"
        }
        
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val resolver = contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val itemUri = resolver.insert(collection, values)!!

        resolver.openOutputStream(itemUri).use { out ->
            out?.write(content.toByteArray())
        }

        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(itemUri, values, null, null)

        return itemUri
    }
    
    private fun shareFile(uri: Uri, fileName: String) {
        val mimeType = when {
            fileName.endsWith(".csv") -> "text/csv"
            fileName.endsWith(".txt") -> "text/plain"
            fileName.endsWith(".json") -> "application/json"
            else -> "text/plain"
        }
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "心情记录数据")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        val shareIntent = Intent.createChooser(intent, "分享数据")
        startActivity(shareIntent)
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
    
    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNav.selectedItemId = R.id.navigation_settings

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    startActivity(
                        Intent(this, MainActivity::class.java),
                        ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
                    )
                    finish()
                    true
                }
                R.id.navigation_history -> {
                    startActivity(
                        Intent(this, HistoryActivity::class.java),
                        ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
                    )
                    finish()
                    true
                }
                R.id.navigation_settings -> true
                else -> false
            }
        }
    }
    
    private fun showAboutDialog() {
        val message = """
            心情记录器 v1.0.0
            
            偶然发现，很多时候，自己都不了解自己的心理状态。
            回想起来，一整年可能大部分时间都不太开心，却没有意识到。
            我做这个应用，是想帮助自己了解自己的心情，
            希望通过这样记录一下能及时发现问题，调整状态，
            让自己的生活更加美好。
            https://github.com/fayeeeeee1/MoodListener
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("关于")
            .setMessage(message)
            .setPositiveButton("我知道了", null)
            .create()
            .apply {
                setOnShowListener {
                    val textView = findViewById<TextView>(android.R.id.message)
                    textView?.apply {
                        textSize = 16f
                        gravity = Gravity.CENTER
                        // 使文本可点击
                        movementMethod = android.text.method.LinkMovementMethod.getInstance()
                        // 将链接部分转换为可点击的链接
                        text = android.text.SpannableString(message).apply {
                            val urlPattern = android.util.Patterns.WEB_URL
                            val matcher = urlPattern.matcher(this)
                            while (matcher.find()) {
                                val url = matcher.group()
                                setSpan(
                                    android.text.style.URLSpan(url),
                                    matcher.start(),
                                    matcher.end(),
                                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                )
                            }
                        }
                    }
                }
            }
            .show()
    }
} 