package com.wmods.wppenhacer.toolkit.insights

import android.database.sqlite.SQLiteDatabase
import java.io.File

/** Read-only queries on msgstore.db for the search, storage, statistics and summary tools. */
class InsightsDb(private val dbFile: File) {

    class Chat(val rowId: Long, val jid: String, val isGroup: Boolean, val subject: String?, val lastTs: Long)

    enum class Kind { TEXT, LINKS, IMAGES, VIDEOS, AUDIO, DOCS }

    class Filter(
        val text: String, val kind: Kind, val chatRowIds: List<Long>?, val fromTs: Long?
    )

    class Hit(
        val rowId: Long, val chatRowId: Long, val chatJid: String, val fromMe: Boolean, val senderJid: String?,
        val ts: Long, val text: String?, val sortId: Long?, val file: String?
    )

    class MediaFile(val path: String, val chatRowId: Long)

    class ChatStorage(
        val chatRowId: Long, val bytes: Long, val files: Int, val byCategory: Map<String, Long>
    )

    class Stats(
        val total: Long, val sent: Long, val firstTs: Long, val last30: Long,
        val byType: Map<Int, Long>, val topChats: List<Pair<Long, Long>>,
        val byWeekday: IntArray, val byHour: IntArray
    )

    class Line(val ts: Long, val fromMe: Boolean, val senderJid: String?, val text: String)

