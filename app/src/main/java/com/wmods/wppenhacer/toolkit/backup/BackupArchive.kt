package com.wmods.wppenhacer.toolkit.backup

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/**
 * Reads the archives written by [EncryptedBackup]: checks that one can be decrypted and holds a
 * usable database, and extracts the databases from it. Pure JVM so it can be unit tested.
 */
object BackupArchive {

    class InvalidBackupException(message: String) : IOException(message)

    /** Database files that are allowed to be extracted from an archive. */
    val DATABASES = listOf("msgstore.db", "wa.db", "chatsettingsstore.db")

    private val SQLITE_MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.ISO_8859_1)

    /**
     * Decrypts the whole archive (every chunk is authenticated, so a flipped bit or a truncated
     * file fails here). With [requireDatabase] it also checks that the ZIP holds a msgstore.db
     * that starts with the SQLite header.
     */
    fun verify(input: InputStream, password: CharArray, workDir: File, requireDatabase: Boolean) {
        if (!requireDatabase) {
            // Large (media) archives: authenticate every chunk without storing the plaintext.
            BackupCrypto.decrypt(input, object : java.io.OutputStream() {
                override fun write(b: Int) {}
                override fun write(b: ByteArray, off: Int, len: Int) {}
            }, password)
            return
        }
        val plain = File.createTempFile("wt-verify", ".zip", workDir)
        try {
            decryptTo(input, password, plain)
            var found = false
            forEachEntry(plain) { name, stream ->
                if (name == "databases/msgstore.db") {
                    checkSqlite(stream)
                    found = true
                }
            }
            if (!found) throw InvalidBackupException("The backup does not contain msgstore.db")
        } finally {
            plain.delete()
        }
    }

    /**
     * Extracts the databases of an archive into [outDir] (as `msgstore.db`, `wa.db`, ...).
     * Only the names in [DATABASES] are written, so a crafted archive cannot write elsewhere.
     * Returns the names extracted.
     */
    fun extractDatabases(input: InputStream, password: CharArray, workDir: File, outDir: File): List<String> {
        val plain = File.createTempFile("wt-extract", ".zip", workDir)
        val written = ArrayList<String>()
        try {
            decryptTo(input, password, plain)
            outDir.mkdirs()
            forEachEntry(plain) { name, stream ->
                val base = name.removePrefix("databases/")
                if (name.startsWith("databases/") && base in DATABASES) {
                    FileOutputStream(File(outDir, base)).use { stream.copyTo(it) }
                    written.add(base)
                }
            }
            val ms = File(outDir, "msgstore.db")
            if (!ms.exists()) throw InvalidBackupException("The backup does not contain msgstore.db")
            ms.inputStream().use { checkSqlite(it) }
        } catch (e: Throwable) {
            outDir.deleteRecursively()
            throw e
        } finally {
            plain.delete()
        }
        return written
    }

    private fun decryptTo(input: InputStream, password: CharArray, target: File) {
        FileOutputStream(target).use { BackupCrypto.decrypt(input, it, password) }
    }

    private fun checkSqlite(stream: InputStream) {
        val header = ByteArray(SQLITE_MAGIC.size)
        var read = 0
        while (read < header.size) {
            val n = stream.read(header, read, header.size - read)
            if (n < 0) break
            read += n
        }
        if (read < header.size || !header.contentEquals(SQLITE_MAGIC)) {
            throw InvalidBackupException("msgstore.db in the backup is not a valid database")
        }
    }

    private fun forEachEntry(zipFile: File, action: (String, InputStream) -> Unit) {
        try {
            ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory) action(entry.name, zip)
                    zip.closeEntry()
                }
            }
        } catch (e: ZipException) {
            throw InvalidBackupException("The backup is not a valid archive")
        }
    }
}
