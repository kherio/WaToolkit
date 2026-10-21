package com.wmods.wppenhacer.toolkit.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class ExternalCopyTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private class FakeTarget(var failWrites: Boolean = false, var truncate: Boolean = false) : ExternalCopy.Target {
        val files = HashMap<String, Long>()
        override fun size(name: String) = files[name]
        override fun list() = files.keys.toList()
        override fun write(name: String, source: File) {
            if (failWrites) throw IOException("disk full")
            files[name] = if (truncate) source.length() - 1 else source.length()
        }
        override fun delete(name: String) { files.remove(name) }
    }

    private fun archive(name: String, size: Int) = tmp.newFile(name).also { it.writeBytes(ByteArray(size) { 1 }) }

    @Test
    fun copiesMissingAndSkipsExisting() {
        val a = archive("WaToolkit-db-20261001-100000.wtbak", 10)
        val b = archive("WaToolkit-db-20261002-100000.wtbak", 20)
        val target = FakeTarget().also { it.files[a.name] = a.length() }
        val r = ExternalCopy.sync(listOf(a, b), target, keepDb = 5)
        assertEquals(1, r.copied)
        assertEquals(1, r.skipped)
        assertEquals(0, r.failed)
        assertEquals(setOf(a.name, b.name), target.files.keys)
    }

    @Test
    fun replacesACopyWithADifferentSize() {
        val a = archive("WaToolkit-db-20261001-100000.wtbak", 10)
        val target = FakeTarget().also { it.files[a.name] = 3 }
        val r = ExternalCopy.sync(listOf(a), target, keepDb = 5)
        assertEquals(1, r.copied)
        assertEquals(10L, target.files[a.name])
    }

    @Test
    fun prunesOldDatabaseArchivesButNeverMedia() {
        val files = listOf(
            archive("WaToolkit-db-20261001-100000.wtbak", 5),
            archive("WaToolkit-db-20261002-100000.wtbak", 5),
            archive("WaToolkit-db-20261003-100000.wtbak", 5),
            archive("WaToolkit-media-20260901-100000.wtbak", 5)
        )
        val target = FakeTarget()
        val r = ExternalCopy.sync(files, target, keepDb = 2)
        assertEquals(1, r.pruned)
        assertTrue("WaToolkit-db-20261001-100000.wtbak" !in target.files)
        assertTrue("WaToolkit-media-20260901-100000.wtbak" in target.files)
    }

    @Test
    fun doesNotPruneWhenACopyFailed() {
        val old = archive("WaToolkit-db-20261001-100000.wtbak", 5)
        val new = archive("WaToolkit-db-20261002-100000.wtbak", 5)
        val target = FakeTarget(failWrites = true).also { it.files[old.name] = old.length() }
        val r = ExternalCopy.sync(listOf(old, new), target, keepDb = 1)
        assertEquals(1, r.failed)
        assertEquals(0, r.pruned)
        assertTrue(old.name in target.files)
        assertEquals("disk full", r.error)
    }

    @Test
    fun incompleteCopyIsRemovedAndReported() {
        val a = archive("WaToolkit-db-20261001-100000.wtbak", 10)
        val target = FakeTarget(truncate = true)
        val r = ExternalCopy.sync(listOf(a), target, keepDb = 3)
        assertEquals(1, r.failed)
        assertTrue(target.files.isEmpty())
    }

    @Test
    fun documentIdsBecomePaths() {
        assertEquals("/storage/emulated/0/Sync/WaToolkit", ExternalCopy.documentIdToPath("primary:Sync/WaToolkit"))
        assertEquals("/storage/emulated/0", ExternalCopy.documentIdToPath("primary:"))
        assertEquals("/storage/1234-ABCD/Backups", ExternalCopy.documentIdToPath("1234-ABCD:Backups/"))
        assertNull(ExternalCopy.documentIdToPath("nocolon"))
        assertNull(ExternalCopy.documentIdToPath("../evil:x"))
    }
}
