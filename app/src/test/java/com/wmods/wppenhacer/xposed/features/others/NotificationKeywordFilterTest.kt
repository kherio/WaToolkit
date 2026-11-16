package com.wmods.wppenhacer.xposed.features.others

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationKeywordFilterTest {
    private fun hide(chats: String, words: String, title: String, text: String) =
        NotificationKeywordFilter.shouldHide(chats, words, listOf(title), listOf(text))

    @Test fun otherChatsAreUntouched() = assertFalse(hide("Familia", "urgente", "Trabajo", "hola"))

    @Test fun quietChatWithoutKeywordIsHidden() = assertTrue(hide("Familia", "urgente", "Familia", "buenos días"))

    @Test fun keywordLetsItThrough() = assertFalse(hide("Familia", "javi, urgente", "Familia", "Javi, ¿vienes?"))

    @Test fun matchIgnoresCaseAndAccents() = assertTrue(hide("familía", "", "FAMILIA (3 mensajes)", "x"))

    @Test fun noChatsConfiguredHidesNothing() = assertFalse(hide("", "x", "Familia", "hola"))

    @Test fun emptyKeywordsHideEverythingFromThoseChats() = assertTrue(hide("Familia", "", "Familia", "urgente"))
}
