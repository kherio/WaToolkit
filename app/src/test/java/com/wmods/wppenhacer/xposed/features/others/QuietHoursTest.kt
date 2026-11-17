package com.wmods.wppenhacer.xposed.features.others

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuietHoursTest {
    @Test fun parsesTimes() {
        assertEquals(23 * 60, QuietHours.parse("23:00"))
        assertEquals(7 * 60 + 30, QuietHours.parse(" 7:30 "))
        assertEquals(8 * 60, QuietHours.parse("8"))
        assertNull(QuietHours.parse("25:00"))
        assertNull(QuietHours.parse("abc"))
    }

    @Test fun windowCrossingMidnight() {
        val s = 23 * 60; val e = 7 * 60
        assertTrue(QuietHours.isQuiet(23 * 60 + 30, s, e))
        assertTrue(QuietHours.isQuiet(2 * 60, s, e))
        assertFalse(QuietHours.isQuiet(7 * 60, s, e))
        assertFalse(QuietHours.isQuiet(12 * 60, s, e))
    }

    @Test fun windowInsideTheDay() {
        assertTrue(QuietHours.isQuiet(14 * 60, 13 * 60, 15 * 60))
        assertFalse(QuietHours.isQuiet(15 * 60, 13 * 60, 15 * 60))
    }

    @Test fun equalBoundsMeanNoWindow() = assertFalse(QuietHours.isQuiet(600, 600, 600))
}
