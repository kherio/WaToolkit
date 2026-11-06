package com.wmods.wppenhacer.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.NestedScrollView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.toolkit.DiagnosticsReport

/** The compatibility report of the installed WhatsApp version, with a copy button. */
object CompatDialog {
    fun show(ctx: Context) {
        val summary = DiagnosticsReport.load(ctx)
        val body = TextView(ctx).apply {
            text = summary?.text ?: ctx.getString(R.string.compat_none)
            typeface = Typeface.MONOSPACE
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
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("WaToolkit", summary.text))
                Toast.makeText(ctx, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
            }
        }
        builder.show()
    }
}
