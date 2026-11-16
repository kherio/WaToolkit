package com.wmods.wppenhacer.toolkit

import com.wmods.wppenhacer.toolkit.WhatsappSupport.Level
import org.junit.Assert.assertEquals
import org.junit.Test

class WhatsappSupportTest {
    private val supported = listOf("2.26.43.xx", "2.26.44.xx")

    @Test fun listedVersion() = assertEquals(Level.LISTED, WhatsappSupport.level("2.26.44.12", supported))
    @Test fun unlistedSameBranch() = assertEquals(Level.KNOWN_BRANCH, WhatsappSupport.level("2.26.45.3", supported))
    @Test fun unknownBranch() = assertEquals(Level.UNKNOWN, WhatsappSupport.level("2.27.1.5", supported))
    @Test fun missingVersion() = assertEquals(Level.UNKNOWN, WhatsappSupport.level(null, supported))
}
