package com.wmods.wppenhacer.toolkit.deleted

import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * Local log with the text of the messages.
 *
 * WhatsApp removes a message's content when it is deleted for everyone, so afterwards it can no
 * longer be read. To be able to show it, this log keeps a copy of each message's text in
 * its own table (`waimport_log`) in msgstore.db. SQLite makes the copy with two triggers, so
 * it does not depend on any hook or on the WhatsApp version. Nothing leaves the device.
 *
 * It can be removed at any time: [disable] drops the triggers and the table.
 */
class DeletedLog(private val dbFile: File) {

    class Status(val enabled: Boolean, val messages: Long)

    private fun openRead(): SQLiteDatabase =
        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)

    private fun openWrite(): SQLiteDatabase {
        val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            db.rawQuery("PRAGMA busy_timeout = 5000;", null).close()
        } catch (_: Exception) {
        }
        return db
    }

    fun status(): Status {
        if (!dbFile.exists()) return Status(false, 0)
        return try {
            openRead().use { db ->
                var triggers = 0
                db.rawQuery(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type = 'trigger' AND name IN ('$TRG_INSERT', '$TRG_UPDATE')",
                    null
                ).use { c -> if (c.moveToFirst()) triggers = c.getInt(0) }
                var table = false
                var count = 0L
                try {
                    db.rawQuery("SELECT COUNT(*) FROM $TABLE", null).use { c ->
                        if (c.moveToFirst()) { table = true; count = c.getLong(0) }
                    }
                } catch (_: Exception) {
                }
                Status(triggers == 2 && table, count)
            }
        } catch (_: Exception) {
            Status(false, 0)
        }
    }

    /**
     * Enables the log and copies the texts of the last [backfillDays] days (future messages
     * are copied automatically). Returns how many messages were copied now.
     */
    fun enable(backfillDays: Int = 90): Long {
        if (!dbFile.exists()) throw IllegalStateException("msgstore.db not found")
        val db = openWrite()
        try {
            val cols = HashSet<String>()
            db.rawQuery("PRAGMA table_info(message)", null).use { c ->
                while (c.moveToNext()) c.getString(1)?.let { cols += it }
            }
            val needed = listOf("_id", "key_id", "chat_row_id", "from_me", "sender_jid_row_id", "timestamp", "message_type", "text_data")
            val missing = needed.filter { it !in cols }
            if (missing.isNotEmpty()) {
                throw IllegalStateException("This WhatsApp version does not have the expected columns: $missing")
            }

            val copy = "INSERT OR REPLACE INTO $TABLE(message_row_id, key_id, chat_row_id, from_me, " +
                    "sender_jid_row_id, message_type, timestamp, text) VALUES(" +
                    "NEW._id, NEW.key_id, NEW.chat_row_id, NEW.from_me, NEW.sender_jid_row_id, " +
                    "NEW.message_type, NEW.timestamp, NEW.text_data);"
            val cond = "WHEN NEW.text_data IS NOT NULL AND NEW.text_data != ''"

            db.beginTransaction()
            try {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS $TABLE (message_row_id INTEGER PRIMARY KEY, key_id TEXT, " +
                            "chat_row_id INTEGER, from_me INTEGER, sender_jid_row_id INTEGER, " +
                            "message_type INTEGER, timestamp INTEGER, text TEXT)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS ${TABLE}_key ON $TABLE(key_id)")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS $TRG_INSERT AFTER INSERT ON message $cond BEGIN $copy END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS $TRG_UPDATE AFTER UPDATE OF text_data ON message $cond BEGIN $copy END")

                val cutoff = System.currentTimeMillis() - backfillDays.toLong() * 24 * 60 * 60 * 1000
                db.execSQL(
                    "INSERT OR IGNORE INTO $TABLE(message_row_id, key_id, chat_row_id, from_me, " +
                            "sender_jid_row_id, message_type, timestamp, text) " +
                            "SELECT _id, key_id, chat_row_id, from_me, sender_jid_row_id, message_type, timestamp, text_data " +
                            "FROM message WHERE text_data IS NOT NULL AND text_data != '' AND timestamp >= $cutoff"
                )
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            return status().messages
        } finally {
            db.close()
        }
    }

    /** Drops the triggers and the table. Triggers first, so they never point to a table that does not exist. */
    fun disable() {
        if (!dbFile.exists()) return
        val db = openWrite()
        try {
            db.beginTransaction()
            try {
                db.execSQL("DROP TRIGGER IF EXISTS $TRG_INSERT")
                db.execSQL("DROP TRIGGER IF EXISTS $TRG_UPDATE")
                db.execSQL("DROP TABLE IF EXISTS $TABLE")
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } finally {
            db.close()
        }
    }

    companion object {
        const val TABLE = "waimport_log"
        const val TRG_INSERT = "waimport_log_ins"
        const val TRG_UPDATE = "waimport_log_upd"
    }
}
