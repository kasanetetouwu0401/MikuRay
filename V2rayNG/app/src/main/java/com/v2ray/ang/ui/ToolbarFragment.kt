package com.v2ray.ang.ui

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.v2ray.ang.R

/**
 * Base fragment yang menyediakan Toolbar (dan opsional CollapsingToolbarLayout)
 * untuk semua fragment di-host MainActivity — pola yang sama dengan NekoBox.
 *
 * Setiap fragment layout WAJIB memiliki view dengan id `R.id.toolbar`,
 * dan opsional `R.id.collapsing_toolbar` jika butuh efek collapse.
 */
open class ToolbarFragment : Fragment {

    constructor() : super()
    constructor(contentLayoutId: Int) : super(contentLayoutId)

    protected lateinit var toolbar: Toolbar
    protected var collapsingToolbar: CollapsingToolbarLayout? = null

    protected var pendingTitle: CharSequence = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbar = view.findViewById(R.id.toolbar)
        collapsingToolbar = view.findViewById(R.id.collapsing_toolbar)

        if (collapsingToolbar != null) {
            collapsingToolbar!!.title = pendingTitle
        } else {
            toolbar.title = pendingTitle
        }

        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_24dp)
        toolbar.setNavigationOnClickListener {
            if (parentFragmentManager.backStackEntryCount > 0) {
                parentFragmentManager.popBackStack()
            } else if (isAdded) {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    protected fun setupToolbar(showHomeAsUp: Boolean = true, title: CharSequence = "") {
        pendingTitle = title
        if (::toolbar.isInitialized) {
            if (collapsingToolbar != null) {
                collapsingToolbar!!.title = title
            } else {
                toolbar.title = title
            }
        }
    }

    open fun onBackPressed(): Boolean = false
    open fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = false
}
