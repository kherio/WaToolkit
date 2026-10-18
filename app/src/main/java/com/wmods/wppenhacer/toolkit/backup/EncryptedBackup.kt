package com.wmods.wppenhacer.toolkit.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.wmods.wppenhacer.toolkit.Host
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Creates encrypted backups of WhatsApp's data inside the WhatsApp process.
 *
 * Two kinds of archive are written to `<WhatsApp media dir>/WaToolkit/Backups/Auto`:
 *  - `WaToolkit-db-<stamp>.wtbak`: the chat databases (always a full snapshot, only the newest N are kept).
 *  - `WaToolkit-media-<stamp>.wtbak`: media files changed since the previous media backup (incremental,
 *    never deleted automatically, because older archives hold the older files).
 * Each archive is a ZIP stream encrypted with [BackupCrypto].
 */
class EncryptedBackup(private val context: Context) {

    class NoSpaceException : IOException("Not enough free space")

    data class Result(val file: File, val sizeBytes: Long, val files: Int)

    private val stamp get() = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())

    private val outDir: File get() = File(Host.backupRoot(context), "Auto").apply { mkdirs() }

    fun createDatabaseBackup(password: CharArray, keep: Int): Result {
        val dbDir = File(Host.accountDataDir(context), "databases")
        val sources = DATABASES.map { File(dbDir, it) }.filter { it.exists() }
        if (sources.none { it.name == "msgstore.db" }) throw IOException("msgstore.db not found")
        val dir = outDir
        if (dir.usableSpace < sources.sumOf { it.length() } * 3) throw NoSpaceException()

        val result = writeArchive(dir, "WaToolkit-db-$stamp.wtbak", password, true) { zip ->
            var count = 0
            for (db in sources) {
                addDatabase(zip, db)
                count++
            }
            count
        }
        prune(dir, "WaToolkit-db-", keep)
        return result
    }

    /** Backs up media files modified after [sinceMs]. Returns null when there is nothing new. */
    fun createMediaBackup(password: CharArray, sinceMs: Long): Result? {
        val mediaRoot = File(Host.rootWhatsAppDir(context), "Media")
        val files = mediaRoot.walkTopDown()
            .onEnter { !it.name.startsWith(".") || it == mediaRoot }
            .filter { it.isFile && !it.name.startsWith(".") && it.lastModified() > sinceMs }
            .toList()
        if (files.isEmpty()) return null
        val dir = outDir
        if (dir.usableSpace < files.sumOf { it.length() } * 11 / 10) throw NoSpaceException()

        return writeArchive(dir, "WaToolkit-media-$stamp.wtbak", password, false) { zip ->
            var count = 0
            for (file in files) {
                try {
                    addFile(zip, "media/" + file.relativeTo(mediaRoot).path.replace('\\', '/'), file)
                    count++
                } catch (_: IOException) {
                    // A file that disappears or cannot be read is skipped.
                }
            }
            count
        }
    }

    private fun writeArchive(
        dir: File,
        name: String,
        password: CharArray,
        requireDatabase: Boolean,
        fill: (ZipOutputStream) -> Int
    ): Result {
        val part = File(dir, "$name.part")
        val target = File(dir, name)
        var count = 0
        try {
            val encrypted: OutputStream = BackupCrypto.EncryptingOutputStream(FileOutputStream(part), password)
            ZipOutputStream(BufferedOutputStream(encrypted)).use { zip ->
                zip.setLevel(1)
                zip.putNextEntry(ZipEntry("info.txt"))
                zip.write("WaToolkit backup\npackage=${context.packageName}\ncreated=${Date()}\n".toByteArray())
                zip.closeEntry()
                count = fill(zip)
            }
            // Read the archive back before trusting it: a backup that cannot be restored is worse than none.
            part.inputStream().buffered().use {
                BackupArchive.verify(it, password, context.cacheDir, requireDatabase)
            }
            if (!part.renameTo(target)) throw IOException("Could not finalize the backup")
        } catch (e: Throwable) {
            part.delete()
            throw e
        }
        return Result(target, target.length(), count)
    }

    /** Uses VACUUM INTO for a consistent snapshot of a live database; falls back to a plain copy. */
    private fun addDatabase(zip: ZipOutputStream, db: File) {
        val snapshot = File(context.cacheDir, "snapshot-${db.name}")
        snapshot.delete()
        var source = db
        try {
            SQLiteDatabase.openDatabase(db.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use {
                it.execSQL("VACUUM INTO '${snapshot.absolutePath.replace("'", "''")}'")
            }
            if (snapshot.exists() && snapshot.length() > 0) source = snapshot
        } catch (_: Throwable) {
            snapshot.delete()
        }
        try {
            addFile(zip, "databases/${db.name}", source)
        } finally {
            snapshot.delete()
        }
    }

    private fun addFile(zip: ZipOutputStream, entryName: String, file: File) {
        zip.putNextEntry(ZipEntry(entryName))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    /** Encrypted database backups in the Auto folder, newest first. */
    fun listDatabaseBackups(): List<File> =
        (outDir.listFiles { f -> f.isFile && f.name.startsWith("WaToolkit-db-") && f.name.endsWith(".wtbak") }
            ?: emptyArray()).sortedByDescending { it.name }

    /**
     * Decrypts [file] and extracts its databases into [outDir], ready for [DbBackup.restore].
     * Throws [BackupCrypto.WrongPasswordException] for a wrong password.
     */
    fun extractDatabases(file: File, password: CharArray, outDir: File): List<String> =
        file.inputStream().buffered().use {
            BackupArchive.extractDatabases(it, password, context.cacheDir, outDir)
        }

    private fun prune(dir: File, prefix: String, keep: Int) {
        val old = (dir.listFiles { f -> f.isFile && f.name.startsWith(prefix) && f.name.endsWith(".wtbak") }
            ?: emptyArray()).sortedByDescending { it.name }
        old.drop(keep.coerceAtLeast(1)).forEach { it.delete() }
    }

    private companion object {
        private val DATABASES = listOf("msgstore.db", "wa.db", "chatsettingsstore.db")
    }
}
