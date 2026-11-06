package com.wmods.wppenhacer.toolkit

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsReportTest {
    private fun base() = JSONObject()
        .put("module", "1.6.13").put("whatsapp", "2.26.41.11").put("supported", true)
        .put("sdk", 36).put("time", 0L).put("resolved_ok", 10)

    @Test
    fun cleanReportSaysEverythingWorks() {
        val s = DiagnosticsReport.format(
            base().put("features", JSONArray().put(JSONObject().put("name", "A").put("ms", 1).put("error", JSONObject.NULL)))
                .put("failures", JSONArray())
        )
        assertEquals(0, s.failedFeatures)
        assertEquals(0, s.failedFinders)
        assertTrue(s.text.contains("Everything resolved correctly"))
    }

    @Test
    fun failuresAreListedWithTheirFeature() {
        val s = DiagnosticsReport.format(
            base().put("supported", false)
                .put("features", JSONArray().put(JSONObject().put("name", "Channels").put("ms", 1).put("error", "boom")))
                .put("failures", JSONArray().put(
                    JSONObject().put("key", "loadHeader").put("feature", "Channels").put("error", "not found").put("count", 2)
                ))
        )
        assertEquals(1, s.failedFeatures)
        assertEquals(1, s.failedFinders)
        assertTrue(!s.supported)
        assertTrue(s.text.contains("NOT in the supported list"))
        assertTrue(s.text.contains("- Channels: boom"))
        assertTrue(s.text.contains("- loadHeader [Channels]: not found"))
    }

    @Test
    fun problemsListEachAffectedFeatureOnceWithTheReportVersion() {
        val s = DiagnosticsReport.format(
            base()
                .put("features", JSONArray()
                    .put(JSONObject().put("name", "Channels").put("ms", 1).put("error", "boom"))
                    .put(JSONObject().put("name", "Others").put("ms", 1).put("error", JSONObject.NULL)))
                .put("failures", JSONArray()
                    .put(JSONObject().put("key", "a").put("feature", "Channels").put("error", "x").put("count", 1))
                    .put(JSONObject().put("key", "b").put("feature", "SeparateGroup").put("error", "y").put("count", 1)))
        )
        assertEquals("2.26.41.11", s.whatsapp)
        assertEquals(listOf("Channels", "SeparateGroup"), s.problems)
    }
}
