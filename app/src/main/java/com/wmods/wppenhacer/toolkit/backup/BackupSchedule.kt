package com.wmods.wppenhacer.toolkit.backup

import java.util.Calendar

/** Decides when a scheduled backup is due, optionally at a fixed hour of the day. */
object BackupSchedule {
    /** Value of the "time of day" setting meaning "any time once the interval has passed". */
    const val ANY_TIME = -1

    private const val HOUR_MS = 3_600_000L

    /**
     * @param hourOfDay the preferred hour (0-23) or [ANY_TIME]
     * @param lastBackup when the last backup finished (0 if none)
     */
    fun isDue(now: Long, lastBackup: Long, intervalHours: Long, hourOfDay: Int): Boolean {
        if (hourOfDay !in 0..23) return now - lastBackup >= intervalHours * HOUR_MS
        // With a fixed hour, only the slot that already passed counts: a backup is due when that
        // slot is at least one interval (minus an hour of tolerance) after the last backup.
        val slot = latestSlot(now, hourOfDay)
        return slot - lastBackup >= (intervalHours - 1).coerceAtLeast(1) * HOUR_MS
    }

    /** The most recent moment at or before [now] whose local time is [hourOfDay]:00. */
    fun latestSlot(now: Long, hourOfDay: Int): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis > now) cal.add(Calendar.DAY_OF_MONTH, -1)
        return cal.timeInMillis
    }

    /** The next moment a backup becomes due, for display in the diagnostics. */
    fun nextRun(now: Long, lastBackup: Long, intervalHours: Long, hourOfDay: Int): Long {
        if (hourOfDay !in 0..23) return lastBackup + intervalHours * HOUR_MS
        var slot = latestSlot(now, hourOfDay)
        while (!isDue(slot, lastBackup, intervalHours, hourOfDay) || slot <= now) {
            val cal = Calendar.getInstance().apply { timeInMillis = slot; add(Calendar.DAY_OF_MONTH, 1) }
            slot = cal.timeInMillis
        }
        return slot
    }
}
