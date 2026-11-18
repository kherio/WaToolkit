package com.wmods.wppenhacer.preference

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.toolkit.ChatNames

/**
 * Pick chats and groups from the list WhatsApp published to the module. The selection is stored as the
 * chosen names separated by new lines (names may contain commas).
 */
class ChatListPreference @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : Preference(context, attrs) {

    private fun selected(): LinkedHashSet<String> =
        LinkedHashSet(getPersistedString("").split('\n').map { it.trim() }.filter { it.isNotEmpty() })

    override fun onAttached() {
        super.onAttached()
        updateSummary()
    }

    private fun updateSummary() {
        val sel = selected()
        summary = when {
            sel.isEmpty() -> context.getString(R.string.chat_picker_none)
            sel.size <= 3 -> sel.joinToString(", ")
            else -> context.getString(R.string.chat_picker_count, sel.size)
        }
    }

    override fun onClick() {
        val all = ChatNames.read(context)
        if (all.isEmpty()) {
            MaterialAlertDialogBuilder(context).setTitle(title).setMessage(R.string.chat_picker_empty)
                .setPositiveButton(android.R.string.ok, null).show()
            return
        }
        // Chats already chosen that are no longer in the list stay selectable so they are not lost silently.
        val sel = selected()
        // Chats already selected first, then real names, then the chats that only have a phone number.
        val isNumber = { n: String -> n.any { it.isDigit() } && n.all { it.isDigit() || it in "+ -()" } }
        val names = (all + sel.filter { it !in all })
            .sortedWith(compareBy({ it !in sel }, { isNumber(it) }, { it.lowercase() }))
        var shown = names
        val list = ListView(context).apply { choiceMode = ListView.CHOICE_MODE_MULTIPLE }
        fun refresh(query: String) {
            val q = query.trim().lowercase()
            shown = if (q.isEmpty()) names else names.filter { it.lowercase().contains(q) }
            list.adapter = ArrayAdapter(context, android.R.layout.simple_list_item_multiple_choice, shown)
            shown.forEachIndexed { i, n -> list.setItemChecked(i, n in sel) }
        }
        list.setOnItemClickListener { _, _, pos, _ -> if (list.isItemChecked(pos)) sel.add(shown[pos]) else sel.remove(shown[pos]) }
        val search = EditText(context).apply {
            hint = context.getString(R.string.chat_picker_search)
            setSingleLine()
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) = refresh(s?.toString().orEmpty())
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        refresh("")
        val pad = (16 * context.resources.displayMetrics.density).toInt()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            addView(search)
            addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        MaterialAlertDialogBuilder(context).setTitle(title).setView(root)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                persistString(sel.joinToString("\n"))
                updateSummary()
            }
            .setNegativeButton(android.R.string.cancel, null).show()
    }
}
