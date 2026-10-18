package com.wmods.wppenhacer.toolkit.update

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Queries the latest GitHub release (kherio/WaToolkit) and compares versions. */
object Updater {
    const val LATEST_URL = "https://api.github.com/repos/kherio/WaToolkit/releases/latest"

    class Release(val version: String, val apkUrl: String, val apkName: String)

    /** true if [remote] ("v0.4.0" or "0.4.0-abcd1234") is greater than [local]. */
    fun isNewer(remote: String, local: String): Boolean {
        val a = parts(remote)
        val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun parts(v: String): List<Int> =
        v.trim().removePrefix("v").removePrefix("V").substringBefore('-').split('.')
            .map { it.takeWhile { c -> c.isDigit() }.toIntOrNull() ?: 0 }

    /**
     * Parses the API response; null if it has no APK.
     * With [business] the "Business" APK is chosen, otherwise the non-Business one.
     */
    fun parse(json: String, business: Boolean = false): Release? {
        val o = JSONObject(json)
        val tag = o.optString("tag_name").ifBlank { return null }
        val assets = o.optJSONArray("assets") ?: return null
        var fallback: Release? = null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            val name = a.optString("name")
            if (!name.endsWith(".apk", ignoreCase = true)) continue
            val rel = Release(tag.removePrefix("v"), a.getString("browser_download_url"), name)
            if (name.contains("business", ignoreCase = true) == business) return rel
            if (fallback == null) fallback = rel
        }
        return fallback
    }

    /** Downloads the latest release info (blocking: call off the main thread). */
    fun fetchLatest(business: Boolean = false): Release? {
        val c = URL(LATEST_URL).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            if (c.responseCode != 200) throw java.io.IOException("HTTP ${c.responseCode}")
            return parse(c.inputStream.bufferedReader().use { it.readText() }, business)
        } finally {
            c.disconnect()
        }
    }

    /** Descarga [url] a [dest] (bloqueante). */
    fun downloadTo(url: String, dest: java.io.File) {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.instanceFollowRedirects = true
            if (c.responseCode != 200) throw java.io.IOException("HTTP ${c.responseCode}")
            c.inputStream.use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
        } finally {
            c.disconnect()
        }
    }

    /** Result of installing with root: null if root is not available. */
    fun installWithRoot(apk: java.io.File): Pair<Boolean, String>? {
        return try {
            val tmp = "/data/local/tmp/watoolkit-update.apk"
            val script = "cp '${apk.absolutePath}' $tmp && chmod 644 $tmp && pm install -r $tmp; r=\$?; rm -f $tmp; exit \$r"
            val p = ProcessBuilder("su", "-c", script).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().use { it.readText() }.trim()
            val code = p.waitFor()
            if (code != 0 && (out.contains("not found", true) || out.contains("denied", true) && !out.contains("Failure"))) null
            else Pair(code == 0 && out.contains("Success"), out)
        } catch (_: java.io.IOException) {
            null
        }
    }

    /** Installed module version according to the system (requires root); null if unknown. */
    fun installedVersionWithRoot(packageName: String): String? = try {
        val p = ProcessBuilder("su", "-c", "dumpsys package $packageName | grep versionName")
            .redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().use { it.readText() }
        p.waitFor()
        Regex("versionName=(\\S+)").find(out)?.groupValues?.get(1)
    } catch (_: Exception) {
        null
    }
}
