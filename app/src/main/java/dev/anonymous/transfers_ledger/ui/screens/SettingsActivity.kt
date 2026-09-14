package dev.anonymous.transfers_ledger.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import dev.anonymous.autostarter.AutoStartHelper
import dev.anonymous.autostarter.AutoStartResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.view.Menu
import android.view.MenuItem
import dev.anonymous.transfers_ledger.R
import dev.anonymous.transfers_ledger.app.TransfersLedgerApplication
import dev.anonymous.transfers_ledger.core.IntentUtils
import dev.anonymous.transfers_ledger.core.backup.BackupCodec
import dev.anonymous.transfers_ledger.databinding.ActivitySettingsBinding
import dev.anonymous.transfers_ledger.service.TrackingForegroundService
import dev.anonymous.transfers_ledger.ui.common.AppDialogs
import dev.anonymous.transfers_ledger.core.JawwalPayMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import dev.anonymous.transfers_ledger.ui.viewmodel.MainViewModel
import androidx.core.net.toUri

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var trackingSwitch: MaterialSwitch
    private var updatingSwitchFromState = false
    private val repository by lazy { (application as TransfersLedgerApplication).repository }
    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(application, repository)
    }
    private val createBackupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) writeBackup(uri)
    }
    private val restoreBackupLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmRestore(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        trackingSwitch = binding.root.findViewById(R.id.trackingSwitch)

        binding.backButton.setOnClickListener { finish() }
        binding.notificationSettingsButton.setOnClickListener {
            startActivity(IntentUtils.getNotificationListenerSettingsIntent(this))
        }
        binding.batterySettingsButton.setOnClickListener {
            startActivity(IntentUtils.getBatteryOptimizationIntent())
        }
        binding.overflowButton.setOnClickListener { v ->
            dev.anonymous.transfers_ledger.ui.common.AnimatedPopupMenu.show(
                this,
                v,
                listOf(
                    dev.anonymous.transfers_ledger.ui.common.AnimatedPopupMenu.Action(
                        title = getString(R.string.privacy_policy_title),
                        onClick = { AppDialogs.showPrivacyPolicy(supportFragmentManager, isCancelable = true) }
                    ),
                    dev.anonymous.transfers_ledger.ui.common.AnimatedPopupMenu.Action(
                        title = getString(R.string.overview_title),
                        onClick = { showOverviewDialog() }
                    ),
                    dev.anonymous.transfers_ledger.ui.common.AnimatedPopupMenu.Action(
                        title = getString(R.string.contact_developer),
                        onClick = {
                            val url = "https://wa.me/970597152714"
                            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                            try {
                                startActivity(intent)
                            } catch (e: Exception) {
                                Snackbar.make(binding.root, "واتساب غير مثبت", Snackbar.LENGTH_LONG).show()
                            }
                        }
                    )
                ),
                tag = POPUP_OVERFLOW
            )
        }
        binding.userGuideButton.setOnClickListener {
            openUserGuide()
        }
        binding.unprocessedNotificationsButton.setOnClickListener {
            startActivity(Intent(this, UnprocessedNotificationsActivity::class.java))
        }
        binding.autostartSettingsButton.setOnClickListener {
            openAutoStartSettings()
        }
        binding.dontKillMyAppButton.setOnClickListener {
            openDontKillMyAppGuide()
        }
        binding.createBackupButton.setOnClickListener {
            createBackupLauncher.launch(backupFileName())
        }
        binding.restoreBackupButton.setOnClickListener {
//            if (requireActivation()) return@setOnClickListener
            restoreBackupLauncher.launch(arrayOf("application/octet-stream", "*/*"))
        }
        trackingSwitch.setOnCheckedChangeListener { _, checked ->
            if (updatingSwitchFromState) return@setOnCheckedChangeListener
            viewModel.toggleTracking(checked)
            updateForegroundService(checked)
        }

        bindAutoStartStatus()

        // Listener for JawwalPay mode selection
        val modeChangeListener = RadioGroup.OnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.radioApp -> JawwalPayMode.APP
                R.id.radioSms -> JawwalPayMode.SMS
                else -> JawwalPayMode.SMS
            }
            viewModel.setJawwalPayMode(mode)
        }
        binding.jawwalPayModeGroup.setOnCheckedChangeListener(modeChangeListener)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (trackingSwitch.isChecked != state.trackingEnabled) {
                        updatingSwitchFromState = true
                        trackingSwitch.isChecked = state.trackingEnabled
                        updatingSwitchFromState = false
                    }
                    bindNotificationStatus(state.listenerConnected)
                    bindBatteryStatus(state.batteryOptimizationIgnored)
                    // Update JawwalPay source mode UI
                    val currentCheckedId = binding.jawwalPayModeGroup.checkedRadioButtonId
                    val targetCheckedId = when (state.jawwalPayMode) {
                        JawwalPayMode.APP -> R.id.radioApp
                        JawwalPayMode.SMS -> R.id.radioSms
                    }
                    if (currentCheckedId != targetCheckedId) {
                        binding.jawwalPayModeGroup.setOnCheckedChangeListener(null)
                        binding.jawwalPayModeGroup.check(targetCheckedId)
                        binding.radioApp.jumpDrawablesToCurrentState()
                        binding.radioSms.jumpDrawablesToCurrentState()
                        binding.jawwalPayModeGroup.setOnCheckedChangeListener(modeChangeListener)
                    }
                }
            }
        }

        if (savedInstanceState?.getString(KEY_ACTIVE_POPUP) == POPUP_OVERFLOW) {
            binding.overflowButton.post {
                binding.overflowButton.performClick()
            }
        }
    }

    private fun bindNotificationStatus(connected: Boolean) {
        if (connected) {
            binding.notificationStatusText.text = getString(R.string.permission_enabled)
            binding.notificationStatusText.setTextColor(getColor(R.color.jawwal_green))
            binding.notificationDescText.text = getString(R.string.notif_permission_enabled_desc)
        } else {
            binding.notificationStatusText.text = getString(R.string.permission_disabled)
            binding.notificationStatusText.setTextColor(getColor(R.color.outgoing))
            binding.notificationDescText.text = getString(R.string.notif_permission_disabled_desc)
        }
    }

    private fun bindBatteryStatus(ignored: Boolean) {
        if (ignored) {
            binding.batteryStatusText.text = getString(R.string.battery_ignored)
            binding.batteryStatusText.setTextColor(getColor(R.color.jawwal_green))
            binding.batteryDescText.text = getString(R.string.battery_ignored_desc)
        } else {
            binding.batteryStatusText.text = getString(R.string.battery_restricted)
            binding.batteryStatusText.setTextColor(getColor(R.color.outgoing))
            binding.batteryDescText.text = getString(R.string.battery_restricted_desc)
        }
    }

    private fun bindAutoStartStatus() {
        val isAvailable = AutoStartHelper.isAutoStartAvailable(this)
        if (isAvailable) {
            binding.autostartStatusText.text = getString(R.string.autostart_supported_status)
            binding.autostartStatusText.setTextColor(getColor(R.color.jawwal_green))
            binding.autostartDescText.text = getString(R.string.autostart_desc)
            binding.autostartNoteText.visibility = View.VISIBLE
            binding.autostartSettingsButton.visibility = View.VISIBLE
            binding.autostartSettingsButton.isEnabled = true
        } else {
            binding.autostartStatusText.text = getString(R.string.autostart_unsupported_status)
            binding.autostartStatusText.setTextColor(getColor(R.color.outgoing))
            binding.autostartDescText.text = getString(R.string.autostart_unsupported_desc)
            binding.autostartNoteText.visibility = View.GONE
            binding.autostartSettingsButton.visibility = View.GONE
        }
    }

    private fun openAutoStartSettings() {
        when (val result = AutoStartHelper.openAutoStartSettings(this)) {
            is AutoStartResult.Success -> {
                // Opened settings activity successfully
            }
            is AutoStartResult.UnsupportedManufacturer,
            is AutoStartResult.ActivityNotFound,
            is AutoStartResult.Error -> {
                Snackbar.make(binding.root, R.string.autostart_error, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun openDontKillMyAppGuide() {
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
        val knownManufacturers = setOf(
            "huawei", "samsung", "xiaomi", "oppo", "vivo",
            "oneplus", "nokia", "meizu", "asus", "lenovo", "sony", "google", "honor", "realme"
        )
        val targetPath = when {
            knownManufacturers.contains(manufacturer) -> manufacturer
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> "huawei"
            manufacturer.contains("samsung") -> "samsung"
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> "xiaomi"
            manufacturer.contains("oppo") -> "oppo"
            manufacturer.contains("vivo") -> "vivo"
            manufacturer.contains("oneplus") -> "oneplus"
            else -> ""
        }
        val url = if (targetPath.isNotEmpty()) "https://dontkillmyapp.com/$targetPath" else "https://dontkillmyapp.com/"
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        runCatching {
            startActivity(intent)
        }
    }

    private fun writeBackup(uri: Uri) {
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val content = BackupCodec.encode(repository.getBackupData())
                    contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(content.toByteArray(Charsets.UTF_8))
                    } ?: error("Unable to open backup file")
                }
            }
            Snackbar.make(
                binding.root,
                if (result.isSuccess) R.string.backup_success else R.string.backup_failed,
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    private fun confirmRestore(uri: Uri) {
        AppDialogs.showConfirmation(
            fragmentManager = supportFragmentManager,
            title = getString(R.string.restore_warning_title),
            message = getString(R.string.restore_warning_message),
            positiveText = getString(R.string.restore_backup)
        ) {
            restoreBackup(uri)
        }
    }

    private fun restoreBackup(uri: Uri) {
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val content = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        ?: error("Unable to read backup file")
                    repository.replaceAllData(BackupCodec.decode(content))
                }
            }
            val message = when {
                result.isSuccess -> getString(R.string.restore_success)
                result.exceptionOrNull() is SecurityException -> getString(R.string.backup_invalid)
                else -> getString(R.string.restore_failed)
            }
            Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun backupFileName(): String {
        return "palpay-tracker-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())}.bdb"
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        dev.anonymous.transfers_ledger.ui.common.AnimatedPopupMenu.activeTag?.let {
            outState.putString(KEY_ACTIVE_POPUP, it)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshSystemStatus()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_settings, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_overview -> {
                showOverviewDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showOverviewDialog() {
        AppDialogs.showOverview(supportFragmentManager)
    }

    private fun openUserGuide() {
        startActivity(Intent(this, UserGuideActivity::class.java))
    }


    private fun updateForegroundService(enabled: Boolean) {
        val serviceIntent = Intent(this, TrackingForegroundService::class.java)
        if (enabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } else {
            stopService(serviceIntent)
        }
    }

    override fun onDestroy() {
        dev.anonymous.transfers_ledger.ui.common.AnimatedPopupMenu.dismissAll()
        super.onDestroy()
    }

    companion object {
        private const val KEY_ACTIVE_POPUP = "active_popup_tag"
        private const val POPUP_OVERFLOW = "overflow"
    }
}
