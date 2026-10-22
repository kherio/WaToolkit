package com.wmods.wppenhacer.toolkit.backup

import android.app.Activity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.wmods.wppenhacer.toolkit.Dlg
import com.wmods.wppenhacer.toolkit.Host
import com.wmods.wppenhacer.toolkit.Monet
import com.wmods.wppenhacer.toolkit.S
import java.io.File
import java.text.DateFormat
import java.util.Date

/** "Backups" screen: create, restore and delete database backups. */
class BackupFlow(private val activity: Activity) {

    private var current: Dlg? = null

    private fun dp(v: Int) = Host.dp(activity, v)
    private fun ui(block: () -> Unit) = activity.runOnUiThread { block() }

    private val backupRoot: File get() = Host.backupRoot(activity)

    private fun store() = DbBackup(Host.accountDataDir(activity), backupRoot)

    private val df get() = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)

    private fun size(bytes: Long): String =
        if (bytes >= 1 shl 20) "%.1f MB".format(bytes / 1048576.0) else "${bytes / 1024} KB"

    fun start() {
        val backups = try { store().list() } catch (_: Throwable) { emptyList() }
        val color = Host.textColor(activity)
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        layout.addView(Button(activity).apply {
            text = S.get(S.backup_create)
            setOnClickListener { create() }
        })
        layout.addView(Button(activity).apply {
            text = S.get(S.backup_enc_restore)
            setOnClickListener { pickEncrypted() }
        })
        layout.addView(TextView(activity).apply {
            text = S.get(S.backup_location, backupRoot.path.substringAfter("/Android/", backupRoot.path))
            textSize = 11f
            setTextColor(color)
            alpha = 0.6f
            setPadding(0, dp(4), 0, dp(8))
        })
        if (backups.isEmpty()) {
            layout.addView(TextView(activity).apply {
                text = S.get(S.backup_empty)
                setTextColor(color)
                setPadding(0, dp(8), 0, dp(8))
            })
        } else {
            val labels = backups.map {
                df.format(Date(it.createdAt)) + "  ·  " + size(it.sizeBytes) +
                        if (it.safety) "  ·  " + S.get(S.backup_safety_tag) else ""
            }
            layout.addView(
                ListView(activity).apply {
                    adapter = Monet.adapter(activity, labels)
                    setOnItemClickListener { _, _, pos, _ -> actions(backups[pos]) }
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(300))
            )
        }
        current = Dlg(activity).setTitle(S.get(S.backup_menu)).setView(layout)
            .setPositiveButton(S.get(S.deleted_close), null).also { it.show() }
    }

    private fun reload() {
        current?.dismiss()
        start()
    }

    private fun create() {
        Host.toast(activity, S.get(S.backup_creating))
        Thread {
            try {
                val b = store().create()
                ui { Host.toast(activity, S.get(S.backup_created, size(b.sizeBytes))); reload() }
            } catch (e: Throwable) {
                fail(e)
            }
        }.start()
    }

    private fun actions(b: DbBackup.Backup) {
        val date = df.format(Date(b.createdAt))
        Dlg(activity).setTitle(S.get(S.backup_actions_title, date))
            .setMessage(date + "  ·  " + size(b.sizeBytes))
            .setPositiveButton(S.get(S.backup_restore)) { _, _ -> confirmRestore(b, date) }
            .setNegativeButton(S.get(S.backup_delete)) { _, _ ->
                Thread {
                    try {
                        store().delete(b)
                        ui { Host.toast(activity, S.get(S.backup_deleted)); reload() }
                    } catch (e: Throwable) {
                        fail(e)
                    }
                }.start()
            }.show()
    }

    private fun confirmRestore(b: DbBackup.Backup, date: String) {
        Dlg(activity).setTitle(S.get(S.backup_restore))
            .setMessage(S.get(S.backup_restore_confirm, date))
            .setPositiveButton(S.get(S.backup_restore)) { _, _ ->
                Thread {
                    try {
                        store().restore(b)
                        ui {
                            Host.toast(activity, S.get(S.backup_restored), android.widget.Toast.LENGTH_LONG)
                            Host.restart(activity)
                        }
                    } catch (e: Throwable) {
                        fail(e)
                    }
                }.start()
            }
            .setNegativeButton(activity.getString(android.R.string.cancel), null).show()
    }

    /** Lists the encrypted backups made by "Local backup" and restores the chosen one. */
    private fun pickEncrypted() {
        val files = try { EncryptedBackup(activity).listDatabaseBackups() } catch (_: Throwable) { emptyList() }
        if (files.isEmpty()) {
            Dlg(activity).setTitle(S.get(S.backup_enc_restore)).setMessage(S.get(S.backup_enc_none))
                .setPositiveButton(S.get(S.deleted_close), null).show()
            return
        }
        val labels = files.map { df.format(Date(it.lastModified())) + "  ·  " + size(it.length()) }
        val list = ListView(activity).apply { adapter = Monet.adapter(activity, labels) }
        val dialog = Dlg(activity).setTitle(S.get(S.backup_enc_pick)).setView(list)
            .setPositiveButton(S.get(S.deleted_close), null).also { it.show() }
        list.setOnItemClickListener { _, _, pos, _ ->
            dialog.dismiss()
            askPassword(files[pos])
        }
    }

    private fun askPassword(file: File) {
        val input = android.widget.EditText(activity).apply {
            hint = S.get(S.backup_enc_password)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            // The module's own backup password is the usual one, so offer it.
            setText(com.wmods.wppenhacer.xposed.utils.Utils.xprefs.getString("auto_backup_password", "").orEmpty())
        }
        val box = LinearLayout(activity).apply {
            setPadding(dp(20), dp(8), dp(20), dp(8))
            addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        Dlg(activity).setTitle(S.get(S.backup_enc_restore)).setView(box)
            .setPositiveButton(S.get(S.backup_restore)) { _, _ ->
                val password = input.text.toString().toCharArray()
                confirmEncryptedRestore(file, password)
            }
            .setNegativeButton(activity.getString(android.R.string.cancel), null).show()
    }

    private fun confirmEncryptedRestore(file: File, password: CharArray) {
        val date = df.format(Date(file.lastModified()))
        val media = try { EncryptedBackup(activity).listMediaBackups() } catch (_: Throwable) { emptyList() }
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        box.addView(TextView(activity).apply {
            text = S.get(S.backup_restore_confirm, date)
            setTextColor(Host.textColor(activity))
        })
        val check = android.widget.CheckBox(activity).apply {
            text = S.get(S.backup_media_check, media.size, size(media.sumOf { it.length() }))
            setTextColor(Host.textColor(activity))
            isChecked = false
        }
        if (media.isNotEmpty()) box.addView(check)
        Dlg(activity).setTitle(S.get(S.backup_restore)).setView(box)
            .setPositiveButton(S.get(S.backup_restore)) { _, _ ->
                Host.toast(activity, S.get(S.backup_enc_working))
                val withMedia = media.isNotEmpty() && check.isChecked
                Thread { restoreEncrypted(file, password, if (withMedia) media else emptyList()) }.start()
            }
            .setNegativeButton(activity.getString(android.R.string.cancel), null).show()
    }

    private fun restoreEncrypted(file: File, password: CharArray, media: List<File>) {
        val work = File(activity.cacheDir, "wt-restore-${System.currentTimeMillis()}")
        try {
            // Decrypting and validating first means a wrong password or a damaged file never touches the live databases.
            EncryptedBackup(activity).extractDatabases(file, password, work)
            val size = (work.listFiles() ?: emptyArray()).sumOf { it.length() }
            val backup = DbBackup.Backup(work, file.lastModified(), size, File(work, "wa.db").exists(), false)
            if (media.isNotEmpty()) restoreMedia(media, password)
            store().restore(backup)
            ui {
                Host.toast(activity, S.get(S.backup_restored), android.widget.Toast.LENGTH_LONG)
                Host.restart(activity)
            }
        } catch (e: BackupCrypto.WrongPasswordException) {
            ui { Host.toast(activity, S.get(S.backup_enc_wrong), android.widget.Toast.LENGTH_LONG) }
        } catch (e: Throwable) {
            fail(e)
        } finally {
            work.deleteRecursively()
        }
    }

    /** Restores the media archives oldest first; a problem with them never blocks the chat restore. */
    private fun restoreMedia(archives: List<File>, password: CharArray) {
        ui { Host.toast(activity, S.get(S.backup_media_working), android.widget.Toast.LENGTH_LONG) }
        val backup = EncryptedBackup(activity)
        var restored = 0
        var skipped = 0
        var error: String? = null
        for (archive in archives) {
            try {
                val r = backup.restoreMedia(archive, password)
                restored += r.restored
                skipped += r.skipped
            } catch (e: Throwable) {
                Host.log(e)
                error = e.message ?: e.javaClass.simpleName
            }
        }
        val message = error?.let { S.get(S.backup_media_failed, it) } ?: S.get(S.backup_media_done, restored, skipped)
        ui { Host.toast(activity, message, android.widget.Toast.LENGTH_LONG) }
    }

    private fun fail(e: Throwable) {
        Host.log(e)
        val msg = if (e is DbBackup.NoSpaceException) S.get(S.backup_no_space) else e.message ?: e.javaClass.simpleName
        ui { Host.toast(activity, S.get(S.backup_error, msg), android.widget.Toast.LENGTH_LONG) }
    }
}