    private fun open(): SQLiteDatabase =
        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)

    private fun columns(db: SQLiteDatabase, table: String): Set<String> {
        val out = HashSet<String>()
        try {
            db.rawQuery("PRAGMA table_info($table)", null).use { c -> while (c.moveToNext()) c.getString(1)?.let { out += it } }
        } catch (_: Exception) {
        }
        return out
    }

    fun chats(): List<Chat> = open().use { db ->
        val cc = columns(db, "chat")
        val subject = if ("subject" in cc) "c.subject" else "NULL"
        val last = if ("sort_timestamp" in cc) "c.sort_timestamp" else "0"
        val out = ArrayList<Chat>()
        db.rawQuery(
            "SELECT c._id, j.raw_string, j.server, $subject, $last FROM chat c JOIN jid j ON j._id = c.jid_row_id", null
        ).use { c ->
            while (c.moveToNext()) {
                val jid = c.getString(1) ?: continue
                out += Chat(c.getLong(0), jid, c.getString(2) == "g.us", c.getString(3), c.getLong(4))
            }
        }
        out.sortedByDescending { it.lastTs }
    }

    fun search(f: Filter, limit: Int = 300): List<Hit> = open().use { db ->
        val mc = columns(db, "message")
        val sender = if ("sender_jid_row_id" in mc) "LEFT JOIN jid sj ON sj._id = NULLIF(m.sender_jid_row_id, 0)" else ""
        val senderRaw = if ("sender_jid_row_id" in mc) "sj.raw_string" else "NULL"
        val sort = if ("sort_id" in mc) "m.sort_id" else "NULL"
        val where = StringBuilder("WHERE m.message_type != 7")
        val args = ArrayList<String>()
        val like = "%" + f.text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        when (f.kind) {
            Kind.TEXT -> {
                where.append(" AND m.text_data IS NOT NULL AND m.text_data != ''")
            }
            Kind.LINKS -> where.append(" AND m.text_data LIKE '%http%'")
            Kind.IMAGES -> where.append(" AND mm.mime_type LIKE 'image/%'")
            Kind.VIDEOS -> where.append(" AND mm.mime_type LIKE 'video/%'")
            Kind.AUDIO -> where.append(" AND mm.mime_type LIKE 'audio/%'")
            Kind.DOCS -> where.append(" AND (mm.mime_type LIKE 'application/%' OR mm.mime_type LIKE 'text/%')")
        }
        if (f.text.isNotBlank()) {
            where.append(" AND (m.text_data LIKE ? ESCAPE '\\' OR mm.file_path LIKE ? ESCAPE '\\')")
            args += like; args += like
        }
        if (!f.chatRowIds.isNullOrEmpty()) where.append(" AND m.chat_row_id IN (${f.chatRowIds.joinToString(",")})")
        if (f.fromTs != null) where.append(" AND m.timestamp >= ${f.fromTs}")
        args += limit.toString()
        val out = ArrayList<Hit>()
        db.rawQuery(
            "SELECT m._id, m.chat_row_id, cj.raw_string, m.from_me, $senderRaw, m.timestamp, m.text_data, $sort, " +
                "mm.file_path FROM message m JOIN chat c ON c._id = m.chat_row_id JOIN jid cj ON cj._id = c.jid_row_id " +
                "LEFT JOIN message_media mm ON mm.message_row_id = m._id $sender $where " +
                "ORDER BY m.timestamp DESC LIMIT ?",
            args.toTypedArray()
        ).use { c ->
            while (c.moveToNext()) {
                out += Hit(
                    c.getLong(0), c.getLong(1), c.getString(2) ?: continue, c.getInt(3) == 1, c.getString(4),
                    c.getLong(5), c.getString(6), if (c.isNull(7)) null else c.getLong(7), c.getString(8)
                )
            }
        }
        out
    }

    private val categorySql = "CASE WHEN mm.mime_type LIKE 'image/%' THEN 'image' WHEN mm.mime_type LIKE 'video/%' " +
        "THEN 'video' WHEN mm.mime_type LIKE 'audio/%' THEN 'audio' ELSE 'doc' END"

    /** Media size per chat, with the breakdown by category (image, video, audio, doc). */
    fun storage(): List<ChatStorage> = open().use { db ->
        val bytes = HashMap<Long, Long>()
        val files = HashMap<Long, Int>()
        val cats = HashMap<Long, HashMap<String, Long>>()
        db.rawQuery(
            "SELECT m.chat_row_id, $categorySql, SUM(mm.file_size), COUNT(*) FROM message_media mm " +
                "JOIN message m ON m._id = mm.message_row_id WHERE mm.file_size > 0 AND mm.file_path IS NOT NULL " +
                "GROUP BY m.chat_row_id, 2", null
        ).use { c ->
            while (c.moveToNext()) {
                val chat = c.getLong(0)
                bytes[chat] = (bytes[chat] ?: 0L) + c.getLong(2)
                files[chat] = (files[chat] ?: 0) + c.getInt(3)
                cats.getOrPut(chat) { HashMap() }[c.getString(1)] = c.getLong(2)
            }
        }
        bytes.keys.map { ChatStorage(it, bytes[it]!!, files[it] ?: 0, cats[it] ?: emptyMap()) }
            .sortedByDescending { it.bytes }
    }

    /** Media files of [chatRowId] (all chats if null) in the given categories, sent before [beforeTs]. */
    fun mediaFiles(chatRowId: Long?, categories: Set<String>, beforeTs: Long): List<MediaFile> = open().use { db ->
        if (categories.isEmpty()) return@use emptyList()
        val where = StringBuilder("WHERE mm.file_path IS NOT NULL AND m.timestamp < $beforeTs")
        if (chatRowId != null) where.append(" AND m.chat_row_id = $chatRowId")
        where.append(" AND ($categorySql) IN (${categories.joinToString(",") { "'" + it.replace("'", "") + "'" }})")
        val out = ArrayList<MediaFile>()
        db.rawQuery(
            "SELECT mm.file_path, m.chat_row_id FROM message_media mm JOIN message m ON m._id = mm.message_row_id $where", null
        ).use { c -> while (c.moveToNext()) c.getString(0)?.let { out += MediaFile(it, c.getLong(1)) } }
        out
    }

    fun stats(now: Long = System.currentTimeMillis()): Stats = open().use { db ->
        var total = 0L; var sent = 0L; var first = 0L
        db.rawQuery(
            "SELECT COUNT(*), COALESCE(SUM(from_me), 0), COALESCE(MIN(NULLIF(timestamp, 0)), 0) " +
                "FROM message WHERE message_type != 7", null
        ).use { if (it.moveToFirst()) { total = it.getLong(0); sent = it.getLong(1); first = it.getLong(2) } }
        var last30 = 0L
        db.rawQuery(
            "SELECT COUNT(*) FROM message WHERE message_type != 7 AND timestamp >= ?",
            arrayOf((now - 30L * 24 * 3600 * 1000).toString())
        ).use { if (it.moveToFirst()) last30 = it.getLong(0) }
        val byType = LinkedHashMap<Int, Long>()
        db.rawQuery(
            "SELECT message_type, COUNT(*) FROM message WHERE message_type != 7 GROUP BY message_type ORDER BY 2 DESC", null
        ).use { while (it.moveToNext()) byType[it.getInt(0)] = it.getLong(1) }
        val top = ArrayList<Pair<Long, Long>>()
        db.rawQuery(
            "SELECT chat_row_id, COUNT(*) FROM message WHERE message_type != 7 GROUP BY chat_row_id ORDER BY 2 DESC LIMIT 10", null
        ).use { while (it.moveToNext()) top += it.getLong(0) to it.getLong(1) }
        val weekday = IntArray(7)
        db.rawQuery(
            "SELECT CAST(strftime('%w', timestamp / 1000, 'unixepoch', 'localtime') AS INTEGER), COUNT(*) " +
                "FROM message WHERE message_type != 7 AND timestamp > 0 GROUP BY 1", null
        ).use { while (it.moveToNext()) it.getInt(0).let { d -> if (d in 0..6) weekday[d] = it.getInt(1) } }
        val hour = IntArray(24)
        db.rawQuery(
            "SELECT CAST(strftime('%H', timestamp / 1000, 'unixepoch', 'localtime') AS INTEGER), COUNT(*) " +
                "FROM message WHERE message_type != 7 AND timestamp > 0 GROUP BY 1", null
        ).use { while (it.moveToNext()) it.getInt(0).let { h -> if (h in 0..23) hour[h] = it.getInt(1) } }
        Stats(total, sent, first, last30, byType, top, weekday, hour)
    }

    /** Text messages of a chat from [sinceTs], oldest first (the latest [limit]). */
    fun textLines(chatRowId: Long, sinceTs: Long, limit: Int = 400): List<Line> = open().use { db ->
        val mc = columns(db, "message")
        val sender = if ("sender_jid_row_id" in mc) "LEFT JOIN jid sj ON sj._id = NULLIF(m.sender_jid_row_id, 0)" else ""
        val senderRaw = if ("sender_jid_row_id" in mc) "sj.raw_string" else "NULL"
        val out = ArrayList<Line>()
        db.rawQuery(
            "SELECT m.timestamp, m.from_me, $senderRaw, m.text_data FROM message m $sender " +
                "WHERE m.chat_row_id = ? AND m.message_type = 0 AND m.text_data IS NOT NULL AND m.text_data != '' " +
                "AND m.timestamp >= ? ORDER BY m.timestamp DESC LIMIT ?",
            arrayOf(chatRowId.toString(), sinceTs.toString(), limit.toString())
        ).use { while (it.moveToNext()) out += Line(it.getLong(0), it.getInt(1) == 1, it.getString(2), it.getString(3)) }
        out.reversed()
    }
}
