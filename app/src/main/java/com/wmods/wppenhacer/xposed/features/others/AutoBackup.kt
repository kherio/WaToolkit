package com.wmods.wppenhacer.xposed.features.others

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.drawable.Icon
import android.content.SharedPreferences
import android.widget.Toast
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.toolkit.backup.EncryptedBackup
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.utils.Utils
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Scheduled, encrypted local backups of the chat databases (and, optionally, the media) that do
 * not depend on Google Drive. WhatsApp has no scheduler we can hook, so the schedule is checked
 * whenever WhatsApp comes to the foreground and, while its process is alive (it usually is, to
 * receive messages), every [CHECK_PERIOD_MIN] minutes: if the chosen interval has passed, a backup runs.
 */
class AutoBackup(
    classLoader: ClassLoader,
    preferences: SharedPreferences
) : Feature(classLoader, preferences) {

    private val running = AtomicBoolean(false)

    override fun getPluginName(): String = "Auto Backup"

    override fun doHook() {
        if (!xprefs.getBoolean("auto_backup", false)) return

        WppCore.addListenerActivity(object : WppCore.ActivityChangeState {
            override fun onChange(activity: Activity, type: WppCore.ActivityChangeState.ChangeType) {
                if (type == WppCore.ActivityChangeState.ChangeType.RESUMED) maybeRun()
            }
        })

        // Also run while WhatsApp is only alive in the background, so the schedule does not depend
        // on the user opening the app.
        Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "WAE-AutoBackup").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }
        }.scheduleWithFixedDelay({
            try {
                maybeRun()
            } catch (t: Throwable) {
                logDebug(t)
            }
        }, FIRST_CHECK_DELAY_MIN, CHECK_PERIOD_MIN, TimeUnit.MINUTES)
    }

    private fun maybeRun() {
        val password = xprefs.getString("auto_backup_password", "").orEmpty()
        if (password.isBlank()) return
        val intervalHours = xprefs.getString("auto_backup_interval", "24")?.toLongOrNull() ?: 24L
        val keep = xprefs.getString("auto_backup_keep", "3")?.toIntOrNull() ?: 3
        val includeMedia = xprefs.getBoolean("auto_backup_media", false)

        val now = System.currentTimeMillis()
        val last = WppCore.getPrivString(LAST_KEY, "0")?.toLongOrNull() ?: 0L
        if (now - last < intervalHours * HOUR_MS) return
        // After a failure wait before retrying, so a persistent problem does not notify every check.
        val failedAt = WppCore.getPrivString(FAIL_KEY, "0")?.toLongOrNull() ?: 0L
        if (now - failedAt < RETRY_AFTER_FAILURE_HOURS * HOUR_MS) return
        if (!running.compareAndSet(false, true)) return

        Thread {
            try {
                val backup = EncryptedBackup(Utils.application)
                val chars = password.toCharArray()
                val db = backup.createDatabaseBackup(chars, keep)
                var total = db.sizeBytes
                if (includeMedia) {
                    val since = WppCore.getPrivString(MEDIA_KEY, "0")?.toLongOrNull() ?: 0L
                    backup.createMediaBackup(chars, since)?.let { total += it.sizeBytes }
                    WppCore.setPrivString(MEDIA_KEY, now.toString())
                }
                WppCore.setPrivString(LAST_KEY, now.toString())
                WppCore.setPrivString(FAIL_KEY, "0")
                val size = "%.1f MB".format(total / 1048576.0)
                toast(R.string.auto_backup_done, size)
                notify(R.string.auto_backup_done, size)
            } catch (e: Throwable) {
                logDebug(e)
                WppCore.setPrivString(FAIL_KEY, System.currentTimeMillis().toString())
                val reason = e.message ?: e.javaClass.simpleName
                toast(R.string.auto_backup_failed, reason)
                notify(R.string.auto_backup_failed, reason)
            } finally {
                running.set(false)
            }
        }.apply {
            priority = Thread.MIN_PRIORITY
            isDaemon = true
            start()
        }
    }

    private fun toast(res: Int, arg: String) {
        // Only while WhatsApp is on screen; in the background the notification carries the result.
        val activity = WppCore.getCurrentActivity() ?: return
        Utils.showToast(activity.getString(res, arg), Toast.LENGTH_LONG)
    }

    /** Posts the result as a notification, so a backup that ran in the background is not silent. */
    private fun notify(res: Int, arg: String) {
        if (!xprefs.getBoolean("auto_backup_notify", true)) return
        try {
            val context = Utils.application
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "WaToolkit backups", NotificationManager.IMPORTANCE_LOW)
            )
            val text = context.getString(res, arg)
            val notification = Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(Icon.createWithResource("android", android.R.drawable.stat_sys_download_done))
                .setContentTitle("WaToolkit")
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .build()
            manager.notify(NOTIFICATION_ID, notification)
        } catch (t: Throwable) {
            logDebug(t) // Notifications are optional (the permission may be denied).
        }
    }

    private companion object {
        private const val HOUR_MS = 3_600_000L
        private const val LAST_KEY = "auto_backup_last"
        private const val MEDIA_KEY = "auto_backup_media_last"
        private const val FAIL_KEY = "auto_backup_failed_at"
        private const val RETRY_AFTER_FAILURE_HOURS = 6L
        private const val CHANNEL_ID = "watoolkit_backup"
        private const val NOTIFICATION_ID = 0x7a7b01
        private const val FIRST_CHECK_DELAY_MIN = 2L
        private const val CHECK_PERIOD_MIN = 30L
    }
}
