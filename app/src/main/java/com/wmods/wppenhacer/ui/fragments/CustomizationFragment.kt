package com.wmods.wppenhacer.ui.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.ui.fragments.base.BasePreferenceFragment
import com.wmods.wppenhacer.ui.fragments.base.SubScreensFragment

/** Customization section: a list of four subscreens (appearance, conversation, home screen, statuses). */
class CustomizationFragment : SubScreensFragment() {

    override fun createRoot(): Fragment = CustomizationRoot()
    override val screens get() = SCREENS

    class CustomizationRoot : BasePreferenceFragment() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            super.onCreatePreferences(savedInstanceState, rootKey)
            setPreferencesFromResource(R.xml.fragment_customization, rootKey)
        }

        override fun onResume() {
            super.onResume()
            setDisplayHomeAsUpEnabled(false)
        }
    }

    class CustomizationAppearance : SubScreen(R.xml.preference_customization_appearance)
    class CustomizationConversation : SubScreen(R.xml.preference_customization_conversation)
    class CustomizationHome : SubScreen(R.xml.preference_customization_home)
    class CustomizationStatus : SubScreen(R.xml.preference_customization_status)

    abstract class SubScreen(private val xml: Int) : BasePreferenceFragment() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            super.onCreatePreferences(savedInstanceState, rootKey)
            setPreferencesFromResource(xml, rootKey)
            setDisplayHomeAsUpEnabled(true)
        }
    }

    private companion object {
        private val SCREENS = listOf(
            SubScreensFragment.Screen(R.xml.preference_customization_appearance, CustomizationAppearance::class.java),
            SubScreensFragment.Screen(R.xml.preference_customization_conversation, CustomizationConversation::class.java),
            SubScreensFragment.Screen(R.xml.preference_customization_home, CustomizationHome::class.java),
            SubScreensFragment.Screen(R.xml.preference_customization_status, CustomizationStatus::class.java)
        )
    }
}
