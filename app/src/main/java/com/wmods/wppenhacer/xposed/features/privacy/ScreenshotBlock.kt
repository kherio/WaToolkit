package com.wmods.wppenhacer.xposed.features.privacy

import android.annotation.SuppressLint
import android.app.Activity
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.core.Diagnostics
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.utils.DesignUtils
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import org.luckypray.dexkit.query.enums.StringMatchType
import java.lang.reflect.Method

/**
 * Blocks screenshots, screen recording and the "recents" thumbnail in the chosen
 * chats (FLAG_SECURE). A chat is enabled from its info screen.
 */
class ScreenshotBlock(
    classLoader: ClassLoader,
    preferences: SharedPreferences
) : Feature(classLoader, preferences) {

    private lateinit var chatUserJidMethod: Method
    private lateinit var groupUserJidMethod: Method

    override fun getPluginName(): String = "Screenshot Block"

    /** Read live, so enabling the switch works without restarting WhatsApp. */
    private fun enabled() = xprefs.getBoolean("secure_chats", false)

    override fun doHook() {
        val contactInfoClass = Unobfuscator.findFirstClassUsingName(
            classLoader, StringMatchType.EndsWith, ".ContactInfoActivity"
        )
        val groupInfoClass = Unobfuscator.findFirstClassUsingName(
            classLoader, StringMatchType.EndsWith, ".GroupChatInfoActivity"
        )
        val userJidClass = Unobfuscator.findFirstClassUsingName(
            classLoader, StringMatchType.EndsWith, "jid.UserJid"
        )
        val groupJidClass = Unobfuscator.findFirstClassUsingName(
            classLoader, StringMatchType.EndsWith, "jid.GroupJid"
        )
        chatUserJidMethod = ReflectionUtils.findMethodUsingFilter(contactInfoClass) { m ->
            m.parameterCount == 0 && userJidClass.isAssignableFrom(m.returnType)
        }
        groupUserJidMethod = ReflectionUtils.findMethodUsingFilter(groupInfoClass) { m ->
            m.parameterCount == 0 && groupJidClass.isAssignableFrom(m.returnType)
        }

        WppCore.addListenerActivity(object : WppCore.ActivityChangeState {
            override fun onChange(activity: Activity, type: WppCore.ActivityChangeState.ChangeType) {
                try {
                    when (type) {
                        WppCore.ActivityChangeState.ChangeType.STARTED -> {
                            if (!enabled()) return
                            if (contactInfoClass.isInstance(activity) || groupInfoClass.isInstance(activity)) {
                                addInfoItem(activity, contactInfoClass.isInstance(activity))
                            } else if (WppCore.getCurrentConversation() === activity) {
                                applyToConversation(activity)
                            }
                        }

                        WppCore.ActivityChangeState.ChangeType.RESUMED -> {
                            if (enabled() && WppCore.getCurrentConversation() === activity) applyToConversation(activity)
                        }

                        else -> {}
                    }
                } catch (e: Throwable) {
                    logDebug(e)
                }
            }
        })
    }

    private fun prefKey(number: String) = "${number}_secure"

    /**
     * Every identifier a chat can be known by: its phone number and its LID number. WhatsApp
     * shows the same chat under either, and the phone number is missing when the LID cannot be
     * converted, so using both keeps the info screen and the conversation in agreement.
     */
    private fun chatKeys(jid: FMessageWpp.UserJid): Set<String> {
        val keys = LinkedHashSet<String>()
        jid.phoneNumber?.takeIf { it.isNotEmpty() }?.let { keys.add(it) }
        try {
            val raw = jid.userJid?.let { ReflectionUtils.callMethod(it, "getRawString") as? String }
            raw?.substringBefore('@')?.substringBefore('.')?.takeIf { it.isNotEmpty() }?.let { keys.add(it) }
        } catch (_: Throwable) {
        }
        return keys
    }

    private fun isSecure(keys: Set<String>) = keys.any { WppCore.getPrivBoolean(prefKey(it), false) }

    /** The chat may take a moment to be ready, so this retries a couple of times. */
    private fun applyToConversation(activity: Activity) {
        val handler = Handler(Looper.getMainLooper())
        for (delay in longArrayOf(0, 300, 1000)) {
            handler.postDelayed({
                try {
                    if (activity.isFinishing || activity.isDestroyed) return@postDelayed
                    val jid = WppCore.getCurrentUserJid()
                    val keys = if (jid == null) emptySet() else chatKeys(jid)
                    if (keys.isEmpty()) {
                        Diagnostics.event("ScreenshotBlock", "conversation opened but its chat id is unknown (try $delay ms)")
                        return@postDelayed
                    }
                    val secure = isSecure(keys)
                    setSecure(activity, secure)
                    if (delay == 0L) Diagnostics.event("ScreenshotBlock", "conversation ${keys.first()}: secure=$secure")
                } catch (e: Throwable) {
                    logDebug(e)
                }
            }, delay)
        }
    }

    /** setAttributes is used instead of addFlags/setFlags because those are hooked by "allow screenshots". */
    private fun setSecure(activity: Activity, secure: Boolean) {
        val window = activity.window
        val lp = window.attributes
        val has = lp.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
        if (has == secure) return
        Diagnostics.event("ScreenshotBlock", "FLAG_SECURE ${if (secure) "set" else "cleared"} on ${activity.javaClass.simpleName}")
        lp.flags = if (secure) {
            lp.flags or WindowManager.LayoutParams.FLAG_SECURE
        } else {
            lp.flags and WindowManager.LayoutParams.FLAG_SECURE.inv()
        }
        window.attributes = lp
    }

    @SuppressLint("ResourceType")
    private fun addInfoItem(activity: Activity, isChat: Boolean) {
        if (activity.findViewById<View>(ITEM_ID) != null) return
        val layoutId = Utils.getID("contact_info_security_card_layout", "id")
        val infoLayout = activity.window.findViewById<ViewGroup>(layoutId) ?: return
        val jid = FMessageWpp.UserJid(
            ReflectionUtils.callMethod(if (isChat) chatUserJidMethod else groupUserJidMethod, activity)
        )
        val keys = chatKeys(jid)
        if (keys.isEmpty()) {
            Diagnostics.event("ScreenshotBlock", "info screen opened but the chat id is unknown")
            return
        }

        val icon = activity.getDrawable(R.drawable.ic_privacy)!!
        icon.setTint(0xff8696a0.toInt())

        val row = LinearLayout(activity).apply {
            id = ITEM_ID
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(16, 16, 16, 16)
        }
        val image = ImageView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(Utils.dipToPixels(20), Utils.dipToPixels(20)).apply {
                setMargins(Utils.dipToPixels(20), 0, Utils.dipToPixels(16), Utils.dipToPixels(20))
            }
            setImageDrawable(icon)
        }
        val texts = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = 16 }
        }
        val title = TextView(activity).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            text = activity.getString(R.string.secure_chat)
            setTextColor(DesignUtils.getPrimaryTextColor())
        }
        val summary = TextView(activity).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }
        fun refresh() {
            summary.text = activity.getString(
                if (isSecure(keys)) R.string.secure_chat_on else R.string.secure_chat_off
            )
        }
        refresh()
        texts.addView(title)
        texts.addView(summary)
        row.addView(image)
        row.addView(texts)
        row.setOnClickListener {
            val enabled = !isSecure(keys)
            keys.forEach { WppCore.setPrivBoolean(prefKey(it), enabled) }
            Diagnostics.event("ScreenshotBlock", "chat ${keys.first()}: secure=$enabled (keys=$keys)")
            refresh()
            Utils.showToast(
                activity.getString(if (enabled) R.string.secure_chat_on else R.string.secure_chat_off),
                Toast.LENGTH_SHORT
            )
        }
        infoLayout.addView(row)
    }

    private companion object {
        private const val ITEM_ID = 0x7f0a9998
    }
}
