package com.wmods.wppenhacer.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import androidx.preference.PreferenceManager
import com.wmods.wppenhacer.toolkit.DiagnosticsReport
import com.wmods.wppenhacer.toolkit.ToolRequest
import com.wmods.wppenhacer.xposed.bridge.service.CallerGuard

class PrefsProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val context = context ?: return null
        if (method == METHOD_TAKE_PENDING_TOOL) {
            if (arg == null || !CallerGuard.isCallerAllowed(context)) return null
            val tool = ToolRequest.take(context, arg) ?: return null
            return Bundle().apply { putString(KEY_TOOL, tool) }
        }
        if (method == METHOD_PUT_DIAGNOSTICS) {
            if (arg == null || arg.length > MAX_DIAGNOSTICS_CHARS || !CallerGuard.isCallerAllowed(context)) return null
            return try {
                org.json.JSONObject(arg) // reject anything that is not valid JSON
                DiagnosticsReport.file(context).writeText(arg)
                Bundle()
            } catch (_: Exception) {
                null
            }
        }
        if (method != METHOD_GET_ALL) return null
        // Exported provider: only the module and WhatsApp/WhatsApp Business can read
        // the preferences (they include transcription API keys).
        if (!CallerGuard.isCallerAllowed(context)) return null
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        val values = Bundle()
        val sets = Bundle()
        for ((key, value) in prefs.all) {
            when (value) {
                is Boolean -> values.putBoolean(key, value)
                is Int -> values.putInt(key, value)
                is Long -> values.putLong(key, value)
                is Float -> values.putFloat(key, value)
                is String -> values.putString(key, value)
                is Set<*> -> sets.putStringArrayList(key, ArrayList(value.filterIsInstance<String>()))
            }
        }
        return Bundle().apply {
            putBundle(KEY_VALUES, values)
            putBundle(KEY_SETS, sets)
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<String?>?,
        selection: String?,
        selectionArgs: Array<String?>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String = ""

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String?>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String?>?
    ): Int = 0

    companion object {
        const val METHOD_GET_ALL = "getAll"
        const val METHOD_TAKE_PENDING_TOOL = "takePendingTool"
        const val METHOD_PUT_DIAGNOSTICS = "putDiagnostics"
        private const val MAX_DIAGNOSTICS_CHARS = 256 * 1024
        const val KEY_TOOL = "tool"
        const val KEY_VALUES = "values"
        const val KEY_SETS = "sets"
    }
}
