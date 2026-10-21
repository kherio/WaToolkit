package com.wmods.wppenhacer.toolkit.backup

import android.os.ParcelFileDescriptor
import com.wmods.wppenhacer.xposed.bridge.WaeIIFace
import java.io.File
import java.io.IOException

/**
 * An [ExternalCopy.Target] inside shared storage, reached through the module app (which holds the
 * all-files permission that WhatsApp does not have).
 */
class BridgeTarget(private val bridge: WaeIIFace, dir: String) : ExternalCopy.Target {
    private val dir = dir.trimEnd('/')

    init {
        if (!bridge.exists(this.dir) && !bridge.createDir(this.dir)) {
            throw IOException("Could not create the external folder")
        }
    }

    private fun path(name: String) = "$dir/$name"

    override fun size(name: String): Long? {
        if (!bridge.exists(path(name))) return null
        val pfd = bridge.openFile(path(name), false) ?: return null
        return pfd.use { it.statSize }
    }

    override fun list(): List<String> =
        bridge.listFiles(dir).filterIsInstance<File>().map { it.name }

    override fun write(name: String, source: File) {
        val part = path("$name.part")
        bridge.deleteFile(part)
        val pfd = bridge.openFile(part, true) ?: throw IOException("Could not write to the external folder")
        ParcelFileDescriptor.AutoCloseOutputStream(pfd).use { out ->
            source.inputStream().use { it.copyTo(out) }
        }
        if (!bridge.renameFile(part, path(name))) {
            bridge.deleteFile(part)
            throw IOException("Could not finish the copy in the external folder")
        }
    }

    override fun delete(name: String) {
        bridge.deleteFile(path(name))
    }
}
