package com.wmods.wppenhacer.toolkit.insights

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightsHelpersTest {
    @Test fun formatsBytes() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("1.5 MB", formatBytes(1_572_864))
    }

    @Test fun normalizesAccents() = assertEquals("mama y papa", normalizeText("Mamá y PAPÁ"))

    @Test fun transcriptKeepsMostRecentWithinLimit() {
        val msgs = (1..5000).map { Triple(it * 60_000L, "A", "x".repeat(50)) }
        val t = SummaryFlow.buildTranscript(msgs)
        assertTrue(t.length <= 24_100)
        assertTrue(t.lines().last().endsWith("x".repeat(50)))
        assertTrue(t.lines().size < 5000)
    }
}
