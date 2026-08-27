package com.miku.ray.ui.dialog


import com.miku.ray.remixicon.R as RemixR
import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.miku.ray.R
import com.miku.ray.databinding.DialogTabIconPickerBinding
import com.miku.ray.util.TabIconPickerAdapter
import com.miku.ray.util.WindowBlurUtils
import com.miku.ray.util.getColorAttr

class TabIconPickerDialog(
    private val context: Context,
    private val currentIcon: String?,
    private val onSelected: (String?) -> Unit,
) {
    fun show(): androidx.appcompat.app.AlertDialog {
        val dialogBinding = DialogTabIconPickerBinding.inflate(LayoutInflater.from(context))
        val dialogView = dialogBinding.root
        val rowNone = dialogBinding.rowNone
        val checkNone = dialogBinding.checkNone
        val rv = dialogBinding.rvIcons

        lateinit var dialog: androidx.appcompat.app.AlertDialog

        val adapter = TabIconPickerAdapter(
            context      = context,
            icons        = TabIconPickerAdapter.DEFAULT_ICONS,
            selectedIcon = currentIcon,
            onSelect     = { name ->
                onSelected(name)
                dialog.dismiss()
            }
        )
        rv.layoutManager = GridLayoutManager(context, 5)
        rv.adapter = adapter

        val noneSelected = currentIcon == null
        checkNone.visibility = if (noneSelected) View.VISIBLE else View.GONE
        checkNone.imageTintList = ColorStateList.valueOf(
            if (noneSelected) context.getColorAttr("colorPrimary") else 0
        )

        rowNone.setOnClickListener {
            onSelected(null)
            dialog.dismiss()
        }

        dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.sub_setting_tab_icon)
            .setIcon(RemixR.drawable.rmx_apps_line)
            .setView(dialogView)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        WindowBlurUtils.applyWindowBlur(dialog.window)
        dialog.show()
        return dialog
    }
}
