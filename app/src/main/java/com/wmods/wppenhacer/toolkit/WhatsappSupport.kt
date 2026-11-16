package com.wmods.wppenhacer.toolkit

/** How well a WhatsApp version is covered by the module, from the supported-versions list. */
object WhatsappSupport {
    enum class Level { LISTED, KNOWN_BRANCH, UNKNOWN }

    private fun branch(v: String) = v.split(".").take(2).joinToString(".") + "."

    /**
     * LISTED: the version matches a supported entry ("2.26.43.xx"). KNOWN_BRANCH: not listed but it shares
     * its "major.minor." branch with one (hooks load, some may fail). UNKNOWN: a branch the module never saw.
     */
    fun level(version: String?, supported: List<String>): Level {
        if (version.isNullOrBlank()) return Level.UNKNOWN
        if (supported.any { version.startsWith(it.replace(".xx", "")) }) return Level.LISTED
        val b = branch(version)
        return if (supported.any { branch(it) == b }) Level.KNOWN_BRANCH else Level.UNKNOWN
    }
}
