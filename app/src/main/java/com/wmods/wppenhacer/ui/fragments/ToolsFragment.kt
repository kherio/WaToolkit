package com.wmods.wppenhacer.ui.fragments

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.toolkit.backup.BackupCrypto
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wmods.wppenhacer.toolkit.DiagnosticsReport
import com.wmods.wppenhacer.toolkit.S
import com.wmods.wppenhacer.toolkit.ToolRequest
import com.wmods.wppenhacer.toolkit.update.UpdateFlow

/**
 * WaToolkit tools inside the module app. They work on WhatsApp's data,
 * so each one opens WhatsApp and launches the tool there; "check for updates" runs
 * right here.
 */
class ToolsFragment : Fragment() {

    private var pendingBackup: Uri? = null
    private var pendingPassword: CharArray? = null

    private val pickBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) askPassword(uri)
    }
    private val createZip = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { target ->
        if (target != null) decrypt(target)
    }

    private class Tool(val icon: String, val title: String, val desc: String, val action: () -> Unit)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

        val list = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(24))
        }
        list.addView(TextView(ctx).apply {
            text = S.get(S.hub_title)
            textSize = 22f
            setPadding(dp(4), dp(4), dp(4), dp(4))
        })

        val tools = listOf(
            Tool("📥", S.get(S.import_chat), S.get(S.tool_import_desc)) { open(ToolRequest.IMPORT) },
            Tool("📤", S.get(S.export_menu), S.get(S.tool_export_desc)) { open(ToolRequest.EXPORT) },
            Tool("🗑", S.get(S.deleted_menu), S.get(S.tool_deleted_desc)) { open(ToolRequest.DELETED) },
            Tool("♻️", S.get(S.recover_menu), S.get(S.tool_recover_desc)) { open(ToolRequest.RECOVER) },
            Tool("💾", S.get(S.backup_menu), S.get(S.tool_backup_desc)) { open(ToolRequest.BACKUP) },
            Tool("🔓", getString(R.string.decrypt_backup), getString(R.string.decrypt_backup_desc)) {
                pickBackup.launch(arrayOf("*/*"))
            },
            Tool("🩺", getString(R.string.compat_title), getString(R.string.compat_desc)) {
                showCompatibility()
            },
            Tool("⬇", S.get(S.update_menu), S.get(S.tool_update_desc)) {
                UpdateFlow(requireActivity()).start()
            }
        )
        for (t in tools) {
            val card = MaterialCardView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(8) }
                radius = dp(16).toFloat()
                isClickable = true
                isFocusable = true
                setOnClickListener { t.action() }
            }
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(16), dp(14), dp(16), dp(14))
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            row.addView(TextView(ctx).apply {
                text = t.icon
                textSize = 26f
                setPadding(0, 0, dp(16), 0)
            })
            val texts = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(TextView(ctx).apply {
                text = t.title
                textSize = 16f
            })
            texts.addView(TextView(ctx).apply {
                text = t.desc
                textSize = 13f
                alpha = 0.7f
            })
            row.addView(texts)
            card.addView(row)
            list.addView(card)
        }
        return NestedScrollView(ctx).apply { addView(list) }
    }

    /** Shows what worked and what did not on the installed WhatsApp version, with a copy button. */
    private fun showCompatibility() {
        val ctx = requireContext()
        val summary = DiagnosticsReport.load(ctx)
        val body = TextView(ctx).apply {
            text = summary?.text ?: getString(R.string.compat_none)
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 12f
            setTextIsSelectable(true)
            val pad = (resources.displayMetrics.density * 20).toInt()
            setPadding(pad, pad / 2, pad, pad / 2)
        }
        val builder = MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.compat_title)
            .setView(NestedScrollView(ctx).apply { addView(body) })
            .setNegativeButton(android.R.string.ok, null)
        if (summary != null) {
            builder.setPositiveButton(R.string.copy_to_clipboard) { _, _ ->
                val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("WaToolkit", summary.text))
                Toast.makeText(ctx, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
            }
        }
        builder.show()
    }

    /** Opens WhatsApp with the tool. */
    private fun open(tool: String) {
        val ctx = requireContext()
        val installed = try {
            ctx.packageManager.getPackageInfo("com.whatsapp", 0)
            true
        } catch (_: Exception) {
            false
        }
        if (installed) launch(tool, "com.whatsapp") else Toast.makeText(ctx, "WhatsApp", Toast.LENGTH_SHORT).show()
    }

    private fun launch(tool: String, pkg: String) {
        if (!ToolRequest.request(requireContext(), tool, pkg)) {
            Toast.makeText(requireContext(), "WhatsApp", Toast.LENGTH_SHORT).show()
        }
    }

    private fun askPassword(source: Uri) {
        val ctx = requireContext()
        val input = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = getString(R.string.decrypt_backup_password)
        }
        AlertDialog.Builder(ctx)
            .setTitle(R.string.decrypt_backup)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                pendingBackup = source
                pendingPassword = input.text.toString().toCharArray()
                createZip.launch("WaToolkit-backup.zip")
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun decrypt(target: Uri) {
        val source = pendingBackup ?: return
        val password = pendingPassword ?: return
        pendingBackup = null
        pendingPassword = null
        val ctx = requireContext().applicationContext
        Thread {
            val message = try {
                ctx.contentResolver.openInputStream(source)!!.use { input ->
                    ctx.contentResolver.openOutputStream(target, "wt")!!.use { out ->
                        BackupCrypto.decrypt(input.buffered(), out.buffered(), password)
                    }
                }
                ctx.getString(R.string.decrypt_backup_done)
            } catch (_: BackupCrypto.WrongPasswordException) {
                ctx.getString(R.string.decrypt_backup_wrong)
            } catch (e: Throwable) {
                ctx.getString(R.string.decrypt_backup_failed, e.message ?: e.javaClass.simpleName)
            }
            activity?.runOnUiThread { Toast.makeText(ctx, message, Toast.LENGTH_LONG).show() }
        }.start()
    }
}
