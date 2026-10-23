package com.wmods.wppenhacer.toolkit.backup

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupScheduleTest {
    private fun at(day: Int, hour: Int, minute: Int = 0): Long = Calendar.getInstance().apply {
        set(2026, Calendar.OCTOBER, day, hour, minute, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test
    fun anyTimeUsesPlainInterval() {
        assertTrue(BackupSchedule.isDue(at(10, 12), at(9, 11), 24, BackupSchedule.ANY_TIME))
        assertFalse(BackupSchedule.isDue(at(10, 10), at(9, 11), 24, BackupSchedule.ANY_TIME))
    }

    @Test
    fun neverBackedUpIsDueAfterFirstSlot() {
        assertTrue(BackupSchedule.isDue(at(10, 4), 0, 24, 3))
    }

    @Test
    fun waitsForTheChosenHour() {
        val last = at(9, 3, 1)
        assertFalse(BackupSchedule.isDue(at(10, 2, 59), last, 24, 3))
        assertTrue(BackupSchedule.isDue(at(10, 3, 0), last, 24, 3))
        assertTrue(BackupSchedule.isDue(at(10, 18), last, 24, 3))
    }

    @Test
    fun doesNotRepeatTheSameDay() {
        val last = at(10, 3, 5)
        assertFalse(BackupSchedule.isDue(at(10, 15), last, 24, 3))
        assertTrue(BackupSchedule.isDue(at(11, 3), last, 24, 3))
    }

    @Test
    fun catchesUpAfterTheDeviceWasOff() {
        assertTrue(BackupSchedule.isDue(at(12, 20), at(9, 3, 1), 24, 3))
    }

    @Test
    fun weeklyIntervalSkipsDays() {
        val last = at(3, 3, 1)
        assertFalse(BackupSchedule.isDue(at(8, 4), last, 168, 3))
        assertTrue(BackupSchedule.isDue(at(10, 4), last, 168, 3))
    }

    @Test
    fun nextRunIsInTheFutureAtTheChosenHour() {
        val now = at(10, 15)
        val next = BackupSchedule.nextRun(now, at(10, 3, 5), 24, 3)
        assertEquals(at(11, 3), next)
    }

    @Test
    fun aManualBackupDoesNotSkipTheNextScheduledOne() {
        // Manual backup at 10:40; the next 03:00 slot is only 16 hours later but is still due.
        val last = at(9, 10, 40)
        assertFalse(BackupSchedule.isDue(at(10, 2, 59), last, 24, 3))
        assertTrue(BackupSchedule.isDue(at(10, 3, 5), last, 24, 3))
    }

    @Test
    fun aLateBackupDoesNotSkipTheNextSlot() {
        // Caught up at 08:00 after a missed 03:00 slot; the next slot is due the following night.
        val last = at(10, 8, 0)
        assertFalse(BackupSchedule.isDue(at(10, 20), last, 24, 3))
        assertTrue(BackupSchedule.isDue(at(11, 3, 0), last, 24, 3))
    }
}
