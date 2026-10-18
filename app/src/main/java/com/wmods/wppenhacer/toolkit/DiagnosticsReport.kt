package com.wmods.wppenhacer.toolkit

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * Module-side view of the compatibility data that WhatsApp sends through PrefsProvider
 * (see [com.wmods.wppenhacer.xposed.core.Diagnostics]).
 */
object DiagnosticsReport {

    class Summary(val text: String, val failedFeatures: Int, val failedFinders: Int, val supported: Boolean)

    fun file(context: Context) = File(context.filesDir, "diagnostics.json")

    /** The last report WhatsApp sent, or null if WhatsApp has not run with the module yet. */
    fun load(context: Context): Summary? {
        val f = file(context)
        if (!f.isFile) return null
        return try {
            format(JSONObject(f.readText()))
        } catch (_: Exception) {
            null
        }
    }

    internal fun format(json: JSONObject): Summary {
        val features = json.optJSONArray("features")
        val failures = json.optJSONArray("failures")
        val failedFeatures = (0 until (features?.length() ?: 0))
            .map { features!!.getJSONObject(it) }
            .filter { !it.isNull("error") }
        val failedFinders = (0 until (failures?.length() ?: 0)).map { failures!!.getJSONObject(it) }
        val supported = json.optBoolean("supported", true)

        // Features that loaded but had finders fail while they were installing their hooks.
        val affected = failedFinders.mapNotNull { it.optString("feature").takeIf { n -> n.isNotEmpty() && n != "null" } }
            .toSortedSet()

        val text = buildString {
            appendLine("WaToolkit ${json.optString("module")} · WhatsApp ${json.optString("whatsapp")}" +
                    if (supported) "" else " (NOT in the supported list)")
            appendLine("Android API ${json.optInt("sdk")} · ${DateFormat.getDateTimeInstance().format(Date(json.optLong("time")))}")
            appendLine("Features: ${(features?.length() ?: 0) - failedFeatures.size} loaded, ${failedFeatures.size} failed")
            appendLine("Finders: ${json.optInt("resolved_ok")} resolved, ${failedFinders.size} failed")
            if (failedFeatures.isEmpty() && failedFinders.isEmpty()) {
                appendLine()
                appendLine("Everything resolved correctly on this WhatsApp version.")
            }
            if (failedFeatures.isNotEmpty()) {
                appendLine()
                appendLine("FEATURES THAT FAILED TO LOAD")
                failedFeatures.forEach { appendLine("- ${it.getString("name")}: ${it.getString("error")}") }
            }
            if (affected.isNotEmpty()) {
                appendLine()
                appendLine("FEATURES WITH A MISSING FINDER (some options may not work)")
                affected.forEach { appendLine("- $it") }
            }
            if (failedFinders.isNotEmpty()) {
                appendLine()
                appendLine("FINDERS THAT FAILED (a feature may have a fallback)")
                failedFinders.forEach {
                    val feature = it.optString("feature").takeIf { n -> n.isNotEmpty() && n != "null" }
                    appendLine("- ${it.getString("key")}${feature?.let { f -> " [$f]" } ?: ""}: ${it.getString("error")}")
                }
            }
        }.trimEnd()
        return Summary(text, failedFeatures.size, failedFinders.size, supported)
    }
}
