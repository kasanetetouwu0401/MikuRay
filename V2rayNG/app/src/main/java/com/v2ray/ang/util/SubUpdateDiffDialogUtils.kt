package com.v2ray.ang.util

import android.content.Context
import android.view.LayoutInflater
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.v2ray.ang.R
import com.v2ray.ang.databinding.DialogSubUpdateDiffBinding
import com.v2ray.ang.dto.ProfileDiffEntry
import com.v2ray.ang.dto.SubscriptionUpdateResult

/**
 * Shows a dialog listing which server profiles were added and/or deleted by a subscription
 * update, mirroring the "group diff" dialog from MikuBox so the user can see at a glance what
 * changed right after a subscription refresh finishes.
 *
 * No-op if the update produced no added/deleted profiles (e.g. nothing changed, or the update
 * failed before any diff could be computed).
 *
 * @param context The context to show the dialog in (should be an Activity context).
 * @param result The subscription update result containing the added/deleted profile diff.
 */
fun showSubUpdateDiffDialog(context: Context, result: SubscriptionUpdateResult) {
    if (result.addedProfiles.isEmpty() && result.deletedProfiles.isEmpty()) return

    // Collect the distinct subscription names involved, so we know whether this update
    // touched a single subscription (e.g. manual per-item update) or several at once
    // (e.g. "Update all subscriptions").
    val subNames = (result.addedProfiles.asSequence().map { it.subscriptionName } +
            result.deletedProfiles.asSequence().map { it.subscriptionName })
        .distinct()
        .toList()
    val multipleSubs = subNames.size > 1

    fun format(entries: List<ProfileDiffEntry>): String = entries.joinToString("\n") { entry ->
        if (multipleSubs) "• [${entry.subscriptionName}] ${entry.profileName}" else "• ${entry.profileName}"
    }

    val titleSubject = if (subNames.size == 1) subNames.first() else context.getString(R.string.title_sub_update)
    val title = context.getString(R.string.title_sub_update_diff, titleSubject)

    val message = buildString {
        if (result.addedProfiles.isNotEmpty()) {
            append(context.getString(R.string.sub_update_diff_added, format(result.addedProfiles)))
        }
        if (result.deletedProfiles.isNotEmpty()) {
            if (isNotEmpty()) append("\n\n")
            append(context.getString(R.string.sub_update_diff_deleted, format(result.deletedProfiles)))
        }
    }

    val binding = DialogSubUpdateDiffBinding.inflate(LayoutInflater.from(context))
    binding.dialogTitle.text = title
    binding.dialogMessage.text = message

    val dialog = MaterialAlertDialogBuilder(context)
        .setView(binding.root)
        .create()
    WindowBlurUtils.applyWindowBlur(dialog.window)

    binding.positiveButton.setOnClickListener {
        dialog.dismiss()
    }
    dialog.show()
}

