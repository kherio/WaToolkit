package com.wmods.wppenhacer.xposed.features.others

/** Scheduled silence window, e.g. 23:00 to 07:00 (it may cross midnight). */
object QuietHours {

    /** "HH:mm" or "H" to minutes since midnight, or null if it is not a valid time. */
    fun parse(s: String?): Int? {
        val m = Regex("^\\s*(\\d{1,2})(?::(\\d{2}))?\\s*$").matchEntire(s ?: return null) ?: return null
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].ifEmpty { "0" }.toInt()
        return if (h in 0..23 && min in 0..59) h * 60 + min else null
    }

    /** True when [nowMinutes] lies in [start, end); equal bounds mean no window. */
    fun isQuiet(nowMinutes: Int, start: Int, end: Int): Boolean = when {
        start == end -> false
        start < end -> nowMinutes in start until end
        else -> nowMinutes >= start || nowMinutes < end
    }
}
