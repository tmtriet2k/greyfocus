package com.indiedev2k.greyfocus

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppEntry(val packageName: String, val label: String, val icon: Drawable?)

class AppPickerActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var adapter: AppListAdapter
    private var allApps: List<AppEntry> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_picker)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        prefs = Prefs(this)

        val search = findViewById<EditText>(R.id.etSearch)
        val progress = findViewById<ProgressBar>(R.id.progress)
        val recycler = findViewById<RecyclerView>(R.id.recycler)

        adapter = AppListAdapter(prefs.blockedPackages.toMutableSet()) { pkg, checked ->
            prefs.blockedPackages = if (checked) prefs.blockedPackages + pkg else prefs.blockedPackages - pkg
        }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        search.doOnTextChanged { text, _, _, _ -> filter(text?.toString().orEmpty()) }

        lifecycleScope.launch {
            allApps = withContext(Dispatchers.IO) { loadLaunchableApps() }
            progress.visibility = View.GONE
            filter(search.text.toString())
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun filter(query: String) {
        val q = query.trim().lowercase()
        val list = if (q.isEmpty()) allApps else allApps.filter {
            it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
        }
        adapter.submit(list)
    }

    private fun loadLaunchableApps(): List<AppEntry> {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val resolved = pm.queryIntentActivities(intent, 0)
        val selected = prefs.blockedPackages
        return resolved
            .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm)) }
            .distinctBy { it.packageName }
            .filter { it.packageName != packageName }
            .sortedWith(compareBy<AppEntry> { it.packageName !in selected }.thenBy { it.label.lowercase() })
    }
}
