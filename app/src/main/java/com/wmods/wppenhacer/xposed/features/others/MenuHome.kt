package com.wmods.wppenhacer.xposed.features.others

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.view.Menu
import android.view.MenuItem
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.wmods.wppenhacer.BuildConfig
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.core.Diagnostics
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore.getPrivBoolean
import com.wmods.wppenhacer.xposed.core.WppCore.homeActivityClass
import com.wmods.wppenhacer.xposed.core.WppCore.setPrivBoolean
import com.wmods.wppenhacer.xposed.core.components.AlertDialogWpp
import com.wmods.wppenhacer.xposed.utils.DesignUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import java.util.concurrent.CopyOnWriteArraySet

class MenuHome(classLoader: ClassLoader, preferences: SharedPreferences) :
    Feature(classLoader, preferences) {
    @Throws(Throwable::class)
    override fun doHook() {
        hookMenu()
        val action = xprefs.getBoolean("buttonaction", true)

        // restart button
        addMenuItem { menu, activity ->
            insertRestartButton(
                menu,
                activity,
                action
            )
        }

        // dnd mode
        addMenuItem { menu, activity ->
            insertDNDOption(
                menu,
                activity,
                action
            )
        }

        // ghost mode
        addMenuItem { menu, activity ->
            insertGhostModeOption(
                menu,
                activity,
                action
            )
        }

        // freeze last seen
        addMenuItem { menu, activity ->
            insertFreezeLastSeenOption(
                menu,
                activity,
                action
            )
        }

        // open WAE
        addMenuItem { menu, activity ->
            this.insertOpenWae(
                menu,
                activity,
                action
            )
        }
    }

    private fun insertOpenWae(menu: Menu, activity: Activity, newSettings: Boolean) {
        if (!xprefs.getBoolean("open_wae", true)) {
            Diagnostics.event("MenuHome", "open module button is disabled in the settings")
            return
        }
        if (menu.findItem(OPEN_MODULE_ITEM_ID) != null) return
        // With the toolbar buttons enabled it is a toolbar icon (moved to the first position afterwards, see
        // moveOpenModuleFirst); otherwise it stays at the end of the overflow menu.
        val itemMenu = menu.add(
            0, OPEN_MODULE_ITEM_ID, if (newSettings) 0 else 9999,
            " " + activity.getString(R.string.app_name)
        )
        val iconDraw = try {
            if (newSettings) {
                activity.getDrawable(R.drawable.ic_open_module)?.apply { setTint(DesignUtils.getPrimaryTextColor()) }
            } else {
                DesignUtils.getDrawableByName("ic_settings")?.apply { setTint(-0x796960) }
            }
        } catch (t: Throwable) {
            Diagnostics.event("MenuHome", "open module icon failed: $t")
            null
        } ?: DesignUtils.getDrawableByName("ic_settings")?.apply { setTint(DesignUtils.getPrimaryTextColor()) }
        itemMenu.icon = iconDraw
        if (newSettings) {
            itemMenu.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        Diagnostics.event(
            "MenuHome",
            "open module button added (icon=${iconDraw != null}, toolbar=$newSettings, items=${menu.size()})"
        )
        val open = MenuItem.OnMenuItemClickListener {
            try {
                val intent = activity.packageManager.getLaunchIntentForPackage(
                    BuildConfig.APPLICATION_ID
                )
                intent!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                activity.startActivity(intent)
            } catch (e: Exception) {
                Utils.showToast(e.message)
            }
            true
        }
        itemMenu.setOnMenuItemClickListener(open)

        // The entry in the overflow menu (above WhatsApp's settings) stays next to the toolbar icon.
        if (newSettings) {
            val overflow = menu.add(0, OPEN_MODULE_OVERFLOW_ID, 9999, " " + activity.getString(R.string.app_name))
            overflow.icon = DesignUtils.getDrawableByName("ic_settings")?.apply { setTint(-0x796960) }
            overflow.setOnMenuItemClickListener(open)
        }
    }

    private fun insertGhostModeOption(menu: Menu, activity: Activity, newSettings: Boolean) {
        val ghostmode = getPrivBoolean("ghostmode", false)
        if (!xprefs.getBoolean("ghostmode", true)) {
            if (ghostmode) {
                setPrivBoolean("ghostmode", false)
                Utils.doRestart(activity)
            }
            return
        }
        val itemMenu = menu.add(0, 0, 0, R.string.ghost_mode)

        val iconDraw =
            activity.getDrawable(if (ghostmode) R.drawable.ghost_enabled else R.drawable.ghost_disabled)
        if (iconDraw != null) {
            iconDraw.setTint(if (newSettings) DesignUtils.getPrimaryTextColor() else -0x796960)
            itemMenu.icon = iconDraw
        }
        if (newSettings) {
            itemMenu.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        itemMenu.setOnMenuItemClickListener {
            AlertDialogWpp(activity).setTitle(
                activity.getString(
                    R.string.ghost_mode_s,
                    (if (ghostmode) "ON" else "OFF")
                )
            ).setMessage(activity.getString(R.string.ghost_mode_message))
                .setPositiveButton(activity.getString(R.string.disable)) { _, _ ->
                    setPrivBoolean("ghostmode", false)
                    Utils.doRestart(activity)
                }
                .setNegativeButton(activity.getString(R.string.enable)) { _, _ ->
                    setPrivBoolean("ghostmode", true)
                    Utils.doRestart(activity)
                }.show()
            true
        }
    }

    private fun insertRestartButton(menu: Menu, activity: Activity, newSettings: Boolean) {
        if (!xprefs.getBoolean("restartbutton", true)) return
        val iconDraw = activity.getDrawable(R.drawable.refresh)
        iconDraw!!.setTint(if (newSettings) DesignUtils.getPrimaryTextColor() else -0x796960)
        val itemMenu = menu.add(0, 0, 0, R.string.restart_whatsapp).setIcon(iconDraw)
        if (newSettings) {
            itemMenu.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        itemMenu.setOnMenuItemClickListener {
            Utils.doRestart(activity)
            true
        }
    }

    @SuppressLint("DiscouragedApi", "UseCompatLoadingForDrawables", "ApplySharedPref")
    private fun insertDNDOption(menu: Menu, activity: Activity, newSettings: Boolean) {
        val dndmode = getPrivBoolean("dndmode", false)
        if (!xprefs.getBoolean("show_dndmode", false)) {
            if (getPrivBoolean("dndmode", false)) {
                setPrivBoolean("dndmode", false)
                Utils.doRestart(activity)
            }
            return
        }
        val item = menu.add(0, 0, 0, activity.getString(R.string.dnd_mode_title))
        val drawable = Utils.application
            .getDrawable(if (dndmode) R.drawable.airplane_enabled else R.drawable.airplane_disabled)
        if (drawable != null) {
            drawable.setTint(if (newSettings) DesignUtils.getPrimaryTextColor() else -0x796960)
            item.icon = drawable
        }
        if (newSettings) {
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        item.setOnMenuItemClickListener {
            if (!dndmode) {
                AlertDialogWpp(activity)
                    .setTitle(activity.getString(R.string.dnd_mode_title))
                    .setMessage(activity.getString(R.string.dnd_message))
                    .setPositiveButton(activity.getString(R.string.activate)) { _, _ ->
                        setPrivBoolean("dndmode", true)
                        Utils.doRestart(activity)
                    }
                    .setNegativeButton(activity.getString(R.string.cancel)) { dialog, _ -> dialog?.dismiss() }
                    .create().show()
                return@setOnMenuItemClickListener true
            }
            setPrivBoolean("dndmode", false)
            Utils.doRestart(activity)
            true
        }
    }

    private fun insertFreezeLastSeenOption(menu: Menu, activity: Activity, newSettings: Boolean) {
        val freezelastseen = getPrivBoolean("freezelastseen", false)
        if (!xprefs.getBoolean("show_freezeLastSeen", true)) {
            if (freezelastseen) {
                setPrivBoolean("freezelastseen", false)
                Utils.doRestart(activity)
            }
            return
        }

        val item = menu.add(0, 0, 0, activity.getString(R.string.freezelastseen_title))
        val drawable = Utils.application
            .getDrawable(if (freezelastseen) R.drawable.eye_disabled else R.drawable.eye_enabled)
        if (drawable != null) {
            drawable.setTint(if (newSettings) DesignUtils.getPrimaryTextColor() else -0x796960)
            item.icon = drawable
        }
        if (newSettings) {
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        item.setOnMenuItemClickListener {
            if (!freezelastseen) {
                AlertDialogWpp(activity)
                    .setTitle(activity.getString(R.string.freezelastseen_title))
                    .setMessage(activity.getString(R.string.freezelastseen_message))
                    .setPositiveButton(activity.getString(R.string.activate)) { _, _ ->
                        setPrivBoolean("freezelastseen", true)
                        Utils.doRestart(activity)
                    }
                    .setNegativeButton(activity.getString(R.string.cancel)) { dialog, _ -> dialog?.dismiss() }
                    .create().show()
                return@setOnMenuItemClickListener true
            }
            setPrivBoolean("freezelastseen", false)
            Utils.doRestart(activity)
            true
        }
    }

    /**
     * Puts the module button at the start of the menu, left of the camera. Android does not allow an order
     * lower than WhatsApp's own items (0), and adding it before WhatsApp fills the menu hides WhatsApp's
     * overflow entries, so it is moved inside the menu's own item list once everything has been added.
     */
    private fun moveOpenModuleFirst(menu: Menu) {
        try {
            val item = menu.findItem(OPEN_MODULE_ITEM_ID) ?: return
            var cls: Class<*>? = menu.javaClass
            while (cls != null && cls != Any::class.java) {
                for (field in cls.declaredFields) {
                    if (!java.util.ArrayList::class.java.isAssignableFrom(field.type) && field.type != List::class.java) continue
                    field.isAccessible = true
                    val list = field.get(menu) as? java.util.ArrayList<Any?> ?: continue
                    if (list.size > 1 && list.contains(item) && list[0] !== item) {
                        list.remove(item)
                        list.add(0, item)
                        Diagnostics.event("MenuHome", "open module button moved to the first position")
                        return
                    }
                }
                cls = cls.superclass
            }
        } catch (t: Throwable) {
            Diagnostics.event("MenuHome", "could not move the open module button: $t")
        }
    }

    private fun hookMenu() {
        homeActivityClass.resolve().firstMethod {
            name = "onCreateOptionsMenu"
            superclass()
            parameters(Menu::class.java)
        }.hook {
            after {
                val menu = args[0] as Menu
                val activity = instance as Activity
                for (menuItem in menuItems) {
                    try {
                        menuItem.addMenu(menu, activity)
                    } catch (t: Throwable) {
                        Diagnostics.event("MenuHome", "menu item failed: $t")
                    }
                }
                moveOpenModuleFirst(menu)
            }
        }
    }

    override fun getPluginName(): String {
        return "Menu Home"
    }

    fun interface HomeMenuItem {
        fun addMenu(menu: Menu, activity: Activity)
    }

    companion object {
        private val menuItems = CopyOnWriteArraySet<HomeMenuItem>()
        private const val OPEN_MODULE_ITEM_ID = 0x57544B01
        private const val OPEN_MODULE_OVERFLOW_ID = 0x57544B02

        @JvmStatic
        fun addMenuItem(homeMenuItem: HomeMenuItem) {
            menuItems += homeMenuItem
        }
    }
}
