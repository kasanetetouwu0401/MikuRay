package com.v2ray.ang.util

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.v2ray.ang.R
import com.v2ray.ang.dto.ProfileDiffEntry
import com.v2ray.ang.dto.SubscriptionUpdateResult
import com.v2ray.ang.util.getColorAttr

/**
 * Shows a dialog listing which server profiles were added and/or deleted by a subscription
 * update, mirroring the "group diff" dialog from MikuBox so the user can see at a glance what
 * changed right after a subscription refresh finishes.
 */
fun showSubUpdateDiffDialog(context: Context, result: SubscriptionUpdateResult) {
    if (result.addedProfiles.isEmpty() && result.deletedProfiles.isEmpty()) return

    val subNames = (result.addedProfiles.asSequence().map { it.subscriptionName } +
            result.deletedProfiles.asSequence().map { it.subscriptionName })
        .distinct()
        .toList()
    val multipleSubs = subNames.size > 1

    val titleSubject = if (subNames.size == 1) subNames.first() else context.getString(R.string.title_sub_update)
    val title = context.getString(R.string.title_sub_update_diff, titleSubject)

    val listItems = mutableListOf<String>()

    fun formatEntry(entry: ProfileDiffEntry): String {
        return if (multipleSubs) "• [${entry.subscriptionName}] ${entry.profileName}" else "• ${entry.profileName}"
    }

    if (result.addedProfiles.isNotEmpty()) {
        val addedHeader = context.getString(R.string.sub_update_diff_added, "").trim()
        listItems.add(addedHeader) 
        result.addedProfiles.forEach { listItems.add(formatEntry(it)) }
    }

    if (result.deletedProfiles.isNotEmpty()) {
        if (listItems.isNotEmpty()) listItems.add("")
        val deletedHeader = context.getString(R.string.sub_update_diff_deleted, "").trim()
        listItems.add(deletedHeader)
        result.deletedProfiles.forEach { listItems.add(formatEntry(it)) }
    }

    val listView = ListView(context).apply {
        adapter = object : ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, listItems) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                val text = getItem(position) ?: ""
                
                view.text = text
                view.setPadding(24, 5, 24, 5)
                
                if (!text.startsWith("• ") && text.isNotBlank()) {
                    TextViewCompat.setTextAppearance(view, com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
                    view.setTextColor(context.getColorAttr("colorPrimary"))
                } else {
                    TextViewCompat.setTextAppearance(view, com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
                    view.setTextColor(context.getColorAttr("colorOnSurfaceVariant"))
                }
                return view
            }
        }
        divider = null 
        dividerHeight = 0
    }

    MaterialAlertDialogBuilder(context)
        .setTitle(title)
        .setView(listView)
        .setPositiveButton(android.R.string.ok, null)
        .showBlur()
}
