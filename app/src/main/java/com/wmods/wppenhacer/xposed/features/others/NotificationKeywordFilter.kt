package com.wmods.wppenhacer.xposed.features.others

import java.text.Normalizer

/**
 * Decides whether a WhatsApp notification must be hidden: it belongs to one of the chosen chats and
 * none of the keywords appears in it. With no keywords every notification of those chats is hidden.
 */
object NotificationKeywordFilter {

    fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

    fun split(csv: String?): List<String> =
        (csv ?: "").split(',', '\n', ';').map { normalize(it.trim()) }.filter { it.isNotEmpty() }

    /** True when one of the notification [titles] belongs to a chat of [chatsCsv]. */
    fun matchesChat(chatsCsv: String?, titles: List<String>): Boolean {
        val chats = split(chatsCsv)
        if (chats.isEmpty()) return false
        return titles.map { normalize(it) }.filter { it.isNotEmpty() }.any { h -> chats.any { c -> h == c || h.contains(c) } }
    }

    fun shouldHide(chatsCsv: String?, wordsCsv: String?, titles: List<String>, texts: List<String>): Boolean {
        val chats = split(chatsCsv)
        if (chats.isEmpty()) return false
        val heads = titles.map { normalize(it) }.filter { it.isNotEmpty() }
        if (heads.none { h -> chats.any { c -> h == c || h.contains(c) } }) return false
        val words = split(wordsCsv)
        if (words.isEmpty()) return true
        val haystack = normalize((titles + texts).joinToString(" "))
        return words.none { haystack.contains(it) }
    }
}
