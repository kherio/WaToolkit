package com.wmods.wppenhacer.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.ui.fragments.base.BaseFragment
import com.wmods.wppenhacer.ui.fragments.base.BasePreferenceFragment

class GeneralFragment : BaseFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = super.onCreateView(inflater, container, savedInstanceState)
        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction()
                .add(R.id.frag_container, GeneralPreferenceFragment())
                .commitNow()
        }

        val activityIntent = activity?.intent
        val scrollToKey = activityIntent?.getStringExtra("scroll_to_preference")
        if (scrollToKey != null) {
            root?.postDelayed({
                (childFragmentManager.findFragmentById(R.id.frag_container) as? BasePreferenceFragment)
                    ?.scrollToPreference(scrollToKey)
            }, 300)
            activityIntent.removeExtra("scroll_to_preference")
        }
        return root
    }

    class GeneralPreferenceFragment : BasePreferenceFragment() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            super.onCreatePreferences(savedInstanceState, rootKey)
            setPreferencesFromResource(R.xml.fragment_general, rootKey)
        }

        override fun onResume() {
            super.onResume()
            setDisplayHomeAsUpEnabled(false)
        }
    }

    class HomeGeneralPreference : BasePreferenceFragment() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            super.onCreatePreferences(savedInstanceState, rootKey)
            setPreferencesFromResource(R.xml.preference_general_home, rootKey)
            setDisplayHomeAsUpEnabled(true)
        }
    }

    class HomeScreenGeneralPreference : BasePreferenceFragment() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            super.onCreatePreferences(savedInstanceState, rootKey)
            setPreferencesFromResource(R.xml.preference_general_homescreen, rootKey)
            setDisplayHomeAsUpEnabled(true)
        }
    }

    class ConversationGeneralPreference : BasePreferenceFragment() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            super.onCreatePreferences(savedInstanceState, rootKey)
            setPreferencesFromResource(R.xml.preference_general_conversation, rootKey)
            setDisplayHomeAsUpEnabled(true)
        }
    }

    // The "Conversation" row opens a list of three subscreens (ConversationGeneralPreference above).
    class MessagesGeneralPreference : ConversationSubScreen(R.xml.preference_general_messages)
    class ChatGeneralPreference : ConversationSubScreen(R.xml.preference_general_chat)
    class OtherGeneralPreference : ConversationSubScreen(R.xml.preference_general_other)

    abstract class ConversationSubScreen(private val xml: Int) : BasePreferenceFragment() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            super.onCreatePreferences(savedInstanceState, rootKey)
            setPreferencesFromResource(xml, rootKey)
            setDisplayHomeAsUpEnabled(true)
        }
    }

    companion object {
        /** Subscreen of "Conversation" that holds [key], or null. Used by search to open the right one. */
        fun conversationScreenFor(resources: android.content.res.Resources, key: String): BasePreferenceFragment? {
            val screens = listOf(
                R.xml.preference_general_messages to MessagesGeneralPreference(),
                R.xml.preference_general_chat to ChatGeneralPreference(),
                R.xml.preference_general_other to OtherGeneralPreference()
            )
            for ((xml, fragment) in screens) {
                val parser = resources.getXml(xml)
                try {
                    while (parser.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                        if (parser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG) {
                            for (i in 0 until parser.attributeCount) {
                                if (parser.getAttributeName(i) == "key" && parser.getAttributeValue(i) == key) return fragment
                            }
                        }
                        parser.next()
                    }
                } finally {
                    parser.close()
                }
            }
            return null
        }
    }
}
