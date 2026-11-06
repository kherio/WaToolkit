package com.wmods.wppenhacer.ui.fragments.base

import android.content.Intent
import android.content.res.XmlResourceParser
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.wmods.wppenhacer.R
import org.xmlpull.v1.XmlPullParser

/**
 * A settings section made of a root list and several subscreens. [openPreference] finds the subscreen that holds
 * a preference key (by reading the preference XMLs), so search results and shortcuts can open it directly.
 */
abstract class SubScreensFragment : BaseFragment() {

    class Screen(val xml: Int, val fragment: Class<out Fragment>)

    protected abstract fun createRoot(): Fragment
    protected abstract val screens: List<Screen>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = super.onCreateView(inflater, container, savedInstanceState)
        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction()
                .add(R.id.frag_container, createRoot())
                .commitNow()
        }
        val activityIntent = activity?.intent
        val scrollToKey = activityIntent?.getStringExtra("scroll_to_preference")
        if (scrollToKey != null) {
            root?.postDelayed({ openPreference(scrollToKey) }, 300)
            activityIntent.removeExtra("scroll_to_preference")
        }
        return root
    }

    /** Opens the subscreen that holds [key] (if it is not the visible one) and scrolls to it. */
    fun openPreference(key: String) {
        if (!isAdded) return
        val target = screens.firstOrNull { keysOf(it.xml).contains(key) }
        val current = childFragmentManager.findFragmentById(R.id.frag_container)
        if (target == null || (current != null && current.javaClass == target.fragment)) {
            (current as? BasePreferenceFragment)?.scrollToPreference(key)
            return
        }
        val next = target.fragment.getDeclaredConstructor().newInstance()
        childFragmentManager.beginTransaction()
            .replace(R.id.frag_container, next)
            .addToBackStack(null)
            .commitAllowingStateLoss()
        view?.postDelayed({ (next as? BasePreferenceFragment)?.scrollToPreference(key) }, 400)
    }

    private fun keysOf(xml: Int): Set<String> {
        val keys = HashSet<String>()
        val parser: XmlResourceParser = resources.getXml(xml)
        try {
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG) {
                    for (i in 0 until parser.attributeCount) {
                        if (parser.getAttributeName(i) == "key") keys.add(parser.getAttributeValue(i))
                    }
                }
                parser.next()
            }
        } finally {
            parser.close()
        }
        return keys
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        childFragmentManager.fragments.forEach { it.onActivityResult(requestCode, resultCode, data) }
    }
}
