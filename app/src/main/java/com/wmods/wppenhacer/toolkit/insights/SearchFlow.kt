package com.wmods.wppenhacer.toolkit.insights

import android.app.Activity
import android.widget.ArrayAdapter
import android.widget.Spinner
import com.wmods.wppenhacer.toolkit.Dlg
import com.wmods.wppenhacer.toolkit.Host
import java.text.DateFormat
import java.util.Date

/** Search across every chat with filters: type of content, person or group, and period. */
class SearchFlow(private val activity: Activity) {

    private val kinds = listOf(
        InsightsDb.Kind.TEXT to tr("Text", "Texto"),
        InsightsDb.Kind.LINKS to tr("Links", "Enlaces"),
        InsightsDb.Kind.IMAGES to tr("Photos", "Fotos"),
        InsightsDb.Kind.VIDEOS to tr("Videos", "Vídeos"),
        InsightsDb.Kind.AUDIO to tr("Audio", "Audios"),
        InsightsDb.Kind.DOCS to tr("Documents", "Documentos")
    )
    private val periods = listOf(
        0L to tr("Any date", "Cualquier fecha"),
        7L to tr("Last 7 days", "Últimos 7 días"),
        30L to tr("Last 30 days", "Últimos 30 días"),
        365L to tr("Last year", "Último año")
    )
    private val env = InsightsEnv(activity)
    private var chats: List<InsightsDb.Chat> = emptyList()

    fun start() {
        InsightsUi.background(activity, tr("Search", "Buscar"), {
            env.load(); env.db.chats()
        }) { loaded -> chats = loaded; showForm() }
    }

    private fun showForm() {
        val text = InsightsUi.edit(activity, tr("Words to find (optional)", "Palabras a buscar (opcional)"))
        val who = InsightsUi.edit(activity, tr("Person or group (optional)", "Persona o grupo (opcional)"))
        val kind = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, kinds.map { it.second })
        }
        val period = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, periods.map { it.second })
        }
        Dlg(activity).setTitle(tr("Search all chats", "Buscar en todos los chats"))
            .setView(InsightsUi.column(activity, text, who, kind, period))
            .setPositiveButton(tr("Search", "Buscar")) { _, _ ->
                val q = who.text.toString().trim()
                val ids = if (q.isEmpty()) null else {
                    val n = normalizeText(q)
                    chats.filter { normalizeText(env.chatName(it)).contains(n) }.map { it.rowId }
                }
                if (ids != null && ids.isEmpty()) {
                    Host.toast(activity, tr("No chat matches that name", "Ningún chat coincide con ese nombre"))
                    return@setPositiveButton
                }
                val days = periods[period.selectedItemPosition].first
                val from = if (days == 0L) null else System.currentTimeMillis() - days * 24 * 3600 * 1000
                run(InsightsDb.Filter(text.text.toString().trim(), kinds[kind.selectedItemPosition].first, ids, from))
            }
            .setNegativeButton(tr("Cancel", "Cancelar"), null).show()
    }

    private fun run(filter: InsightsDb.Filter) {
        InsightsUi.background(activity, tr("Searching…", "Buscando…"), { env.db.search(filter) }) { hits ->
            if (hits.isEmpty()) {
                Host.toast(activity, tr("No results", "Sin resultados"))
                return@background
            }
            val chatById = chats.associateBy { it.rowId }
            val df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            val rows = hits.map { h ->
                val chat = chatById[h.chatRowId]
                val chatName = chat?.let { env.chatName(it) } ?: h.chatJid.substringBefore('@')
                val who = when {
                    h.fromMe -> tr("You", "Tú")
                    chat?.isGroup == true -> env.personName(h.senderJid)
                    else -> chatName
                }
                val body = (h.text?.takeIf { it.isNotBlank() } ?: h.file?.substringAfterLast('/') ?: "").replace('\n', ' ')
                "$chatName · $who" to (df.format(Date(h.ts)) + "\n" + body.take(160))
            }
            InsightsUi.list(
                activity, tr("%d results", "%d resultados").format(hits.size), rows
            ) { i ->
                val h = hits[i]
                if (!Host.openConversation(activity, h.chatJid, h.rowId, h.sortId)) {
                    Host.toast(activity, tr("Could not open the chat", "No se pudo abrir el chat"))
                }
            }.show()
        }
    }
}
