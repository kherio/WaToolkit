package com.wmods.wppenhacer.toolkit

import android.app.Activity
import android.content.Context
import android.net.Uri
import com.wmods.wppenhacer.BuildConfig
import com.wmods.wppenhacer.provider.PrefsProvider
import com.wmods.wppenhacer.toolkit.insights.InsightsDb
import com.wmods.wppenhacer.toolkit.insights.InsightsEnv
import com.wmods.wppenhacer.xposed.core.Diagnostics
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp
import org.json.JSONArray
import java.io.File

/**
 * Names of the chats and groups, copied from WhatsApp to the module (which cannot read WhatsApp's database)
 * so settings can offer a list to pick from. It never leaves the phone.
 */
object ChatNames {
    fun file(context: Context) = File(context.filesDir, "chat_names.json")

    /** Module side: sorted names, empty until WhatsApp has published them. */
    fun read(context: Context): List<String> = try {
        val arr = JSONArray(file(context).readText())
        (0 until arr.length()).map { arr.getString(it) }.distinct().sortedBy { it.lowercase() }
    } catch (_: Exception) {
        emptyList()
    }

    /**
     * The name WhatsApp shows for the chat (the same one that appears in its notifications). For people it asks
     * WhatsApp itself, which also resolves LID identifiers; the database lookup is only the fallback.
     */
    private fun displayName(env: InsightsEnv, chat: InsightsDb.Chat): String {
        if (!chat.isGroup) {
            try {
                val userJid = FMessageWpp.UserJid(chat.jid)
                if (!userJid.isNull) {
                    val name = WppCore.getContactName(userJid)
                    if (name.isNotBlank() && name != "Whatsapp Contact") return name
                }
            } catch (_: Throwable) {
            }
        }
        return env.chatName(chat)
    }

    /** WhatsApp side. Best effort: it must never affect WhatsApp. */
    fun publish(activity: Activity) {
        try {
            val env = InsightsEnv(activity)
            env.load()
            val names = env.db.chats().map { displayName(env, it) }.filter { it.isNotBlank() }
            val uri = Uri.parse("content://${BuildConfig.APPLICATION_ID}.preferences")
            val reply = activity.contentResolver.call(uri, PrefsProvider.METHOD_PUT_CHAT_NAMES, JSONArray(names).toString(), null)
            Diagnostics.event(
                "ChatNames",
                if (reply != null) "published ${names.size} chats (${names.count { n -> n.all { it.isDigit() || it == '+' || it == ' ' } }} shown as numbers)" else "module rejected the chat list (${names.size} chats)"
            )
        } catch (t: Throwable) {
            Diagnostics.event("ChatNames", "publish failed: ${t.javaClass.simpleName}: ${t.message}")
        }
    }
}
