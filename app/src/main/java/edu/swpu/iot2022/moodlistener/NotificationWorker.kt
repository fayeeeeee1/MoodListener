package edu.swpu.iot2022.moodlistener

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.time.LocalTime

class NotificationWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    private val prefs by lazy {
        applicationContext.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)
    }

    private val notificationManager by lazy {
        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun standardizeTimeFormat(time: String): String {
        return try {
            val parts = time.split(":")
            if (parts.size != 2) return "09:00"
            
            val hour = parts[0].trim().toInt().coerceIn(0, 23).toString().padStart(2, '0')
            val minute = parts[1].trim().toInt().coerceIn(0, 59).toString().padStart(2, '0')
            "$hour:$minute"
        } catch (e: Exception) {
            "09:00"
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun isTimeInRange(startTime: LocalTime, endTime: LocalTime): Boolean {
        val currentTime = LocalTime.now()
        return when {
            startTime == endTime -> true  // 如果开始时间等于结束时间，认为全天都在范围内
            startTime < endTime -> currentTime in startTime..endTime  // 普通情况：9:00-21:00
            else -> {  // 跨天情况：23:00-6:00
                val midnight = LocalTime.MIDNIGHT
                val nextMidnight = LocalTime.MAX
                // 检查是否在开始时间到午夜之间，或者午夜到结束时间之间
                currentTime in startTime..nextMidnight || currentTime in midnight..endTime
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun doWork(): Result {
        return try {
            // 检查是否在冷却时间内
            if (isInCooldown()) {
                return Result.success()
            }

            // 检查通知权限
            if (!checkNotificationPermission()) {
                return Result.success()
            }

            // 获取并验证时间设置
            val timeSettings = getTimeSettings() ?: return Result.failure()
            if (!isTimeInRange(timeSettings.first, timeSettings.second)) {
                return Result.success()
            }

            // 检查通知设置
            val settings = try {
                MoodDatabase(applicationContext).use { db ->
                    db.getUserSettings()
                }
            } catch (e: Exception) {
                return Result.failure()
            }
            
            if (!settings.enabled) {
                return Result.success()
            }

            // 发送通知
            return showNotificationSafely(settings.notificationText)
        } catch (e: Exception) {
            Result.failure()
        }
    }

    private fun isInCooldown(): Boolean {
        val lastNotificationTime = prefs.getLong("last_notification_time", 0)
        return System.currentTimeMillis() - lastNotificationTime < 30 * 60 * 1000
    }

    private fun checkNotificationPermission(): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                notificationManager.areNotificationsEnabled()
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                val channel = notificationManager.getNotificationChannel(CHANNEL_ID)
                channel?.importance != NotificationManager.IMPORTANCE_NONE
            }
            else -> true
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun getTimeSettings(): Pair<LocalTime, LocalTime>? {
        return try {
            val startTimeStr = inputData.getString("start_time") ?: "09:00"
            val endTimeStr = inputData.getString("end_time") ?: "21:00"
            
            val startTime = LocalTime.parse(standardizeTimeFormat(startTimeStr))
            val endTime = LocalTime.parse(standardizeTimeFormat(endTimeStr))
            
            Pair(startTime, endTime)
        } catch (e: Exception) {
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun showNotificationSafely(text: String): Result {
        return try {
            createNotificationChannel()
            showNotification(text)
            prefs.edit().putLong("last_notification_time", System.currentTimeMillis()).apply()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "心情记录提醒",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "提醒您记录当前的心情"
                enableLights(true)
                lightColor = Color.BLUE
                enableVibration(true)
                setShowBadge(false)  // 禁用角标
            }

            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(text: String) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("记录心情")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)  // 避免重复提醒
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "mood_reminder_channel"
        private const val NOTIFICATION_ID = 1
    }
} 