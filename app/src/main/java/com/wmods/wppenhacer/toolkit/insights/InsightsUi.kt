package com.wmods.wppenhacer.toolkit.insights

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import com.wmods.wppenhacer.toolkit.Dlg
import com.wmods.wppenhacer.toolkit.Host
import com.wmods.wppenhacer.toolkit.Names
import com.wmods.wppenhacer.toolkit.deleted.DeletedMessagesReader
import com.wmods.wppenhacer.toolkit.importer.ChatImporter
import java.io.File

/** Shared state of the insight tools: the database, the names of contacts and the media folder. */
class InsightsEnv(val activity: Activity) {
    private val dataDir = Host.accountDataDir(activity)
    val dbFile = File(dataDir, "databases/msgstore.db")
    val db = InsightsDb(dbFile)
    val mediaRoot: File = Host.rootWhatsAppDir(activity)
    lateinit var names: Names

    /** Blocking: call off the main thread. */
    fun load() {
        val lids = DeletedMessagesReader(dbFile).phoneByLid()
        val waNames = ChatImporter(dbFile, mediaRoot).contactNames(File(dataDir, "databases/wa.db"))
        names = Names(activity, waNames, lids)
    }

    fun chatName(c: InsightsDb.Chat): String =
        if (c.isGroup) c.subject?.takeIf { it.isNotBlank() } ?: c.jid.substringBefore('@') else names.of(c.jid)

    fun personName(jid: String?): String = names.of(jid)
}

object InsightsUi {
    fun dp(activity: Activity, v: Int) = Host.dp(activity, v)

    fun column(activity: Activity, vararg views: View): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(activity, 20), dp(activity, 12), dp(activity, 20), dp(activity, 8))
        views.forEach { addView(it) }
    }

    fun label(activity: Activity, text: String, size: Float = 14f, alpha: Float = 1f) = TextView(activity).apply {
        this.text = text
        textSize = size
        this.alpha = alpha
        setTextColor(Host.textColor(activity))
    }

    fun edit(activity: Activity, hint: String) = EditText(activity).apply {
        this.hint = hint
        setSingleLine()
        setTextColor(Host.textColor(activity))
    }

    /** Runs [work] off the main thread behind a progress dialog; [done] runs on the main thread. */
    fun <T> background(activity: Activity, title: String, work: () -> T, done: (T) -> Unit) {
        val dlg = Dlg(activity).setTitle(title).setView(
            column(activity, ProgressBar(activity).apply { isIndeterminate = true })
        )
        dlg.show()
        Thread {
            try {
                val r = work()
                activity.runOnUiThread { dlg.dismiss(); done(r) }
            } catch (t: Throwable) {
                Host.log(t)
                activity.runOnUiThread { dlg.dismiss(); Host.toast(activity, t.message ?: t.javaClass.simpleName) }
            }
        }.start()
    }

    /** Dialog with a two-line list; [onClick] gets the index of the tapped row. */
    fun list(
        activity: Activity, title: String, rows: List<Pair<String, String>>,
        header: View? = null, onClick: ((Int) -> Unit)? = null
    ): Dlg {
        val lv = ListView(activity).apply {
            adapter = object : BaseAdapter() {
                override fun getCount() = rows.size
                override fun getItem(p: Int) = rows[p]
                override fun getItemId(p: Int) = p.toLong()
                override fun getView(p: Int, v: View?, parent: ViewGroup): View {
                    val box = LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(activity, 20), dp(activity, 10), dp(activity, 20), dp(activity, 10))
                    }
                    box.addView(label(activity, rows[p].first, 15f))
                    if (rows[p].second.isNotBlank()) box.addView(label(activity, rows[p].second, 12f, 0.7f))
                    return box
                }
            }
            if (onClick != null) setOnItemClickListener { _, _, pos, _ -> onClick(pos) }
        }
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            if (header != null) addView(header)
            addView(lv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        return Dlg(activity).setTitle(title).setView(root)
            .setPositiveButton(tr("Close", "Cerrar"), null)
    }
}
