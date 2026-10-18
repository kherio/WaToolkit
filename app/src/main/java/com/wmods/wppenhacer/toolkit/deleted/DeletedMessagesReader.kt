package com.wmods.wppenhacer.toolkit.deleted

import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * Reads from msgstore.db the messages that were deleted "for everyone" and who did it.
 *
 * - Individual chat: only the author can delete their message for everyone.
 * - Group: the author or an admin deletes it; WhatsApp stores the admin in
 *   `message_revoked.admin_jid_row_id`.
 *
 * Messages deleted "only for me" leave no trace and cannot be detected.
 */
class DeletedMessagesReader(private val dbFile: File) {

    enum class Source {
        /** `message_revoked` table (has the deletion date and the admin). */
        REVOKED_TABLE,

        /** Fallback: messages marked as deleted in `message` (no date or admin). */
        MESSAGE_TYPE,

        /** No deletion data was found. */
        NONE
    }

    data class Entry(
        val messageRowId: Long,
        val chatRowId: Long,
        val chatJid: String,
        val chatIsGroup: Boolean,
        val chatSubject: String?,
        /** The message was yours. */
        val fromMe: Boolean,
        /** JID of the author; null if it is yours or unknown. */
        val authorJid: String?,
        /** JID of the admin who deleted it, if the author did not. */
        val adminJid: String?,
        val sentAt: Long,
        /** Time of deletion, if WhatsApp stored it. */
        val deletedAt: Long?,
        val keyId: String? = null,
        /** Position of the message in the chat (to open it at that position). */
        val sortId: Long? = null,
        /** Original text, if it could be recovered (WaImport log or search index). */
        val text: String? = null
    ) {
        val deletedByAdmin: Boolean get() = adminJid != null && adminJid != authorJid
    }

    class Result(val entries: List<Entry>, val source: Source)

