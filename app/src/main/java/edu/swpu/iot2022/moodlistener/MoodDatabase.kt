package edu.swpu.iot2022.moodlistener


import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.*

class MoodDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "mood_db"
        private const val DATABASE_VERSION = 3
        private const val TABLE_NAME = "mood_entries"
        private const val COLUMN_ID = "id"
        private const val COLUMN_TIMESTAMP = "timestamp"
        private const val COLUMN_MOODS = "moods"
        private const val COLUMN_CATEGORY = "category"
        private const val COLUMN_NOTE = "note"
        private const val TABLE_SETTINGS = "user_settings"
        private const val COLUMN_START_TIME = "start_time"
        private const val COLUMN_END_TIME = "end_time"
        private const val COLUMN_INTERVAL = "interval"
        private const val COLUMN_ENABLED = "enabled"
        private const val COLUMN_NOTIFICATION_TEXT = "notification_text"
        private const val COLUMN_THEME = "theme"
        private const val COLUMN_NIGHT_MODE = "night_mode"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_TIMESTAMP INTEGER,
                $COLUMN_MOODS TEXT,
                $COLUMN_CATEGORY TEXT,
                $COLUMN_NOTE TEXT
            )
        """.trimIndent()
        db.execSQL(createTable)

        val createSettingsTable = """
            CREATE TABLE $TABLE_SETTINGS (
                id INTEGER PRIMARY KEY,
                $COLUMN_START_TIME TEXT DEFAULT '09:00',
                $COLUMN_END_TIME TEXT DEFAULT '21:00',
                $COLUMN_INTERVAL INTEGER DEFAULT 180,
                $COLUMN_ENABLED INTEGER DEFAULT 1,
                $COLUMN_NOTIFICATION_TEXT TEXT DEFAULT '现在感觉怎么样？点击记录一下吧！',
                $COLUMN_THEME TEXT DEFAULT 'blue',
                $COLUMN_NIGHT_MODE INTEGER DEFAULT 0
            )
        """.trimIndent()
        db.execSQL(createSettingsTable)
        
        db.execSQL("""
            INSERT INTO $TABLE_SETTINGS 
            (id, $COLUMN_START_TIME, $COLUMN_END_TIME, $COLUMN_INTERVAL, $COLUMN_ENABLED, $COLUMN_NOTIFICATION_TEXT, $COLUMN_THEME) 
            VALUES (1, '09:00', '21:00', 180, 1, '现在感觉怎么样？点击记录一下吧！', 'blue')
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 3) {
            val backupTable = "mood_entries_backup"
            db.execSQL("ALTER TABLE $TABLE_NAME RENAME TO $backupTable")
            
            onCreate(db)
            
            db.execSQL("""
                INSERT INTO $TABLE_NAME ($COLUMN_ID, $COLUMN_TIMESTAMP, $COLUMN_MOODS, $COLUMN_CATEGORY, $COLUMN_NOTE)
                SELECT id, timestamp, mood, 
                CASE 
                    WHEN mood IN ('开心', '激动', '满足', '感激', '自信') THEN 'POSITIVE'
                    WHEN mood IN ('平静', '无聊', '一般') THEN 'NEUTRAL'
                    ELSE 'NEGATIVE'
                END,
                note FROM $backupTable
            """)
            
            db.execSQL("DROP TABLE $backupTable")
        }
    }

    fun addMoodEntry(entry: MoodEntry): Long {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_TIMESTAMP, entry.timestamp)
            put(COLUMN_MOODS, entry.moods.joinToString(","))
            put(COLUMN_CATEGORY, entry.category)
            put(COLUMN_NOTE, entry.note)
        }
        return db.insert(TABLE_NAME, null, values)
    }

    fun getUserSettings(): UserSettings {
        val db = readableDatabase
        val cursor = db.query(TABLE_SETTINGS, null, null, null, null, null, null)
        
        return cursor.use {
            if (it.moveToFirst()) {
                UserSettings(
                    startTime = it.getString(it.getColumnIndexOrThrow(COLUMN_START_TIME)),
                    endTime = it.getString(it.getColumnIndexOrThrow(COLUMN_END_TIME)),
                    interval = it.getInt(it.getColumnIndexOrThrow(COLUMN_INTERVAL)),
                    enabled = it.getInt(it.getColumnIndexOrThrow(COLUMN_ENABLED)) == 1
                )
            } else {
                UserSettings()
            }
        }
    }

    fun updateUserSettings(settings: UserSettings) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_START_TIME, settings.startTime)
            put(COLUMN_END_TIME, settings.endTime)
            put(COLUMN_INTERVAL, settings.interval)
            put(COLUMN_ENABLED, if (settings.enabled) 1 else 0)
            put(COLUMN_NOTIFICATION_TEXT, settings.notificationText)
        }
        db.update(TABLE_SETTINGS, values, "id = 1", null)
    }

    fun exportMoodData(): String {
        val db = readableDatabase
        val cursor = db.query(TABLE_NAME, null, null, null, null, null, "$COLUMN_TIMESTAMP DESC")
        
        return cursor.use { cursor ->
            val csv = StringBuilder()
            csv.append("时间,心情,备注\n")
            
            while (cursor.moveToNext()) {
                val timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP))
                val mood = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MOODS))
                val note = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NOTE))
                
                val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(Date(timestamp))
                
                csv.append("$date,$mood,$note\n")
            }
            csv.toString()
        }
    }

    fun getMoodStats(): Map<String, Int> {
        val db = readableDatabase
        val stats = mutableMapOf<String, Int>()
        
        val cursor = db.rawQuery("""
            SELECT $COLUMN_MOODS, COUNT(*) as count 
            FROM $TABLE_NAME 
            GROUP BY $COLUMN_MOODS
        """.trimIndent(), null)
        
        cursor.use {
            while (it.moveToNext()) {
                val mood = it.getString(0)
                val count = it.getInt(1)
                stats[mood] = count
            }
        }
        
        return stats
    }

    fun getAllMoodEntries(): List<MoodEntry> {
        val entries = mutableListOf<MoodEntry>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_NAME,
            null,
            null,
            null,
            null,
            null,
            "$COLUMN_TIMESTAMP DESC"
        )

        cursor.use {
            while (it.moveToNext()) {
                entries.add(MoodEntry(
                    id = it.getLong(it.getColumnIndexOrThrow(COLUMN_ID)),
                    timestamp = it.getLong(it.getColumnIndexOrThrow(COLUMN_TIMESTAMP)),
                    moods = it.getString(it.getColumnIndexOrThrow(COLUMN_MOODS)).split(","),
                    category = it.getString(it.getColumnIndexOrThrow(COLUMN_CATEGORY)),
                    note = it.getString(it.getColumnIndexOrThrow(COLUMN_NOTE))
                ))
            }
        }
        return entries
    }

    fun updateTheme(theme: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_THEME, theme)
        }
        db.update(TABLE_SETTINGS, values, "id = 1", null)
    }

    fun getTheme(): String {
        val db = readableDatabase
        val cursor = db.query(TABLE_SETTINGS, arrayOf(COLUMN_THEME), null, null, null, null, null)
        
        return cursor.use {
            if (it.moveToFirst()) {
                it.getString(it.getColumnIndexOrThrow(COLUMN_THEME))
            } else {
                "blue"
            }
        }
    }

    fun updateMoodEntry(entry: MoodEntry) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_MOODS, entry.moods.joinToString(","))
            put(COLUMN_CATEGORY, entry.category)
            put(COLUMN_NOTE, entry.note)
        }
        db.update(TABLE_NAME, values, "$COLUMN_ID = ?", arrayOf(entry.id.toString()))
    }

    fun deleteMoodEntry(id: Long) {
        val db = writableDatabase
        db.delete(TABLE_NAME, "$COLUMN_ID = ?", arrayOf(id.toString()))
    }

    fun getNightMode(): Boolean {
        val db = readableDatabase
        val cursor = db.query(TABLE_SETTINGS, arrayOf(COLUMN_NIGHT_MODE), null, null, null, null, null)
        
        return cursor.use {
            if (it.moveToFirst()) {
                it.getInt(it.getColumnIndexOrThrow(COLUMN_NIGHT_MODE)) == 1
            } else {
                false
            }
        }
    }

    fun updateNightMode(enabled: Boolean) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_NIGHT_MODE, if (enabled) 1 else 0)
        }
        db.update(TABLE_SETTINGS, values, "id = 1", null)
    }

    fun updateSettings(newSettings: UserSettings) {

    }
}