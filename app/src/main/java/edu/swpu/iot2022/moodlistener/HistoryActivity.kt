package edu.swpu.iot2022.moodlistener

import edu.swpu.iot2022.moodlistener.MoodHistoryAdapter
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import edu.swpu.iot2022.moodlistener.MoodDatabase
import edu.swpu.iot2022.moodlistener.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.android.material.bottomnavigation.BottomNavigationView
import android.app.ActivityOptions

class HistoryActivity : AppCompatActivity() {
    private lateinit var db: MoodDatabase
    private lateinit var adapter: MoodHistoryAdapter
    private lateinit var emptyView: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = MoodDatabase(this)
        val currentTheme = db.getTheme()
        setTheme(getThemeResourceId(currentTheme))
        setContentView(R.layout.activity_history)
        
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "历史记录"
        
        emptyView = findViewById(R.id.emptyView)
        recyclerView = findViewById(R.id.historyRecyclerView)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        swipeRefresh.setOnRefreshListener {
            loadHistory()
        }
        
        setupRecyclerView()
        loadHistory()
        setupBottomNavigation()
    }

    override fun onResume() {
        super.onResume()
        loadHistory() // 从编辑页面返回时刷新数据
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        adapter = MoodHistoryAdapter(
            onEditClick = { entry -> showEditDialog(entry) },
            onDeleteClick = { entry -> showDeleteDialog(entry) }
        )
        
        recyclerView.adapter = adapter
    }

    private fun loadHistory() {
        scope.launch {
            try {
                swipeRefresh.isRefreshing = true
                val history = withContext(Dispatchers.IO) {
                    db.getAllMoodEntries()
                }
                adapter.submitList(history)
                
                if (history.isEmpty()) {
                    emptyView.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    emptyView.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                Toast.makeText(this@HistoryActivity, "加载失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun showDeleteDialog(entry: MoodEntry) {
        AlertDialog.Builder(this)
            .setTitle("删除记录")
            .setMessage("确定要删除这条心情记录吗？")
            .setPositiveButton("删除") { _, _ ->
                deleteEntry(entry)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteEntry(entry: MoodEntry) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    db.deleteMoodEntry(entry.id)
                }
                Toast.makeText(this@HistoryActivity, "删除成功", Toast.LENGTH_SHORT).show()
                loadHistory()
            } catch (e: Exception) {
                Toast.makeText(this@HistoryActivity, "删除失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showEditDialog(entry: MoodEntry) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("EDIT_MODE", true)
            putExtra("ENTRY_ID", entry.id)
            putExtra("ENTRY_MOODS", entry.moods.toTypedArray())
            putExtra("ENTRY_CATEGORY", entry.category)
            putExtra("ENTRY_NOTE", entry.note)
        }
        startActivity(intent)
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

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel() // 清理协程
    }

    private fun setupBottomNavigation() {
        findViewById<BottomNavigationView>(R.id.bottomNavigation).apply {
            selectedItemId = R.id.navigation_history
            
            setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.navigation_home -> {
                        startActivity(
                            Intent(this@HistoryActivity, MainActivity::class.java),
                            ActivityOptions.makeCustomAnimation(
                                this@HistoryActivity,
                                android.R.anim.fade_in,
                                android.R.anim.fade_out
                            ).toBundle()
                        )
                        finish()
                        false
                    }
                    R.id.navigation_history -> true
                    R.id.navigation_settings -> {
                        startActivity(
                            Intent(this@HistoryActivity, SettingsActivity::class.java),
                            ActivityOptions.makeCustomAnimation(
                                this@HistoryActivity,
                                android.R.anim.fade_in,
                                android.R.anim.fade_out
                            ).toBundle()
                        )
                        false
                    }
                    else -> false
                }
            }
        }
    }
} 