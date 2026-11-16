package com.wmods.wppenhacer.toolkit.insights

import android.app.Activity
import android.widget.ScrollView
import com.wmods.wppenhacer.toolkit.Dlg
import java.text.DateFormat
import java.util.Date

/** Usage statistics computed locally from msgstore.db. */
class StatsFlow(private val activity: Activity) {

    fun start() {
        val env = InsightsEnv(activity)
        InsightsUi.background(activity, tr("Statistics", "Estadísticas"), {
            env.load(); Triple(env.db.stats(), env.db.chats().associateBy { it.rowId }, env)
        }) { (s, chats, e) ->
            val days = if (s.firstTs > 0) ((System.currentTimeMillis() - s.firstTs) / 86_400_000L).coerceAtLeast(1) else 1
            val df = DateFormat.getDateInstance(DateFormat.MEDIUM)
            val weekdays = tr("Sun,Mon,Tue,Wed,Thu,Fri,Sat", "dom,lun,mar,mié,jue,vie,sáb").split(",")
            val busyDay = s.byWeekday.indices.maxByOrNull { s.byWeekday[it] } ?: 0
            val busyHour = s.byHour.indices.maxByOrNull { s.byHour[it] } ?: 0
            val types = mapOf(
                0 to tr("Text", "Texto"), 1 to tr("Photos", "Fotos"), 2 to tr("Voice notes", "Notas de voz"),
                3 to tr("Videos", "Vídeos"), 9 to tr("Documents", "Documentos"), 20 to tr("Stickers", "Stickers"),
                13 to "GIF", 5 to tr("Locations", "Ubicaciones"), 4 to tr("Contacts", "Contactos")
            )
            val text = buildString {
                appendLine(tr("Messages: %d (sent %d, received %d)", "Mensajes: %d (enviados %d, recibidos %d)")
                    .format(s.total, s.sent, s.total - s.sent))
                if (s.firstTs > 0) appendLine(tr("Since %s · %d per day on average", "Desde %s · %d al día de media")
                    .format(df.format(Date(s.firstTs)), s.total / days))
                appendLine(tr("Last 30 days: %d", "Últimos 30 días: %d").format(s.last30))
                appendLine(tr("Busiest day: %s · busiest hour: %02d:00", "Día más activo: %s · hora más activa: %02d:00")
                    .format(weekdays[busyDay], busyHour))
                appendLine()
                appendLine(tr("By type", "Por tipo"))
                s.byType.entries.take(8).forEach { (t, n) -> appendLine("  " + (types[t] ?: tr("Other", "Otros")) + ": " + n) }
                appendLine()
                appendLine(tr("Most active chats", "Chats más activos"))
                s.topChats.forEachIndexed { i, (id, n) ->
                    val name = chats[id]?.let { e.chatName(it) } ?: "#$id"
                    appendLine("  ${i + 1}. $name: $n")
                }
            }
            Dlg(activity).setTitle(tr("Your WhatsApp in numbers", "Tu WhatsApp en cifras"))
                .setView(ScrollView(activity).apply {
                    addView(InsightsUi.label(activity, text, 14f).also { it.setPadding(
                        InsightsUi.dp(activity, 20), InsightsUi.dp(activity, 12), InsightsUi.dp(activity, 20), InsightsUi.dp(activity, 12)) })
                })
                .setPositiveButton(tr("Close", "Cerrar"), null).show()
        }
    }
}
