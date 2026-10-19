package com.clipvault.smartclipboard.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.clipvault.smartclipboard.R
import com.clipvault.smartclipboard.SmartClipboardApp
import com.clipvault.smartclipboard.app
import com.clipvault.smartclipboard.data.backup.BackupManager
import com.clipvault.smartclipboard.data.prefs.PrefKeys
import com.clipvault.smartclipboard.monitor.ClipboardMonitorService
import kotlinx.coroutines.launch

class SettingsFragment : PreferenceFragmentCompat() {

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@registerForActivityResult
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { BackupManager.exportTo(requireContext(), uri) }
                .onSuccess { toast(R.string.backup_export_ok) }
                .onFailure { toast(R.string.backup_failed) }
        }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { BackupManager.importFrom(requireContext(), uri) }
                .onSuccess { toast(R.string.backup_import_ok) }
                .onFailure { toast(R.string.backup_failed) }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        findPreference<ListPreference>(PrefKeys.AUTO_CLEAR_PERIOD)?.summaryProvider =
            ListPreference.SimpleSummaryProvider.getInstance()
        findPreference<ListPreference>(PrefKeys.NEW_NOTE_POSITION)?.summaryProvider =
            ListPreference.SimpleSummaryProvider.getInstance()
        findPreference<ListPreference>(PrefKeys.KB_SIZE)?.summaryProvider =
            ListPreference.SimpleSummaryProvider.getInstance()
        findPreference<ListPreference>(PrefKeys.DARK_MODE)?.summaryProvider =
            ListPreference.SimpleSummaryProvider.getInstance()
        findPreference<ListPreference>(PrefKeys.DEFAULT_ACTION)?.summaryProvider =
            ListPreference.SimpleSummaryProvider.getInstance()
        findPreference<ListPreference>(PrefKeys.NOTES_ORDER)?.summaryProvider =
            ListPreference.SimpleSummaryProvider.getInstance()
        findPreference<ListPreference>(PrefKeys.TRASH_PERIOD)?.summaryProvider =
            ListPreference.SimpleSummaryProvider.getInstance()
        findPreference<EditTextPreference>(PrefKeys.MERGE_SEPARATOR)?.summaryProvider =
            EditTextPreference.SimpleSummaryProvider.getInstance()

        findPreference<SwitchPreferenceCompat>(PrefKeys.ENABLE_MONITORING)?.setOnPreferenceChangeListener { _, value ->
            val enabled = value as Boolean
            if (enabled) ClipboardMonitorService.start(requireContext())
            else ClipboardMonitorService.stop(requireContext())
            true
        }
        findPreference<SwitchPreferenceCompat>(PrefKeys.SHOW_CONFIRMATION)?.setOnPreferenceChangeListener { _, value ->
            if (value as Boolean && !Settings.canDrawOverlays(requireContext())) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${requireContext().packageName}"),
                )
                startActivity(intent)
                toast(R.string.overlay_permission_hint)
            }
            true
        }
        findPreference<ListPreference>(PrefKeys.DARK_MODE)?.setOnPreferenceChangeListener { _, value ->
            SmartClipboardApp.applyNightMode(value as String)
            true
        }
        findPreference<Preference>("keyboard_enable_info")?.setOnPreferenceClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            true
        }
        findPreference<Preference>("backup_export")?.setOnPreferenceClickListener {
            exportLauncher.launch("clipvault-backup.json")
            true
        }
        findPreference<Preference>("backup_import")?.setOnPreferenceClickListener {
            importLauncher.launch(arrayOf("application/json", "text/*"))
            true
        }
    }

    private fun toast(res: Int) {
        Toast.makeText(requireContext().app(), res, Toast.LENGTH_SHORT).show()
    }
}
