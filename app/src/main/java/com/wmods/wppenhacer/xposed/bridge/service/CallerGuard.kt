package com.wmods.wppenhacer.xposed.bridge.service

import android.content.Context
import android.os.Binder
import android.os.Process
import java.io.File

/**
 * Controls who can use the bridge (HookBinder / remote preferences).
 *
 * Only calls from the module itself and from WhatsApp / WhatsApp Business are accepted.
 * Any other installed app is rejected, even though the service and the providers
 * are exported (needed so the hooked process can reach them).
 */
object CallerGuard {

    private val ALLOWED_PACKAGES = arrayOf("com.whatsapp", "com.whatsapp.w4b")

    // Allowed shared-storage roots (after canonicalizing the path).
    private val ALLOWED_PATH_ROOTS = arrayOf(
        "/storage/",
        "/mnt/user/",
        "/mnt/pass_through/",
        "/mnt/runtime/",
        "/sdcard/",
        "/mnt/sdcard/"
    )

    /** True if the UID of the current transaction is the module's or WhatsApp's. */
    @Suppress("DEPRECATION")
    fun isCallerAllowed(context: Context?): Boolean {
        val callingUid = Binder.getCallingUid()
        if (callingUid == Process.myUid()) return true
        val pm = context?.packageManager ?: return false
        return ALLOWED_PACKAGES.any { pkg ->
            try {
                pm.getPackageUid(pkg, 0) == callingUid
            } catch (_: Exception) {
                false
            }
        }
    }

    /**
     * Resolves [path] to its canonical form (no "..", no symlinks) and only
     * returns it if it falls in shared storage. Private paths (/data/...) and
     * system paths are rejected, so the bridge does not expose other apps' data or
     * the module's own.
     */
    fun resolveSharedPath(path: String): File? {
        if (path.isEmpty() || path.indexOf('\u0000') >= 0) return null
        val canonical = try {
            File(path).canonicalFile
        } catch (_: Exception) {
            return null
        }
        val canonicalPath = canonical.path
        val allowed = ALLOWED_PATH_ROOTS.any { root ->
            canonicalPath.startsWith(root) || canonicalPath == root.trimEnd('/')
        }
        return if (allowed) canonical else null
    }
}
