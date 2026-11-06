package com.wmods.wppenhacer.ui.fragments

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wmods.wppenhacer.toolkit.backup.ExternalCopy
import android.view.View
import androidx.fragment.app.Fragment
import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.preference.ContactPickerPreference
import com.wmods.wppenhacer.ui.fragments.base.SubScreensFragment
import com.wmods.wppenhacer.ui.fragments.base.BasePreferenceFragment

/**
 * Privacy section. The root lists five subscreens (home screen, read receipts, conversations and statuses, calls,
 * local backup); the preferences of each one live in preference_privacy_*.xml.
 */
class PrivacyFragment : SubScreensFragment() {

    override fun createRoot(): Fragment = PrivacyRoot()
    override val screens get() = SCREENS

    class PrivacyRoot : BasePreferenceFragment() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            super.onCreatePreferences(savedInstanceState, rootKey)
            setPreferencesFromResource(R.xml.fragment_privacy, rootKey)
        }

        override fun onResume() {
            super.onResume()
            setDisplayHomeAsUpEnabled(false)
        }
    }

    class PrivacyHome : BasePreferenceFragment() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            super.onCreatePreferences(savedInstanceState, rootKey)
            setPreferencesFromResource(R.xml.preference_privacy_home, rootKey)
            setDisplayHomeAsUpEnabled(true)
        }
    }

    class PrivacyRead : BasePreferenceFragment() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            super.onCreatePreferences(savedInstanceState, rootKey)
            setPreferencesFromResource(R.xml.preference_privacy_read, rootKey)
            setDisplayHomeAsUpEnabled(true)
        }
    }

    class PrivacyConversation : BasePreferenceFragment() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            super.onCreatePreferences(savedInstanceState, rootKey)
            setPreferencesFromResource(R.xml.preference_privacy_conversation, rootKey)
            setDisplayHomeAsUpEnabled(true)
        }
    }

    class PrivacyCalls : BasePreferenceFragment() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            super.onCreatePreferences(savedInstanceState, rootKey)
            setPreferencesFromResource(R.xml.preference_privacy_calls, rootKey)
            setDisplayHomeAsUpEnabled(true)
        }

        @Suppress("DEPRECATION")
        @Deprecated("Deprecated in Java")
        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            super.onActivityResult(requestCode, resultCode, data)
            if (requestCode == ContactPickerPreference.REQUEST_CONTACT_PICKER && resultCode == Activity.RESULT_OK) {
                val key = data!!.getStringExtra("key") ?: return
                val preference = findPreference<Preference>(key) as? ContactPickerPreference
                preference?.handleActivityResult(requestCode, resultCode, data)
            }
        }
    }

    class PrivacyBackup : BasePreferenceFragment() {

    private val pickBackupFolder =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) onBackupFolderPicked(uri)
        }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        setPreferencesFromResource(R.xml.preference_privacy_backup, rootKey)
        setDisplayHomeAsUpEnabled(true)
        findPreference<Preference>(EXTERNAL_DIR_KEY)?.let { pref ->
            showExternalFolder(pref)
            pref.setOnPreferenceClickListener {
                chooseBackupFolder(pref)
                true
            }
        }
        findPreference<Preference>("auto_backup_now")?.setOnPreferenceClickListener {
            val password = preferenceManager.sharedPreferences?.getString("auto_backup_password", "").orEmpty()
            val ctx = requireContext()
            if (password.isBlank()) {
                Toast.makeText(ctx, R.string.backup_now_no_password, Toast.LENGTH_LONG).show()
            } else if (!com.wmods.wppenhacer.toolkit.ToolRequest.request(
                    ctx, com.wmods.wppenhacer.toolkit.ToolRequest.BACKUP_NOW, "com.whatsapp")) {
                Toast.makeText(ctx, "WhatsApp", Toast.LENGTH_SHORT).show()
            }
            true
        }
        findPreference<EditTextPreference>("auto_backup_password")?.setOnBindEditTextListener {
            it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
    }

    override fun onResume() {
        super.onResume()
        setDisplayHomeAsUpEnabled(true)
    }

    private fun storedFolder(): String =
        preferenceManager.sharedPreferences?.getString(EXTERNAL_DIR_KEY, "").orEmpty()

    private fun showExternalFolder(pref: Preference) {
        val dir = storedFolder()
        pref.summary = if (dir.isEmpty()) getString(R.string.auto_backup_external_sum)
        else getString(R.string.auto_backup_external_set, dir)
    }

    private fun chooseBackupFolder(pref: Preference) {
        val ctx = requireContext()
        if (storedFolder().isEmpty()) {
            launchFolderPicker()
            return
        }
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.auto_backup_external)
            .setItems(
                arrayOf(
                    getString(R.string.auto_backup_external_choose),
                    getString(R.string.auto_backup_external_remove)
                )
            ) { _, which ->
                if (which == 0) {
                    launchFolderPicker()
                } else {
                    preferenceManager.sharedPreferences?.edit()?.remove(EXTERNAL_DIR_KEY)?.apply()
                    showExternalFolder(pref)
                }
            }
            .show()
    }

    private fun launchFolderPicker() {
        val ctx = requireContext()
        // The module app writes the copies on WhatsApp's behalf, so it needs the all-files permission.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            Toast.makeText(ctx, R.string.auto_backup_external_perm, Toast.LENGTH_LONG).show()
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${ctx.packageName}")
                )
            )
            return
        }
        pickBackupFolder.launch(null)
    }

    private fun onBackupFolderPicked(uri: Uri) {
        val path = try {
            ExternalCopy.documentIdToPath(DocumentsContract.getTreeDocumentId(uri))
        } catch (_: Exception) {
            null
        }
        if (path == null) {
            Toast.makeText(requireContext(), R.string.auto_backup_external_bad, Toast.LENGTH_LONG).show()
            return
        }
        preferenceManager.sharedPreferences?.edit()?.putString(EXTERNAL_DIR_KEY, path)?.apply()
        findPreference<Preference>(EXTERNAL_DIR_KEY)?.let { showExternalFolder(it) }
    }

    }

    private companion object {
        private const val EXTERNAL_DIR_KEY = "auto_backup_external_dir"
        private val SCREENS = listOf(
            SubScreensFragment.Screen(R.xml.preference_privacy_home, PrivacyHome::class.java),
            SubScreensFragment.Screen(R.xml.preference_privacy_read, PrivacyRead::class.java),
            SubScreensFragment.Screen(R.xml.preference_privacy_conversation, PrivacyConversation::class.java),
            SubScreensFragment.Screen(R.xml.preference_privacy_calls, PrivacyCalls::class.java),
            SubScreensFragment.Screen(R.xml.preference_privacy_backup, PrivacyBackup::class.java)
        )
    }
}