    private fun openRead(): SQLiteDatabase =
        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)

    private fun columns(db: SQLiteDatabase, table: String): Set<String> {
        val out = HashSet<String>()
        try {
            db.rawQuery("PRAGMA table_info($table)", null).use { c ->
                while (c.moveToNext()) c.getString(1)?.let { out += it }
            }
        } catch (_: Exception) {
        }
        return out
    }

    /** For chats WhatsApp stores with a LID: LID -> phone number of the same contact. */
    fun phoneByLid(): Map<String, String> {
        val out = HashMap<String, String>()
        try {
            openRead().use { db ->
                db.rawQuery(
                    "SELECT l.raw_string, p.raw_string FROM jid_map m " +
                            "JOIN jid l ON l._id = m.lid_row_id JOIN jid p ON p._id = m.jid_row_id", null
                ).use { c ->
                    while (c.moveToNext()) {
                        val lid = c.getString(0) ?: continue
                        val phone = c.getString(1) ?: continue
                        out[lid] = phone
                    }
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    /** Most recent deletions first. */
    fun list(limit: Int = 500): Result {
        if (!dbFile.exists()) return Result(emptyList(), Source.NONE)
        return openRead().use { db ->
            val chatCols = columns(db, "chat")
            val msgCols = columns(db, "message")
            val subject = if ("subject" in chatCols) "c.subject" else "NULL"
            val sender = if ("sender_jid_row_id" in msgCols)
                "LEFT JOIN jid sj ON sj._id = NULLIF(m.sender_jid_row_id, 0)" else ""
            val senderRaw = if ("sender_jid_row_id" in msgCols) "sj.raw_string" else "NULL"
            val sortCol = if ("sort_id" in msgCols) "m.sort_id" else "NULL"

            val rc = columns(db, "message_revoked")
            if ("message_row_id" in rc) {
                val hasAdmin = "admin_jid_row_id" in rc
                val adminJoin = if (hasAdmin) "LEFT JOIN jid aj ON aj._id = NULLIF(r.admin_jid_row_id, 0)" else ""
                val adminRaw = if (hasAdmin) "aj.raw_string" else "NULL"
                val rts = if ("revoke_timestamp" in rc) "NULLIF(r.revoke_timestamp, 0)" else "NULL"
                val found = query(
                    db,
                    "SELECT m._id, m.chat_row_id, cj.raw_string, cj.server, $subject, m.from_me, " +
                            "$senderRaw, $adminRaw, m.timestamp, $rts, m.key_id, $sortCol " +
                            "FROM message_revoked r JOIN message m ON m._id = r.message_row_id " +
                            "JOIN chat c ON c._id = m.chat_row_id JOIN jid cj ON cj._id = c.jid_row_id " +
                            "$sender $adminJoin ORDER BY COALESCE($rts, m.timestamp) DESC LIMIT ?",
                    limit
                )
                if (found.isNotEmpty()) return@use Result(enrich(db, found), Source.REVOKED_TABLE)
            }

            // No deletions table: messages marked as "deleted" (types 15 and 64).
            val fallback = query(
                db,
                "SELECT m._id, m.chat_row_id, cj.raw_string, cj.server, $subject, m.from_me, " +
                        "$senderRaw, NULL, m.timestamp, NULL, m.key_id, $sortCol " +
                        "FROM message m JOIN chat c ON c._id = m.chat_row_id " +
                        "JOIN jid cj ON cj._id = c.jid_row_id $sender " +
                        "WHERE m.message_type IN (15, 64) ORDER BY m.timestamp DESC LIMIT ?",
                limit
            )
            Result(enrich(db, fallback), if (fallback.isEmpty()) Source.NONE else Source.MESSAGE_TYPE)
        }
    }

    private fun tableExists(db: SQLiteDatabase, name: String): Boolean = try {
        db.rawQuery("SELECT 1 FROM sqlite_master WHERE name = ? AND type = 'table'", arrayOf(name))
            .use { it.moveToFirst() }
    } catch (_: Exception) {
        false
    }

    /** Adds the original text: first the WaImport log and, failing that, the search index. */
    private fun enrich(db: SQLiteDatabase, entries: List<Entry>): List<Entry> {
        val hasLog = tableExists(db, DeletedLog.TABLE)
        val hasFts = tableExists(db, "message_ftsv2")
        if (!hasLog && !hasFts) return entries
        return entries.map { e ->
            var text: String? = null
            try {
                if (hasLog) {
                    db.rawQuery(
                        "SELECT text FROM ${DeletedLog.TABLE} WHERE message_row_id = ? " +
                                "OR (key_id = ? AND chat_row_id = ?) LIMIT 1",
                        arrayOf(e.messageRowId.toString(), e.keyId ?: "", e.chatRowId.toString())
                    ).use { c -> if (c.moveToFirst()) text = c.getString(0) }
                }
                if (text.isNullOrEmpty() && hasFts) {
                    db.rawQuery(
                        "SELECT content FROM message_ftsv2 WHERE docid = ?", arrayOf(e.messageRowId.toString())
                    ).use { c -> if (c.moveToFirst()) text = c.getString(0) }
                }
            } catch (_: Exception) {
            }
            if (text.isNullOrEmpty()) e else e.copy(text = text)
        }
    }

    private fun query(db: SQLiteDatabase, sql: String, limit: Int): List<Entry> {
        val out = ArrayList<Entry>()
        try {
            db.rawQuery(sql, arrayOf(limit.toString())).use { c ->
                while (c.moveToNext()) {
                    val chatJid = c.getString(2) ?: continue
                    val isGroup = c.getString(3) == "g.us"
                    val fromMe = c.getInt(5) == 1
                    val senderJid = c.getString(6)
                    // In an individual chat the author of a received message is the chat itself.
                    val author = when {
                        fromMe -> null
                        senderJid != null -> senderJid
                        !isGroup -> chatJid
                        else -> null
                    }
                    out += Entry(
                        c.getLong(0), c.getLong(1), chatJid, isGroup, c.getString(4), fromMe,
                        author, c.getString(7),
                        if (c.isNull(8)) 0L else c.getLong(8),
                        if (c.isNull(9)) null else c.getLong(9),
                        c.getString(10),
                        if (c.isNull(11)) null else c.getLong(11)
                    )
                }
            }
        } catch (_: Exception) {
            return emptyList()
        }
        return out
    }
}
