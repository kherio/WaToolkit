package com.wmods.wppenhacer.toolkit.insights

import java.util.Locale

/** Picks the Spanish or English text (these tools run inside WhatsApp, where module resources are not available). */
fun tr(en: String, es: String): String = if (Locale.getDefault().language == "es") es else en

/** "1.4 GB", "230 MB"... */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var v = bytes / 1024.0
    var i = 0
    while (v >= 1024 && i < units.size - 1) { v /= 1024; i++ }
    return if (v >= 100) "%.0f %s".format(v, units[i]) else "%.1f %s".format(v, units[i])
}

/** Accent-insensitive, lower-case form used to compare names. */
fun normalizeText(s: String): String =
    java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()
