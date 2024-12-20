package edu.swpu.iot2022.moodlistener

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.annotation.RequiresApi
import android.app.ActivityOptions
import java.util.*
import kotlinx.coroutines.*
import androidx.work.WorkManager
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkRequest
import java.util.concurrent.TimeUnit
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.workDataOf
import java.time.LocalTime
import android.view.ViewGroup
import android.content.Context
import android.app.NotificationManager
import android.view.View
import android.util.Log
import android.os.PowerManager

/**
 * MoodListener - 心情记录应用
 * 
 * @author fayeeeeee1 (https://github.com/fayeeeeee1)
 * @version 1.0.0
 */
class MainActivity : AppCompatActivity(), CoroutineScope {
    
    private val job = SupervisorJob()
    override val coroutineContext = Dispatchers.Main + job

    private lateinit var db: MoodDatabase
    private var currentTheme: String = "blue"
    private var isNotificationScheduled = false
    private lateinit var moodOptionsAdapter: MoodOptionsAdapter
    private var editingEntryId: Long? = null
    
    // 添加内存缓存
    private val viewCache = mutableMapOf<Int, View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 使用缓存的主题设置
        val cachedTheme = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .getString("cached_theme", "blue")
        setTheme(getThemeResourceId(cachedTheme!!))
        setContentView(R.layout.activity_main)
        
