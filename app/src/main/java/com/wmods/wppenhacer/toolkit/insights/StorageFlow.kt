package com.wmods.wppenhacer.toolkit.insights

import android.app.Activity
import android.widget.CheckBox
import android.widget.Spinner
import android.widget.ArrayAdapter
import com.wmods.wppenhacer.toolkit.Dlg
import com.wmods.wppenhacer.toolkit.Host
import java.io.File

/**
 * Shows how much media each chat takes and frees space by deleting the media files of a chat
 * (or of every chat) by type and age. Message text and the database are never touched.
 */
class StorageFlow(private val activity: Activity) {

    private val env = InsightsEnv(activity)
    private val categories = listOf(
        "video" to tr("Videos", "Vídeos"), "image" to tr("Photos", "Fotos"),
        "audio" to tr("Audio", "Audios"), "doc" to tr("Documents", "Documentos")
    )
    private val ages = listOf(
        0L to tr("All of it", "Todo"), 30L to tr("Older than 30 days", "Más de 30 días"),
        90L to tr("Older than 90 days", "Más de 90 días"), 365L to tr("Older than 1 year", "Más de 1 año")
    )

    fun start() {
        InsightsUi.background(activity, tr("Storage", "Almacenamiento"), {
            env.load(); env.db.chats() to env.db.storage()
        }) { (chats, storage) ->
            val byId = chats.associateBy { it.rowId }
            val total = storage.sumOf { it.bytes }
            val top = storage.take(60)
            val rows = listOf(
                tr("All chats", "Todos los chats") to
                    formatBytes(total) + " · " + storage.sumOf { it.files } + " " + tr("files", "archivos")
            ) + top.map { s ->
                val name = byId[s.chatRowId]?.let { env.chatName(it) } ?: "#${s.chatRowId}"
                name to formatBytes(s.bytes) + " · " + categories.filter { (s.byCategory[it.first] ?: 0) > 0 }
                    .joinToString(" · ") { it.second + " " + formatBytes(s.byCategory[it.first] ?: 0) }
            }
            InsightsUi.list(
                activity, tr("Media per chat", "Multimedia por chat"), rows,
                InsightsUi.column(
                    activity,
                    InsightsUi.label(activity, tr("Tap a chat to free space. Messages are kept.", "Toca un chat para liberar espacio. Los mensajes se conservan."), 12f, 0.7f)
                )
            ) { i ->
                if (i == 0) clean(null, rows[0].first) else clean(top[i - 1].chatRowId, rows[i].first)
            }.show()
        }
    }

    private fun clean(chatRowId: Long?, title: String) {
        val boxes = categories.map { (_, label) -> CheckBox(activity).apply { text = label; isChecked = false } }
        val age = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, ages.map { it.second })
        }
        Dlg(activity).setTitle(tr("Free space: ", "Liberar espacio: ") + title)
            .setView(InsightsUi.column(activity, *boxes.toTypedArray(), age))
            .setPositiveButton(tr("Review", "Revisar")) { _, _ ->
                val chosen = categories.filterIndexed { idx, _ -> boxes[idx].isChecked }.map { it.first }.toSet()
                if (chosen.isEmpty()) return@setPositiveButton
                val days = ages[age.selectedItemPosition].first
                val before = if (days == 0L) Long.MAX_VALUE else System.currentTimeMillis() - days * 24 * 3600 * 1000
                review(chatRowId, chosen, before)
            }
            .setNegativeButton(tr("Cancel", "Cancelar"), null).show()
    }

    private fun review(chatRowId: Long?, chosen: Set<String>, before: Long) {
        InsightsUi.background(activity, tr("Checking files…", "Comprobando archivos…"), {
            env.db.mediaFiles(chatRowId, chosen, before).map { File(env.mediaRoot, it.path) }.filter { it.isFile }
        }) { files ->
            if (files.isEmpty()) {
                Host.toast(activity, tr("Nothing to delete", "No hay nada que borrar"))
                return@background
            }
            val bytes = files.sumOf { it.length() }
            Dlg(activity).setTitle(tr("Delete media?", "¿Borrar multimedia?"))
                .setMessage(
                    tr(
                        "%d files (%s) will be deleted from this phone. Messages stay in the chat; media sent by others can be downloaded again only if WhatsApp still has it.",
                        "Se borrarán %d archivos (%s) de este móvil. Los mensajes se conservan en el chat; lo recibido solo se podrá volver a descargar si WhatsApp aún lo tiene."
                    ).format(files.size, formatBytes(bytes))
                )
                .setPositiveButton(tr("Delete", "Borrar")) { _, _ ->
                    InsightsUi.background(activity, tr("Deleting…", "Borrando…"), {
                        var freed = 0L
                        files.forEach { f -> val s = f.length(); if (f.delete()) freed += s }
                        freed
                    }) { freed ->
                        Host.toast(activity, tr("Freed %s", "Liberados %s").format(formatBytes(freed)), android.widget.Toast.LENGTH_LONG)
                    }
                }
                .setNegativeButton(tr("Cancel", "Cancelar"), null).show()
        }
    }
}
