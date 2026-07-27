package dev.anonymous.transfers_ledger

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
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
import dev.anonymous.transfers_ledger.core.IntentUtils
import dev.anonymous.transfers_ledger.core.backup.BackupCodec
import dev.anonymous.transfers_ledger.databinding.ActivitySettingsBinding
import dev.anonymous.transfers_ledger.service.TrackingForegroundService
import dev.anonymous.transfers_ledger.ui.common.AppDialogs
import dev.anonymous.transfers_ledger.core.JawwalPayMode
import java.text.SimpleDateFormat
import java.util.Date
import dev.anonymous.transfers_ledger.R
import java.util.Locale
import dev.anonymous.transfers_ledger.ui.viewmodel.MainViewModel

class SettingsActivity : ComponentActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var trackingSwitch: MaterialSwitch
    private var updatingSwitchFromState = false
    private val repository by lazy { (application as PalPayApplication).repository }
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
            startActivity(IntentUtils.getBatteryOptimizationIntent(this))
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
            restoreBackupLauncher.launch(arrayOf("application/octet-stream", "*/*"))
        }
        trackingSwitch.setOnCheckedChangeListener { _, checked ->
            if (updatingSwitchFromState) return@setOnCheckedChangeListener
            viewModel.toggleTracking(checked)
            updateForegroundService(checked)
        }

        bindAutoStartStatus()

        // Listener for JawwalPay mode selection
        binding.jawwalPayModeGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.radioApp -> JawwalPayMode.APP
                R.id.radioSms -> JawwalPayMode.SMS
                else -> JawwalPayMode.SMS
            }
            viewModel.setJawwalPayMode(mode)
        }

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
                    when (state.jawwalPayMode) {
                        JawwalPayMode.APP -> binding.jawwalPayModeGroup.check(R.id.radioApp)
                        JawwalPayMode.SMS -> binding.jawwalPayModeGroup.check(R.id.radioSms)
                    }
                }
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
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
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
            context = this,
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

    override fun onResume() {
        super.onResume()
        viewModel.refreshSystemStatus()
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
}
