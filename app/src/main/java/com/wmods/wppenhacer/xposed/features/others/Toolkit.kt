package com.wmods.wppenhacer.xposed.features.others

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.wmods.wppenhacer.BuildConfig
import androidx.core.net.toUri
import com.wmods.wppenhacer.provider.PrefsProvider
import com.wmods.wppenhacer.toolkit.ToolRequest
import com.wmods.wppenhacer.toolkit.Host
import com.wmods.wppenhacer.toolkit.S
import com.wmods.wppenhacer.toolkit.backup.BackupFlow
import com.wmods.wppenhacer.toolkit.backup.RecoverChatFlow
import com.wmods.wppenhacer.toolkit.deleted.DeletedMessagesFlow
import com.wmods.wppenhacer.toolkit.export.ExportChatFlow
import com.wmods.wppenhacer.toolkit.importer.ImportChatFlow
import com.wmods.wppenhacer.toolkit.Dlg
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore.homeActivityClass

/**
 * WaToolkit tools inside WhatsApp (import/export chats, deleted messages,
 * recover chat and backups). They are launched from the "Tools" screen of the module app,
 * which opens WhatsApp and leaves the request here (see [ToolRequest]); this hook picks it up
 * when the main screen is shown and receives the results of the file pickers.
 */
class Toolkit(classLoader: ClassLoader, preferences: SharedPreferences) :
    Feature(classLoader, preferences) {

    @Throws(Throwable::class)
    override fun doHook() {
        hookActivityResult()
        hookPendingTool()
    }

    /** When the main screen opens, handles the tool requested from the module app. */
    private fun hookPendingTool() {
        homeActivityClass.resolve().firstMethod {
            name = "onResume"
            superclass()
        }.hook {
            after {
                val activity = instance as? Activity ?: return@after
                if (!homeActivityClass.isInstance(activity)) return@after
                announceUpdate(activity)
                Thread {
                    try {
                        val uri = "content://${BuildConfig.APPLICATION_ID}.preferences".toUri()
                        val tool = activity.contentResolver
                            .call(uri, PrefsProvider.METHOD_TAKE_PENDING_TOOL, activity.packageName, null)
                            ?.getString(PrefsProvider.KEY_TOOL)
                        if (tool != null) activity.runOnUiThread { runTool(activity, tool) }
                    } catch (t: Throwable) {
                        Host.log(t)
                    }
                }.start()
            }
        }
    }

    private fun runTool(activity: Activity, tool: String) {
        try {
            when (tool) {
                ToolRequest.IMPORT -> openPicker(activity)
                ToolRequest.EXPORT -> ExportChatFlow(activity).start()
                ToolRequest.DELETED -> DeletedMessagesFlow(activity).start()
                ToolRequest.RECOVER -> RecoverChatFlow(activity).start()
                ToolRequest.BACKUP -> BackupFlow(activity).start()
            }
        } catch (t: Throwable) {
            Host.log(t)
            Host.toast(activity, t.message)
        }
    }

    private fun hookActivityResult() {
        homeActivityClass.resolve().firstMethod {
            name = "onActivityResult"
            superclass()
            parameters(
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
                Intent::class.java
            )
        }.hook {
            after {
                val requestCode = args[0] as Int
                val resultCode = args[1] as Int
                val data = args[2] as? Intent
                val activity = instance as? Activity ?: return@after

                if (requestCode == ExportChatFlow.REQUEST_SAVE) {
                    try {
                        ExportChatFlow.onResult(resultCode, data)
                    } catch (t: Throwable) {
                        Host.log(t)
                    }
                    return@after
                }
                if (requestCode != REQUEST_PICK_FILE || resultCode != Activity.RESULT_OK) return@after
                val uri = data?.data ?: return@after
                try {
                    ImportChatFlow(activity, uri).start()
                } catch (t: Throwable) {
                    Host.log(t)
                    Host.toast(activity, t.message)
                }
            }
        }
    }

    /** After the module is updated, announces the new version once. */
    private fun announceUpdate(activity: Activity) {
        if (announced) return
        announced = true
        try {
            val prefs = activity.getSharedPreferences("watoolkit", android.content.Context.MODE_PRIVATE)
            val last = prefs.getString("last_version", null)
            prefs.edit().putString("last_version", BuildConfig.VERSION_NAME).apply()
            if (last != null && last != BuildConfig.VERSION_NAME) {
                activity.runOnUiThread {
                    Dlg(activity).setTitle(S.get(S.update_after_title))
                        .setMessage(S.get(S.update_after_msg, BuildConfig.VERSION_NAME, last))
                        .setPositiveButton(S.get(S.deleted_close), null).show()
                }
            }
        } catch (t: Throwable) {
            Host.log(t)
        }
    }

    private fun openPicker(activity: Activity) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf(
                    "text/plain",
                    "application/zip",
                    "application/x-zip-compressed",
                    "application/octet-stream"
                )
            )
        }
        try {
            activity.startActivityForResult(intent, REQUEST_PICK_FILE)
        } catch (e: Exception) {
            Host.toast(activity, e.message)
        }
    }

    override fun getPluginName(): String = "Toolkit"

    private companion object {
        const val REQUEST_PICK_FILE = 0x5A17
        @Volatile var announced = false
    }
}
