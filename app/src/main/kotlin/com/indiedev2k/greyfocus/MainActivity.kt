package com.indiedev2k.greyfocus

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private val handler = Handler(Looper.getMainLooper())
    private val statusTick = object : Runnable {
        override fun run() {
            renderLiveStatus()
            handler.postDelayed(this, 1000)
        }
    }

    private lateinit var switchEnabled: MaterialSwitch
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var btnAccessibility: MaterialButton
    private lateinit var tvPermissionStatus: TextView
    private lateinit var tvAdbCommand: TextView
    private lateinit var btnCopyAdb: MaterialButton
    private lateinit var tvStatusApp: TextView
    private lateinit var tvStatusUrl: TextView
    private lateinit var tvStatusGrey: TextView
    private lateinit var btnTestGrey: MaterialButton
    private lateinit var tvAppsSummary: TextView
    private lateinit var btnChooseApps: MaterialButton
    private lateinit var etSite: EditText
    private lateinit var btnAddSite: MaterialButton
    private lateinit var siteList: LinearLayout
    private lateinit var tvSitesEmpty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        switchEnabled = findViewById(R.id.switchEnabled)
        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus)
        btnAccessibility = findViewById(R.id.btnAccessibility)
        tvPermissionStatus = findViewById(R.id.tvPermissionStatus)
        tvAdbCommand = findViewById(R.id.tvAdbCommand)
        btnCopyAdb = findViewById(R.id.btnCopyAdb)
        tvStatusApp = findViewById(R.id.tvStatusApp)
        tvStatusUrl = findViewById(R.id.tvStatusUrl)
        tvStatusGrey = findViewById(R.id.tvStatusGrey)
        btnTestGrey = findViewById(R.id.btnTestGrey)
        tvAppsSummary = findViewById(R.id.tvAppsSummary)
        btnChooseApps = findViewById(R.id.btnChooseApps)
        etSite = findViewById(R.id.etSite)
        btnAddSite = findViewById(R.id.btnAddSite)
        siteList = findViewById(R.id.siteList)
        tvSitesEmpty = findViewById(R.id.tvSitesEmpty)

        switchEnabled.isChecked = prefs.enabled
        switchEnabled.setOnCheckedChangeListener { _, checked ->
            prefs.enabled = checked
            if (!checked) GreyscaleController.apply(this, false)
        }

        btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        tvAdbCommand.text = ADB_COMMAND
        btnCopyAdb.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("adb", ADB_COMMAND))
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
        }

        btnTestGrey.setOnClickListener {
            if (!GreyscaleController.hasPermission(this)) {
                Toast.makeText(this, R.string.test_grey_missing_permission, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            GreyscaleController.apply(this, true)
            handler.postDelayed({ GreyscaleController.apply(this, false) }, 3000)
        }

        btnChooseApps.setOnClickListener {
            startActivity(Intent(this, AppPickerActivity::class.java))
        }

        btnAddSite.setOnClickListener { addSite() }
        etSite.setOnEditorActionListener { _, _, _ -> addSite(); true }
    }

    override fun onResume() {
        super.onResume()
        renderSetup()
        if (shouldRunWatchdog()) {
            KeepAliveService.start(this)
        } else {
            KeepAliveService.stop(this)
        }
        renderApps()
        renderSites()
        handler.post(statusTick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(statusTick)
    }

    private fun addSite() {
        val raw = etSite.text.toString()
        if (prefs.addSite(raw)) {
            etSite.setText("")
            renderSites()
        } else {
            Toast.makeText(this, R.string.site_invalid, Toast.LENGTH_SHORT).show()
        }
    }

    private fun renderSetup() {
        val accessibilityOn = isAccessibilityServiceEnabled()
        tvAccessibilityStatus.setText(
            if (accessibilityOn) R.string.accessibility_on else R.string.accessibility_off
        )
        tvAccessibilityStatus.alpha = if (accessibilityOn) 1f else 0.6f

        val permissionOn = GreyscaleController.hasPermission(this)
        tvPermissionStatus.setText(if (permissionOn) R.string.permission_on else R.string.permission_off)
        tvPermissionStatus.alpha = if (permissionOn) 1f else 0.6f
    }

    private fun renderLiveStatus() {
        if (!LiveStatus.serviceRunning) {
            tvStatusApp.setText(
                if (isAccessibilityServiceEnabled()) {
                    R.string.status_service_running_background
                } else {
                    R.string.status_service_stopped
                }
            )
            tvStatusUrl.text = ""
        } else {
            val pkg = LiveStatus.currentPackage
            tvStatusApp.text = getString(R.string.status_app, pkg?.let { labelFor(it) } ?: getString(R.string.none))
            tvStatusUrl.text = getString(R.string.status_url, LiveStatus.currentSite ?: getString(R.string.none))
        }
        tvStatusGrey.setText(
            if (GreyscaleController.isSystemGrey(this)) R.string.status_grey_on else R.string.status_grey_off
        )
    }

    private fun renderApps() {
        val labels = prefs.blockedPackages.map { labelFor(it) }.sortedBy { it.lowercase() }
        tvAppsSummary.text =
            if (labels.isEmpty()) getString(R.string.apps_empty) else labels.joinToString("\n")
    }

    private fun renderSites() {
        siteList.removeAllViews()
        val sites = prefs.sites
        tvSitesEmpty.visibility = if (sites.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        for (site in sites) {
            val row = layoutInflater.inflate(R.layout.item_site, siteList, false)
            row.findViewById<TextView>(R.id.tvSite).text = site
            row.findViewById<ImageButton>(R.id.btnRemove).setOnClickListener {
                prefs.removeSite(site)
                renderSites()
            }
            siteList.addView(row)
        }
    }

    private fun labelFor(packageName: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        packageName
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val component = ComponentName(this, FocusAccessibilityService::class.java)
        val globalAccessibilityEnabled = Settings.Secure.getInt(
            contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0
        )
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return AccessibilityServiceState.isEnabled(
            globalAccessibilityEnabled,
            enabled,
            setOf(component.flattenToString(), component.flattenToShortString()),
        )
    }

    private fun shouldRunWatchdog(): Boolean {
        val component = ComponentName(this, FocusAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return AccessibilityServiceState.shouldRunWatchdog(
            enabled,
            setOf(component.flattenToString(), component.flattenToShortString()),
        )
    }

    companion object {
        const val ADB_COMMAND =
            "adb shell pm grant com.indiedev2k.greyfocus android.permission.WRITE_SECURE_SETTINGS"
    }
}
