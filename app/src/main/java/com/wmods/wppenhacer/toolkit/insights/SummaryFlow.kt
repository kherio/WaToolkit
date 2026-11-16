package com.wmods.wppenhacer.toolkit.insights

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.ArrayAdapter
import android.widget.ScrollView
import android.widget.Spinner
import com.wmods.wppenhacer.toolkit.Dlg
import com.wmods.wppenhacer.toolkit.Host
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Summarises the recent messages of a chat with Groq (the API key is the one used for audio transcription). */
class SummaryFlow(private val activity: Activity, private val groqKey: String) {

    private val env = InsightsEnv(activity)
    private var chats: List<InsightsDb.Chat> = emptyList()
    private val periods = listOf(
        1L to tr("Last 24 hours", "Últimas 24 horas"),
        3L to tr("Last 3 days", "Últimos 3 días"),
        7L to tr("Last 7 days", "Últimos 7 días")
    )

    fun start() {
        if (groqKey.isBlank()) {
            Dlg(activity).setTitle(tr("Summary", "Resumen"))
                .setMessage(
                    tr(
                        "Summaries use Groq. Add your Groq API key in the module (Media > Audio transcription) first.",
                        "Los resúmenes usan Groq. Primero añade tu clave de API de Groq en el módulo (Medios > Transcripción de audio)."
                    )
                )
                .setPositiveButton(tr("Close", "Cerrar"), null).show()
            return
        }
        InsightsUi.background(activity, tr("Summary", "Resumen"), { env.load(); env.db.chats() }) { loaded ->
            chats = loaded
            pickChat()
        }
    }

    private fun pickChat() {
        val filter = InsightsUi.edit(activity, tr("Filter by name", "Filtrar por nombre"))
        var shown = chats
        val names = chats.map { env.chatName(it) }
        val rowsOf = { list: List<InsightsDb.Chat> -> list.map { env.chatName(it) to if (it.isGroup) tr("Group", "Grupo") else "" } }
        var dlg: Dlg? = null
        val lv = android.widget.ListView(activity)
        fun refresh() {
            val q = normalizeText(filter.text.toString())
            shown = if (q.isEmpty()) chats.take(100) else chats.filterIndexed { i, _ -> normalizeText(names[i]).contains(q) }.take(100)
            val rows = rowsOf(shown)
            lv.adapter = ArrayAdapter(activity, android.R.layout.simple_list_item_2, android.R.id.text1, rows.map { it.first })
        }
        filter.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) = refresh()
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        lv.setOnItemClickListener { _, _, pos, _ -> dlg?.dismiss(); choosePeriod(shown[pos]) }
        refresh()
        dlg = Dlg(activity).setTitle(tr("Which chat?", "¿Qué chat?"))
            .setView(InsightsUi.column(activity, filter, lv))
            .setNegativeButton(tr("Cancel", "Cancelar"), null)
        dlg.show()
    }

    private fun choosePeriod(chat: InsightsDb.Chat) {
        val spinner = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, periods.map { it.second })
        }
        Dlg(activity).setTitle(env.chatName(chat))
            .setView(
                InsightsUi.column(
                    activity, spinner,
                    InsightsUi.label(
                        activity,
                        tr(
                            "Up to 400 recent text messages will be sent to Groq to write the summary.",
                            "Se enviarán a Groq hasta 400 mensajes de texto recientes para redactar el resumen."
                        ), 12f, 0.7f
                    )
                )
            )
            .setPositiveButton(tr("Summarise", "Resumir")) { _, _ ->
                val since = System.currentTimeMillis() - periods[spinner.selectedItemPosition].first * 86_400_000L
                summarise(chat, since)
            }
            .setNegativeButton(tr("Cancel", "Cancelar"), null).show()
    }

    private fun summarise(chat: InsightsDb.Chat, since: Long) {
        InsightsUi.background(activity, tr("Summarising…", "Resumiendo…"), {
            val lines = env.db.textLines(chat.rowId, since)
            if (lines.isEmpty()) null else {
                val me = tr("Me", "Yo")
                val transcript = buildTranscript(lines.map {
                    val who = if (it.fromMe) me else if (chat.isGroup) env.personName(it.senderJid) else env.chatName(chat)
                    Triple(it.ts, who, it.text)
                })
                request(transcript)
            }
        }) { summary ->
            if (summary == null) {
                Host.toast(activity, tr("No text messages in that period", "No hay mensajes de texto en ese periodo"))
                return@background
            }
            Dlg(activity).setTitle(env.chatName(chat))
                .setView(ScrollView(activity).apply { addView(InsightsUi.label(activity, summary).also {
                    it.setPadding(InsightsUi.dp(activity, 20), InsightsUi.dp(activity, 12), InsightsUi.dp(activity, 20), InsightsUi.dp(activity, 12)) }) })
                .setPositiveButton(tr("Close", "Cerrar"), null)
                .setNegativeButton(tr("Copy", "Copiar")) { _, _ ->
                    (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("summary", summary))
                }.show()
        }
    }

    private fun request(transcript: String): String {
        val language = Locale.getDefault().displayLanguage
        val body = JSONObject()
            .put("model", MODEL)
            .put("temperature", 0.3)
            .put(
                "messages", JSONArray()
                    .put(
                        JSONObject().put("role", "system").put(
                            "content",
                            "You summarise WhatsApp conversations. Reply in $language. Give the key points as short bullet " +
                                "points, then list decisions made and questions still unanswered if there are any. Be faithful: " +
                                "do not invent anything."
                        )
                    )
                    .put(JSONObject().put("role", "user").put("content", transcript))
            )
        val req = Request.Builder().url(URL)
            .header("Authorization", "Bearer $groqKey")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        CLIENT.newCall(req).execute().use { r ->
            val text = r.body.string()
            if (!r.isSuccessful) throw IllegalStateException("Groq ${r.code}: ${text.take(200)}")
            return JSONObject(text).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
        }
    }

    companion object {
        private const val URL = "https://api.groq.com/openai/v1/chat/completions"
        private const val MODEL = "llama-3.3-70b-versatile"
        private const val MAX_CHARS = 24_000
        private val CLIENT by lazy { OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build() }

        /** "[dd/MM HH:mm] Name: text" per message, keeping the most recent ones within [MAX_CHARS]. */
        fun buildTranscript(messages: List<Triple<Long, String, String>>): String {
            val fmt = SimpleDateFormat("dd/MM HH:mm", Locale.US)
            val out = ArrayList<String>()
            var size = 0
            for (m in messages.asReversed()) {
                val line = "[${fmt.format(Date(m.first))}] ${m.second}: ${m.third.replace('\n', ' ')}"
                if (size + line.length > MAX_CHARS) break
                out += line
                size += line.length + 1
            }
            return out.asReversed().joinToString("\n")
        }
    }
}
