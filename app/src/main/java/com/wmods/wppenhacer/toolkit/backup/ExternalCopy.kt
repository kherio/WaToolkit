package com.wmods.wppenhacer.toolkit.backup

import java.io.File
import java.io.IOException

/**
 * Mirrors the encrypted backups into a folder chosen by the user (for example one that Syncthing
 * or Nextcloud keeps in sync), so a copy exists outside the phone's WhatsApp folder.
 * The archives are already encrypted, so the folder can safely be synchronised anywhere.
 *
 * The logic is separate from how files are written ([Target]) so it can be tested on the JVM.
 */
object ExternalCopy {

    /** A folder that archives can be written to. */
    interface Target {
        /** Size of [name] in the folder, or null if it is not there. */
        fun size(name: String): Long?

        fun list(): List<String>

        /** Writes [source] as [name]; it must not appear under that name until it is complete. */
        fun write(name: String, source: File)

        fun delete(name: String)
    }

    class Report(val copied: Int, val skipped: Int, val failed: Int, val pruned: Int, val error: String?)

    /**
     * Copies every archive in [files] that is missing (or has a different size) in [target].
     * Afterwards, only if everything was copied, database archives beyond the newest [keepDb]
     * are removed from the target; media archives are never removed.
     */
    fun sync(files: List<File>, target: Target, keepDb: Int): Report {
        var copied = 0
        var skipped = 0
        var failed = 0
        var error: String? = null
        for (file in files.sortedBy { it.name }) {
            try {
                if (target.size(file.name) == file.length()) {
                    skipped++
                    continue
                }
                target.write(file.name, file)
                if (target.size(file.name) != file.length()) {
                    target.delete(file.name)
                    throw IOException("The copy of ${file.name} is incomplete")
                }
                copied++
            } catch (e: Exception) {
                failed++
                if (error == null) error = e.message ?: e.javaClass.simpleName
            }
        }
        var pruned = 0
        if (failed == 0) {
            val databases = target.list()
                .filter { it.startsWith("WaToolkit-db-") && it.endsWith(".wtbak") }
                .sortedDescending()
            for (old in databases.drop(keepDb.coerceAtLeast(1))) {
                try {
                    target.delete(old)
                    pruned++
                } catch (_: Exception) {
                }
            }
        }
        return Report(copied, skipped, failed, pruned, error)
    }

    /**
     * Converts the tree URI document id returned by the system folder picker
     * (`primary:Sync/WaToolkit`, `1234-ABCD:Backups`) to a file system path, or null if it is
     * not a storage volume we can address.
     */
    fun documentIdToPath(documentId: String): String? {
        val colon = documentId.indexOf(':')
        if (colon <= 0) return null
        val volume = documentId.substring(0, colon)
        val rest = documentId.substring(colon + 1).trim('/')
        val root = if (volume == "primary") "/storage/emulated/0" else "/storage/$volume"
        if (volume != "primary" && !Regex("[0-9A-Za-z-]+").matches(volume)) return null
        return if (rest.isEmpty()) root else "$root/$rest"
    }
}
