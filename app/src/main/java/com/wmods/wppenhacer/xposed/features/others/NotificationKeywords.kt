package com.wmods.wppenhacer.xposed.features.others

import android.app.Notification
import android.app.NotificationManager
import android.content.SharedPreferences
import android.os.Bundle
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.utils.Utils

/**
 * Quiet chats with keywords: the notifications of the chosen chats are hidden unless the message contains
 * one of the keywords. It works on the notification WhatsApp posts, so it does not depend on its internals.
 */
class NotificationKeywords(loader: ClassLoader, preferences: SharedPreferences) : Feature(loader, preferences) {

    @Volatile private var lastHiddenAt = 0L
    @Volatile private var lastShownAt = 0L

    override fun doHook() {
        if (!xprefs.getBoolean("kw_notif", false)) return
        NotificationManager::class.java.resolve().method { name = "notify" }.hookAll {
            before {
                val n = args.lastOrNull() as? Notification ?: return@before
                if (shouldHide(n)) result = null
            }
        }
    }

    private fun shouldHide(n: Notification): Boolean {
        val extras: Bundle = n.extras ?: return false
        val now = System.currentTimeMillis()
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) {
            // The summary of a bundle whose only new message was hidden would linger alone.
            return lastHiddenAt > lastShownAt && now - lastHiddenAt < SUMMARY_WINDOW_MS
        }
        val titles = listOfNotNull(
            extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE), extras.getCharSequence(Notification.EXTRA_TITLE)
        ).map { it.toString() }
        val texts = ArrayList<String>()
        listOf(Notification.EXTRA_TEXT, Notification.EXTRA_BIG_TEXT, Notification.EXTRA_SUB_TEXT).forEach { k ->
            extras.getCharSequence(k)?.let { texts += it.toString() }
        }
        @Suppress("DEPRECATION")
        extras.getParcelableArray(Notification.EXTRA_MESSAGES)?.forEach { p ->
            (p as? Bundle)?.getCharSequence("text")?.let { texts += it.toString() }
        }
        val hide = NotificationKeywordFilter.shouldHide(
            xprefs.getString("kw_chats", ""), xprefs.getString("kw_words", ""), titles, texts
        )
        if (hide) lastHiddenAt = now else if (titles.isNotEmpty()) lastShownAt = now
        return hide
    }

    override fun getPluginName(): String = "Notification keywords"

    private companion object {
        const val SUMMARY_WINDOW_MS = 3000L
    }
}
