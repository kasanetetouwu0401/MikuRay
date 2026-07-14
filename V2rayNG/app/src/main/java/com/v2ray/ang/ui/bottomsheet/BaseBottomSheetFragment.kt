package com.v2ray.ang.ui.bottomsheet

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.v2ray.ang.R
import com.v2ray.ang.util.WindowBlurUtils
import com.v2ray.ang.util.getColorAttr

abstract class BaseBottomSheetFragment : BottomSheetDialogFragment() {

    override fun onStart() {
        super.onStart()
        val sheetDialog = dialog as? BottomSheetDialog ?: return

        val bgColor = requireContext().getColorAttr(R.attr.colorBg)

        sheetDialog.window?.let { window ->
            WindowBlurUtils.applyWindowBlur(window)
            
            // 1. Wajib: Matikan batas window agar layout bisa tembus ke belakang area gestur
            WindowCompat.setDecorFitsSystemWindows(window, false)
            
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
            
            // 2. Ubah ini jadi transparan, biarkan background dari bottomSheet yang ambil alih
            window.navigationBarColor = Color.TRANSPARENT 
        }
        
        val bottomSheet = sheetDialog.findViewById<android.view.View>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return

        bottomSheet.backgroundTintList = ColorStateList.valueOf(bgColor)
        bottomSheet.clipToOutline = true

        sheetDialog.behavior.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }

        ViewCompat.setOnApplyWindowInsetsListener(bottomSheet) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val screenHeight = view.resources.displayMetrics.heightPixels
            val margin = (8 * view.resources.displayMetrics.density).toInt()

            sheetDialog.behavior.maxHeight = screenHeight - systemBars.top - margin

            // 3. PERBAIKAN: Kembalikan padding bawah yang terhapus agar background memanjang menyentuh dasar layar
            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                systemBars.bottom 
            )

            insets
        }
    }
}
