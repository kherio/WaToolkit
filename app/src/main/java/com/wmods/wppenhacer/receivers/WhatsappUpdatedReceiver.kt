package com.wmods.wppenhacer.receivers

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.toolkit.WhatsappSupport

/** Tells the user right after WhatsApp updates to a version the module does not list as supported. */
class WhatsappUpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_PACKAGE_REPLACED) return
        val pkg = intent.data?.schemeSpecificPart ?: return
        if (pkg != "com.whatsapp" && pkg != "com.whatsapp.w4b") return
        try {
            val version = context.packageManager.getPackageInfo(pkg, 0).versionName ?: return
            val supported = context.resources.getStringArray(R.array.supported_versions_wpp).toList()
            val level = WhatsappSupport.level(version, supported)
            if (level == WhatsappSupport.Level.LISTED) return
            notify(context, version, level)
        } catch (_: Throwable) {
        }
    }

    private fun notify(context: Context, version: String, level: WhatsappSupport.Level) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.wa_update_channel), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val open = PendingIntent.getActivity(
            context, 0,
            context.packageManager.getLaunchIntentForPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val text = context.getString(
            if (level == WhatsappSupport.Level.KNOWN_BRANCH) R.string.wa_update_known else R.string.wa_update_unknown,
            version
        )
        nm.notify(
            NOTIF_ID,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_round_error_outline_24)
                .setContentTitle(context.getString(R.string.wa_update_title))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
        )
    }

    private companion object {
        const val CHANNEL = "wa_update"
        const val NOTIF_ID = 0x57544B10
    }
}
