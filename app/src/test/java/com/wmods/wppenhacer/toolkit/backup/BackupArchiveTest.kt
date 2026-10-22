package com.wmods.wppenhacer.toolkit.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupArchiveTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val sqlite = "SQLite format 3\u0000".toByteArray(Charsets.ISO_8859_1) + ByteArray(100) { 1 }

    private fun archive(vararg entries: Pair<String, ByteArray>, password: String = "pw"): ByteArray {
        val out = ByteArrayOutputStream()
        BackupCrypto.EncryptingOutputStream(out, password.toCharArray()).use { enc ->
            ZipOutputStream(enc).use { zip ->
                for ((name, data) in entries) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(data)
                    zip.closeEntry()
                }
            }
        }
        return out.toByteArray()
    }

    private fun verify(bytes: ByteArray, pw: String = "pw", db: Boolean = true) =
        BackupArchive.verify(ByteArrayInputStream(bytes), pw.toCharArray(), tmp.root, db)

    @Test
    fun validBackupVerifies() {
        verify(archive("info.txt" to "x".toByteArray(), "databases/msgstore.db" to sqlite))
    }

    @Test
    fun wrongPasswordIsRejected() {
        val a = archive("databases/msgstore.db" to sqlite)
        try { verify(a, "other"); fail() } catch (_: BackupCrypto.WrongPasswordException) {}
    }

    @Test
    fun corruptedByteIsRejected() {
        val a = archive("databases/msgstore.db" to sqlite)
        a[a.size - 5] = (a[a.size - 5].toInt() xor 0x55).toByte()
        try { verify(a); fail() } catch (_: java.io.IOException) {}
    }

    @Test
    fun missingOrInvalidDatabaseIsRejected() {
        try { verify(archive("info.txt" to "x".toByteArray())); fail() } catch (_: BackupArchive.InvalidBackupException) {}
        try { verify(archive("databases/msgstore.db" to ByteArray(200))); fail() } catch (_: BackupArchive.InvalidBackupException) {}
    }

    @Test
    fun mediaArchiveOnlyNeedsToDecrypt() {
        verify(archive("media/a.jpg" to ByteArray(10)), db = false)
    }

    @Test
    fun extractWritesOnlyKnownDatabases() {
        val a = archive(
            "databases/msgstore.db" to sqlite,
            "databases/wa.db" to sqlite,
            "databases/../evil.db" to sqlite,
            "databases/other.db" to sqlite,
            "../escape.db" to sqlite
        )
        val out = tmp.newFolder("out")
        val names = BackupArchive.extractDatabases(ByteArrayInputStream(a), "pw".toCharArray(), tmp.root, out)
        assertEquals(setOf("msgstore.db", "wa.db"), names.toSet())
        assertArrayEquals(sqlite, java.io.File(out, "msgstore.db").readBytes())
        assertFalse(java.io.File(tmp.root, "evil.db").exists())
        assertTrue(out.listFiles()!!.all { it.name in BackupArchive.DATABASES })
    }

    @Test
    fun extractFailureLeavesNothingBehind() {
        val out = tmp.newFolder("out2")
        try {
            BackupArchive.extractDatabases(ByteArrayInputStream(archive("info.txt" to "x".toByteArray())), "pw".toCharArray(), tmp.root, out)
            fail()
        } catch (_: BackupArchive.InvalidBackupException) {}
        assertFalse(out.exists())
    }

    private fun extract(bytes: ByteArray, root: java.io.File, pw: String = "pw") =
        BackupArchive.extractMedia(ByteArrayInputStream(bytes), pw.toCharArray(), root)

    @Test
    fun mediaIsRestoredAndExistingFilesKept() {
        val root = tmp.newFolder("Media")
        java.io.File(root, "Images").mkdirs()
        java.io.File(root, "Images/a.jpg").writeText("current")
        val a = archive(
            "info.txt" to "x".toByteArray(),
            "media/Images/a.jpg" to "old".toByteArray(),
            "media/Images/b.jpg" to "new".toByteArray(),
            "media/Voice/c.opus" to "voice".toByteArray()
        )
        val r = extract(a, root)
        assertEquals(2, r.restored)
        assertEquals(1, r.skipped)
        assertEquals("current", java.io.File(root, "Images/a.jpg").readText())
        assertEquals("new", java.io.File(root, "Images/b.jpg").readText())
        assertEquals("voice", java.io.File(root, "Voice/c.opus").readText())
    }

    @Test
    fun mediaPathsOutsideTheRootAreIgnored() {
        val root = tmp.newFolder("Media2")
        val a = archive(
            "media/../evil.txt" to "x".toByteArray(),
            "media//abs.txt" to "x".toByteArray(),
            "other/file.txt" to "x".toByteArray(),
            "media/ok.txt" to "ok".toByteArray()
        )
        val r = extract(a, root)
        assertEquals(1, r.restored)
        assertFalse(java.io.File(root.parentFile, "evil.txt").exists())
        assertTrue(java.io.File(root, "ok.txt").exists())
    }

    @Test
    fun mediaWithWrongPasswordFails() {
        val root = tmp.newFolder("Media3")
        val a = archive("media/a.txt" to "x".toByteArray())
        try { extract(a, root, "other"); fail() } catch (_: BackupCrypto.WrongPasswordException) {}
        assertFalse(java.io.File(root, "a.txt").exists())
    }
}