        // 异步初始化
        launch(Dispatchers.Default) {
            try {
                // 在后台线程初始化数据库
                withContext(Dispatchers.IO) {
                    db = MoodDatabase(this@MainActivity)
                    currentTheme = db.getTheme()
                }
                
                // 缓存主题设置
                withContext(Dispatchers.IO) {
                    getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                        .edit()
                        .putString("cached_theme", currentTheme)
                        .apply()
                }
                
                withContext(Dispatchers.Main) {
                    // 设置UI组件
                    setupUI()
                    
                    // 检查通知设置
                    checkNotificationSettings()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "初始化失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        job.cancel() // 取消所有协程
        super.onDestroy()
        // 清理资源
        viewCache.clear()
        
        // 解除数据库引用
        if (::db.isInitialized) {
            db.close()
        }
    }

    private fun setupUI() {
        try {
            // 设置工具栏
            setSupportActionBar(findViewById(R.id.toolbar))
            supportActionBar?.title = "记录心情"
            
            // 初始化适配器
            initializeAdapter()
            
            // 设置视图
            setupViews()
            
            // 设置底部导航
            setupBottomNavigation()
            
            // 如果是编辑模式，设置编辑模式
            if (intent.getBooleanExtra("EDIT_MODE", false)) {
                editingEntryId = intent.getLongExtra("ENTRY_ID", -1)
                setupEditMode()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "界面初始化失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun checkNotificationSettings() {
        launch(Dispatchers.IO) {
            try {
                val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                if (!prefs.getBoolean("notification_initialized", false)) {
                    val settings = db.getUserSettings()
                    withContext(Dispatchers.Main) {
                        if (settings.enabled) {
                            setupNotificationWork(settings)
                        }
                    }
                    prefs.edit().putBoolean("notification_initialized", true).apply()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "初始化通知设置失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun initializeAdapter() {
        moodOptionsAdapter = MoodOptionsAdapter()
        findViewById<RecyclerView>(R.id.moodOptionsRecyclerView).apply {
            layoutManager = FlexboxLayoutManager(context).apply {
                flexDirection = FlexDirection.ROW
                flexWrap = FlexWrap.WRAP
            }
            adapter = moodOptionsAdapter
        }
    }

    private fun setupViews() {
        val submitButton = findViewById<Button>(R.id.submitButton)
        val noteEditText = findViewById<EditText>(R.id.noteEditText)
        val moodCategoryGroup = findViewById<RadioGroup>(R.id.moodCategoryGroup)
        
        // 默认选中积极按钮
        moodCategoryGroup.check(R.id.positiveMoodRadio)
        moodOptionsAdapter.updateCategory("积极")
        
        moodCategoryGroup.setOnCheckedChangeListener { _, checkedId ->
            val category = when (checkedId) {
                R.id.positiveMoodRadio -> "积极"
                R.id.neutralMoodRadio -> "平静"
                R.id.negativeMoodRadio -> "消极"
                else -> null
            }
            category?.let { moodOptionsAdapter.updateCategory(it) }
        }

        submitButton.setOnClickListener {
            val selectedMoods = moodOptionsAdapter.getSelectedMoods()
            if (selectedMoods.isEmpty()) {
                Toast.makeText(this, "请至少选择一个心情", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val category = when (moodCategoryGroup.checkedRadioButtonId) {
                R.id.positiveMoodRadio -> "积极"
                R.id.neutralMoodRadio -> "平静"
                R.id.negativeMoodRadio -> "消极"
                else -> return@setOnClickListener
            }

            val note = noteEditText.text.toString().trim()
            val entry = MoodEntry(
                id = editingEntryId ?: 0,
                moods = selectedMoods.toList(),
                category = category,
                note = note
            )

            if (editingEntryId != null) {
                updateMoodEntry(entry)
            } else {
                submitMoodEntry(entry)
            }
        }

        // 只在 Android 13 及以上版本请求通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission()
        }
    }

    private fun setupEditMode() {
        supportActionBar?.title = "编辑心情"
        
        val moods = intent.getStringArrayExtra("ENTRY_MOODS") ?: emptyArray()
        val category = intent.getStringExtra("ENTRY_CATEGORY") ?: ""
        val note = intent.getStringExtra("ENTRY_NOTE") ?: ""

        val categoryGroup = findViewById<RadioGroup>(R.id.moodCategoryGroup)
        when (category) {
            "积极" -> categoryGroup.check(R.id.positiveMoodRadio)
            "平静" -> categoryGroup.check(R.id.neutralMoodRadio)
            "消极" -> categoryGroup.check(R.id.negativeMoodRadio)
        }

        findViewById<EditText>(R.id.noteEditText).setText(note)
        moodOptionsAdapter.setSelectedMoods(moods.toList())

        // 修改提交按钮文字
        val submitButton = findViewById<Button>(R.id.submitButton)
        submitButton.text = "保存修改"

        // 添加取消按钮
        val cancelButton = Button(this).apply {
            text = "取消"
            setOnClickListener { finish() }
            layoutParams = submitButton.layoutParams
        }
        (submitButton.parent as ViewGroup).addView(cancelButton)
    }

    private fun updateMoodEntry(entry: MoodEntry) {
        launch {
            try {
                withContext(Dispatchers.IO) {
                    db.updateMoodEntry(entry)
                }
                Toast.makeText(this@MainActivity, "修改已保存", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
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

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestNotificationPermission() {
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != 
            PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                1001
            )
        }
    }

    private fun scheduleNotification() {
        launch {
            try {
                val settings = withContext(Dispatchers.IO) {
                    db.getUserSettings()
                }
                if (settings.enabled) {
                    setupNotificationWork(settings)
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "设置通知失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun checkAndRequestPermissions() {
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        
        if (!alarmManager.canScheduleExactAlarms()) {
            val intent = Intent().apply {
                action = android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
            }
            startActivity(intent)
        }
        
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != 
            PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                1001
            )
        }
    }

    override fun onResume() {
        super.onResume()
        launch(Dispatchers.IO) {
            try {
                val newTheme = db.getTheme()
                if (newTheme != currentTheme) {
                    currentTheme = newTheme
                    withContext(Dispatchers.Main) {
                        recreate()
                    }
                }
            } catch (e: Exception) {
                // 处理异常
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SETTINGS_REQUEST_CODE && resultCode == RESULT_OK) {
            val notificationChanged = data?.getBooleanExtra("NOTIFICATION_CHANGED", false) ?: false
            val themeChanged = data?.getBooleanExtra("THEME_CHANGED", false) ?: false
            
            // 只在知设置发生变化时更新通知
            if (notificationChanged) {
                val settings = db.getUserSettings()
                if (settings.enabled) {
                    setupNotificationWork(settings)
                    isNotificationScheduled = true
                } else {
                    // 如果关闭了通知，取消所有通知工作
                    WorkManager.getInstance(applicationContext).cancelUniqueWork("mood_notification")
                    isNotificationScheduled = false
                }
            }
            
            // 只在主题发生变化时重建活动
            if (themeChanged) {
                currentTheme = db.getTheme()
                setTheme(getThemeResourceId(currentTheme))
                recreate()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && 
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    scheduleNotification()
                } else {
                    showPermissionExplanationDialog()
                }
            }
        }
    }

    private fun showPermissionExplanationDialog() {
        AlertDialog.Builder(this)
            .setTitle("需要通知权限")
            .setMessage("为了能够按时提醒您记录心情,应用需要通知权限")
            .setPositiveButton("去设置") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun submitMoodEntry(entry: MoodEntry) {
        launch {
            try {
                withContext(Dispatchers.IO) {
                    db.addMoodEntry(entry)
                }
                Toast.makeText(this@MainActivity, "心情已经记录好啦!", Toast.LENGTH_SHORT).show()
                clearInputs()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun clearInputs() {
        findViewById<RadioGroup>(R.id.moodCategoryGroup).clearCheck()
        findViewById<EditText>(R.id.noteEditText).text.clear()
        moodOptionsAdapter.clearSelection()
    }

    override fun recreate() {
        finish()
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        startActivity(intent)
    }

    private fun setupBottomNavigation() {
        findViewById<BottomNavigationView>(R.id.bottomNavigation).apply {
            selectedItemId = R.id.navigation_home
            
            setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.navigation_home -> true
                    R.id.navigation_history -> {
                        startActivity(
                            Intent(this@MainActivity, HistoryActivity::class.java)
                        )
                        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                        false
                    }
                    R.id.navigation_settings -> {
                        startActivityForResult(
                            Intent(this@MainActivity, SettingsActivity::class.java),
                            SETTINGS_REQUEST_CODE
                        )
                        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                        false
                    }
                    else -> false
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupNotificationWork(settings: UserSettings) {
        launch(Dispatchers.Default) {
            try {
                Log.d(TAG, "[Notification] Start setting up notification...")
                
                // 请求忽略电池优化
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                    if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                        withContext(Dispatchers.Main) {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:$packageName")
                            }
                            startActivity(intent)
                        }
                    }
                }
                
                // 取消已有的通知
                val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent = Intent(this@MainActivity, NotificationReceiver::class.java).apply {
                    action = "edu.swpu.iot2022.moodlistener.NOTIFICATION"
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    this@MainActivity,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.cancel(pendingIntent)
                Log.d(TAG, "[Notification] Cancelled existing notifications")
                
                // 如果通知已禁用，直接返回
                if (!settings.enabled) {
                    Log.d(TAG, "[Notification] Notifications are disabled")
                    isNotificationScheduled = false
                    return@launch
                }
                
                // 如果间隔为0分钟，立即发送一次通知
                if (settings.interval == 0) {
                    Log.d(TAG, "[Notification] Interval is 0 minutes, sending immediate notification")
                    withContext(Dispatchers.Main) {
                        sendBroadcast(intent)
                        isNotificationScheduled = true
                    }
                    return@launch
                }
                
                // 计算下一次通知的时间
                val initialDelay = calculateInitialDelay(settings)
                val triggerTime = System.currentTimeMillis() + initialDelay
                Log.d(TAG, "[Notification] Next notification time: ${java.util.Date(triggerTime)}, delay: ${initialDelay}ms")
                
                // 检查闹钟权限
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                    Log.d(TAG, "[Notification] No permission for exact alarms")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "请在系统设置中允许应用设置精确闹钟", Toast.LENGTH_LONG).show()
                        val settingsIntent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = Uri.fromParts("package", packageName, null)
                        }
                        startActivity(settingsIntent)
                    }
                    return@launch
                }
                
                try {
                    val intervalMillis = settings.interval * 60 * 1000L // 转换为毫秒
                    
                    // 获取唤醒锁
                    val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                    val wakeLock = powerManager.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "MoodListener:NotificationWakeLock"
                    ).apply {
                        acquire(10*60*1000L /*10 minutes*/)
                    }
                    
                    if (settings.interval <= 1) {
                        // 对于0-1分钟的间隔使用 setAlarmClock
                        Log.d(TAG, "[Notification] Using setAlarmClock for short interval")
                        alarmManager.setAlarmClock(
                            AlarmManager.AlarmClockInfo(triggerTime, pendingIntent),
                            pendingIntent
                        )
                    } else {
                        // 使用 setExactAndAllowWhileIdle 确保在设备休眠时也能触发
                        Log.d(TAG, "[Notification] Using setExactAndAllowWhileIdle for interval: ${settings.interval} minutes")
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerTime,
                            pendingIntent
                        )
                        
                        // 设置一个重复的闹钟作为备份
                        alarmManager.setAlarmClock(
                            AlarmManager.AlarmClockInfo(triggerTime + intervalMillis, pendingIntent),
                            pendingIntent
                        )
                    }
                    
                    // 释放唤醒锁
                    wakeLock.release()
                    
                    isNotificationScheduled = true
                    Log.d(TAG, "[Notification] Setup completed successfully")
                    
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            applicationContext, 
                            "通知已设置，下次提醒时间: ${java.util.Date(triggerTime)}", 
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: SecurityException) {
                    Log.e(TAG, "[Notification] Security error: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            applicationContext,
                            "设置通知失败，请检查应用权限",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "[Notification] Setup failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "设置通知失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun calculateInitialDelay(settings: UserSettings): Long {
        val currentTime = LocalTime.now()
        // 标准化时间格式
        val startTimeStr = standardizeTimeFormat(settings.startTime)
        val endTimeStr = standardizeTimeFormat(settings.endTime)
        val startTime = LocalTime.parse(startTimeStr)
        val endTime = LocalTime.parse(endTimeStr)
        
        return when {
            // 如果当前时间在范围内，立即开始
            currentTime in startTime..endTime -> 0L
            // 如果当前时间在结束时间之后，等到明天的开始时间
            currentTime > endTime -> {
                val tomorrow = startTime.plusHours(24)
                val duration = java.time.Duration.between(currentTime, tomorrow)
                duration.toMillis()
            }
            // 如果当前时间在开始时间之前，等到今天的开始时间
            else -> {
                val duration = java.time.Duration.between(currentTime, startTime)
                duration.toMillis()
            }
        }
    }

    private fun standardizeTimeFormat(time: String): String {
        // 处理时间格式，确保是 HH:mm 格式
        val parts = time.split(":")
        if (parts.size != 2) return "09:00"
        
        val hour = parts[0].trim().padStart(2, '0')
        val minute = parts[1].trim().padStart(2, '0')
        return "$hour:$minute"
    }

    companion object {
        const val PERMISSION_REQUEST_CODE = 1001
        const val SETTINGS_REQUEST_CODE = 1002
        const val MAX_MOOD_SELECTIONS = 3  // 最多选择3个心情
        private const val TAG = "MainActivity"  // 添加日志标签
    }

    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}