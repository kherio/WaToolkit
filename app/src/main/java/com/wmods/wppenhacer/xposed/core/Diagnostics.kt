package com.wmods.wppenhacer.xposed.core

import android.net.Uri
import com.wmods.wppenhacer.BuildConfig
import com.wmods.wppenhacer.provider.PrefsProvider
import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Records, inside the WhatsApp process, which features and which obfuscation finders worked for
 * the installed WhatsApp version, and sends the result to the module app (see
 * [com.wmods.wppenhacer.toolkit.DiagnosticsReport]) so it can show a compatibility report.
 */
object Diagnostics {
    private const val MAX_FAILURES = 150
    private const val MAX_TEXT = 300
    private const val MAX_EVENTS = 40
    private const val PUSH_DELAY_MS = 3000L

    private class FeatureResult(val name: String, val ms: Long, val error: String?)
    private class Failure(val key: String, val feature: String?, val error: String) {
        var count = 1
    }

    private val features = Collections.synchronizedList(ArrayList<FeatureResult>())
    private val failures = Collections.synchronizedMap(LinkedHashMap<String, Failure>())
    private val events = Collections.synchronizedList(ArrayList<String>())
    private val resolvedKeys: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    private val currentFeature = ThreadLocal<String?>()
    private val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "WAE-Diagnostics").apply { isDaemon = true }
    }
    private var pending: ScheduledFuture<*>? = null

    @Volatile
    var whatsAppVersion: String = ""

    @Volatile
    var supported: Boolean = true

    /** Records a runtime event (last [MAX_EVENTS] kept) so a feature that did nothing can be explained. */
    fun event(tag: String, message: String) {
        val line = "${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.ROOT).format(java.util.Date())} [$tag] ${clip(message)}"
        synchronized(events) {
            events.add(line)
            while (events.size > MAX_EVENTS) events.removeAt(0)
        }
        schedulePush()
    }

    /** Marks the feature whose hooks are being installed on this thread (null to clear). */
    fun enterFeature(name: String?) = currentFeature.set(name)

    fun featureLoaded(name: String, ms: Long, error: Throwable?) {
        features.add(FeatureResult(name, ms, error?.let { clip(it.message ?: it.javaClass.simpleName) }))
        schedulePush()
    }

    fun resolved(key: String, error: Throwable?) {
        if (error == null) {
            resolvedKeys.add(key)
            return
        }
        synchronized(failures) {
            val existing = failures[key]
            if (existing != null) {
                existing.count++
            } else if (failures.size < MAX_FAILURES) {
                failures[key] = Failure(key, currentFeature.get(), clip(error.message ?: error.javaClass.simpleName))
            }
        }
        schedulePush()
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("module", BuildConfig.VERSION_NAME)
        put("whatsapp", whatsAppVersion)
        put("supported", supported)
        put("sdk", android.os.Build.VERSION.SDK_INT)
        put("time", System.currentTimeMillis())
        put("resolved_ok", resolvedKeys.size)
        put("features", JSONArray().also { arr ->
            synchronized(features) {
                features.forEach {
                    arr.put(JSONObject().put("name", it.name).put("ms", it.ms).put("error", it.error))
                }
            }
        })
        put("events", JSONArray().also { arr -> synchronized(events) { events.forEach { arr.put(it) } } })
        put("failures", JSONArray().also { arr ->
            synchronized(failures) {
                failures.values.forEach {
                    arr.put(
                        JSONObject().put("key", it.key).put("feature", it.feature)
                            .put("error", it.error).put("count", it.count)
                    )
                }
            }
        })
    }

    @Synchronized
    private fun schedulePush() {
        pending?.cancel(false)
        pending = scheduler.schedule({ push() }, PUSH_DELAY_MS, TimeUnit.MILLISECONDS)
    }

    private fun push() {
        try {
            val app = FeatureLoader.mApp ?: return
            val uri = Uri.parse("content://${BuildConfig.APPLICATION_ID}.preferences")
            app.contentResolver.call(uri, PrefsProvider.METHOD_PUT_DIAGNOSTICS, toJson().toString(), null)
        } catch (_: Throwable) {
            // Diagnostics are best-effort and must never affect WhatsApp.
        }
    }

    private fun clip(s: String) = if (s.length > MAX_TEXT) s.take(MAX_TEXT) + "…" else s
}
