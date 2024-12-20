package edu.swpu.iot2022.moodlistener

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import androidx.preference.PreferenceManager

/**
 * MoodListener - 心情记录应用
 * 
 * @author fayeeeeee1 (https://github.com/fayeeeeee1)
 * @version 1.0.0
 */
class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "[Notification] Received broadcast: action=${intent.action}")
        
        // 获取唤醒锁
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MoodListener:NotificationWakeLock"
        ).apply {
            acquire(10*60*1000L /*10 minutes*/)
        }
        
        try {
            // 获取用户设置
            val db = MoodDatabase(context)
            val settings = db.getUserSettings()
            Log.d(TAG, "[Notification] Settings: enabled=${settings.enabled}, interval=${settings.interval}, " +
                  "startTime=${settings.startTime}, endTime=${settings.endTime}")
            
            if (!settings.enabled) {
                Log.d(TAG, "[Notification] Notifications are disabled")
                return
            }
            
            // 检查是否在允许的时间范围内
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    val currentTime = LocalTime.now()
                    val startTime = LocalTime.parse(standardizeTimeFormat(settings.startTime))
                    val endTime = LocalTime.parse(standardizeTimeFormat(settings.endTime))
                    
                    Log.d(TAG, "[Notification] Time check: current=$currentTime, start=$startTime, end=$endTime")
                    
                    // 检查是否在时间范围内
                    val isInRange = if (startTime <= endTime) {
                        currentTime in startTime..endTime
                    } else {
                        // 处理跨午夜的情况
                        currentTime >= startTime || currentTime <= endTime
                    }
                    
                    if (!isInRange) {
                        Log.d(TAG, "[Notification] Current time is outside allowed range")
                        return
                    }
                } catch (e: DateTimeParseException) {
                    Log.e(TAG, "[Notification] Time parsing error: ${e.message}")
                    return
                }
            }
            
            Log.d(TAG, "[Notification] Creating notification...")
            
            // 创建通知渠道
            createNotificationChannel(context)
            
            // 创建打开应用的 Intent
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                PendingIntent.FLAG_IMMUTABLE
            )
            
            // 获取 SharedPreferences
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            
            // 从设置中读取自定义通知文字，如果没有设置则使用默认值
            val notificationText = sharedPreferences.getString(
                "notification_text",
                settings.notificationText // 使用数据库中保存的通知文本作为默认值
            ) ?: settings.notificationText
            
            // 构建通知
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(notificationText)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setVibrate(longArrayOf(0, 500, 200, 500))
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .build()
            
            // 发送通知
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)
            Log.d(TAG, "[Notification] Notification sent")
            
            // 设置下一次通知
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val nextIntent = Intent(context, NotificationReceiver::class.java).apply {
                action = "edu.swpu.iot2022.moodlistener.NOTIFICATION"
            }
            val nextPendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            val nextTriggerTime = System.currentTimeMillis() + (settings.interval * 60 * 1000L)
            try {
                if (settings.interval <= 1) {
                    alarmManager.setAlarmClock(
                        AlarmManager.AlarmClockInfo(nextTriggerTime, nextPendingIntent),
                        nextPendingIntent
                    )
                } else {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextTriggerTime,
                        nextPendingIntent
                    )
                }
                Log.d(TAG, "[Notification] Next notification scheduled for: ${java.util.Date(nextTriggerTime)}")
            } catch (e: SecurityException) {
                Log.e(TAG, "[Notification] Failed to schedule next notification: ${e.message}")
            }
        } finally {
            // 释放唤醒锁
            wakeLock.release()
        }
    }
    
    private fun standardizeTimeFormat(time: String): String {
        val parts = time.split(":")
        val hour = parts[0].trim().padStart(2, '0')
        val minute = parts[1].trim().padStart(2, '0')
        return "$hour:$minute"
    }
    
    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "心情���录提醒"
            val descriptionText = "定时提醒记录心情"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                enableLights(true)
                setShowBadge(true)
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "通知渠道已创建")
        }
    }
    
    companion object {
        const val CHANNEL_ID = "mood_reminder_channel"
        const val NOTIFICATION_ID = 1
        private const val TAG = "NotificationReceiver"
    }
}